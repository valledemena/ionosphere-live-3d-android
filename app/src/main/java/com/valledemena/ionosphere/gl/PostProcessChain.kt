package com.valledemena.ionosphere.gl

import android.opengl.GLES20
import android.opengl.GLES30
import com.valledemena.ionosphere.render.BloomPass
import com.valledemena.ionosphere.render.GodRaysPass
import com.valledemena.ionosphere.render.ToneMapPass

/**
 * Slice C (R4 ii): cadena de post-proceso real.
 *
 * Secuencia por frame:
 *   scene HDR -> bloom (umbral + horizontal + vertical) -> ACES tone map
 *   -> god-rays post-ACES -> default framebuffer.
 *
 * Todo el estado GL que toca se restaura en [render]; el host recupera
 * depth test y viewport on-screen.
 */
class PostProcessChain(
    private val width: Int,
    private val height: Int,
    private val halfFloat: Boolean,
) {
    private var quadVao = 0
    private var quadVbo = 0

    private var thresholdProgram = 0
    private var horizontalProgram = 0
    private var verticalProgram = 0
    private var toneProgram = 0
    private var godRaysProgram = 0

    private var bloomFboA = 0
    private var bloomTexA = 0
    private var bloomFboB = 0
    private var bloomTexB = 0
    private var ldrFbo = 0
    private var ldrTex = 0

    private val bloomWidth = maxOf(1, width / 2)
    private val bloomHeight = maxOf(1, height / 2)

    private var uThreshold = -1
    private var uBlurDirection = -1
    private var uToneScene = -1
    private var uToneBloom = -1
    private var uToneBloomStrength = -1
    private var uToneExposure = -1
    private var uRaysScene = -1
    private var uRaysSun = -1
    private var uRaysAspect = -1
    private var uRaysDecay = -1
    private var uRaysMaxRadius = -1
    private var uRaysGain = -1

    private var ready = false

    /** Ganancia del bloom en el tone map; 0 lo apaga para la medicion W11. */
    var bloomStrength: Float = BloomPass.STRENGTH

    fun isReady(): Boolean = ready

    fun init() {
        try {
            val vao = IntArray(1)
            GLES30.glGenVertexArrays(1, vao, 0)
            quadVao = vao[0]
            GLES30.glBindVertexArray(quadVao)
            val verts = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
            val buffers = IntArray(1)
            GLES20.glGenBuffers(1, buffers, 0)
            quadVbo = buffers[0]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
            GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                verts.size * 4,
                verts.toFloatBuffer(),
                GLES20.GL_STATIC_DRAW,
            )
            GLES20.glEnableVertexAttribArray(0)
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            GLES30.glBindVertexArray(0)

            thresholdProgram = ShaderProgram.create(QUAD_VERT, BloomPass.THRESHOLD_FRAGMENT)
            horizontalProgram = ShaderProgram.create(QUAD_VERT, BloomPass.HORIZONTAL_FRAGMENT)
            verticalProgram = ShaderProgram.create(QUAD_VERT, BloomPass.VERTICAL_FRAGMENT)
            toneProgram = ShaderProgram.create(QUAD_VERT, ToneMapPass.FRAGMENT)
            godRaysProgram = ShaderProgram.create(QUAD_VERT, GodRaysPass.FRAGMENT)

            uThreshold = GLES20.glGetUniformLocation(thresholdProgram, "uThreshold")
            uBlurDirection = GLES20.glGetUniformLocation(horizontalProgram, "uDirection")
            uToneScene = GLES20.glGetUniformLocation(toneProgram, "uScene")
            uToneBloom = GLES20.glGetUniformLocation(toneProgram, "uBloom")
            uToneBloomStrength = GLES20.glGetUniformLocation(toneProgram, "uBloomStrength")
            uToneExposure = GLES20.glGetUniformLocation(toneProgram, "uExposure")
            uRaysScene = GLES20.glGetUniformLocation(godRaysProgram, "uScene")
            uRaysSun = GLES20.glGetUniformLocation(godRaysProgram, "uSunScreen")
            uRaysAspect = GLES20.glGetUniformLocation(godRaysProgram, "uAspect")
            uRaysDecay = GLES20.glGetUniformLocation(godRaysProgram, "uDecay")
            uRaysMaxRadius = GLES20.glGetUniformLocation(godRaysProgram, "uMaxRadius")
            uRaysGain = GLES20.glGetUniformLocation(godRaysProgram, "uGain")

            val a = makeTarget(bloomWidth, bloomHeight)
            bloomFboA = a[0]
            bloomTexA = a[1]
            val b = makeTarget(bloomWidth, bloomHeight)
            bloomFboB = b[0]
            bloomTexB = b[1]
            val l = makeTarget(width, height)
            ldrFbo = l[0]
            ldrTex = l[1]

            ready = bloomFboA != 0 && bloomFboB != 0 && ldrFbo != 0
        } catch (e: Throwable) {
            ready = false
            release()
        }
    }

    private fun makeTarget(w: Int, h: Int): IntArray {
        val f = IntArray(1)
        GLES30.glGenFramebuffers(1, f, 0)
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        val tex = t[0]
        val format = if (halfFloat) GLES30.GL_RGBA16F else GLES30.GL_RGBA8
        val type = if (halfFloat) GLES30.GL_HALF_FLOAT else GLES20.GL_UNSIGNED_BYTE
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, format, w, h, 0,
            GLES20.GL_RGBA, type, null,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, f[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, tex, 0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
            GLES30.glDeleteFramebuffers(1, f, 0)
            return intArrayOf(0, 0)
        }
        return intArrayOf(f[0], tex)
    }

    /**
     * Pinta la cadena completa. [sunU]/[sunV] es la posicion del sol en UV
     * (0..1); cuando [godRays] es false el pase se omite (CPU gate).
     */
    fun render(sceneTex: Int, sunU: Float, sunV: Float, godRays: Boolean) {
        if (!ready) return
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES30.glBindVertexArray(quadVao)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboA)
        GLES20.glViewport(0, 0, bloomWidth, bloomHeight)
        GLES20.glUseProgram(thresholdProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(thresholdProgram, "uScene"), 0)
        GLES20.glUniform1f(uThreshold, BloomPass.THRESHOLD)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboB)
        GLES20.glViewport(0, 0, bloomWidth, bloomHeight)
        GLES20.glUseProgram(horizontalProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomTexA)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(horizontalProgram, "uScene"), 0)
        val horizontal = BloomPass.horizontalDirection(1f / bloomWidth)
        GLES20.glUniform2f(uBlurDirection, horizontal[0], horizontal[1])
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboA)
        GLES20.glViewport(0, 0, bloomWidth, bloomHeight)
        GLES20.glUseProgram(verticalProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomTexB)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(verticalProgram, "uScene"), 0)
        val vertical = BloomPass.verticalDirection(1f / bloomHeight)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(verticalProgram, "uDirection"), vertical[0], vertical[1])
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, ldrFbo)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(toneProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTex)
        GLES20.glUniform1i(uToneScene, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomTexA)
        GLES20.glUniform1i(uToneBloom, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1f(uToneBloomStrength, bloomStrength)
        GLES20.glUniform1f(uToneExposure, 1.12f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)

        if (godRays) {
            GLES30.glBindVertexArray(quadVao)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glUseProgram(godRaysProgram)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ldrTex)
            GLES20.glUniform1i(uRaysScene, 0)
            GLES20.glUniform2f(uRaysSun, sunU, sunV)
            GLES20.glUniform1f(uRaysAspect, width.toFloat() / height.toFloat())
            GLES20.glUniform1f(uRaysDecay, GodRaysPass.DECAY)
            GLES20.glUniform1f(uRaysMaxRadius, GodRaysPass.MAX_RADIUS)
            GLES20.glUniform1f(uRaysGain, GodRaysPass.GAIN)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glBindVertexArray(0)
            GLES20.glUseProgram(0)
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, ldrFbo)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, width, height)
            GLES30.glBlitFramebuffer(
                0, 0, width, height, 0, 0, width, height,
                GLES20.GL_COLOR_BUFFER_BIT, GLES20.GL_NEAREST,
            )
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 0)
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun release() {
        if (bloomTexA != 0) GLES20.glDeleteTextures(1, intArrayOf(bloomTexA), 0)
        if (bloomTexB != 0) GLES20.glDeleteTextures(1, intArrayOf(bloomTexB), 0)
        if (ldrTex != 0) GLES20.glDeleteTextures(1, intArrayOf(ldrTex), 0)
        if (bloomFboA != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(bloomFboA), 0)
        if (bloomFboB != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(bloomFboB), 0)
        if (ldrFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(ldrFbo), 0)
        if (quadVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        if (quadVao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(quadVao), 0)
        if (thresholdProgram != 0) GLES20.glDeleteProgram(thresholdProgram)
        if (horizontalProgram != 0) GLES20.glDeleteProgram(horizontalProgram)
        if (verticalProgram != 0) GLES20.glDeleteProgram(verticalProgram)
        if (toneProgram != 0) GLES20.glDeleteProgram(toneProgram)
        if (godRaysProgram != 0) GLES20.glDeleteProgram(godRaysProgram)
        bloomTexA = 0
        bloomTexB = 0
        ldrTex = 0
        bloomFboA = 0
        bloomFboB = 0
        ldrFbo = 0
        quadVbo = 0
        quadVao = 0
        thresholdProgram = 0
        horizontalProgram = 0
        verticalProgram = 0
        toneProgram = 0
        godRaysProgram = 0
        ready = false
    }

    private companion object {
        private val QUAD_VERT = """
            precision highp float;
            layout(location = 0) in vec2 aPos;
            out vec2 vUv;
            void main() {
                vUv = aPos * 0.5 + vec2(0.5);
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """.trimIndent()
    }
}

private fun FloatArray.toFloatBuffer(): java.nio.FloatBuffer =
    java.nio.ByteBuffer.allocateDirect(size * 4)
        .order(java.nio.ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(this)
        .also { it.position(0) }

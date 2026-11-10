package com.valledemena.ionosphere.gl

import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log

/**
 * Slice A plumbing + Slice C resolver (R4 ii).
 *
 * Multisampled RGBA16F color renderbuffer + depth, resolved into a single
 * sample HDR texture consumed by the post chain (bloom -> ACES -> god-rays).
 *
 * The extension guard is functional: the host queries GL_EXTENSIONS at runtime
 * and this class picks RGBA16F when renderable, RGBA8 otherwise.
 */
class OffscreenSurface(
    private val width: Int,
    private val height: Int,
    private val samplesRequested: Int = 4,
) {
    private var fbo = 0
    private var colorMs = 0
    private var depthMs = 0
    private var resolveFbo = 0
    private var resolveColor = 0
    private var samplesUsed = 0
    private var ready = false
    private var resolved = false

    // Hook de medicion (temporal): limpieza por quad fullscreen.
    private var quadProgram = 0
    private var quadVao = 0
    private var quadVbo = 0
    private var aQuadPos = -1

    companion object {
        private const val TAG = "OffscreenSurface"
        private const val EXT_COLOR_BUFFER_FLOAT = "EXT_color_buffer_float"
        private const val EXT_COLOR_BUFFER_HALF_FLOAT = "EXT_color_buffer_half_float"

        /** Pure sample clamp, exercised by unit tests without an EGL context. */
        fun targetSamples(maxSamples: Int, requested: Int): Int =
            maxOf(1, minOf(requested, maxOf(1, maxSamples)))
    }

    fun width(): Int = width
    fun height(): Int = height

    fun supportsHalfFloatExtension(extensions: String): Boolean =
        extensions.contains(EXT_COLOR_BUFFER_FLOAT) || extensions.contains(EXT_COLOR_BUFFER_HALF_FLOAT)

    fun init(extensions: String = "") {
        val halfFloat = supportsHalfFloatExtension(extensions)
        val colorFormat = if (halfFloat) GLES30.GL_RGBA16F else GLES30.GL_RGBA8
        val colorType = if (halfFloat) GLES30.GL_HALF_FLOAT else GLES20.GL_UNSIGNED_BYTE
        try {
            val ms = IntArray(1).also { GLES20.glGetIntegerv(GLES30.GL_MAX_SAMPLES, it, 0) }
            samplesUsed = targetSamples(ms[0], samplesRequested)

            val f = IntArray(1)
            GLES30.glGenFramebuffers(1, f, 0)
            fbo = f[0]
            GLES30.glGenRenderbuffers(1, f, 0)
            colorMs = f[0]
            GLES30.glGenRenderbuffers(1, f, 0)
            depthMs = f[0]

            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, colorMs)
            GLES30.glRenderbufferStorageMultisample(
                GLES30.GL_RENDERBUFFER,
                samplesUsed,
                colorFormat,
                width,
                height,
            )
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthMs)
            GLES30.glRenderbufferStorageMultisample(
                GLES30.GL_RENDERBUFFER,
                samplesUsed,
                GLES30.GL_DEPTH24_STENCIL8,
                width,
                height,
            )

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glFramebufferRenderbuffer(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_RENDERBUFFER, colorMs,
            )
            GLES30.glFramebufferRenderbuffer(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
                GLES30.GL_RENDERBUFFER, depthMs,
            )
            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                Log.w(TAG, "FBO incompleto: 0x${Integer.toHexString(status)}")
                ready = false
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                return
            }
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            ready = true

            // Single-sample resolve target that bloom/ACES consume.
            GLES30.glGenFramebuffers(1, f, 0)
            resolveFbo = f[0]
            GLES20.glGenTextures(1, f, 0)
            resolveColor = f[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, resolveColor)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, colorFormat, width, height, 0,
                GLES20.GL_RGBA, colorType, null,
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, resolveFbo)
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, resolveColor, 0,
            )
            val resolveStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            resolved = resolveStatus == GLES30.GL_FRAMEBUFFER_COMPLETE
            if (!resolved) {
                Log.w(TAG, "Resolve FBO incompleto: 0x${Integer.toHexString(resolveStatus)}")
            }
            initClearQuad()
        } catch (e: Throwable) {
            Log.w(TAG, "FBO init fallo: $e")
            ready = false
            resolved = false
        }
    }

    private fun initClearQuad() {
        quadProgram = ShaderProgram.create(
            "in vec2 aPos;\nvoid main() { gl_Position = vec4(aPos, 0.0, 1.0); }\n",
            "precision mediump float;\nout vec4 fragColor;\n" +
                "void main() { fragColor = vec4(0.0, 0.0, 0.0, 1.0); }\n",
        )
        if (quadProgram == 0) return
        aQuadPos = GLES20.glGetAttribLocation(quadProgram, "aPos")
        val v = java.nio.ByteBuffer.allocateDirect(8 * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        v.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).rewind()
        val ids = IntArray(2)
        GLES30.glGenVertexArrays(1, ids, 0)
        quadVao = ids[0]
        GLES20.glGenBuffers(1, ids, 0)
        quadVbo = ids[0]
        GLES30.glBindVertexArray(quadVao)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 32, v, GLES20.GL_STATIC_DRAW)
        GLES20.glEnableVertexAttribArray(aQuadPos)
        GLES20.glVertexAttribPointer(aQuadPos, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Hook de medicion (temporal): limpia el color del FBO MSAA dibujando un
     * quad fullscreen opaco en vez de confiar en glClear.
     */
    fun clearColorByQuad() {
        if (!ready || quadProgram == 0) return
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        val blendOn = GLES20.glIsEnabled(GLES20.GL_BLEND)
        val depthOn = GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST)
        val cullOn = GLES20.glIsEnabled(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(false)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(quadProgram)
        GLES30.glBindVertexArray(quadVao)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
        GLES20.glUseProgram(0)
        GLES20.glDepthMask(true)
        if (blendOn) GLES20.glEnable(GLES20.GL_BLEND)
        if (depthOn) GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        if (cullOn) GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    fun isReady(): Boolean = ready
    fun resolvedReady(): Boolean = resolved
    fun samples(): Int = samplesUsed

    fun bindOffscreen() {
        if (ready) GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
    }

    /** MSAA resolve into the single-sample HDR texture. */
    fun resolveColor() {
        if (!ready || !resolved) return
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, fbo)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, resolveFbo)
        GLES30.glBlitFramebuffer(
            0, 0, width, height, 0, 0, width, height,
            GLES20.GL_COLOR_BUFFER_BIT, GLES30.GL_LINEAR,
        )
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 0)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
    }

    fun resolvedTexture(): Int = if (resolved) resolveColor else 0

    /** Direct blit used only when the post chain is unavailable. */
    fun blitToDefault(width: Int, height: Int) {
        if (!ready) return
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, fbo)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
        GLES30.glBlitFramebuffer(
            0, 0, width, height, 0, 0, width, height,
            GLES20.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST,
        )
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 0)
    }

    /** Frees every GL object; called on every resize to avoid leaking FBOs. */
    fun release() {
        if (quadProgram != 0) GLES20.glDeleteProgram(quadProgram)
        if (quadVao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(quadVao), 0)
        if (quadVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
        quadProgram = 0
        quadVao = 0
        quadVbo = 0
        if (resolveColor != 0) GLES20.glDeleteTextures(1, intArrayOf(resolveColor), 0)
        if (resolveFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(resolveFbo), 0)
        if (colorMs != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(colorMs), 0)
        if (depthMs != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(depthMs), 0)
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        resolveColor = 0
        resolveFbo = 0
        colorMs = 0
        depthMs = 0
        fbo = 0
        ready = false
        resolved = false
    }
}

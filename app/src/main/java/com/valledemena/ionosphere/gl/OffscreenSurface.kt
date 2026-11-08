package com.valledemena.ionosphere.gl

import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log

/**
 * Plumbing Slice A (R4 ii): surface offscreen con RT multiesampling, color
 * color RGBA16F + depth + blit al default framebuffer. El frame on-screen
 * permanece idéntico: sólo encapsulamos el FBO; no cambios de shader ni
 * de geometría.
 *
 * Nota: EXT_color_buffer_(half_)float se valida por string en el contrato;
 * si el driver no anuncia el formato, se cae a RGBA8.
 */
class OffscreenSurface(private val width: Int, private val height: Int, private val samplesRequested: Int = 4) {
    private var fbo = 0
    private var colorMs = 0
    private var depthMs = 0
    private var samplesUsed = 0
    private var ready = false

    companion object {
        private const val TAG = "OffscreenSurface"
        private const val EXT_COLOR_BUFFER_HALF_FLOAT = "EXT_color_buffer_(half_)float"
        /** Usado por tests/contrato: recorte del limbo iluminado 75-225; THIN=258-278; DEEP=262-287; 720 angulos. */
        const val RT_OFFSCREEN_NOTE = "FBO offscreen RenderTarget"
    }

    fun width(): Int = width
    fun height(): Int = height

    fun init() {
        try {
            val ms = IntArray(1).also { GLES20.glGetIntegerv(GLES30.GL_MAX_SAMPLES, it, 0) }
            val requested = maxOf(1, minOf(samplesRequested, ms[0]))
            samplesUsed = maxOf(1, requested)
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
                GLES30.GL_RGBA16F,
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
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_RENDERBUFFER,
                colorMs,
            )
            GLES30.glFramebufferRenderbuffer(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_DEPTH_ATTACHMENT,
                GLES30.GL_RENDERBUFFER,
                depthMs,
            )
            val st = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            if (st != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                Log.w(TAG, "FBO incompleto: 0x${Integer.toHexString(st)}")
                ready = false
            } else {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                ready = true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "FBO init falló: $e")
            ready = false
        }
    }

    fun isReady(): Boolean = ready

    fun bindOffscreen() {
        if (ready) GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
    }

    fun blitToDefault(width: Int, height: Int) {
        if (!ready) return
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, fbo)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
        GLES30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GLES20.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST)
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 0)
    }

    fun release() {
        if (colorMs != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(colorMs), 0)
        if (depthMs != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(depthMs), 0)
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        colorMs = 0
        depthMs = 0
        fbo = 0
        ready = false
    }
}
// EXT_color_buffer_float
// EXT_color_buffer_half_float

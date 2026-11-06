package com.valledemena.ionosphere.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import com.valledemena.ionosphere.core.FramePacer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay

/**
 * Superficie GL con **render-on-demand**: no dibuja en bucle a pantalla completa.
 *
 * - `RENDERMODE_WHEN_DIRTY`: solo se dibuja cuando alguien lo pide.
 * - Un `Choreographer.FrameCallback` pide frames con un techo de
 *   [Rotation.TARGET_FPS] mientras la escena está viva (`start()`).
 * - `stop()` corta el callback: en background no se dibuja ni se consume batería.
 * - El contexto EGL se pide 3.1 y se acepta 3.0 como respaldo (el cuerpo de los
 *   shaders es el mismo; la cabecera la elige [ShaderProgram]).
 */
class GlobeSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    private val renderer = GlobeRenderer(context)
    private val choreographer = Choreographer.getInstance()
    private var running = false

    private val pacer = FramePacer()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (pacer.onVsync(frameTimeNanos)) requestRender()
            choreographer.postFrameCallback(this)  // sin delay: en la rejilla de vsync
        }
    }

    init {
        setEGLContextFactory(Es31ContextFactory())
        setEGLConfigChooser(8, 8, 8, 0, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** Empieza a pedir frames. Idempotente. */
    fun start() {
        if (running) return
        running = true
        pacer.reset()
        choreographer.postFrameCallback(frameCallback)
    }

    /** Deja de pedir frames. Idempotente. */
    fun stop() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    fun framesDrawn(): Int = renderer.framesDrawn

    fun textureSize(): IntArray = renderer.loadedTextureSize

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    /**
     * Contexto EGL 3.1 con respaldo a 3.0. Pedir 3.1 explícitamente evita
     * depender del valor por defecto del driver.
     */
    private class Es31ContextFactory : EGLContextFactory {

        override fun createContext(egl: EGL10, display: EGLDisplay, config: EGLConfig): EGLContext {
            val requested = create(egl, display, config, major = 3, minor = 1)
            if (requested !== EGL10.EGL_NO_CONTEXT && requested != null) return requested

            Log.w(TAG, "Contexto GLES 3.1 no disponible; se intenta 3.0")
            val fallback = create(egl, display, config, major = 3, minor = 0)
            return fallback ?: EGL10.EGL_NO_CONTEXT
        }

        private fun create(
            egl: EGL10,
            display: EGLDisplay,
            config: EGLConfig,
            major: Int,
            minor: Int,
        ): EGLContext? {
            val attribs = intArrayOf(
                EGL_CONTEXT_MAJOR_VERSION_KHR, major,
                EGL_CONTEXT_MINOR_VERSION_KHR, minor,
                EGL10.EGL_NONE,
            )
            val context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attribs)
            return if (context == EGL10.EGL_NO_CONTEXT) null else context
        }

        override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
            egl.eglDestroyContext(display, context)
        }
    }

    private companion object {
        private const val TAG = "GlobeSurfaceView"
        private const val EGL_CONTEXT_MAJOR_VERSION_KHR = 0x3098
        private const val EGL_CONTEXT_MINOR_VERSION_KHR = 0x30FB
    }
}

package com.valledemena.ionosphere.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import com.valledemena.ionosphere.core.FramePacer
import com.valledemena.ionosphere.data.model.IonosondeData
import kotlin.math.hypot
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
    atmoSteps: Int? = null,
    atmoLightSteps: Int? = null,
) : GLSurfaceView(context, attrs) {

    private val renderer = GlobeRenderer(
        context,
        atmoStepsOverride = atmoSteps,
        atmoLightStepsOverride = atmoLightSteps,
        measure = MeasureConfig.from(context),
    )
    private val choreographer = Choreographer.getInstance()
    private var running = false

    private val pacer = FramePacer()

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                renderer.onCameraScale(detector.scaleFactor)
                return true
            }

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                renderer.onCameraStopInertia()
                return true
            }
        },
    )
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragPx = 0f
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null

    /** R3.4: null = tap fuera / sin estacion. Se entrega en el hilo de UI. */
    var onStationPick: ((String?) -> Unit)? = null

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
        renderer.onStationPick = { code -> post { onStationPick?.invoke(code) } }
    }

    /** R3.4: alimenta la capa de puntos; no hay fetch propio. */
    fun setStations(data: IonosondeData?) {
        renderer.setStations(data)
    }

    /** R3.4: valor crudo del slider; el clamp [2,30] vive en el renderer. */
    fun setPointSize(raw: Float) {
        renderer.setPointSize(raw)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!renderer.cameraInteractive) return super.onTouchEvent(event)

        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                downX = event.x
                downY = event.y
                dragPx = 0f
                dragging = true
                renderer.onCameraStopInertia()
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                dragging = false
                dragPx = Float.MAX_VALUE
                velocityTracker?.clear()
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    dragPx = hypot(event.x - downX, event.y - downY)
                }
                if (dragging && !scaleDetector.isInProgress && event.pointerCount == 1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    if (dx != 0f || dy != 0f) {
                        renderer.onCameraDrag(dx, dy, height)
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    velocityTracker?.addMovement(event)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                dragging = false
            }
            MotionEvent.ACTION_UP -> {
                if (dragging && !scaleDetector.isInProgress) {
                    velocityTracker?.let { tracker ->
                        tracker.computeCurrentVelocity(1000)
                        renderer.onCameraFling(tracker.xVelocity, tracker.yVelocity, height)
                    }
                }
                if (dragPx <= 6f && !scaleDetector.isInProgress) {
                    renderer.requestPick(event.x, event.y, width, height)
                }
                dragging = false
                dragPx = 0f
                velocityTracker?.recycle()
                velocityTracker = null
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                dragPx = 0f
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        stop()
        velocityTracker?.recycle()
        velocityTracker = null
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

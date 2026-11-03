package com.valledemena.ionosphere.core

/**
 * Decide en qué vsyncs hay que dibujar para no superar un techo de FPS.
 *
 * La fuente de tiempo es la **rejilla de vsync** ([frameTimeNanos]), no el reloj
 * de pared y no un contador. Esto importa por dos motivos:
 *  - contar callbacks con un N fijo (p. ej. "cada 2º callback") daría 45 o 60 fps
 *    en un panel de 90/120 Hz y rompería el techo declarado;
 *  - usar `uptimeMillis` re-introduce el offset δ entre el vsync que despacha
 *    el callback y el momento en que lo re-programas — el mismo bug, con otras
 *    letras.
 *
 * El período de vsync se estima como el **mínimo observado**: un vsync perdido
 * no debe inflar el período.
 */
class FramePacer(
    private val targetFps: Int = Rotation.TARGET_FPS,
    initialVsyncPeriodNanos: Long = DEFAULT_VSYNC_PERIOD_NANOS,
) {

    var vsyncPeriodNanos: Long = initialVsyncPeriodNanos
        private set

    private var lastFrameTimeNanos = 0L
    private var lastRenderFrameNanos = 0L

    var renders = 0
        private set

    /**
     * Llamado una vez por cada callback de `Choreographer` (cada vsync).
     * Devuelve `true` si toca dibujar en este vsync.
     */
    fun onVsync(frameTimeNanos: Long): Boolean {
        require(targetFps > 0) { "targetFps debe ser > 0" }
        if (lastFrameTimeNanos > 0L) {
            val delta = frameTimeNanos - lastFrameTimeNanos
            if (delta in MIN_VSYNC_NANOS..MAX_VSYNC_NANOS && delta < vsyncPeriodNanos) {
                vsyncPeriodNanos = delta
            }
        }
        lastFrameTimeNanos = frameTimeNanos

        val shouldRender =
            lastRenderFrameNanos == 0L ||
                frameTimeNanos - lastRenderFrameNanos >=
                    Rotation.renderThresholdNanos(vsyncPeriodNanos, targetFps)

        if (shouldRender) {
            lastRenderFrameNanos = frameTimeNanos
            renders++
        }
        return shouldRender
    }

    /** Restablece el estado. Llamar al (re)arrancar la animación. */
    fun reset() {
        lastFrameTimeNanos = 0L
        lastRenderFrameNanos = 0L
        vsyncPeriodNanos = DEFAULT_VSYNC_PERIOD_NANOS
        renders = 0
    }

    companion object {
        const val DEFAULT_VSYNC_PERIOD_NANOS = 16_666_666L  // 60 Hz
        const val MIN_VSYNC_NANOS = 4_000_000L              // 250 Hz
        const val MAX_VSYNC_NANOS = 40_000_000L             // 25 Hz
    }
}

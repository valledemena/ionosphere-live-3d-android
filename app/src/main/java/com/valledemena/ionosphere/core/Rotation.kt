package com.valledemena.ionosphere.core

/**
 * Reloj de rotación del globo, independiente de Android para poder testearse en JVM.
 *
 * R1 no dibuja "todo lo que puede": dibuja lo que necesita, a un ritmo fijado.
 * Esa decisión (render-on-demand con techo de FPS) es arquitectura, no una
 * optimización posterior: `ARCHITECTURE_DECISION.md` §3 y el gate de energía de
 * `TEST_GATES.md` v1.1.
 */
object Rotation {

    /** Una vuelta completa cada 60 s: lento, legible y barato en batería. */
    const val DEGREES_PER_SECOND: Float = 6f

    /** Techo de refresco mientras el globo gira. */
    const val TARGET_FPS: Int = 30

    /** Ángulo en grados, siempre en [0, 360). `elapsedMs` negativo se trata como 0. */
    fun angleDegrees(elapsedMs: Long, degreesPerSecond: Float = DEGREES_PER_SECOND): Float {
        val safeElapsed = if (elapsedMs <= 0L) 0L else elapsedMs
        val raw = (safeElapsed / 1000.0) * degreesPerSecond
        val wrapped = raw % 360.0
        return wrapped.toFloat()
    }

    /** Intervalo mínimo entre frames para un techo de FPS dado. */
    fun frameIntervalMs(targetFps: Int = TARGET_FPS): Long {
        require(targetFps > 0) { "targetFps debe ser > 0" }
        return 1000L / targetFps
    }

    /**
     * ¿Toca dibujar? Con `lastFrameMs == 0` (primer frame) siempre sí.
     * Evita el bucle a pantalla completa cuando el dispositivo va sobrado.
     */
    fun shouldRenderNow(
        lastFrameMs: Long,
        nowMs: Long,
        targetFps: Int = TARGET_FPS,
    ): Boolean {
        if (lastFrameMs <= 0L) return true
        return (nowMs - lastFrameMs) >= frameIntervalMs(targetFps)
    }
}

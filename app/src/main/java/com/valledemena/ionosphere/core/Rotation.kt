package com.valledemena.ionosphere.core

/**
 * Reloj de rotación del globo, independiente de Android para poder testearse en JVM.
 *
 * R1 no dibuja "todo lo que puede": dibuja lo que necesita, a un ritmo fijado.
 * Esa decisión (render-on-demand con techo de FPS) es arquitectura, no una
 * optimización posterior: `ARCHITECTURE_DECISION.md` §3 y el gate de energía de
 * `TEST_GATES.md` v1.1.
 *
 * El ritmo lo decide [FramePacer] sobre la rejilla de vsync; aquí queda solo la
 * aritmética del techo y la del ángulo.
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

    /** Igual que [frameIntervalMs] pero en nanosegundos, que es la unidad del vsync. */
    fun frameIntervalNanos(targetFps: Int = TARGET_FPS): Long {
        require(targetFps > 0) { "targetFps debe ser > 0" }
        return 1_000_000_000L / targetFps
    }

    /**
     * Umbral, en nanosegundos, a partir del cual toca dibujar.
     *
     * Se resta **medio vsync** al intervalo ideal a propósito. La división entera
     * trunca y deja el intervalo justo por encima del múltiplo exacto del vsync:
     * a 60 Hz, `1e9/30 = 33 333 333 ns` frente a `2 × 16 666 666 = 33 333 332 ns`.
     * Sin ese margen de 1 ns el segundo vsync se pierde y el frame cae al tercero
     * (50 ms): eso es exactamente el fallo que tuvo R1, con 20 fps reales sobre 30
     * declarados. El medio vsync absorbe además el offset δ entre el vsync que
     * despacha el callback y el momento en que se re-programa.
     */
    fun renderThresholdNanos(
        vsyncPeriodNanos: Long,
        targetFps: Int = TARGET_FPS,
    ): Long {
        require(vsyncPeriodNanos > 0L) { "vsyncPeriodNanos debe ser > 0" }
        val interval = frameIntervalNanos(targetFps)
        val tolerance = vsyncPeriodNanos / 2
        return if (interval > tolerance) interval - tolerance else 1L
    }
}

package com.valledemena.ionosphere.core

/**
 * Reloj de rotación cuya base sobrevive a la recreación de la superficie GL.
 *
 * `onSurfaceCreated` puede llamarse más de una vez (contexto perdido, retorno
 * del background): en ese caso el ángulo **no** se reinicia a 0°, que es lo que
 * pasaría si `startedAtMs` se reasignara incondicionalmente.
 */
class RotationClock(
    private val degreesPerSecond: Float = Rotation.DEGREES_PER_SECOND,
) {
    private var baseMs = 0L
    var surfaceCreations = 0
        private set

    /** Idempotente en el sentido de «solo la primera cuenta». */
    fun onSurfaceCreated(nowMs: Long) {
        surfaceCreations++
        if (baseMs == 0L) baseMs = nowMs
    }

    fun angleAt(nowMs: Long): Float = Rotation.angleDegrees(nowMs - baseMs, degreesPerSecond)
}

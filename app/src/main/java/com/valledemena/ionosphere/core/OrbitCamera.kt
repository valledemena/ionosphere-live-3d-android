package com.valledemena.ionosphere.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cámara orbital del globo de producción (R4-D).
 *
 * Mira siempre al origen: el pan está deshabilitado por diseño. Un dedo
 * arrastra (rotación) y el pinch cambia la distancia; la inercia se decae con
 * el factor exacto del pin web (0.06). Las constantes están congeladas por la
 * ANCLA-D: fov 42, distancia inicial 3.3 R_E, minDistance 1.12 y maxDistance 30.
 */
class OrbitCamera(initialDistance: Float = INITIAL_DISTANCE) {

    private var yawRad = 0f
    private var pitchRad = 0f
    private var distance = initialDistance.coerceIn(minDistance, maxDistance)
    private var yawVelocity = 0f
    private var pitchVelocity = 0f

    /** El plan pide «pan disabled»: no hay traslación de target. */
    val panEnabled = false

    @Synchronized
    fun dragFromPixels(dxPx: Float, dyPx: Float, viewportHeight: Int) {
        val height = viewportHeight.coerceAtLeast(1).toFloat()
        yawRad = wrap(yawRad - dxPx / height * TWO_PI)
        pitchRad = (pitchRad - dyPx / height * PI.toFloat())
            .coerceIn(-MAX_PITCH_RAD, MAX_PITCH_RAD)
        yawVelocity = 0f
        pitchVelocity = 0f
    }

    @Synchronized
    fun flingFromPixels(
        xVelocityPxPerSecond: Float,
        yVelocityPxPerSecond: Float,
        viewportHeight: Int,
    ) {
        val height = viewportHeight.coerceAtLeast(1).toFloat()
        yawVelocity = -xVelocityPxPerSecond / height * TWO_PI
        pitchVelocity = -yVelocityPxPerSecond / height * PI.toFloat()
    }

    @Synchronized
    fun stopInertia() {
        yawVelocity = 0f
        pitchVelocity = 0f
    }

    /** Factor de pinch: > 1 acerca (divide la distancia), < 1 aleja. */
    @Synchronized
    fun scaleBy(factor: Float) {
        if (!factor.isFinite() || factor <= 0f) return
        distance = (distance / factor).coerceIn(minDistance, maxDistance)
    }

    @Synchronized
    fun update(dtSeconds: Float) {
        if (!dtSeconds.isFinite() || dtSeconds <= 0f) return

        yawRad = wrap(yawRad + yawVelocity * dtSeconds)
        val nextPitch = pitchRad + pitchVelocity * dtSeconds
        pitchRad = nextPitch.coerceIn(-MAX_PITCH_RAD, MAX_PITCH_RAD)
        if (nextPitch != pitchRad) pitchVelocity = 0f

        val decay = 1f - damping
        yawVelocity *= decay
        pitchVelocity *= decay
        if (abs(yawVelocity) < VELOCITY_EPSILON) yawVelocity = 0f
        if (abs(pitchVelocity) < VELOCITY_EPSILON) pitchVelocity = 0f
    }

    /** Escribe el ojo en coordenadas de mundo (globo unitario, +Y arriba). */
    @Synchronized
    fun writeEye(out: FloatArray) {
        require(out.size >= 3) { "out debe tener al menos 3 componentes" }
        val cosPitch = cos(pitchRad)
        out[0] = distance * cosPitch * sin(yawRad)
        out[1] = distance * sin(pitchRad)
        out[2] = distance * cosPitch * cos(yawRad)
    }

    @Synchronized
    fun distance(): Float = distance

    @Synchronized
    fun pitchDegrees(): Float = Math.toDegrees(pitchRad.toDouble()).toFloat()

    @Synchronized
    fun yawVelocity(): Float = yawVelocity

    private fun wrap(angle: Float): Float {
        var wrapped = angle % TWO_PI
        if (wrapped < 0f) wrapped += TWO_PI
        return wrapped
    }

    companion object {
        const val INITIAL_DISTANCE = 3.3f

        private const val minDistance = 1.12f
        private const val maxDistance = 30f
        private const val damping = 0.06f

        private val MAX_PITCH_RAD = Math.toRadians(80.0).toFloat()
        private val TWO_PI = (2.0 * PI).toFloat()
        private const val VELOCITY_EPSILON = 0.001f
    }
}

package com.valledemena.ionosphere.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Direccion del Sol en el marco de escena de la textura.
 *
 * Misma formula de baja precision que el oraculo web (`sunDirScene`): el
 * resultado apunta del centro de la Tierra hacia el Sol y usa la convencion
 * de [SphereMesh]/[StationPoints] (lon 0 -> +X, lon 90E -> -Z, +Y norte).
 */
object SolarPosition {

    private const val DEG = PI / 180.0

    /** Vector unitario hacia el Sol para un instante Unix (milisegundos UTC). */
    fun directionAt(epochMillis: Long): FloatArray {
        val utcSeconds = epochMillis / 1000.0
        val j2000 = utcSeconds / 86400.0 - 10957.5
        val t = j2000 / 36525.0

        val meanLon = mod(280.46646 + 36000.76983 * t + 0.0003032 * t * t, 360.0)
        val meanAnomaly = mod(357.52911 + 35999.05029 * t - 0.0001537 * t * t, 360.0)
        val m = meanAnomaly * DEG
        val equation =
            (1.914602 - 0.004817 * t) * sin(m) +
                (0.019993 - 0.000101 * t) * sin(2.0 * m) +
                0.000289 * sin(3.0 * m)
        val eclipticLon = (meanLon + equation) * DEG
        val obliquity = (23.439291 - 0.0130042 * t) * DEG

        val x = cos(eclipticLon)
        val y = sin(eclipticLon) * cos(obliquity)
        val z = sin(eclipticLon) * sin(obliquity)

        val gmst = mod(
            280.46061837 + 360.98564736629 * j2000 + 0.000387933 * t * t,
            360.0,
        )
        val rotation = gmst * DEG
        val fx = x * cos(rotation) + y * sin(rotation)
        val fy = -x * sin(rotation) + y * cos(rotation)

        // Marco escena = (-motor.x, motor.y, -motor.z), con
        // motor = normalize(-fx, z, fy). Se evita pasar por lat/lon.
        val sx = fx
        val sy = z
        val sz = -fy
        val len = hypot(hypot(sx, sy), sz)
        return floatArrayOf(
            (sx / len).toFloat(),
            (sy / len).toFloat(),
            (sz / len).toFloat(),
        )
    }

    private fun mod(value: Double, modulus: Double): Double {
        val r = value % modulus
        return if (r < 0.0) r + modulus else r
    }
}

package com.valledemena.ionosphere.core.iono

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Modelo puro del volumen de densidad electronica, espejo de
 * `src/lib/iono/densityVolume.ts` del pin web `8cdc334`.
 *
 * Layout: `data[(altIdx * latRes + latIdx) * lonRes + lonIdx]`.
 * `lonIdx 0` = -180, `latIdx 0` = sur (-90), `altIdx 0` = 60 km.
 * `data` guarda log10(Ne) normalizado en [0,1]; `layer` guarda la rama
 * ganadora (0=D, 1=E, 2=F1, 3=F2).
 *
 * Sin GL, sin UI y sin red: solo aritmetica y datos planos.
 */
const val VOL_LON_RES = 72
const val VOL_LAT_RES = 72
const val VOL_ALT_RES = 48
const val VOL_ALT_MIN_KM = 60.0
const val VOL_ALT_MAX_KM = 500.0
const val VOL_LOG_MIN = 8.0
const val VOL_LOG_RANGE = 4.5
const val VOL_PEAK_FLOOR = 8.5

/** Volumen de densidad con arrays planos y layout del oraculo. */
data class DensityVolume(
    val lonRes: Int,
    val latRes: Int,
    val altRes: Int,
    val altMinKm: Double,
    val altMaxKm: Double,
    val data: DoubleArray,
    val layer: IntArray,
    val valid: Boolean,
    val timestamp: Long = 0L,
)

/** Indice lineal del layout `data[(a * H + y) * W + x]`. */
fun volumeIndex(x: Int, y: Int, a: Int, w: Int, h: Int): Int =
    (a * h + y) * w + x

/**
 * Altura de la muestra [i] en una malla de [n] niveles:
 * `altMin + (altMax - altMin) * (i / (n - 1))^1.5`.
 */
fun volumeAltKm(i: Int, n: Int, altMinKm: Double, altMaxKm: Double): Double {
    val f = if (n <= 1) 0.0 else i.toDouble() / (n - 1)
    return altMinKm + (altMaxKm - altMinKm) * f.pow(1.5)
}

private fun smoothstep(e0: Double, e1: Double, x: Double): Double {
    val t = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return t * t * (3.0 - 2.0 * t)
}

/**
 * Direccion unitaria en el marco del motor:
 * `theta = pi - radians(lon)`, no `radians(lon)`.
 *
 * Pines: `(0,0) -> (-1,0,0)`, `(0,90E) -> (0,0,1)`, polo -> `(0,1,0)`.
 */
fun densityDir(latDeg: Double, lonDeg: Double): DoubleArray {
    val phi = Math.toRadians(90.0 - latDeg)
    val theta = PI - Math.toRadians(lonDeg)
    val sn = sin(phi)
    return doubleArrayOf(
        sn * cos(theta),
        cos(phi),
        sn * sin(theta),
    )
}

/**
 * Atenua E/F1 de noche sobre una rejilla de perfiles, espejo funcional de
 * `applyDayNight` del oraculo.
 *
 * La web muta los perfiles y devuelve `void`; esta version devuelve una lista
 * nueva y deja intacta la entrada. `e = smoothstep(-0.05, 0.25, dot)`;
 * multiplica NmE/NmF1 y apaga los flags E/F1 si `e <= 0`. Una rejilla de
 * tamano incorrecto es un no-op.
 */
fun applyDayNight(
    profiles: List<LayerProfile>,
    lonRes: Int,
    latRes: Int,
    sunDir: DoubleArray,
): List<LayerProfile> {
    if (profiles.size != lonRes * latRes) return profiles

    val out = ArrayList<LayerProfile>(profiles.size)
    for (latIdx in 0 until latRes) {
        val latDeg = -90.0 + (180.0 * (latIdx + 0.5)) / latRes
        val phi = Math.toRadians(90.0 - latDeg)
        for (lonIdx in 0 until lonRes) {
            val lonDeg = -180.0 + (360.0 * (lonIdx + 0.5)) / lonRes
            val theta = PI - Math.toRadians(lonDeg)
            val cx = sin(phi) * cos(theta)
            val cy = cos(phi)
            val cz = sin(phi) * sin(theta)
            val e = smoothstep(
                -0.05,
                0.25,
                cx * sunDir[0] + cy * sunDir[1] + cz * sunDir[2],
            )
            val p = profiles[latIdx * lonRes + lonIdx]
            val hasE = if (e <= 0.0) false else p.hasE
            val hasF1 = if (e <= 0.0) false else p.hasF1
            out.add(
                p.copy(
                    nmE = p.nmE * e,
                    nmF1 = p.nmF1 * e,
                    hasE = hasE,
                    hasF1 = hasF1,
                ),
            )
        }
    }
    return out
}

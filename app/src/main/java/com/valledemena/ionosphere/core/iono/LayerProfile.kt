package com.valledemena.ionosphere.core.iono

import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Perfil de capas y constructor del volumen, espejo de
 * `src/lib/iono/layerProfile.ts` del pin web `8cdc334` (parte pura).
 *
 * `evalNeTotal` usa la combinacion del volumen: D con `cosChi > 0` y flare
 * externo, E/F1 sin atenuar (lo hace `applyDayNight`), F2 plena con puerta
 * `f2Floor`. `sanitizeB0` usa el default 100 del oraculo, no el 90 legacy de
 * ionomath.
 */

/** Nm = 1.24e10 * fo^2 (fo en MHz, Nm en m^-3). */
const val FO_TO_NM = 1.24e10

data class LayerProfile(
    val nmE: Double,
    val hmE: Double,
    val nmF1: Double,
    val hmF1: Double,
    val nmF2: Double,
    val hmF2: Double,
    val b0: Double,
    val b1: Double,
    val hasE: Boolean,
    val hasF1: Boolean,
    val hasF2: Boolean,
)

data class NeWinner(
    val ne: Double,
    /** 0=D, 1=E, 2=F1, 3=F2; -1 = ninguna rama. */
    val winner: Int,
)

fun blankProfile(): LayerProfile = LayerProfile(
    nmE = 0.0,
    hmE = 110.0,
    nmF1 = 0.0,
    hmF1 = 180.0,
    nmF2 = 0.0,
    hmF2 = 300.0,
    b0 = 100.0,
    b1 = 3.0,
    hasE = false,
    hasF1 = false,
    hasF2 = false,
)

fun sanitizeB0(value: Double): Double {
    if (!(value > 0.0)) return 100.0
    if (value < 20.0) return 20.0
    if (value > 300.0) return 300.0
    return value
}

fun sanitizeB1(value: Double): Double {
    if (!(value > 0.0)) return 3.0
    if (value < 1.7) return 1.7
    if (value > 4.0) return 4.0
    return value
}

/** Perfil desde frecuencias medidas (port de `profileFromStation`). */
fun profileFromFo(
    foE: Double,
    foF1: Double,
    foF2: Double,
    hmF2: Double,
    b0: Double,
    b1: Double,
    nmF2: Double = 0.0,
): LayerProfile {
    var p = blankProfile()
    if (foE > 0.0) {
        p = p.copy(
            nmE = FO_TO_NM * foE * foE,
            hmE = 110.0,
            hasE = true,
        )
    }
    if (foF1 > 0.0) {
        p = p.copy(
            nmF1 = FO_TO_NM * foF1 * foF1,
            hmF1 = if (hmF2 > 0.0) max(150.0, 0.8 * hmF2) else 180.0,
            hasF1 = true,
        )
    }
    if (foF2 > 0.0 && hmF2 > 0.0) {
        p = p.copy(
            nmF2 = if (nmF2 > 0.0) nmF2 else FO_TO_NM * foF2 * foF2,
            hmF2 = hmF2,
            b0 = sanitizeB0(b0),
            b1 = sanitizeB1(b1),
            hasF2 = true,
        )
    }
    return p
}

/** Chapman-alfa: `Nm * exp(0.5 * (1 - z - exp(-z)))`, `z=(h-hm)/H`. */
fun chapmanAlpha(nm: Double, hm: Double, hScale: Double, hKm: Double): Double {
    val z = (hKm - hm) / hScale
    return nm * exp(0.5 * (1.0 - z - exp(-z)))
}

/**
 * Suelo de validez F2: mayor h con NeE(h) >= NeF2(h), recortado a hmE.
 * Sin E o F2 devuelve -1.
 */
fun f2FloorKm(p: LayerProfile): Double {
    if (!(p.hasE && p.hasF2)) return -1.0

    val b0e = if (p.b0 > 0.0) p.b0 else 100.0
    val xe = (p.hmF2 - p.hmE) / b0e
    val xmaxe = 1.6
    val xce = if (xe < xmaxe) xe else xmaxe
    var neF2AtHmE = p.nmF2 * exp(-xce.pow(p.b1) / cosh(xce))
    if (xe > xmaxe) neF2AtHmE *= exp(-(xe - xmaxe) / 1.2)
    if (p.nmE <= neF2AtHmE) return p.hmE

    var sup = p.hmE
    var h = p.hmF2
    while (h >= 60.0) {
        val e = chapmanAlpha(p.nmE, p.hmE, 10.0, h)
        val b0 = if (p.b0 > 0.0) p.b0 else 100.0
        val x = (p.hmF2 - h) / b0
        val xc = if (x < 1.6) x else 1.6
        var f = p.nmF2 * exp(-xc.pow(p.b1) / cosh(xc))
        if (x > 1.6) f *= exp(-(x - 1.6) / 1.2)
        if (e >= f && h > sup) sup = h
        h -= 1.0
    }
    return sup
}

private fun evalWithFloor(
    p: LayerProfile,
    hKm: Double,
    cosChi: Double,
    flareFactor: Double,
    floor: Double,
): NeWinner {
    var best = 0.0
    var win = -1

    if (cosChi > 0.0) {
        val nmD = 1e9 * sqrt(cosChi) * flareFactor
        val v = chapmanAlpha(nmD, 75.0, 10.0, hKm)
        if (v > best) {
            best = v
            win = 0
        }
    }
    if (p.hasE) {
        val v = chapmanAlpha(p.nmE, p.hmE, 10.0, hKm)
        if (v > best) {
            best = v
            win = 1
        }
    }
    if (p.hasF1) {
        val v = chapmanAlpha(p.nmF1, p.hmF1, 25.0, hKm)
        if (v > best) {
            best = v
            win = 2
        }
    }
    if (p.hasF2) {
        var neF2 = 0.0
        if (hKm <= p.hmF2) {
            val b0 = if (p.b0 > 0.0) p.b0 else 100.0
            val x = (p.hmF2 - hKm) / b0
            val xmax = 1.6
            val xc = if (x < xmax) x else xmax
            val base = p.nmF2 * exp(-xc.pow(p.b1) / cosh(xc))
            neF2 = if (x > xmax) base * exp(-(x - xmax) / 1.2) else base
        } else {
            val hTop = 60.0 * (1.0 + (hKm - p.hmF2) / 300.0)
            neF2 = p.nmF2 * exp(-(hKm - p.hmF2) / hTop)
        }
        // Estricto: E gana su propio voxel pico (puerta del suelo).
        if (neF2 > best) {
            best = neF2
            if (hKm > floor) win = 3
        }
    }
    return NeWinner(ne = best, winner = win)
}

/** Densidad total [m^-3] a altura [hKm]; empates -> rama mas baja. */
fun evalNeTotal(
    p: LayerProfile,
    hKm: Double,
    cosChi: Double,
    flareFactor: Double,
): NeWinner = evalWithFloor(p, hKm, cosChi, flareFactor, f2FloorKm(p))

/** Rango log10 [8, 12.5] -> [0,1]. */
fun normLogNe(ne: Double): Double {
    val l10 = if (ne > 0.0) log10(ne) else 0.0
    val n = (l10 - VOL_LOG_MIN) / VOL_LOG_RANGE
    return min(1.0, max(0.0, n))
}

/**
 * Construye el volumen muestreando `evalNeTotal` sobre la rejilla de
 * perfiles. `sunDir` va en el marco del motor; `flareFactor` es global.
 * El suelo F2 se memoiza una vez por perfil.
 *
 * Si `profiles.size != lonRes * latRes`, devuelve un volumen invalido con
 * arrays del tamano completo (sin excepcion).
 */
fun buildDensityVolume(
    profiles: List<LayerProfile>,
    sunDir: DoubleArray,
    flareFactor: Double = 1.0,
    lonRes: Int = VOL_LON_RES,
    latRes: Int = VOL_LAT_RES,
    altRes: Int = VOL_ALT_RES,
    altMinKm: Double = VOL_ALT_MIN_KM,
    altMaxKm: Double = VOL_ALT_MAX_KM,
): DensityVolume {
    val cells = lonRes * latRes * altRes
    val volume = DensityVolume(
        lonRes = lonRes,
        latRes = latRes,
        altRes = altRes,
        altMinKm = altMinKm,
        altMaxKm = altMaxKm,
        data = DoubleArray(cells),
        layer = IntArray(cells),
        valid = false,
        timestamp = 0L,
    )
    if (profiles.size != lonRes * latRes) return volume

    val floors = DoubleArray(profiles.size) { f2FloorKm(profiles[it]) }
    for (latIdx in 0 until latRes) {
        val latDeg = -90.0 + (180.0 * (latIdx + 0.5)) / latRes
        for (lonIdx in 0 until lonRes) {
            val lonDeg = -180.0 + (360.0 * (lonIdx + 0.5)) / lonRes
            val direction = densityDir(latDeg, lonDeg)
            val cosChi = direction[0] * sunDir[0] +
                direction[1] * sunDir[1] +
                direction[2] * sunDir[2]
            val pi = latIdx * lonRes + lonIdx
            val p = profiles[pi]
            for (a in 0 until altRes) {
                val h = volumeAltKm(a, altRes, altMinKm, altMaxKm)
                val r = evalWithFloor(p, h, cosChi, flareFactor, floors[pi])
                val ii = volumeIndex(lonIdx, latIdx, a, lonRes, latRes)
                volume.data[ii] = normLogNe(r.ne)
                volume.layer[ii] = when {
                    r.winner < 0 -> 0
                    r.winner > 3 -> 3
                    else -> r.winner
                }
            }
        }
    }
    return volume.copy(valid = true, timestamp = 0L)
}

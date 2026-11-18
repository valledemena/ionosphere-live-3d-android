package com.valledemena.ionosphere.core.iono

import com.valledemena.ionosphere.data.model.IonosondeStation
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rejilla de perfiles desde ionosondas, port de
 * `src/lib/iono/profileGrid.ts` del pin web (interpLayerProfiles,
 * `App.cpp:676-766`, y modelFoF2/modelHmF2 de `LayerProfile.h`).
 *
 * IDW por campo con fondo de mediana, distancia de gran circulo en grados y
 * mezcla medicion<->modelo SOLO en foF2/hmF2. La rejilla de perfiles usa
 * ESQUINAS (`latG = -90 + cellLat * i`, sin `+0.5`) mientras el constructor del
 * volumen consume centros: el desfase de media celda (1,25 grados) existe en
 * produccion y se replica igual, fidelidad sobre correccion.
 *
 * R4.6.1: es la pieza que faltaba para que `setSliceVolume` deje de recibir
 * `null`. Puro: sin GL, sin UI, sin red.
 */

/** Radio de influencia de una muestra, en grados de gran circulo. */
const val IDW_MAX_DIST_DEG = 50.0

/** Peso del fondo de mediana: evita dividir por cero lejos de toda estacion. */
const val IDW_BACKGROUND = 0.004

/**
 * F10.7 de respaldo, solo si el payload de clima espacial no trae uno usable.
 * Valor medio de ciclo (150 sfu); se declara en vez de inventarlo en silencio.
 */
const val F107_DEFAULT = 150.0

/** Muestra de una ionosonda ya mapeada a los campos del interpolar. */
data class ProfileSample(
    val latDeg: Double,
    val lonDeg: Double,
    val foE: Double,
    val foF1: Double,
    val foF2: Double,
    val hmF2: Double,
    val b0: Double,
    val b1: Double,
    val valid: Boolean,
    val stale: Boolean,
)

/** Distancia haversine en GRADOS (port de `havDeg`). */
fun havDeg(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
    val d2r = PI / 180.0
    val dla = (la2 - la1) * d2r
    val dlo = (lo2 - lo1) * d2r
    val a = sin(dla / 2) * sin(dla / 2) +
        cos(la1 * d2r) * cos(la2 * d2r) * sin(dlo / 2) * sin(dlo / 2)
    return (2 * asin(min(1.0, sqrt(a)))) / d2r
}

/** Dia del ano UTC 1..366 (port de `dayOfYear`: `tm_yday + 1`). */
fun dayOfYearUtc(epochSec: Long): Int =
    Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC).dayOfYear

/**
 * Mapeo estacion -> muestra: campo ausente a 0 y las puertas `> 0` del
 * interpolar convierten ese 0 en fondo de mediana, como el oraculo con dato
 * ausente. `valid` es "la estacion publica foF2".
 *
 * `stale` combina la marca de la fuente con la edad del ultimo ionograma
 * (>6 h fuera del volumen, banner `App.cpp:4326`).
 */
fun stationToProfileSample(station: IonosondeStation, nowMs: Long): ProfileSample =
    ProfileSample(
        latDeg = station.lat,
        lonDeg = station.lon,
        foE = station.foE ?: 0.0,
        foF1 = station.foF1 ?: 0.0,
        foF2 = station.foF2 ?: 0.0,
        hmF2 = station.hmF2 ?: 0.0,
        b0 = station.B0 ?: 0.0,
        b1 = station.B1 ?: 0.0,
        valid = station.foF2 != null,
        stale = station.stale || isStaleByAge(station, nowMs),
    )

/** Stale por edad del ultimo ionograma; sin historia legible no se marca. */
private fun isStaleByAge(station: IonosondeStation, nowMs: Long): Boolean {
    val last = station.history.lastOrNull() ?: return false
    val raw = last.t.let { if (it.endsWith("Z")) it else it + "Z" }
    val atMs = try {
        Instant.parse(raw).toEpochMilli()
    } catch (_: Exception) {
        return false
    }
    return nowMs - atMs > 6L * 3600_000L
}

/** smoothstep con aristas posiblemente invertidas (55 -> 25), como el pin. */
private fun smoothstep(e0: Double, e1: Double, x: Double): Double {
    val t = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return t * t * (3.0 - 2.0 * t)
}

/** Mediana SUPERIOR de los valores `> 0` (oraculo `medOf`). */
private fun medOf(values: List<Double>): Double {
    val v = values.filter { it > 0.0 }.sorted()
    if (v.isEmpty()) return 0.0
    return v[v.size / 2]
}

/** foF2 climatologico (MHz) con `cosChi` del mismo vector que el volumen. */
internal fun modelFoF2Core(cosChi: Double, latDeg: Double, doy: Int, f107: Double): Double {
    val lat = kotlin.math.abs(latDeg)
    val solar = 0.62 + 0.0046 * min(220.0, max(60.0, f107))
    val dayBase = 3.2 + 7.8 * max(cosChi, 0.0).pow(0.7)
    val nightBase = 1.6 + 1.8 * exp(-lat / 40.0)
    val t = smoothstep(-0.12, 0.15, cosChi)
    var fo = (1 - t) * nightBase + t * dayBase
    val eq = exp(-((lat - 16) / 12).pow(2)) - 0.55 * exp(-(lat / 20).pow(2))
    fo *= 1 + 0.32 * eq * t
    val seasonal = cos(((doy - 172) / 365.0) * 2 * PI) * (if (latDeg > 0) 1.0 else -1.0)
    fo *= 1 + 0.06 * seasonal * (lat / 90.0)
    return fo * solar
}

/** hmF2 climatologico (km): sube de noche. */
internal fun modelHmF2Core(cosChi: Double, latDeg: Double): Double {
    val lat = kotlin.math.abs(latDeg)
    val dayH = 250 - 0.35 * lat
    val nightH = 300 + 0.2 * lat
    val t = smoothstep(-0.12, 0.15, cosChi)
    return (1 - t) * nightH + t * dayH
}

/**
 * Interpola perfiles de capa a la rejilla `lonRes x latRes`.
 *
 * Fondo = mediana por campo EXCEPTO foF2/hmF2, que mezclan el fondo
 * climatologico por cercania (`wG`). `sunDir` va en el marco del motor, el
 * mismo vector que [applyDayNight] y [buildDensityVolume].
 */
fun interpProfiles(
    samples: List<ProfileSample>,
    lonRes: Int,
    latRes: Int,
    sunDir: DoubleArray,
    epochSec: Long,
    f107: Double,
): List<LayerProfile> {
    val out = MutableList(lonRes * latRes) { blankProfile() }
    val pts = samples.filter { it.valid && !it.stale }
    if (pts.isEmpty()) return out

    val cellLat = 180.0 / latRes
    val cellLon = 360.0 / lonRes
    val fE = medOf(pts.map { it.foE })
    val fF1 = medOf(pts.map { it.foF1 })
    val fF2 = medOf(pts.map { it.foF2 })
    val mNmE = if (fE > 0) FO_TO_NM * fE * fE else 0.0
    val mNmF1 = if (fF1 > 0) FO_TO_NM * fF1 * fF1 else 0.0
    val mNmF2 = if (fF2 > 0) FO_TO_NM * fF2 * fF2 else 0.0
    val hmedF2 = medOf(pts.map { it.hmF2 })
    val mB0 = sanitizeB0(medOf(pts.map { it.b0 }))
    val mB1 = sanitizeB1(medOf(pts.map { it.b1 }))
    val doy = dayOfYearUtc(epochSec)

    for (latIdx in 0 until latRes) {
        val latG = -90.0 + cellLat * latIdx
        for (lonIdx in 0 until lonRes) {
            val lonG = -180.0 + cellLon * lonIdx
            var wNmE = IDW_BACKGROUND
            var vNmE = IDW_BACKGROUND * mNmE
            var wNmF1 = IDW_BACKGROUND
            var vNmF1 = IDW_BACKGROUND * mNmF1
            var wNmF2 = IDW_BACKGROUND
            var vNmF2 = IDW_BACKGROUND * mNmF2
            var wHm = IDW_BACKGROUND
            var vHm = IDW_BACKGROUND * hmedF2
            var wB0 = IDW_BACKGROUND
            var vB0 = IDW_BACKGROUND * mB0
            var wB1 = IDW_BACKGROUND
            var vB1 = IDW_BACKGROUND * mB1
            var nearDeg = 180.0
            for (p in pts) {
                val d = havDeg(latG, lonG, p.latDeg, p.lonDeg)
                if (d < nearDeg) nearDeg = d
                if (d > IDW_MAX_DIST_DEG) continue
                val wt = 1.0 / (d * d + 0.5)
                if (p.foE > 0) {
                    wNmE += wt
                    vNmE += wt * FO_TO_NM * p.foE * p.foE
                }
                if (p.foF1 > 0) {
                    wNmF1 += wt
                    vNmF1 += wt * FO_TO_NM * p.foF1 * p.foF1
                }
                if (p.foF2 > 0) {
                    wNmF2 += wt
                    vNmF2 += wt * FO_TO_NM * p.foF2 * p.foF2
                }
                if (p.hmF2 > 0) {
                    wHm += wt
                    vHm += wt * p.hmF2
                }
                if (p.b0 > 0) {
                    wB0 += wt
                    vB0 += wt * p.b0
                }
                if (p.b1 > 0) {
                    wB1 += wt
                    vB1 += wt * p.b1
                }
            }

            // Mezcla medicion<->modelo SOLO en foF2/hmF2, con el MISMO cosChi
            // que applyDayNight y el constructor.
            val wG = smoothstep(55.0, 25.0, nearDeg)
            val n = densityDir(latG, lonG)
            val cosChi = n[0] * sunDir[0] + n[1] * sunDir[1] + n[2] * sunDir[2]
            val nmF2Idw = vNmF2 / wNmF2
            val hmF2Idw = vHm / wHm
            val foF2Idw = if (nmF2Idw > 0) sqrt(nmF2Idw / FO_TO_NM) else 0.0
            val foF2Cell = wG * foF2Idw + (1 - wG) * modelFoF2Core(cosChi, latG, doy, f107)
            val nmF2Cell = FO_TO_NM * foF2Cell * foF2Cell
            val hmF2Cell = if (hmF2Idw > 0) {
                wG * hmF2Idw + (1 - wG) * modelHmF2Core(cosChi, latG)
            } else {
                modelHmF2Core(cosChi, latG)
            }

            val nmE = vNmE / wNmE
            val nmF1 = vNmF1 / wNmF1
            out[latIdx * lonRes + lonIdx] = blankProfile().copy(
                nmE = nmE,
                hasE = nmE > 0,
                nmF1 = nmF1,
                hasF1 = nmF1 > 0,
                nmF2 = nmF2Cell,
                hmF2 = hmF2Cell,
                hasF2 = nmF2Cell > 0 && hmF2Cell > 0,
                hmF1 = if (hmF2Cell > 0) max(150.0, 0.8 * hmF2Cell) else 180.0,
                b0 = sanitizeB0(vB0 / wB0),
                b1 = sanitizeB1(vB1 / wB1),
            )
        }
    }
    return out
}

/**
 * Cadena completa estaciones -> volumen Ne(h): mapper, interpolacion,
 * atenuacion nocturna E/F1 y constructor. Sin la atenuacion el fondo mediano
 * interpola densidad fantasma y el argmax nocturno se queda en ~110 km.
 *
 * `sunDir` en el marco del motor; `epochSec` alimenta el termino estacional.
 */
fun buildVolumeFromStations(
    stations: List<IonosondeStation>,
    sunDir: DoubleArray,
    epochSec: Long,
    f107: Double = F107_DEFAULT,
    lonRes: Int = VOL_LON_RES,
    latRes: Int = VOL_LAT_RES,
    altRes: Int = VOL_ALT_RES,
    altMinKm: Double = VOL_ALT_MIN_KM,
    altMaxKm: Double = VOL_ALT_MAX_KM,
): DensityVolume {
    val samples = stations.map { stationToProfileSample(it, epochSec * 1000L) }
    val profiles = interpProfiles(samples, lonRes, latRes, sunDir, epochSec, f107)
    val dayNight = applyDayNight(profiles, lonRes, latRes, sunDir)
    return buildDensityVolume(
        profiles = dayNight,
        sunDir = sunDir,
        flareFactor = 1.0,
        lonRes = lonRes,
        latRes = latRes,
        altRes = altRes,
        altMinKm = altMinKm,
        altMaxKm = altMaxKm,
    )
}

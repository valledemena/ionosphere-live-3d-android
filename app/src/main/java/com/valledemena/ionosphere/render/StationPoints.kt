package com.valledemena.ionosphere.render

import com.valledemena.ionosphere.data.model.IonosondeStation
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Referencia de tamano de punto del web (`types.ts`: 11). */
const val STATION_POINT_REF = 11f

private const val STATION_RADIUS = 1.012f
private const val POINT_MIN = 2f
private const val POINT_MAX = 30f
private const val COLOR_SPAN_MIN = 1.5f
private const val PICK_CANDIDATE_PX = 60f
private const val PICK_BEST_PX = 24f
private const val PICK_DRAG_PX = 6f

/**
 * Clamp [2,30] con el mismo orden de guardas que `types.ts:393`.
 *
 * La guarda `!(v > 0)` no es decorativa: sin ella NaN atraviesa las tres
 * comparaciones y sale NaN. Se conserva literalmente.
 */
fun stationPointSizeClamp(v: Float): Float {
    if (!(v > 0f)) return POINT_MIN
    if (v < POINT_MIN) return POINT_MIN
    if (v > POINT_MAX) return POINT_MAX
    return v
}

/**
 * Posiciones xyz de las estaciones, radio 1.012, espejo de
 * `latLonToVec3` del web (`geo.ts:17`): lon 0 -> +X, Y = norte.
 */
fun stationPositions(stations: List<IonosondeStation>): FloatArray {
    val out = FloatArray(stations.size * 3)
    for (i in stations.indices) {
        val s = stations[i]
        val lat = Math.toRadians(s.lat)
        val phi = Math.toRadians(s.lon + 180.0)
        val cosLat = cos(lat)
        out[i * 3] = (-STATION_RADIUS * cos(phi) * cosLat).toFloat()
        out[i * 3 + 1] = (STATION_RADIUS * sin(lat)).toFloat()
        out[i * 3 + 2] = (STATION_RADIUS * sin(phi) * cosLat).toFloat()
    }
    return out
}

/**
 * Rango robusto de color: percentiles 2/98 y span minimo 1.5.
 *
 * R3.4 usa las foF2 recibidas, no el grid del shell (divergencia declarada en
 * el plan). Se descartan valores no finitos y no positivos.
 */
fun stationColorRange(foF2: List<Double>): Pair<Float, Float> {
    val sorted = foF2.filter { it.isFinite() && it > 0.0 }.sorted()
    if (sorted.isEmpty()) return 0f to COLOR_SPAN_MIN

    val lo = sorted[(sorted.size * 0.02).toInt().coerceIn(0, sorted.lastIndex)]
    val hi = sorted[(sorted.size * 0.98).toInt().coerceIn(0, sorted.lastIndex)]
    val span = max(COLOR_SPAN_MIN.toDouble(), hi - lo)
    return lo.toFloat() to (lo + span).toFloat()
}

/**
 * Normaliza foF2 a 0..1. `null`, no finito o <= 0 -> 0, como el web.
 * El denominador conserva la guarda `(fMax - fMin || 1)`.
 */
fun stationFoValues(
    stations: List<IonosondeStation>,
    fMin: Float,
    fMax: Float,
): FloatArray {
    val rawSpan = fMax - fMin
    val span = if (rawSpan.isFinite() && rawSpan != 0f) rawSpan else 1f
    return FloatArray(stations.size) { i ->
        val f = stations[i].foF2
        // Espejo del web: `f && f > 0`. NaN cae por la comparacion; +Inf
        // entra y el clamp lo lleva a 1 (el web hace lo mismo).
        if (f == null || !(f > 0.0)) {
            0f
        } else {
            ((f.toFloat() - fMin) / span).coerceIn(0f, 1f)
        }
    }
}

/**
 * Formula del vertex shader extraida a Kotlin para testearla sin GPU.
 * `uPointSize` llega ya clampeado; el shader sigue calculando el resultado.
 */
fun stationPointPixels(uPointSize: Float, dist: Float, pixelRatio: Float): Float {
    val d = if (dist.isFinite() && dist > 0f) dist else 1e-6f
    val raw = 140f * uPointSize / 11f / d
    val ceiling = min(26f * uPointSize / 11f, 35f)
    return raw.coerceIn(3f, ceiling) * pixelRatio
}

/** Punto ya proyectado a pantalla, con su visibilidad de hemisferio. */
data class StationScreenPoint(
    val code: String,
    val x: Float,
    val y: Float,
    val facing: Boolean,
)

/**
 * Picking puro: mismas guardas que el web (drag > 6 px descarta, candidato
 * < 60 px, mejor < 24 px, hemisferio oculto descartado).
 */
fun stationPick(
    points: List<StationScreenPoint>,
    touchX: Float,
    touchY: Float,
    dragPx: Float,
    dragThresholdPx: Float = PICK_DRAG_PX,
    bestDistancePx: Float = PICK_BEST_PX,
): String? {
    if (!dragPx.isFinite() || dragPx > dragThresholdPx) return null
    var bestCode: String? = null
    var bestDistance = bestDistancePx
    for (p in points) {
        if (!p.facing || !p.x.isFinite() || !p.y.isFinite()) continue
        val dd = hypot(p.x - touchX, p.y - touchY)
        if (dd < PICK_CANDIDATE_PX && dd < bestDistance) {
            bestCode = p.code
            bestDistance = dd
        }
    }
    return bestCode
}

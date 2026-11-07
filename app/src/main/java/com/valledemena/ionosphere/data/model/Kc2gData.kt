package com.valledemena.ionosphere.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Modelo puro portado de src/lib/iono/fallback.ts (pin web 8cdc334,
 * W-4 clase 244, ruling 243). Sin red, sin DOM. Redistribucion sancionada
 * por GIRO.
 *
 * Precedencia declarada (espejo resolveSource): live > kc2g > cache (techo
 * KC2G_CACHE_MAX_AGE_MIN min) > model. El source fluye siempre del payload
 * (B5 de R3.3, sin fuente en texto).
 */
@Serializable
data class Kc2gStation(
    val code: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val foF2: Double? = null,
    val hmF2: Double? = null,
    val foE: Double? = null,
    val foF1: Double? = null,
    val mufd: Double? = null,
    val timeMs: Double = Double.NaN,
)

@Serializable
data class Kc2gData(
    val stations: List<Kc2gStation> = emptyList(),
    val updated: Long = 0L,
    val total: Int = stations.size,
    val source: String = "kc2g fallback",
    val cacheAgeMin: Int? = null,
)

const val KC2G_EXCLUDED_URSI = "EG931"

val KC2G_EXCLUDED = setOf(KC2G_EXCLUDED_URSI)

const val KC2G_CACHE_MAX_AGE_MIN = 24 * 60

fun kc2gNum(v: Any?): Double? = when (v) {
    is Number -> if (v.toDouble().isFinite()) v.toDouble() else null
    is String -> v.toDoubleOrNull()?.takeIf { it.isFinite() }
    else -> null
}

fun normalizeLon(v: Any?): Double {
    val n = kc2gNum(v) ?: return Double.NaN
    if (!n.isFinite()) return Double.NaN
    return ((n + 540.0) % 360.0) - 180.0
}

fun kc2gTimeMs(v: Any?): Double {
    if (v !is String) return Double.NaN
    val t = v.trim()
    if (t.isEmpty()) return Double.NaN
    val withZ = if (t.endsWith("Z", ignoreCase = true)) t else "$t${'Z'}"
    return try {
        java.time.Instant.parse(withZ).toEpochMilli().toDouble()
    } catch (_: Throwable) {
        Double.NaN
    }
}

fun parseKc2gRow(row: Any?): Kc2gStation? {
    val r = asMap(row) ?: return null
    val st = asMap(r["station"]) ?: emptyMap<Any?, Any?>()
    val code = (st["code"] as? String)?.trim().orEmpty()
    if (code.isEmpty() || code in KC2G_EXCLUDED) return null
    val lat = kc2gNum(st["latitude"]) ?: return null
    val lon = normalizeLon(st["longitude"])
    if (!lon.isFinite()) return null
    val name = st["name"] as? String ?: code
    return Kc2gStation(
        code = code,
        name = name,
        lat = lat,
        lon = lon,
        foF2 = kc2gNum(r["fof2"]),
        hmF2 = kc2gNum(r["hmf2"]),
        foE = kc2gNum(r["foe"]),
        foF1 = kc2gNum(r["fof1"]),
        mufd = kc2gNum(r["mufd"]),
        timeMs = kc2gTimeMs(r["time"]),
    )
}

private fun asMap(v: Any?): Map<*, *>? = when (v) {
    is Map<*, *> -> v
    is JsonObject -> v.toMap()
    else -> null
}

private fun JsonObject.toMap(): Map<String, Any?> = entries.associate { (k, vv) ->
    k to when (vv) {
        is kotlinx.serialization.json.JsonPrimitive -> if (vv.isString) vv.content else vv.content.toDoubleOrNull()
        is JsonObject -> vv.toMap()
        else -> vv.toString()
    }
}

fun filterFresh(rows: List<Kc2gStation>, nowMs: Long, maxAgeMs: Long): List<Kc2gStation> {
    return rows.filter { s -> s.timeMs.isFinite() && (nowMs - s.timeMs) <= maxAgeMs }
}

data class Kc2gResolveArgs(
    val live: Int = 0,
    val kc2g: Int = 0,
    val cacheAgeMin: Int? = null,
)

fun resolveSource(live: Int, kc2g: Int, cacheAgeMin: Int?): String {
    return resolveSource(Kc2gResolveArgs(live, kc2g, cacheAgeMin))
}

fun resolveSource(args: Kc2gResolveArgs): String = when {
    args.live > 0 -> "live"
    args.kc2g > 0 -> "kc2g"
    isCacheUsable(args.cacheAgeMin) -> "cache"
    else -> "model"
}

fun resolveSource(args: Map<String, Any?>): String {
    val live = (args["live"] as? Number)?.toInt() ?: 0
    val kc2g = (args["kc2g"] as? Number)?.toInt() ?: 0
    val cacheAgeMin = (args["cacheAgeMin"] as? Number)?.toInt()
    return resolveSource(live, kc2g, cacheAgeMin)
}

fun cacheable(stationCount: Int): Boolean = stationCount > 0

fun isCacheUsable(cacheAgeMin: Int?): Boolean = cacheAgeMin != null && cacheAgeMin <= KC2G_CACHE_MAX_AGE_MIN

data class Kc2gSourceBadge(val label: String, val tone: String)

fun sourceBadge(source: String): Kc2gSourceBadge {
    val s = source.lowercase()
    return when {
        s.contains("live") -> Kc2gSourceBadge("live", "green")
        s.contains("kc2g") || s.contains("fallback") -> Kc2gSourceBadge("kc2g", "amber")
        s.contains("cache") -> Kc2gSourceBadge("cache", "slate")
        s.contains("model") -> Kc2gSourceBadge("model", "blue")
        else -> Kc2gSourceBadge("down", "red")
    }
}

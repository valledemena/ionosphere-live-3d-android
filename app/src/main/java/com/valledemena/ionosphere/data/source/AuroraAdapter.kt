package com.valledemena.ionosphere.data.source

import com.valledemena.ionosphere.data.DataTtl
import com.valledemena.ionosphere.data.model.AuroraCell
import com.valledemena.ionosphere.data.model.AuroraData
import com.valledemena.ionosphere.data.net.CacheHit
import com.valledemena.ionosphere.data.net.HttpClient
import com.valledemena.ionosphere.data.net.NetCache
import com.valledemena.ionosphere.data.net.Outcome
import com.valledemena.ionosphere.data.net.UA
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val URL_OVATION = "https://services.swpc.noaa.gov/json/ovation_aurora_latest.json"
private const val MIN_P = 8.0

class AuroraAdapter(
    private val http: HttpClient,
    private val cache: NetCache? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun fetch(): Outcome<AuroraData> {
        val now = clock()
        val hit = cache?.load("aurora")
        val cached = hit?.let { r ->
            runCatching { Json.decodeFromString<AuroraData>(r.rawJson) }.getOrNull()?.let { value ->
                CachedParsed(value, r.source, r.fetchedAtMs, (now - r.fetchedAtMs) >= DataTtl.AURORA_MS)
            }
        }

        return try {
            val response = http.get(URL_OVATION)
            if (!response.ok) {
                return if (cached != null) {
                    Outcome.Cached(cached.value, cached.source, cached.fetchedAtMs, cached.stale, IllegalStateException("HTTP ${response.code}"))
                } else {
                    Outcome.Failed(IllegalStateException("upstream HTTP ${response.code}"))
                }
            }
            val parsed = parseLive(now, response.body ?: throw IllegalStateException("empty body"))
            val stored = Json.encodeToString(AuroraData.serializer(), parsed.value)
            cache?.store("aurora", parsed.source, stored, parsed.fetchedAtMs)
            Outcome.Live(parsed.value, parsed.source, parsed.fetchedAtMs)
        } catch (e: Throwable) {
            if (cached != null) Outcome.Cached(cached.value, cached.source, cached.fetchedAtMs, cached.stale, e)
            else Outcome.Failed(e)
        }
    }

    private fun parseLive(now: Long, rawBody: String): LoadedRaw {
        val rawJson = Json.parseToJsonElement(rawBody).jsonObject
        val obsTime = rawJson["Observation Time"]?.jsonPrimitive?.content ?: ""
        val fcstTime = rawJson["Forecast Time"]?.jsonPrimitive?.content ?: ""
        val coordsArray = rawJson["coordinates"]?.jsonArray ?: throw IllegalStateException("missing coordinates")

        val agg = LinkedHashMap<String, DoubleArray>()
        for (entry in coordsArray) {
            val arr = entry.jsonArray
            if (arr.size < 3) continue
            val lon = arr[0].jsonPrimitive.content.toDouble()
            val lat = arr[1].jsonPrimitive.content.toDouble()
            val p = arr[2].jsonPrimitive.content.toDouble()
            val lat2 = Math.round(lat / 2.0) * 2
            val lon2 = Math.round(lon / 2.0) * 2
            val key = "$lat2,$lon2"
            val cur = agg.getOrPut(key) { doubleArrayOf(0.0, 0.0, lat2.toDouble(), lon2.toDouble()) }
            cur[0] += p
            cur[1] += 1.0
        }
        val cells = mutableListOf<AuroraCell>()
        for (e in agg.values) {
            if (e[1] <= 0.0) continue
            val pAvg = e[0] / e[1]
            if (pAvg > MIN_P) cells.add(AuroraCell(lat = e[2], lon = e[3], p = Math.round(pAvg).toInt()))
        }

        val data = AuroraData(
            updated = now,
            observationTime = obsTime,
            forecastTime = fcstTime,
            cells = cells,
            source = "NOAA SWPC Ovation (live)",
        )
        return LoadedRaw(data, data.source, now)
    }

    private data class CachedParsed<T>(val value: T, val source: String, val fetchedAtMs: Long, val stale: Boolean)
    private data class LoadedRaw(val value: AuroraData, val source: String, val fetchedAtMs: Long)
}

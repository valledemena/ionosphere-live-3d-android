package com.valledemena.ionosphere.data.source.ionosondes

import com.valledemena.ionosphere.data.DataTtl
import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.data.model.IonosondeParser
import com.valledemena.ionosphere.data.model.IonosondeStation
import com.valledemena.ionosphere.data.model.StationHistoryPoint
import com.valledemena.ionosphere.data.net.HttpClient
import com.valledemena.ionosphere.data.net.NetCache
import com.valledemena.ionosphere.data.net.Outcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val URL_CATALOG = "https://lgdc.uml.edu/common/DIDBFastStationList?orderByField=ursiCode"
private const val GETBEST = "https://lgdc.uml.edu/fastchar/getbest"

private val CORE_STATIONS = listOf("EB040", "EA036", "PQ052", "JR055", "KL154")
private val ANCHOR_EXTRA_CHARS = listOf("foE", "foF1", "hmF2", "B0", "B1")
private val ANCHOR_STATIONS = listOf("EB040", "JR055", "PQ052", "FF051", "BC840", "TO535", "PF765", "BVJ03", "RL052", "AU930", "AT138", "TR170")

class IonosondeAdapter(
    private val http: HttpClient,
    private val cache: NetCache? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val stations: List<String> = CORE_STATIONS,
    private val anchorStations: Set<String> = ANCHOR_STATIONS.toSet(),
    private val antiHerdDelayMs: Long = 250,
) {
    suspend fun fetch(): Outcome<IonosondeData> {
        val now = clock()
        val hit = cache?.load("ionosondes")
        val cached = hit?.let { h ->
            runCatching { Json.decodeFromString<IonosondeData>(h.rawJson) }.getOrNull()?.let { v ->
                CachedSnapshot(v, (now - h.fetchedAtMs) >= DataTtl.IONOSONDES_DATA_MS)
            }
        }
        // TTL throttle: if fresh cache, return without network usage.
        cached?.let { if (!it.stale) return Outcome.Cached(it.value, it.value.source, hit!!.fetchedAtMs, false) }
        return try {
            val meta = loadCatalog(now)
            val extra = loadExtras(now, anchorStations)
            val stationData = mutableListOf<IonosondeStation>()
            var anyFailure = false

            for ((idx, code) in stations.withIndex()) {
                val info = meta[code]
                // Keep only stations where coordinates exist — avoids invisible globe points.
                if (info == null) { delay(antiHerdDelayMs); continue }
                val data = fetchStation(code, now)
                if (data != null) {
                    val ex = extra[code] ?: emptyMap()
                    val sd = IonosondeStation(
                        code = data.code.ifEmpty { code },
                        name = info.name,
                        lat = info.lat,
                        lon = info.lon,
                        foF2 = data.foF2,
                        muf3000 = data.muf3000,
                        history = data.history.map { StationHistoryPoint(it.t, it.foF2) }.takeLast(2500),
                        foE = ex["foE"] ?: data.foE,
                        foF1 = ex["foF1"] ?: data.foF1,
                        hmF2 = ex["hmF2"] ?: data.hmF2,
                        B0 = ex["B0"] ?: data.B0,
                        B1 = ex["B1"] ?: data.B1,
                        stale = false,
                    )
                    stationData.add(sd)
                } else {
                    anyFailure = true
                }
                if (idx < stations.size - 1) delay(antiHerdDelayMs)
            }

            if (anyFailure && stationData.isEmpty() && cached != null) {
                return Outcome.Cached(cached.value, cached.value.source, cached.value.updated, true)
            }

            val data = IonosondeData(
                updated = now,
                stations = stationData,
                total = stationData.size,
                catalogSize = meta.size,
                source = if (stationData.isNotEmpty()) "GIRO DIDBase (live, kc2g failover reserved)" else "unavailable",
            )

            if (stationData.isNotEmpty()) {
                cache?.store("ionosondes", data.source, Json.encodeToString(IonosondeData.serializer(), data), now)
            }
            Outcome.Live(data, data.source, now)
        } catch (e: Throwable) {
            if (cached != null) Outcome.Cached(cached.value, cached.value.source, cached.value.updated, true, e)
            else Outcome.Failed(e)
        }
    }

    private data class CachedSnapshot(val value: IonosondeData, val stale: Boolean)

    private suspend fun loadCatalog(now: Long): Map<String, IonosondeParser.StationInfo> {
        val html = runCatching { http.get(URL_CATALOG) }.getOrNull()?.let { if (it.ok) it.body else null } ?: return emptyMap()
        return IonosondeParser.parseCatalog(html).associateBy { it.code }
    }

    private suspend fun fetchStation(code: String, now: Long): IonosondeParser.StationData? {
        val from = now - 168L * 60L * 60L * 1000L
        val fmt = SimpleDateFormat("yyyy.MM.dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val url = "$GETBEST?ursiCode=$code&charName=foF2&fromDate=${fmt.format(java.util.Date(from))}&toDate=${fmt.format(java.util.Date(now))}"
        val text = runCatching { http.get(url) }.getOrNull()?.let { if (it.ok) it.body else null }
        if (text == null || text.contains("STATUS: ERROR")) return null
        return IonosondeParser.parseGetBest(text)
    }

    private suspend fun loadExtras(now: Long, anchors: Set<String>) =
        mutableMapOf<String, Map<String, Double?>>().apply {
            val fmt = SimpleDateFormat("yyyy.MM.dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val from = now - 65L * 60L * 1000L
            for (code in anchors) {
                val anchorExtras = mutableMapOf<String, Double?>()
                for (charName in ANCHOR_EXTRA_CHARS) {
                    val url = "$GETBEST?ursiCode=$code&charName=$charName&fromDate=${fmt.format(java.util.Date(from))}&toDate=${fmt.format(java.util.Date(now))}"
                    val txt = runCatching { http.get(url) }.getOrNull()?.let { if (it.ok) it.body else null }
                    if (txt == null || txt.contains("STATUS: ERROR")) anchorExtras[charName] = null
                    else anchorExtras[charName] = IonosondeParser.parseCharValue(txt)
                    delay(250)
                }
                this[code] = anchorExtras
            }
        }
}

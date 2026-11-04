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
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

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
                Pair(v, (now - h.fetchedAtMs) >= DataTtl.IONOSONDES_DATA_MS)
            }
        }

        return try {
            val meta = loadCatalog(now)
            val extra = loadExtras(now, anchorStations).toMap()
            val stationData = mutableListOf<IonosondeStation>()
            var anyFoRequestFailed = false

            for ((idx, code) in stations.withIndex()) {
                val info = meta[code]
                val data = fetchStation(code, now)
                if (data != null) {
                    val ex = extra[code] ?: emptyMap()
                    val sd = IonosondeStation(
                        code = data.code.ifEmpty { code },
                        name = info?.name ?: code,
                        lat = info?.lat ?: 0.0,
                        lon = info?.lon ?: 0.0,
                        foF2 = data.foF2,
                        muf3000 = data.muf3000,
                        history = data.history.map { StationHistoryPoint(it.t, it.foF2) },
                        foE = ex["foE"] ?: data.foE,
                        foF1 = ex["foF1"] ?: data.foF1,
                        hmF2 = ex["hmF2"] ?: data.hmF2,
                        B0 = ex["B0"] ?: data.B0,
                        B1 = ex["B1"] ?: data.B1,
                        stale = false,
                    )
                    stationData.add(sd)
                } else {
                    anyFoRequestFailed = true
                }
                if (idx < stations.size - 1) delay(antiHerdDelayMs)
            }

            val data = IonosondeData(
                updated = now,
                stations = stationData,
                total = stationData.size,
                catalogSize = meta.size,
                source = if (stationData.isNotEmpty()) "GIRO DIDBase (live, kc2g failover reserved)" else "unavailable",
            )

            if (anyFoRequestFailed && stationData.isEmpty() && cached != null) {
                return Outcome.Cached(cached.first, cached.first.source, cached.first.updated, true)
            }

            if (stationData.isNotEmpty()) {
                cache?.store("ionosondes", data.source, Json.encodeToString(IonosondeData.serializer(), data), now)
            }
            Outcome.Live(data, data.source, now)
        } catch (e: Throwable) {
            if (cached != null) Outcome.Cached(cached.first, cached.first.source, cached.first.updated, true, e)
            else Outcome.Failed(e)
        }
    }

    private suspend fun loadCatalog(now: Long): Map<String, IonosondeParser.StationInfo> {
        val html = runCatching { http.get(URL_CATALOG) }.getOrNull()?.let { if (it.ok) it.body else null } ?: return emptyMap()
        return IonosondeParser.parseCatalog(html).associateBy { it.code }
    }

    private suspend fun fetchStation(code: String, now: Long): IonosondeParser.StationData? {
        val from = now - DataTtl.IONOSONDES_DATA_MS.coerceAtLeast(1L) * (168 * 6) // 168h
        val fmt = SimpleDateFormat("yyyy.MM.dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val url = "$GETBEST?ursiCode=$code&charName=foF2&fromDate=${fmt.format(java.util.Date(from))}&toDate=${fmt.format(java.util.Date(now))}"
        val text = runCatching { http.get(url) }.getOrNull()?.let { if (it.ok) it.body else null }
        if (text == null || text.contains("STATUS: ERROR")) return null
        return IonosondeParser.parseGetBest(text)
    }

    private suspend fun loadExtras(now: Long, anchors: Set<String>) =
        mutableMapOf<String, Map<String, Double?>>().apply {
            for (code in anchors) {
                val anchorExtras = mutableMapOf<String, Double?>()
                for (charName in ANCHOR_EXTRA_CHARS) {
                    val txt = runCatching { http.get("$GETBEST?ursiCode=$code&charName=$charName&fromDate=now&toDate=now") }.getOrNull()?.let { if (it.ok) it.body else null }
                    if (txt == null || txt.contains("STATUS: ERROR")) anchorExtras[charName] = null
                    else anchorExtras[charName] = IonosondeParser.parseCharValue(txt)
                    delay(250)
                }
                this[code] = anchorExtras
            }
        }
}

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
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private const val URL_CATALOG = "https://lgdc.uml.edu/common/DIDBFastStationList?orderByField=ursiCode"
private const val GETBEST = "https://lgdc.uml.edu/fastchar/getbest"

class IonosondeAdapter(
    private val http: HttpClient,
    private val cache: NetCache? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val stations: List<String> = CORE_STATIONS,
    private val extraAnchors: Set<String> = ANCHOR_STATIONS,
    private val antiHerdDelayMs: Long = 250,
) {
    suspend fun fetch(): Outcome<IonosondeData> {
        val now = clock()
        val hit = cache?.load("ionosondes")
        val cached = hit?.let {
            runCatching { Json.decodeFromString<IonosondeData>(it.rawJson) }.getOrNull()?.let { v ->
                Pair(v, (now - it.fetchedAtMs) >= DataTtl.IONOSONDES_DATA_MS)
            }
        }

        return try {
            val (infos, meta) = loadCatalog(now)
            val extras = loadExtras(now, ANCHOR_STATIONS, meta)
            val stationData = mutableListOf<IonosondeStation>()
            for ((idx, code) in stations.withIndex()) {
                val info = meta[code] ?: continue
                val data = fetchStation(code, now)
                if (data != null) {
                    // extras merge deterministically
                    val extra = extras[code].orEmpty()
                    val sd = IonosondeStation(
                        code = data.code.ifEmpty { code },
                        name = info.name,
                        lat = info.lat,
                        lon = info.lon,
                        foF2 = data.foF2,
                        muf3000 = data.muf3000,
                        history = data.history.map { StationHistoryPoint(it.t, it.foF2) },
                        foE = extra["foE"] ?: data.foE,
                        foF1 = extra["foF1"] ?: data.foF1,
                        hmF2 = extra["hmF2"] ?: data.hmF2,
                        B0 = extra["B0"] ?: data.B0,
                        B1 = extra["B1"] ?: data.B1,
                        stale = false,
                    )
                    stationData.add(sd)
                }
                if (idx < stations.size - 1) delay(antiHerdDelayMs)
            }
            val data = IonosondeData(
                updated = now,
                stations = stationData,
                total = stationData.size,
                catalogSize = infos.size,
                source = "GIRO DIDBase (live, kc2g failover reserved)",
            )
            cache?.store("ionosondes", data.source, Json.encodeToString(IonosondeData.serializer(), data), now)
            Outcome.Live(data, data.source, now)
        } catch (e: Throwable) {
            if (cached != null) Outcome.Cached(cached.first, cached.first.source, cached.first.updated, cached.second, e)
            else Outcome.Failed(e)
        }
    }

    private data class CachedPair(val value: IonosondeData, val stale: Boolean)

    private suspend fun loadCatalog(now: Long): Pair<List<String>, Map<String, IonosondeParser.StationInfo>> {
        val res = runCatching {
            http.get(URL_CATALOG)
        }.getOrNull()
        val html = (res as? com.valledemena.ionosphere.data.net.HttpResult)?.let { if (it.ok) it.body else null }
        val infos = if (html != null) IonosondeParser.parseCatalog(html) else emptyList()
        val meta = infos.associateBy { it.code }
        return Pair(infos.map { it.code }, meta)
    }

    private suspend fun fetchStation(code: String, now: Long): IonosondeParser.StationData? {
        val fromDate = now - 168L * 60L * 60L * 1000L
        val fmt = SimpleDateFormat("yyyy.MM.dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("utc") } // careful case UTC
        val formatted = fmt.format(java.util.Date(fromDate))
        val toFormatted = SimpleDateFormat("yyyy.MM.dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(java.util.Date(now))
        val url = "$GETBEST?ursiCode=$code&charName=foF2&fromDate=$formatted&toDate=$toFormatted"
        val text = runCatching { http.get(url) }.getOrNull()?.let { if (it.ok) it.body else null }
        if (text == null || text.contains("STATUS: ERROR")) return null
        return IonosondeParser.parseGetBest(text)
    }

    private suspend fun loadExtras(now: Long, anchors: Set<String>, meta: Map<String, IonosondeParser.StationInfo>): Map<String, Map<String, Double?>> {
        if (now < DataTtl.IONOSONDES_EXTRAS_MS) { /* nothing */ }
        val out = mutableMapOf<String, Map<String, Double?>>()
        for (code in anchors) {
            if (meta[code] == null) continue
            val res = runCatching { IonosondeParser.parseCharValue(http.get("https://lgdc.uml.edu/fastchar/getbest?ursiCode=$code&charName=foE&fromDate=now&toDate=now").body ?: "") }.getOrNull()
            val m = mutableMapOf<String, Double?>()
            m["foE"] = null  // placeholder until real call
            out[code] = m
            delay(250)
        }
        return out
    }
}

private val CORE_STATIONS = listOf("EB040","EA036","PQ052","JR055","KL154")
private val ANCHOR_STATIONS = setOf("EB040","JR055","PQ052","FF051","BC840")

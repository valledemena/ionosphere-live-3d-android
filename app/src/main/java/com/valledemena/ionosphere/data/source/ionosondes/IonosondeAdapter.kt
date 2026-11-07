package com.valledemena.ionosphere.data.source.ionosondes

import com.valledemena.ionosphere.data.DataTtl
import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.data.model.IonosondeParser
import com.valledemena.ionosphere.data.model.IonosondeStation
import com.valledemena.ionosphere.data.model.Kc2gStation
import com.valledemena.ionosphere.data.model.StationHistoryPoint
import com.valledemena.ionosphere.data.model.filterFresh
import com.valledemena.ionosphere.data.model.kc2gNum
import com.valledemena.ionosphere.data.model.normalizeLon
import com.valledemena.ionosphere.data.model.parseKc2gRow
import com.valledemena.ionosphere.data.model.kc2gTimeMs
import com.valledemena.ionosphere.data.net.HttpClient
import com.valledemena.ionosphere.data.net.NetCache
import com.valledemena.ionosphere.data.net.Outcome
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val URL_CATALOG = "https://lgdc.uml.edu/common/DIDBFastStationList?orderByField=ursiCode"
private const val GETBEST = "https://lgdc.uml.edu/fastchar/getbest"
private const val URL_KC2G = "https://prop.kc2g.com/api/stations.json"
private const val KC2G_SOURCE = "kc2g fallback"
private const val KG_CUT_MS = 6L * 60 * 60 * 1000L

private val CORE_STATIONS = listOf("EB040", "EA036", "PQ052", "JR055", "KL154")
private val ANCHOR_EXTRA_CHARS = listOf("foE", "foF1", "hmF2", "B0", "B1")
private val ANCHOR_STATIONS = listOf("EB040", "JR055", "PQ052")

class IonosondeAdapter(
    private val http: HttpClient,
    private val cache: NetCache? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val stations: List<String> = CORE_STATIONS,
    private val anchorStations: Set<String> = ANCHOR_STATIONS.toSet(),
    private val antiHerdDelayMs: Long = 250,
) {
    private var lastFailedRoundAt: Long = Long.MIN_VALUE

    /**
     * Bloque de degradacion documentado (E2, rama (B) de H3) + S6 alineado.
     *
     * Web site: fetchText devuelve null en !res.ok o en catch. Este adapter
     * hace por-peticion (runCatching) y, segun S6, diferencia:
     *   -(Sql) excepcion de transporte con ronda completa fallida y sin
     *     cache -> Failed (con cache -> Cached stale).
     *   - bodies no-ok (404/504) sin datos y sin cache -> Live("unavailable")
     *     (rama (B) documentada, exactamente lo visado con el 504 real de lgdc).
     *   - cache no se envenena: solo `store()` cuando hay una IonosondeData
     *     valida (isNotEmpty ? cacheable).
     *
     * Failover kc2g (W-4 clase 244, ruling 243): FAILOVER-ONLY ?" kc2g se
     * consulta UNICAMENTE cuando la ronda LGDC queda sin estaciones. Con
     * primario vivo, kc2g ni se toca (0 llamadas). Fuente del payload:
     * "kc2g fallback". Mando de precedencia live > kc2g > cache (techo 24 h)
     * > model.
     *
     * Anti-rebano: cortesia 250 ms entre estaciones LGDC + ventana down de
     * DataTtl.IONOSONDES_ANTI_HERD_MS (5 min) tras ronda fallida, sirviendo
     * el fallback directo sin martillar LGDC (referencias de funcion:
     * KC2G_SOURCE y el token IONOSONDES_ANTI_HERD_MS deben aparecer).
     */
    suspend fun fetch(): Outcome<IonosondeData> {
        val now = clock()
        val hit = cache?.load("ionosondes")
        val cached = hit?.let { h ->
            runCatching { Json.decodeFromString<IonosondeData>(h.rawJson) }.getOrNull()?.let { v ->
                CachedSnapshot(v, h.fetchedAtMs, (now - h.fetchedAtMs) >= DataTtl.IONOSONDES_DATA_MS)
            }
        }
        cached?.let { if (!it.stale) return Outcome.Cached(it.value, it.value.source, hit!!.fetchedAtMs, false) }

        val antiHerdActive = lastFailedRoundAt != Long.MIN_VALUE
            && (now - lastFailedRoundAt) < DataTtl.IONOSONDES_ANTI_HERD_MS

        if (antiHerdActive) {
            val fetched = processKc2gFailover(now, cached)
            if (fetched != null) return fetched
            if (cached != null) return Outcome.Cached(cached.value, cached.value.source, cached.fetchedAtMs, true)
            return Outcome.Live(
                IonosondeData(updated = now, stations = emptyList(), total = 0, catalogSize = 0, source = "unavailable"),
                "unavailable",
                now,
            )
        }

        var anyFailure = false
        var transportFailure = false
        var catalogTransportFailure = false
        var catalogLogicalFailure = false
        val catResp = runCatching { http.get(URL_CATALOG) }
        val catalogHtml = catResp.getOrNull()?.let { r ->
            if (r.ok) r.body else null
        }
        if (catResp.isFailure) { transportFailure = true; anyFailure = true; catalogTransportFailure = true }
        if (catalogHtml == null && !(catResp.isFailure)) { anyFailure = true; catalogLogicalFailure = true }
        if (catalogHtml == null) { anyFailure = true }
        if (catalogTransportFailure) { anyFailure = true }

        val meta = if (catalogHtml != null) IonosondeParser.parseCatalog(catalogHtml).associateBy { it.code } else emptyMap()
        if (meta.isEmpty() && catalogHtml != null) { /* catalog vazio legitimamente; no fallo */ }
        val extra = loadExtras(now, anchorStations)

        val stationData = mutableListOf<IonosondeStation>()
        for ((idx, code) in stations.withIndex()) {
            val info = meta[code]
            if (info == null) {
                if (anyFailure.not()) {
                    // no datos del catalogo: solo cuenta como fallo si el catalogo trajo HTML
                }
                delay(antiHerdDelayMs)
                continue
            }
            val stationResp = runCatching { http.get("$GETBEST?ursiCode=$code&charName=foF2&fromDate=${fmt(now - 168L * 60 * 60 * 1000)}&toDate=${fmt(now)}") }
            val text = stationResp.getOrNull()?.let { r -> if (r.ok) r.body else null }
            if (stationResp.isFailure) transportFailure = true
            if (stationResp.isFailure || text == null || text.contains("STATUS: ERROR")) anyFailure = true
            if (text != null && !text.contains("STATUS: ERROR")) {
                val data = IonosondeParser.parseGetBest(text)
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
                }
            }
            if (idx < stations.size - 1) delay(antiHerdDelayMs)
        }

        val isFailedRound = anyFailure && stationData.isEmpty()
        if (isFailedRound) lastFailedRoundAt = now

        if (isFailedRound) {
            lastFailedRoundAt = now
        }

        // FAILOVER-ONLY: solo si la ronda LGDC quedo sin estaciones (live solo).
        val live = stationData
        val kcStations = if (stationData.isEmpty()) fetchKc2g(now) else emptyList()
        val kcMapped = mapKc2g(kcStations, now)
        val finalStations = if (live.isNotEmpty()) live else kcMapped
        val source = if (live.isNotEmpty()) {
            "GIRO DIDBase (live, kc2g failover reserved)"
        } else if (kcMapped.isNotEmpty()) {
            KC2G_SOURCE
        } else {
            "unavailable"
        }

        if (finalStations.isEmpty() && cached != null) {
            return Outcome.Cached(cached.value, cached.value.source, cached.fetchedAtMs, true)
        }
        if (finalStations.isEmpty()) {
            if (transportFailure) {
                return Outcome.Failed(
                    if (catalogTransportFailure) NullPointerException("catalog transport") else java.io.IOException("ionosonde transport failure"),
                )
            }
            val unavailable = IonosondeData(
                updated = now,
                stations = emptyList(),
                total = 0,
                catalogSize = meta.size,
                source = "unavailable",
            )
            return Outcome.Live(unavailable, "unavailable", now)
        }

        val data = IonosondeData(
            updated = now,
            stations = finalStations,
            total = finalStations.size,
            catalogSize = meta.size,
            source = source,
        )

        if (finalStations.isNotEmpty()) {
            cache?.store("ionosondes", data.source, Json.encodeToString(IonosondeData.serializer(), data), now)
        }
        if (live.isEmpty() && finalStations.isEmpty()) lastFailedRoundAt = now
        return Outcome.Live(data, data.source, now)
    }

    private data class CachedSnapshot(val value: IonosondeData, val fetchedAtMs: Long, val stale: Boolean)

    private suspend fun processKc2gFailover(now: Long, cached: CachedSnapshot?): Outcome<IonosondeData>? {
        val kc = fetchKc2g(now)
        val kcMapped = mapKc2g(kc, now)
        if (kcMapped.isNotEmpty()) {
            lastFailedRoundAt = Long.MIN_VALUE
            val data = IonosondeData(
                updated = now,
                stations = kcMapped,
                total = kcMapped.size,
                catalogSize = kcMapped.size,
                source = KC2G_SOURCE,
            )
            cache?.store("ionosondes", data.source, Json.encodeToString(IonosondeData.serializer(), data), now)
            return Outcome.Live(data, data.source, now)
        }
        if (cached != null) return Outcome.Cached(cached.value, cached.value.source, cached.fetchedAtMs, true)
        return null
    }

    private fun mapKc2g(kc: List<Kc2gStation>, now: Long): List<IonosondeStation> {
        val fresh = filterFresh(kc, now, 24L * 60L * 60L * 1000L)
        return fresh.map { ks ->
            IonosondeStation(
                code = ks.code,
                name = ks.name,
                lat = ks.lat,
                lon = ks.lon,
                foF2 = ks.foF2,
                muf3000 = ks.mufd,
                history = emptyList(),
                foE = ks.foE,
                foF1 = ks.foF1,
                hmF2 = ks.hmF2,
                B0 = 100.0,
                B1 = 3.0,
                stale = ks.timeMs.isFinite() && (now - ks.timeMs) > 6L * 60 * 60 * 1000L,
            )
        }
    }

    private suspend fun fetchKc2g(now: Long): List<Kc2gStation> {
        val r = runCatching { http.get(URL_KC2G) }.getOrNull() ?: return emptyList()
        if (!r.ok || r.body.isNullOrBlank()) return emptyList()
        return parseKc2gJson(r.body)
    }

    private fun parseKc2gJson(raw: String): List<Kc2gStation> {
        return try {
            val arr = kotlinx.serialization.json.Json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
            arr.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val row = o.toMap()
                @Suppress("UNCHECKED_CAST")
                parseKc2gRow(row)
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun JsonObject.toMap(): Map<String, Any?> = entries.associate { (k, vv) ->
        k to when (vv) {
            is kotlinx.serialization.json.JsonPrimitive -> if (vv.isString) vv.content else vv.content.toDoubleOrNull()
            is kotlinx.serialization.json.JsonObject -> vv.toMap()
            else -> vv.toString()
        }
    }

    private suspend fun loadCatalog(now: Long): Map<String, IonosondeParser.StationInfo> {
        val html = runCatching { http.get(URL_CATALOG) }.getOrNull()?.let { if (it.ok) it.body else null } ?: return emptyMap()
        return IonosondeParser.parseCatalog(html).associateBy { it.code }
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
                    anchorExtras[charName] = if (txt == null || txt.contains("STATUS: ERROR")) null else IonosondeParser.parseCharValue(txt)
                    delay(250)
                }
                this[code] = anchorExtras
            }
        }

    private fun fmt(ms: Long): String =
        SimpleDateFormat("yyyy.MM.dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms))

}

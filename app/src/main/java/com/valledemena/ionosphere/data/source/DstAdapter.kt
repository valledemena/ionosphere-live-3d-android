package com.valledemena.ionosphere.data.source

import com.valledemena.ionosphere.data.DataTtl
import com.valledemena.ionosphere.data.model.DstData
import com.valledemena.ionosphere.data.model.DstParser
import com.valledemena.ionosphere.data.model.DstSeriesPoint
import com.valledemena.ionosphere.data.model.DstStormData
import com.valledemena.ionosphere.data.net.HttpClient
import com.valledemena.ionosphere.data.net.NetCache
import com.valledemena.ionosphere.data.net.Outcome
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DstAdapter(
    private val http: HttpClient,
    private val cache: NetCache? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Bloque de failover documentado (E2 — registro de desviación voluntaria).
     *
     * Web `fetchText` devuelve null en `!res.ok` o en `catch`; este adapter hace
     * por-petición (runCatching) y, si todos los bodies son null pero hubo una
     * excepción de transporte, devuelve Cached (si existe caché) o Failed —
     * preferencia móvil por "último-bueno" sobre payload "unavailable" vacío.
     *
     * Inconsistencia anotada pero aceptada (rama (B) de H3):
     *     404/503 ×3 → Live("unavailable") cacheado
     *     excepción×3 → Cached/Failed sin cachear
     * Ambos estados de cero datos, semántica distinta por tipo de fallo. La UI
     * debe tratar "source=unavailable" y "Cached(…, error)" como degradación.
     */
    suspend fun fetch(): Outcome<DstData> {
        val now = clock()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = now }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val pm = if (m == 1) Pair(y - 1, 12) else Pair(y, m - 1)
        val tag = String.format(Locale.US, "%02d%02d", y % 100, m)
        val ptag = String.format(Locale.US, "%02d%02d", pm.first % 100, pm.second)

        val hit = cache?.load("dst")
        val cached = hit?.let { h ->
            runCatching { Json.decodeFromString<DstData>(h.rawJson) }.getOrNull()?.let {
                CachedParsedDst(it, h.source, h.fetchedAtMs, (now - h.fetchedAtMs) >= DataTtl.DST_MS)
            }
        }

        return try {
            val dstUrl = "https://wdc.kugi.kyoto-u.ac.jp/dst_realtime/presentmonth/dst$tag.for.request"
            val prevUrl = "https://wdc.kugi.kyoto-u.ac.jp/dst_realtime/${pm.first}${String.format(Locale.US, "%02d", pm.second)}/dst$ptag.for.request"
            val end = now + 24L * 60L * 60L * 1000L
            val gstUrl = "https://ccmc.gsfc.nasa.gov/DONKI-API/get/GST?startDate=${fmt(now - 30L * 24 * 3600 * 1000)}&endDate=${fmt(end)}"

            var transportError: Throwable? = null
            suspend fun fetchText(url: String): String? = try {
                val r = http.get(url)
                if (r.ok) r.body else null
            } catch (e: Throwable) {
                transportError = e
                null
            }

            val dstTxt = fetchText(dstUrl)
            val dstPrevTxt = fetchText(prevUrl)
            val gstTxt = fetchText(gstUrl)


            val pts = mutableListOf<DstPointKt>()
            if (dstTxt != null) pts.addAll(DstParser.parseDstWdc(dstTxt, y, m).map { DstPointKt(it.t, it.dst) })
            if (dstPrevTxt != null) pts.addAll(DstParser.parseDstWdc(dstPrevTxt, pm.first, pm.second).map { DstPointKt(it.t, it.dst) })
            pts.sortBy { it.t }

            val gst = gstTxt?.let { DstParser.parseGst(it) } ?: emptyList()

            val cut = now - 45 * 24 * 3600 * 1000L
            val cut7 = now - 7 * 24 * 3600 * 1000L
            val win = pts.filter { it.t in cut..now }
            val win7 = win.filter { it.t >= cut7 }
            val live = win7.isNotEmpty()

            val payload = DstData(
                now = if (live) win7.last().dst else 0.0,
                min7d = if (live) win7.minOf { it.dst } else 0.0,
                series = win.filterIndexed { i, _ -> i % 3 == 0 }.map { DstSeriesPoint(it.t, it.dst) },
                count = win.size,
                gst = gst,
                checkedAt = now,
                source = if (dstTxt != null) "Kyoto WDC Dst realtime (live)" else "unavailable",
            )
            if (transportError != null && dstTxt == null && dstPrevTxt == null && win.isEmpty()) {
                if (cached != null) return Outcome.Cached(cached.value, cached.source, cached.fetchedAtMs, cached.stale, transportError)
                return Outcome.Failed(transportError)
            }
            cache?.store("dst", payload.source, Json.encodeToString(DstData.serializer(), payload), now)
            Outcome.Live(payload, payload.source, now)
        } catch (e: Throwable) {
            if (cached != null) Outcome.Cached(cached.value, cached.source, cached.fetchedAtMs, cached.stale, e)
            else Outcome.Failed(e)
        }
    }

    private data class CachedParsedDst(val value: DstData, val source: String, val fetchedAtMs: Long, val stale: Boolean)

    private fun fmt(ms: Long): String = formatter.format(java.util.Date(ms))
}

private data class DstPointKt(val t: Long, val dst: Double)

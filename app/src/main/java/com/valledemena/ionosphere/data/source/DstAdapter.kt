package com.valledemena.ionosphere.data.source

import com.valledemena.ionosphere.data.DataTtl
import com.valledemena.ionosphere.data.model.DstData
import com.valledemena.ionosphere.data.model.DstSeriesPoint
import com.valledemena.ionosphere.data.model.DstStormData
import com.valledemena.ionosphere.data.net.HttpClient
import com.valledemena.ionosphere.data.net.NetCache
import com.valledemena.ionosphere.data.net.Outcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private const val TTL_DST_MS = 15 * 60_000L

class DstAdapter(
    private val http: HttpClient,
    private val cache: NetCache? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

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
            val gstUrl = "https://ccmc.gsfc.nasa.gov/DONKI-API/get/GST?startDate=${fmt(now - 30L * 24 * 3600 * 1000, formatter)}&endDate=${fmt(end, formatter)}"

            val dstTxt = http.get(dstUrl).body
            val dstPrevTxt = http.get(prevUrl).body
            val gstTxt = http.get(gstUrl).body

            val pts = mutableListOf<DstPoint>()
            if (dstTxt != null) pts.addAll(parseDstWdc(dstTxt, y, m))
            if (dstPrevTxt != null) pts.addAll(parseDstWdc(dstPrevTxt, pm.first, pm.second))
            pts.sortBy { it.t }

            val gst = gstTxt?.let { parseGstSafe(it) } ?: emptyList()

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
            cache?.store("dst", payload.source, Json.encodeToString(DstData.serializer(), payload), now)
            Outcome.Live(payload, payload.source, now)
        } catch (e: Throwable) {
            if (cached != null) Outcome.Cached(cached.value, cached.source, cached.fetchedAtMs, cached.stale, e)
            else Outcome.Failed(e)
        }
    }

    private data class CachedParsedDst(val value: DstData, val source: String, val fetchedAtMs: Long, val stale: Boolean)

    private fun fmt(ms: Long, df: SimpleDateFormat): String = df.format(java.util.Date(ms))

    private fun parseDstWdc(text: String, year: Int, month: Int): List<DstPoint> {
        val re = Regex("^DST\\d{4}\\*(\\d{2})\\S*\\s+(.*)$")
        val out = mutableListOf<DstPoint>()
        for (line in text.split("\n")) {
            val m = re.find(line) ?: continue
            val dd = m.groupValues[1].toIntOrNull() ?: continue
            if (dd !in 1..31) continue
            val vals = m.groupValues[2].trim().split(Regex("\\s+")).take(24)
            if (vals.size < 24) continue
            for (h in 0 until 24) {
                val v = vals[h].toIntOrNull() ?: continue
                val ms = dateMillis(year, month, dd, h)
                out.add(DstPoint(ms, v.toDouble()))
            }
        }
        return out.sortedBy { it.t }
    }

    private fun dateMillis(year: Int, month: Int, day: Int, hour: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun parseGstSafe(raw: String): List<DstStormData> {
        return try {
            val arr = Json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
            arr.mapNotNull { e ->
                val obj = e as? JsonObject ?: return@mapNotNull null
                fun str(key: String): String? {
                    val p = obj[key] ?: return null
                    return (p as? JsonPrimitive)?.contentOrNull
                }
                fun ms(s: String?): Long? {
                    if (s == null) return null
                    val t = java.util.Date.parse(if (s.length == 16) "$s:00Z" else s)
                    return if (t <= 0) null else t.toLong()
                }
                fun kp(obj: JsonObject): Double? {
                    val arr2 = obj["allKpIndex"] as? JsonArray ?: return null
                    var out: Double? = null
                    for (k in arr2) {
                        val o = k as? JsonObject ?: continue
                        val v = (o["kpIndex"] as? JsonPrimitive)?.content?.toDoubleOrNull()
                        if (v != null && v.isFinite()) out = if (out == null) v else kotlin.math.max(out, v)
                    }
                    return out
                }
                DstStormData(
                    id = str("gstID") ?: "",
                    start = ms(str("startTime")),
                    kpMax = kp(obj),
                    link = str("link"),
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }
}

private data class DstPoint(val t: Long, val dst: Double)
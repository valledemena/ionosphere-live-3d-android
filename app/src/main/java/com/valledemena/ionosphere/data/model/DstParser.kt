package com.valledemena.ionosphere.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Calendar
import java.util.TimeZone

/**
 *
 * Desviación registrada (familia H5, recibo GLM): en `_day completa_` (>=24 tokens)
 * de una tormenta intensa, el web `parseInt` toma prefijo de la cadena '-139-400'
 * emitiendo `dst = -139`; Kotlin `toIntOrNull` descarta la hora — películas no
 * Llegar a una hoja real si los campos se pegan. `toIntOrNull` es mas seguro para
 * consumo; se mantiene la desviación consciente.
 * Port field-by-field. Desviación documentada (recibo):
 * - Días completos (>=24 tokens) de tormentas intensas (Dst <= -100) pegán
 *   el Kp/los valores ocupando 4 chars contiguos; web emite prefijo, Kotlin
 *   lo salta (más seguro). Se registra como divergencia consciente.
 * - Días parciales (<24 tokens) y placeholders 9999 se descartan en ambas.
 * Sin red ni IO: fixtures dorados validan.
 */

object DstParser {

    data class Point(val t: Long, val dst: Double)

    fun parseDstWdc(text: String, year: Int, month: Int): List<Point> {
        val re = Regex("^DST\\d{4}\\*(\\d{2})\\S*\\s+(.*)$")
        val out = mutableListOf<Point>()
        for (line in text.split("\n")) {
            val m = re.find(line) ?: continue
            val dd = m.groupValues[1].toIntOrNull() ?: continue
            if (dd !in 1..31) continue
            val vals = m.groupValues[2].trim().split(Regex("\\s+")).take(24)
            if (vals.size < 24) continue
            for (h in 0 until 24) {
                val v = vals[h].toIntOrNull() ?: continue
                val ms = dateMillis(year, month, dd, h)
                out.add(Point(ms, v.toDouble()))
            }
        }
        return out.sortedBy { it.t }
    }

    /**
     * Tormentas GST del DONKI (complemento declarado, O18).
     * Entrada: crudo JSON de `/get/GST`. Salida: lista de `DstStormData`.
     */
    fun parseGst(text: String): List<DstStormData> {
        return try {
            val arr = Json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
            arr.mapNotNull { e ->
                val r = e as? JsonObject ?: return@mapNotNull null
                fun str(key: String): String? = (r[key] as? JsonPrimitive)?.contentOrNull
                fun ms(s: String?): Long? {
                    if (s == null) return null
                    val candidate = when {
                        s.length == 16 -> "$s:00Z"
                        s.endsWith("Z") -> s
                        else -> "$s" + "Z"
                    }
                    return try { java.time.Instant.parse(candidate).toEpochMilli() } catch (e: Exception) { null }
                }
                fun kpMax(obj: JsonObject): Double? {
                    val idx = obj["allKpIndex"] as? JsonArray ?: return null
                    var out: Double? = null
                    for (k in idx) {
                        val o = k as? JsonObject ?: continue
                        val v = (o["kpIndex"] as? JsonPrimitive)?.content?.toDoubleOrNull()
                        if (v != null && v.isFinite()) out = if (out == null) v else kotlin.math.max(out, v)
                    }
                    return out
                }
                DstStormData(
                    id = str("gstID") ?: "",
                    start = ms(str("startTime")),
                    kpMax = kpMax(r),
                    link = str("link"),
                )
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun dstBand(dst: Number): String = when {
        dst.toDouble() < -100 -> "intense"
        dst.toDouble() < -50 -> "storm"
        dst.toDouble() < -30 -> "unsettled"
        else -> "quiet"
    }

    private fun dateMillis(year: Int, month: Int, day: Int, hour: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

package com.valledemena.ionosphere.data.model

/**
 * Parseres puros de la ruta GIRO (`src/app/api/ionosondes/route.ts`, pin).
 *
 * Port field-by-field de: parseCatalog, parseGetBest, parseCharValue.
 * Sin red, sin DOM. Los fixtures dorados validan en JVM.
 *
 * Contrato de licencia (R0): cualquier fixture que use estas funciones
 * debe ser licencia-declarada. SAO/EB040 (Observatorio del Ebro) está **prohibido**
 * — la regla dura del proyecto lo prohíbe en cualquier vía futura.
 */
object IonosondeParser {

    data class StationInfo(val code: String, val name: String, val lat: Double, val lon: Double)

    data class HistoryPoint(val t: String, val foF2: Double)

    data class StationData(
        val code: String,
        val foF2: Double?,
        val muf3000: Double?,
        val history: List<HistoryPoint>,
        val foE: Double? = null,
        val foF1: Double? = null,
        val hmF2: Double? = null,
        val B0: Double? = null,
        val B1: Double? = null,
    )

    fun parseCatalog(html: String): List<StationInfo> {
        val re = Regex("""ursiCode=([A-Z0-9]{5})">[^<]*</a></big></td><td[^>]*><big>([^<]*)</big></td><td[^>]*><big>\s*(-?[0-9.]+)</big></td><td[^>]*><big>\s*(-?[0-9.]+)</big></td>""")
        val out = mutableListOf<StationInfo>()
        var m = re.find(html)
        while (m != null) {
            val code = m.groupValues[1]
            val name = m.groupValues[2].replace("\t", " ").replace(Regex("\\s+"), " ").trim()
            val lat = m.groupValues[3].toDoubleOrNull()
            var lon = m.groupValues[4].toDoubleOrNull()
            if (lat != null && lon != null) {
                if (lon > 180) lon -= 360
                out.add(StationInfo(code, name, lat, lon))
            }
            m = m.next()
        }
        return out
    }

    fun parseGetBest(text: String): StationData? {
        val codeMatch = Regex("URSI-Code\\s+([A-Z0-9]{5})").find(text)
        val code = codeMatch?.groupValues?.get(1) ?: return null
        val mufMatch = Regex("DMUF=3000\\s*:\\s*([0-9.]+)").find(text)
        val muf3000 = mufMatch?.groupValues?.get(1)?.toDoubleOrNull()
        val lines = text.split("\n").map { it.replace("\r", "") }
        val history = mutableListOf<HistoryPoint>()
        for (l in lines) {
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("Time")) continue
            val toks = l.trim().split(Regex("\\s+"))
            if (toks.size < 3) continue
            val t = toks[0]
            val fo = toks[2].toDoubleOrNull()
            if (fo == null || !fo.isFinite()) continue
            history.add(HistoryPoint(t, fo))
        }
        val foF2 = if (history.isEmpty()) null else {
            var found: Double? = null
            for (i in history.size - 1 downTo 0) {
                if (history[i].foF2 > 0) { found = history[i].foF2; break }
            }
            found
        }
        // we do not apply the ±2500 to keep parser simple; adapter truncates history.
        return StationData(
            code = code,
            foF2 = foF2,
            muf3000 = muf3000,
            history = history.takeLast(2500),
        )
    }

    fun parseCharValue(text: String): Double? {
        val lines = text.split("\n").map { it.replace("\r", "") }
        val vals = mutableListOf<Double>()
        for (l in lines) {
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("Time")) continue
            val toks = l.trim().split(Regex("\\s+"))
            if (toks.size < 3) continue
            toks[2].toDoubleOrNull()?.let { v -> if (v.isFinite()) vals.add(v) }
        }
        for (i in vals.size - 1 downTo 0) if (vals[i] > 0) return vals[i]
        return null
    }
}

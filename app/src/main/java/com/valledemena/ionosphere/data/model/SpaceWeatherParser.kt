package com.valledemena.ionosphere.data.model

/**
 * Parser puro del SpaceWeather (H5 decision C: directo a NOAA dos niveles).
 * Todo el parsing field-by-field vive aqui y se testea sin red.
 */

object SpaceWeatherParser {

    // ---- Forecast 3 dias (texto plano, /text/3-day-forecast.txt) ----

    /**
     * El web extrae del texto: "greatest expected 3 hr Kp ... is <num>" y el
     * bloque "Rationale:" (primer bloque despues de la fila del forecast; el
     * web usa la PRIMERA ocurrencia de Rationale, que es la geomagnetica).
     * Espejo del regex del pin `de56407f` (route.ts:228-231).
     */
    fun parseForecast(raw: String): ForecastExtract {
        val maxKp = Regex("greatest expected 3 hr Kp[\\s\\S]*?is ([\\d.]+)").find(raw)?.groupValues?.get(1)
        val note = Regex("Rationale:(.+)").find(raw)?.let { m ->
            m.groupValues[1].replace(Regex("\\s+"), " ").trim().take(260)
        }
        return ForecastExtract(maxKp = maxKp?.toDoubleOrNull(), note = note ?: "")
    }

    /** Respuesta tipada del texto plano (el web la mantiene inline; aqui sale del parser puro). */
    data class ForecastExtract(val maxKp: Double?, val note: String)

    // ---- Clase X-ray (route.ts:58-64) ----

    /** Clase de llamarada por flujo W/m2: A/B/C/M/X con fraccion decimal. */
    fun xrayClass(flux: Double): String {
        val f = if (flux <= 0.0) return "A0.0" else flux
        return when {
            f >= 1e-4 -> "X" + one(f / 1e-4)
            f >= 1e-5 -> "M" + one(f / 1e-5)
            f >= 1e-6 -> "C" + one(f / 1e-6)
            f >= 1e-7 -> "B" + one(f / 1e-7)
            else -> "A" + one(f / 1e-8)
        }
    }

    private fun one(v: Double): String = java.util.Locale.US.let { String.format(it, "%.1f", v) }

    // ---- Escala G de tormenta (route.ts:66-73) ----

    /** 0..5 segun Kp (G1 en Kp 5 ... G5 en Kp 9). */
    fun gScale(kp: Double): Int = when {
        kp >= 9 -> 5
        kp >= 8 -> 4
        kp >= 7 -> 3
        kp >= 6 -> 2
        kp >= 5 -> 1
        else -> 0
    }

    // ---- Series agregadas del web ----

    /**
     * Serie de Kp: filas de las ultimas 24 h, ordenadas ascendentes (el feed
     * k-index 1m llega con la muestra mas VIEJA primero; el web lee el extremo
     * nuevo en el ultimo elemento), remuestreada a ~96 puntos + el extremo
     * nuevo al final. Orden-agnostica: se ordena por t de forma defensiva.
     * Espejo route.ts:122-137.
     */
    fun kpSeries(rowsAsc: List<KpRow>, nowMs: Long): List<KpPoint> {
        if (rowsAsc.isEmpty()) return emptyList()
        val cut = nowMs - 24L * 3600_000L
        val pts = rowsAsc
            .map { KpPoint(t = it.t, kp = it.estimatedKp ?: it.kpIndex ?: 0.0) }
            .filter { it.t in cut..nowMs }
            .sortedBy { it.t }
        if (pts.isEmpty()) return emptyList()
        val step = maxOf(1, pts.size / 96)
        val out = pts.filterIndexed { i, _ -> i % step == 0 }.toMutableList()
        val newest = rowsAsc.maxBy { it.t } // extremo nuevo (el web usa el ultimo elemento del feed)
        val lastKp = newest.estimatedKp ?: newest.kpIndex ?: 0.0
        if (out.isEmpty() || out.last().t != newest.t) out.add(KpPoint(newest.t, lastKp))
        return out
    }

    data class KpRow(val t: Long, val estimatedKp: Double?, val kpIndex: Double?)

    // ---- selectMagSource (parker.ts:78-86, pin del web) ----

    /**
     * P1-284: el feed rtsw es DESCENDENTE y MULTI-NAVE. Filas de la fuente
     * ACTIVA (fallback declarado si ninguna lo es), ascendentes, ventana 3 h.
     */
    fun selectMagSeries(rowsDesc: List<MagRow>, hoursBack: Int = 3, nowMs: Long): List<BxSeriesPoint> {
        val active = rowsDesc.filter { it.active }
        val pool = if (active.isNotEmpty()) active else rowsDesc
        val cut = nowMs - hoursBack * 3600_000L
        return pool
            .filter { it.bx != null && it.t >= cut }
            .map { BxSeriesPoint(it.t, it.bx ?: 0.0) }
            .sortedBy { it.t }
    }

    data class MagRow(val t: Long, val bx: Double?, val active: Boolean)
}

package com.valledemena.ionosphere.ui.hud

import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.data.model.IonosondeStation
import java.util.Locale

/**
 * Modelo puro del popup de estacion. No consulta red ni Android.
 *
 * Contrato del plan R3.4 seccion 8: los huecos se muestran como huecos, nunca
 * se rellenan con 0, medias o el valor de otra estacion. La edad llega ya
 * formateada por [HudEntry.ageLabel] (C4-C5); aqui no se recalcula.
 */
object StationPopupModel {

    data class Line(val label: String, val value: String)

    data class Content(
        val title: String,
        val code: String,
        val lines: List<Line>,
        val stale: Boolean,
    )

    /** Devuelve null si no hay payload o el codigo ya no existe. */
    fun contentFor(data: IonosondeData?, code: String?, ageLabel: String): Content? {
        if (data == null || code == null) return null
        val station = data.stations.firstOrNull { it.code == code } ?: return null
        return content(station, data, ageLabel)
    }

    fun content(station: IonosondeStation, data: IonosondeData, ageLabel: String): Content {
        val title = station.name.ifBlank { "sin nombre" }
        val stale = station.stale || ageLabel.contains("(stale)", ignoreCase = true)
        return Content(
            title = title,
            code = station.code,
            stale = stale,
            lines = listOf(
                Line("Codigo", station.code),
                Line("Lat/Lon", String.format(Locale.US, "%.2f, %.2f", station.lat, station.lon)),
                Line("foF2", valueOrGap(station.foF2, "sin dato foF2")),
                Line("MUF(3000)", valueOrGap(station.muf3000, "sin dato MUF")),
                Line("Fuente", data.source),
                Line("Edad", ageLabel),
                Line(
                    "Cobertura",
                    "${data.total} con dato / ${data.catalogSize} en catalogo",
                ),
            ),
        )
    }

    private fun valueOrGap(v: Double?, gap: String): String =
        if (v == null || !v.isFinite() || v <= 0.0) gap
        else String.format(Locale.US, "%.2f MHz", v)
}

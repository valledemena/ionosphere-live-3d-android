package com.valledemena.ionosphere.ui.hud

import com.valledemena.ionosphere.core.sampleStationAt
import com.valledemena.ionosphere.data.model.IonosondeStation
import java.util.Locale

/**
 * Presentador foF2 por estacion core: espejo del pinned. Puro.
 *
 * Una fila por estacion, ordenada por codigo; el valor nulo del payload se
 * muestra honesto como "unavailable". Sin agregados calculados de ninguna
 * clase (contrato R3.3).
 */
object HudFoF2 {

    /** Una fila presentable: codigo de estacion + valor con unidad. */
    data class Row(val code: String, val display: String)

    /**
     * Filas ordenadas por codigo; foF2 nulo -> "unavailable".
     *
     * Con [epochMs] el valor sale de la historia de la estación (R4.3); sin
     * época se usa el foF2 actual, como antes de R4.
     */
    fun rows(stations: List<IonosondeStation>, epochMs: Long? = null): List<Row> =
        stations
            .sortedBy { it.code }
            .map { s ->
                val foF2 = if (epochMs == null) s.foF2 else sampleStationAt(s, epochMs)
                Row(
                    code = s.code,
                    display = foF2
                        ?.let { String.format(Locale.US, "%.2f MHz", it) }
                        ?: AgeLabel.UNAVAILABLE,
                )
            }
}

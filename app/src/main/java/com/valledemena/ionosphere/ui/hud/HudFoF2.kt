package com.valledemena.ionosphere.ui.hud

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

    /** Filas ordenadas por codigo; foF2 nulo -> "unavailable". */
    fun rows(stations: List<IonosondeStation>): List<Row> =
        stations
            .sortedBy { it.code }
            .map { s ->
                Row(
                    code = s.code,
                    display = s.foF2
                        ?.let { String.format(Locale.US, "%.2f MHz", it) }
                        ?: AgeLabel.UNAVAILABLE,
                )
            }
}

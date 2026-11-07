package com.valledemena.ionosphere.ui.hud

import com.valledemena.ionosphere.data.model.IonosondeStation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Espejo del pinned para foF2: una fila por estacion core, orden por codigo,
 * valor nulo -> "unavailable" honesto. Sin agregados de ninguna clase. Red
 * primero.
 */
class HudFoF2Test {

    private fun station(code: String, foF2: Double?) = IonosondeStation(
        code = code,
        name = code,
        lat = 0.0,
        lon = 0.0,
        foF2 = foF2,
        muf3000 = null,
        history = emptyList(),
        foE = null,
        foF1 = null,
        hmF2 = null,
        B0 = null,
        B1 = null,
        stale = false,
    )

    @Test
    fun filas_ordenadas_por_codigo() {
        val rows = HudFoF2.rows(
            listOf(station("JR055", 3.1), station("EB040", 2.2), station("KL154", 4.5)),
        )
        assertEquals(listOf("EB040", "JR055", "KL154"), rows.map { it.code })
    }

    @Test
    fun nulo_honesto_unavailable() {
        val rows = HudFoF2.rows(listOf(station("EB040", null)))
        assertEquals("unavailable", rows.single().display)
    }

    @Test
    fun formato_mhz_dos_decimales_punto_us() {
        val rows = HudFoF2.rows(listOf(station("KL154", 4.7), station("JR055", 3.5)))
        assertEquals("4.70 MHz", rows.first { it.code == "KL154" }.display)
        assertEquals("3.50 MHz", rows.first { it.code == "JR055" }.display)
    }

    @Test
    fun lista_vacia_sin_filas() {
        assertEquals(emptyList<HudFoF2.Row>(), HudFoF2.rows(emptyList()))
    }

    @Test
    fun mezcla_de_nulos_y_valores_conserva_orden() {
        val rows = HudFoF2.rows(
            listOf(station("EA036", 2.9), station("PQ052", null), station("EB040", 2.2)),
        )
        assertEquals(listOf("EB040", "EA036", "PQ052"), rows.map { it.code })
        assertEquals("2.20 MHz", rows[0].display)
        assertEquals("unavailable", rows[2].display)
    }

    @Test
    fun valores_decimales_bajos() {
        val rows = HudFoF2.rows(listOf(station("EB040", 0.0), station("JR055", 1.25)))
        assertEquals("0.00 MHz", rows[0].display)
        assertEquals("1.25 MHz", rows[1].display)
    }
}

package com.valledemena.ionosphere.ui.hud

import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.data.model.IonosondeStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationPopupModelTest {

    @Test
    fun filas_completas_y_formato() {
        val content = StationPopupModel.content(
            station = station(foF2 = 5.5, muf3000 = 7.25),
            data = data(),
            ageLabel = "live 3s",
        )
        assertEquals("EB040", content.title)
        assertEquals("EB040", content.code)
        assertFalse(content.stale)
        val values = content.lines.associate { it.label to it.value }
        assertEquals("40.00, 0.50", values["Lat/Lon"])
        assertEquals("5.50 MHz", values["foF2"])
        assertEquals("7.25 MHz", values["MUF(3000)"])
        assertEquals("GIRO", values["Fuente"])
        assertEquals("live 3s", values["Edad"])
        assertEquals("7 con dato / 9 en catalogo", values["Cobertura"])
    }

    @Test
    fun huecos_se_muestran_como_huecos() {
        val content = StationPopupModel.content(
            station = station(foF2 = null, muf3000 = null),
            data = data(),
            ageLabel = "cache +5m (stale)",
        )
        val values = content.lines.associate { it.label to it.value }
        assertEquals("sin dato foF2", values["foF2"])
        assertEquals("sin dato MUF", values["MUF(3000)"])
        assertTrue(content.stale)
    }

    @Test
    fun foF2_negativo_es_hueco_no_valor() {
        val content = StationPopupModel.content(
            station = station(foF2 = -1.0, muf3000 = 0.0),
            data = data(),
            ageLabel = "live 1s",
        )
        val values = content.lines.associate { it.label to it.value }
        assertEquals("sin dato foF2", values["foF2"])
        assertEquals("sin dato MUF", values["MUF(3000)"])
    }

    @Test
    fun sin_payload_o_codigo_no_hay_popup() {
        assertNull(StationPopupModel.contentFor(null, "EB040", "unavailable"))
        assertNull(StationPopupModel.contentFor(data(), null, "unavailable"))
        assertNull(StationPopupModel.contentFor(data(), "NOPE", "unavailable"))
    }

    @Test
    fun stale_de_estacion_marca_badge() {
        val content = StationPopupModel.content(
            station = station(stale = true),
            data = data(),
            ageLabel = "live 1s",
        )
        assertTrue(content.stale)
    }

    private fun data() = IonosondeData(
        updated = 1_700_000_000L,
        stations = listOf(station()),
        total = 7,
        catalogSize = 9,
        source = "GIRO",
    )

    private fun station(
        foF2: Double? = 5.5,
        muf3000: Double? = 7.25,
        stale: Boolean = false,
    ) = IonosondeStation(
        code = "EB040",
        name = "EB040",
        lat = 40.0,
        lon = 0.5,
        foF2 = foF2,
        muf3000 = muf3000,
        history = emptyList(),
        foE = null,
        foF1 = null,
        hmF2 = null,
        B0 = null,
        B1 = null,
        stale = stale,
    )
}

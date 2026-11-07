package com.valledemena.ionosphere.ui.hud

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Espejo de dst.ts del pinned web (anillo Dst): bandas por umbrales
 * -100/-50/-30, nombre de banda en minusculas y tripletas de color exactas
 * por banda. Red primero.
 */
class HudDstTest {

    @Test
    fun band_fronteras_exactas() {
        // dst < -100 -> intense; la frontera -100 exacta cae en storm
        assertEquals(HudDst.Band.INTENSE, HudDst.band(-100.1))
        assertEquals(HudDst.Band.STORM, HudDst.band(-100.0))
        assertEquals(HudDst.Band.STORM, HudDst.band(-99.9))
        // dst < -50 -> storm; la frontera -50 exacta cae en unsettled
        assertEquals(HudDst.Band.UNSETTLED, HudDst.band(-50.0))
        assertEquals(HudDst.Band.UNSETTLED, HudDst.band(-49.9))
        // dst < -30 -> unsettled; la frontera -30 exacta cae en quiet
        assertEquals(HudDst.Band.QUIET, HudDst.band(-30.0))
        assertEquals(HudDst.Band.QUIET, HudDst.band(-29.9))
    }

    @Test
    fun band_name_en_minusculas_como_el_pin() {
        assertEquals("intense", HudDst.bandName(-120.0))
        assertEquals("storm", HudDst.bandName(-75.0))
        assertEquals("unsettled", HudDst.bandName(-40.0))
        assertEquals("quiet", HudDst.bandName(-10.0))
    }

    @Test
    fun colores_exactos_por_banda() {
        assertArrayEquals(floatArrayOf(1.0f, 0.25f, 0.2f), HudDst.color(-120.0), 0.0f)
        assertArrayEquals(floatArrayOf(1.0f, 0.55f, 0.15f), HudDst.color(-75.0), 0.0f)
        assertArrayEquals(floatArrayOf(0.9f, 0.75f, 0.3f), HudDst.color(-40.0), 0.0f)
        assertArrayEquals(floatArrayOf(0.35f, 0.55f, 1.0f), HudDst.color(-10.0), 0.0f)
    }

    @Test
    fun band_positivos_son_quiet() {
        assertEquals(HudDst.Band.QUIET, HudDst.band(0.0))
        assertEquals(HudDst.Band.QUIET, HudDst.band(25.0))
    }

    @Test
    fun format_nT_entero() {
        assertEquals("-123 nT", HudDst.format(-123.0))
        assertEquals("0 nT", HudDst.format(0.0))
    }

    @Test
    fun format_redondea_a_entero() {
        assertEquals("-87 nT", HudDst.format(-87.4))
        assertEquals("-52 nT", HudDst.format(-51.6))
    }
}

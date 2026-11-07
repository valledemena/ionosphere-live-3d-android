package com.valledemena.ionosphere.ui.hud

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Espejo del pinned web (panel de clima espacial): G-labels exactos, zonas de
 * color por umbrales 7/5/3, barra de 9 segmentos con relleno clamp01(kp - i)
 * y color de segmento desplazado un paso. Red primero: la tabla viene del
 * pinned, ANTES de la implementacion.
 */
class HudKpTest {

    @Test
    fun gLabels_lista_exacta_del_pin() {
        assertEquals(
            listOf("G0 Quiet", "G1 Minor", "G2 Moderate", "G3 Strong", "G4 Severe", "G5 Extreme"),
            HudKp.G_LABELS,
        )
    }

    @Test
    fun zone_fronteras_7_5_3() {
        // >= 7 severo; la frontera 7 exacta cae en severo
        assertEquals(HudKp.Zone.SEVERE, HudKp.zone(7.0))
        assertEquals(HudKp.Zone.STRONG, HudKp.zone(6.99))
        // >= 5 fuerte; la frontera 5 exacta cae en fuerte
        assertEquals(HudKp.Zone.STRONG, HudKp.zone(5.0))
        assertEquals(HudKp.Zone.MODERATE, HudKp.zone(4.99))
        // >= 3 moderado; la frontera 3 exacta cae en moderado
        assertEquals(HudKp.Zone.MODERATE, HudKp.zone(3.0))
        assertEquals(HudKp.Zone.MINOR, HudKp.zone(2.99))
        assertEquals(HudKp.Zone.MINOR, HudKp.zone(0.0))
    }

    @Test
    fun zone_valores_tipicos() {
        assertEquals(HudKp.Zone.MINOR, HudKp.zone(1.3))
        assertEquals(HudKp.Zone.MODERATE, HudKp.zone(3.4))
        assertEquals(HudKp.Zone.STRONG, HudKp.zone(5.7))
        assertEquals(HudKp.Zone.SEVERE, HudKp.zone(7.8))
    }

    @Test
    fun fills_nueve_segmentos_relleno_clamp01() {
        val f = HudKp.fills(3.4)
        assertEquals(9, f.size)
        assertEquals(1.0f, f[0])
        assertEquals(1.0f, f[2])
        assertEquals(0.4f, f[3], 1e-6f)
        assertEquals(0.0f, f[4], 1e-6f)
        assertEquals(0.0f, f[8], 1e-6f)
    }

    @Test
    fun fills_kp_entero_exacto() {
        val f = HudKp.fills(2.0)
        assertEquals(1.0f, f[0])
        assertEquals(1.0f, f[1])
        assertEquals(0.0f, f[2])
    }

    @Test
    fun fills_tope_sup_no_excede_uno() {
        HudKp.fills(9.5).forEach { assertEquals(1.0f, it) }
        HudKp.fills(0.0).forEach { assertEquals(0.0f, it) }
    }

    @Test
    fun segmentZone_colores_del_pin_desplazados_un_paso() {
        // color del segmento n (1..9): <= 4 verde, == 5 amarillo, == 6 naranja, >= 7 rojo
        assertEquals(HudKp.Zone.MINOR, HudKp.segmentZone(1))
        assertEquals(HudKp.Zone.MINOR, HudKp.segmentZone(4))
        assertEquals(HudKp.Zone.MODERATE, HudKp.segmentZone(5))
        assertEquals(HudKp.Zone.STRONG, HudKp.segmentZone(6))
        assertEquals(HudKp.Zone.SEVERE, HudKp.segmentZone(7))
        assertEquals(HudKp.Zone.SEVERE, HudKp.segmentZone(9))
    }

    @Test
    fun gLabel_indice_y_coercion_a_rango() {
        assertEquals("G0 Quiet", HudKp.gLabel(0))
        assertEquals("G2 Moderate", HudKp.gLabel(2))
        assertEquals("G5 Extreme", HudKp.gLabel(5))
        assertEquals("G0 Quiet", HudKp.gLabel(-1))
        assertEquals("G5 Extreme", HudKp.gLabel(9))
    }

    @Test
    fun format_dos_decimales_punto_us() {
        assertEquals("3.50", HudKp.format(3.5))
        assertEquals("0.00", HudKp.format(0.0))
        assertEquals("9.00", HudKp.format(9.0))
    }
}

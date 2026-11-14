package com.valledemena.ionosphere.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationPickTest {

    @Test
    fun centro_gana_y_estacion_a_30px_no_es_candidata() {
        val center = StationScreenPoint("CENTER", 100f, 100f, true)
        assertEquals("CENTER", stationPick(listOf(center), 100f, 100f, 0f))

        val at30 = StationScreenPoint("FAR", 130f, 100f, true)
        assertNull(stationPick(listOf(at30), 100f, 100f, 0f))
    }

    @Test
    fun arrastre_mayor_de_6px_no_selecciona() {
        val center = StationScreenPoint("CENTER", 100f, 100f, true)
        assertNull(stationPick(listOf(center), 100f, 100f, 7f))
    }

    @Test
    fun hemisferio_oculto_nunca_gana() {
        val hidden = StationScreenPoint("HIDDEN", 100f, 100f, false)
        assertNull(stationPick(listOf(hidden), 100f, 100f, 0f))
    }

    @Test
    fun candidato_mas_cercano_gana() {
        val near = StationScreenPoint("NEAR", 104f, 100f, true)
        val far = StationScreenPoint("FAR", 112f, 100f, true)
        assertEquals("NEAR", stationPick(listOf(far, near), 100f, 100f, 0f))
    }
}

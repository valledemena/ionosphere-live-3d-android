package com.valledemena.ionosphere.core.iono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G8_37: pin de valor del exponente 1,5 de la cortina.
 *
 * `sliceAltKm` calcula `pow` en doble y redondea a `Float`
 * (`f.toDouble().pow(1.5).toFloat()`), la unica frontera
 * `Float -> Double -> Float` declarada en R4.5. Este pin fija cuatro niveles de
 * una cortina `60..500 km` con `LVL = 48` (los del volumen por defecto), que es
 * donde el redondeo del `pow` es observable; los indices 0 y 47 son los
 * extremos exactos y 1, 7 y 19 cubren la zona baja de la curva.
 *
 * La tolerancia (1e-4 km = 0,1 m) esta por encima del ulp de `Float` en este
 * rango (~1,5e-5 en 173 km) y muy por debajo de cualquier error de exponente.
 */
class SliceAltPinTest {

    @Test
    fun `G8_37 sliceAltKm pinea el pow 1_5 en los indices 0 1 7 19 sobre 60 500 con 48 niveles`() {
        val niveles = 48
        val altMin = 60.0f
        val altMax = 500.0f

        val esperado = mapOf(
            0 to 60.0f,
            1 to 61.365545f,
            7 to 85.29024f,
            19 to 173.09315f,
        )
        for ((j, valor) in esperado) {
            assertEquals("j = $j", valor, sliceAltKm(j, altMin, altMax, niveles), 1e-4f)
        }

        // Extremos exactos: `f = 0` cae en el minimo y `f = 1` en el maximo.
        assertEquals(altMin, sliceAltKm(0, altMin, altMax, niveles), 0.0f)
        assertEquals(altMax, sliceAltKm(niveles - 1, altMin, altMax, niveles), 0.0f)

        // Monotona estricta en todos los niveles.
        for (j in 1 until niveles) {
            assertTrue(
                "altura no creciente en j = $j",
                sliceAltKm(j, altMin, altMax, niveles) > sliceAltKm(j - 1, altMin, altMax, niveles),
            )
        }
    }
}

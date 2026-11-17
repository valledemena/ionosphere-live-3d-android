package com.valledemena.ionosphere.ui.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * G8: readout de la cortina (P4b). Espejo de `App.cpp:5337-5345`.
 *
 * El oraculo imprime `"%s -> %s  pk %.1f"` (dos espacios antes de `pk`) con
 * `printf`, que redondea el valor binario exacto con ties-to-even y escribe
 * `.` como separador decimal (locale C).
 */
class HudSliceTest {

    // G8_42
    @Test
    fun `G8_42 readout usa el formato del oraculo con dos espacios y un decimal`() {
        // 8 + 4,5 * 0,5 = 10,25 exacto en Float: printf("%.1f") da 10.2
        // (ties-to-even), no 10.3 (HALF_UP de String.format).
        assertEquals("MAD -> ROA  pk 10.2", HudSlice.readout("MAD", "ROA", 10.25f, hasData = true))
        assertEquals("10.2", HudSlice.formatPeakLog(10.25f))
        assertEquals("10.8", HudSlice.formatPeakLog(10.75f))
        assertEquals("9.0", HudSlice.formatPeakLog(9.0f))

        // Separador literal del oraculo: exactamente dos espacios.
        val linea = HudSlice.readout("MAD", "ROA", 10.25f, hasData = true)
        assertTrue("separador '  pk '", linea.contains("MAD -> ROA  pk "))
        assertEquals("MAD -> ROA  pk 10.2".length, linea.length)
    }

    // G8_43
    @Test
    fun `G8_43 sin malla el readout es el prompt del oraculo y no depende del locale`() {
        assertEquals("Pin a station (A), hover another (B).", HudSlice.PIN_PROMPT)

        // Sin malla horneada el oraculo no imprime la linea aunque haya par.
        assertEquals(HudSlice.PIN_PROMPT, HudSlice.readout("MAD", "ROA", 10.25f, hasData = false))
        assertEquals(HudSlice.PIN_PROMPT, HudSlice.readout(null, "ROA", 0.0f, hasData = true))
        assertEquals(HudSlice.PIN_PROMPT, HudSlice.readout("MAD", "", 0.0f, hasData = true))

        // Locale del dispositivo: con es-ES `String.format("%.1f")` daria "10,2".
        val previo = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-ES"))
            assertEquals("10.2", HudSlice.formatPeakLog(10.25f))
            assertEquals("MAD -> ROA  pk 10.2", HudSlice.readout("MAD", "ROA", 10.25f, hasData = true))
        } finally {
            Locale.setDefault(previo)
        }
    }
}

package com.valledemena.ionosphere.ui.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * G8: readout de la cortina (P4b) y gate por la capa encendida (R4.6.2).
 * Espejo de `App.cpp:5319-5345`.
 *
 * El oraculo imprime `"%s -> %s  pk %.1f"` (dos espacios antes de `pk`) con
 * `printf`, que redondea el valor binario exacto con ties-to-even y escribe
 * `.` como separador decimal (locale C). Todo el readout vive dentro de
 * `if (sliceOn)`, y el par que imprime es el del usuario (`sliceA`/`sliceB`),
 * no el que la capa tenga horneado.
 */
class HudSliceTest {

    private val codes = listOf("MAD", "ROA")

    // G8_42
    @Test
    fun `G8_42 readout usa el formato del oraculo con dos espacios y un decimal`() {
        // 8 + 4,5 * 0,5 = 10,25 exacto en Float: printf("%.1f") da 10.2
        // (ties-to-even), no 10.3 (HALF_UP de String.format).
        assertEquals(
            "MAD -> ROA  pk 10.2",
            HudSlice.panelState(true, "MAD", "ROA", codes, 10.25f, hasData = true).line,
        )
        assertEquals("10.2", HudSlice.formatPeakLog(10.25f))
        assertEquals("10.8", HudSlice.formatPeakLog(10.75f))
        assertEquals("9.0", HudSlice.formatPeakLog(9.0f))

        // Separador literal del oraculo: exactamente dos espacios.
        val linea = HudSlice.panelState(true, "MAD", "ROA", codes, 10.25f, hasData = true).line
        assertTrue("separador '  pk '", linea!!.contains("MAD -> ROA  pk "))
        assertEquals("MAD -> ROA  pk 10.2".length, linea.length)
    }

    // G8_43
    @Test
    fun `G8_43 sin malla el readout es el prompt del oraculo y no depende del locale`() {
        assertEquals("Pin a station (A), hover another (B).", HudSlice.PIN_PROMPT)

        // Sin malla horneada el oraculo no imprime la linea aunque haya par.
        assertEquals(HudSlice.PIN_PROMPT, HudSlice.panelState(true, "MAD", "ROA", codes, 10.25f, false).line)
        assertEquals(HudSlice.PIN_PROMPT, HudSlice.panelState(true, null, "ROA", codes, 0.0f, true).line)
        assertEquals(HudSlice.PIN_PROMPT, HudSlice.panelState(true, "MAD", "", codes, 0.0f, true).line)

        // Locale del dispositivo: con es-ES `String.format("%.1f")` daria "10,2".
        val previo = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-ES"))
            assertEquals("10.2", HudSlice.formatPeakLog(10.25f))
            assertEquals(
                "MAD -> ROA  pk 10.2",
                HudSlice.panelState(true, "MAD", "ROA", codes, 10.25f, true).line,
            )
        } finally {
            Locale.setDefault(previo)
        }
    }

    // G8_44
    @Test
    fun `G8_44 con la capa apagada no se pinta readout y los selectores no aceptan toques`() {
        // `App.cpp:5336` mete todo el readout en `if (sliceOn)`: apagada, ni
        // linea ni prompt. Un par elegido y una malla viva no cambian nada.
        val apagada = HudSlice.panelState(false, "MAD", "ROA", codes, 42.5f, hasData = true)
        assertNull("sin linea con la capa apagada", apagada.line)
        assertEquals(false, apagada.pickersEnabled)

        // Sin catalogo de estaciones tampoco se habilitan.
        assertEquals(false, HudSlice.panelState(false, null, null, emptyList(), 0f, false).pickersEnabled)

        // Encendida si se habilitan (con catalogo y sin el).
        assertEquals(true, HudSlice.panelState(true, "MAD", "ROA", codes, 42.5f, true).pickersEnabled)
        assertEquals(false, HudSlice.panelState(true, "MAD", "ROA", emptyList(), 42.5f, true).pickersEnabled)
    }

    // G8_45
    @Test
    fun `G8_45 reapertura muestra el prompt y no el pico de antes de apagar`() {
        // Secuencia del oraculo: encendida con par y malla -> `pk`;
        // se apaga (`sliceA`/`sliceB` se vacian, `5336` deja de pintar);
        // se reenciende: la capa conserva su malla horneada (`hasData` sigue
        // true, `planSliceFrame` devuelve Hide con `pair = current`), pero el
        // par del usuario esta vacio, asi que el texto es el prompt.
        val encendida = HudSlice.panelState(true, "MAD", "ROA", codes, 42.5f, hasData = true)
        assertEquals("MAD -> ROA  pk 42.5", encendida.line)

        val apagada = HudSlice.panelState(false, "MAD", "ROA", codes, 42.5f, hasData = true)
        assertNull(apagada.line)

        // Al apagar, el HUD limpia solo su par; el renderer conserva la malla.
        val reabierta = HudSlice.panelState(true, null, null, codes, 42.5f, hasData = true)
        assertEquals(HudSlice.PIN_PROMPT, reabierta.line)
        assertEquals(true, reabierta.pickersEnabled)

        // El pico vivo de la capa por si solo no produce linea: manda el par
        // del usuario, igual que `sliceA`/`sliceB` en el oraculo.
        assertEquals(
            HudSlice.PIN_PROMPT,
            HudSlice.panelState(true, null, "ROA", codes, 42.5f, hasData = true).line,
        )
        assertEquals(
            HudSlice.PIN_PROMPT,
            HudSlice.panelState(true, "MAD", null, codes, 42.5f, hasData = true).line,
        )
    }
}

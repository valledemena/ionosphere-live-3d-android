package com.valledemena.ionosphere.ui.hud

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Readout de la cortina Ne(h), espejo de `App.cpp:5337-5345`.
 *
 * ```
 * if (!sliceA.empty() && !sliceB.empty() && sliceLayer.hasData())
 *     ImGui::Text("%s -> %s  pk %.1f", sliceA, sliceB, slicePeakLog);
 * else if (sliceSrc == 0)
 *     ImGui::TextDisabled("Pin a station (A), hover another (B).");
 * else
 *     ImGui::TextDisabled("Waiting for volume...");
 * ```
 *
 * En R4.5 el unico origen de extremos previsto es `sliceSrc = 0` (par
 * pin+hover), asi que la rama sin datos es la del prompt; la rama "Waiting for
 * volume..." pertenece a `Manual A/B` y `Fixed point`, diferidos, y no se
 * implementa aqui para no dejar codigo muerto.
 *
 * Formato del pico: el oraculo usa `printf("%.1f")`, que redondea el valor
 * binario exacto al decimal mas cercano con **ties-to-even** y escribe `.` como
 * separador (locale C). Por eso no se usa `String.format`, que redondea
 * `HALF_UP` y depende del locale del dispositivo: con locale es-ES imprimiria
 * `10,2` y con `HALF_UP` daria `10,3` donde el oraculo da `10,2`.
 */
object HudSlice {

    /** Rama sin datos y sin volumen del oraculo (`sliceSrc == 0`). */
    const val PIN_PROMPT = "Pin a station (A), hover another (B)."

    /** Separador literal del oraculo: dos espacios antes de `pk`. */
    private const val PEAK_SEPARATOR = "  pk "

    /**
     * Linea del panel, o el prompt si todavia no hay malla en la GPU.
     *
     * `hasData` debe venir de la capa GL (`hasData` del renderer), igual que
     * `sliceLayer.hasData()` en el oraculo.
     */
    fun readout(codeA: String?, codeB: String?, peakLogNe: Float, hasData: Boolean): String {
        if (!hasData || codeA.isNullOrEmpty() || codeB.isNullOrEmpty()) return PIN_PROMPT
        return codeA + " -> " + codeB + PEAK_SEPARATOR + formatPeakLog(peakLogNe)
    }

    /**
     * `printf("%.1f", value)` para un `float` promocionado a `double`:
     * un decimal, `.` como separador y redondeo ties-to-even sobre el valor
     * binario exacto.
     */
    fun formatPeakLog(value: Float): String =
        BigDecimal(value.toDouble()).setScale(1, RoundingMode.HALF_EVEN).toPlainString()
}

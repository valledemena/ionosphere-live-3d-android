package com.valledemena.ionosphere.ui.hud

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Estado del bloque de la cortina Ne(h) en el HUD, espejo de
 * `App.cpp:5319-5345`.
 *
 * El oraculo encapsula **todo** el readout en el `if (impl->sliceOn)` del
 * checkbox, y al desmarcarlo vacia el par del usuario:
 *
 * ```
 * if (ImGui::Checkbox("Path slice", &impl->sliceOn)) {
 *     impl->sliceLayer.setVisible(impl->sliceOn);
 *     if (!impl->sliceOn) { impl->sliceA.clear(); impl->sliceB.clear(); }
 * }
 * ...
 * if (impl->sliceOn) {
 *     if (!sliceA.empty() && !sliceB.empty() && sliceLayer.hasData())
 *         ImGui::Text("%s -> %s  pk %.1f", sliceA, sliceB, slicePeakLog);
 *     else if (sliceSrc == 0)
 *         ImGui::TextDisabled("Pin a station (A), hover another (B).");
 *     else
 *         ImGui::TextDisabled("Waiting for volume...");
 * }
 * ```
 *
 * Con la capa apagada el oraculo **no imprime ninguna linea** (`line == null`).
 * Encendida y sin par del usuario imprime el prompt, aunque la GPU conserve
 * una malla horneada de antes: el par que decide la linea es el del usuario
 * (`sliceA`/`sliceB`), no el que la capa tenga horneado. Por eso
 * [panelState] recibe los codigos del HUD y el pico de la capa por separado.
 *
 * En Android el par pin+hover se sustituye por dos selectores (divergencia de
 * forma declarada en R4.6.1); en R4.6.2 se **deshabilitan** con la capa
 * apagada en vez de ocultarse, para no mover el alto del panel.
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
     * Bloque cortina del HUD ya decidido.
     *
     * @param line texto del readout, o `null` si con la capa apagada no debe
     *   pintarse nada (el oraculo no imprime linea).
     * @param pickersEnabled si los selectores A/B aceptan toques.
     */
    data class State(val line: String?, val pickersEnabled: Boolean)

    /**
     * Decide el readout y el estado de los selectores.
     *
     * `codeA`/`codeB` son los codigos **del usuario** (`sliceA`/`sliceB` del
     * oraculo, en Android el par elegido en el HUD). `peakLogNe` y `hasData`
     * vienen de la capa GL (`slicePeakLog` y `sliceLayer.hasData()`), igual que
     * en el oraculo: un pico vivo no basta, hace falta el par del usuario.
     */
    fun panelState(
        on: Boolean,
        codeA: String?,
        codeB: String?,
        codes: List<String>,
        peakLogNe: Float,
        hasData: Boolean,
    ): State {
        val pickersEnabled = on && codes.isNotEmpty()
        if (!on) return State(null, pickersEnabled)
        if (codeA.isNullOrEmpty() || codeB.isNullOrEmpty() || !hasData) {
            return State(PIN_PROMPT, pickersEnabled)
        }
        return State(
            codeA + " -> " + codeB + PEAK_SEPARATOR + formatPeakLog(peakLogNe),
            pickersEnabled,
        )
    }

    /**
     * `printf("%.1f", value)` para un `float` promocionado a `double`:
     * un decimal, `.` como separador y redondeo ties-to-even sobre el valor
     * binario exacto.
     */
    fun formatPeakLog(value: Float): String =
        BigDecimal(value.toDouble()).setScale(1, RoundingMode.HALF_EVEN).toPlainString()
}

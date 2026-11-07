package com.valledemena.ionosphere.ui.hud

import java.util.Locale

/**
 * Presentador Dst: espejo de `dst.ts` del pinned web (anillo). Puro.
 *
 * Tabla del pinned: banda por umbrales -100/-50/-30 (intense/storm/
 * unsettled/quiet, frontera exacta cae en la banda mas tranquila) y
 * tripletas RGB exactas por banda.
 */
object HudDst {

    /** Bandas del anillo, de mas intensa a mas tranquila. */
    enum class Band { INTENSE, STORM, UNSETTLED, QUIET }

    /** Banda por umbrales del pinned. */
    fun band(dst: Double): Band = when {
        dst < -100.0 -> Band.INTENSE
        dst < -50.0 -> Band.STORM
        dst < -30.0 -> Band.UNSETTLED
        else -> Band.QUIET
    }

    /** Nombre de banda en minusculas, como el texto del pinned. */
    fun bandName(dst: Double): String = when (band(dst)) {
        Band.INTENSE -> "intense"
        Band.STORM -> "storm"
        Band.UNSETTLED -> "unsettled"
        Band.QUIET -> "quiet"
    }

    /** Tripleta RGB 0..1 del anillo, exacta por banda. */
    fun color(dst: Double): FloatArray = when (band(dst)) {
        Band.INTENSE -> floatArrayOf(1.0f, 0.25f, 0.2f)
        Band.STORM -> floatArrayOf(1.0f, 0.55f, 0.15f)
        Band.UNSETTLED -> floatArrayOf(0.9f, 0.75f, 0.3f)
        Band.QUIET -> floatArrayOf(0.35f, 0.55f, 1.0f)
    }

    /** Valor en nT como entero ("-123 nT"), espejo del gate de legibilidad. */
    fun format(nT: Double): String = String.format(Locale.US, "%.0f nT", nT)
}

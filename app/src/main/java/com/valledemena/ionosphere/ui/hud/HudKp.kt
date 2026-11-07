package com.valledemena.ionosphere.ui.hud

import java.util.Locale

/**
 * Presentador Kp: espejo del pinned web (panel de clima espacial). Puro,
 * sin estado ni Android.
 *
 * Tabla del pinned:
 *  - G-labels exactos, indexados por `gScale` del payload.
 *  - Color del numero: >= 7 rojo, >= 5 naranja, >= 3 amarillo, resto verde.
 *  - Barra de 9 segmentos con relleno clamp01(kp - i) y color por segmento
 *    desplazado un paso (<= 4 verde, == 5 amarillo, == 6 naranja, >= 7 rojo).
 */
object HudKp {

    /** G-labels exactos del pinned. */
    val G_LABELS: List<String> = listOf(
        "G0 Quiet",
        "G1 Minor",
        "G2 Moderate",
        "G3 Strong",
        "G4 Severe",
        "G5 Extreme",
    )

    /** Zonas de color del valor del Kp. */
    enum class Zone { SEVERE, STRONG, MODERATE, MINOR }

    /** Segmentos de la barra (0..9, espejo del pinned). */
    const val SEGMENT_COUNT = 9

    /** Zona de color del valor: 7/5/3 como en el pinned. */
    fun zone(kp: Double): Zone = when {
        kp >= 7.0 -> Zone.SEVERE
        kp >= 5.0 -> Zone.STRONG
        kp >= 3.0 -> Zone.MODERATE
        else -> Zone.MINOR
    }

    /**
     * Color del segmento `segmentOneBased` (1..9), tal cual el pinned aplica
     * el color por segmento con el desplazamiento de un paso.
     */
    fun segmentZone(segmentOneBased: Int): Zone = when {
        segmentOneBased >= 7 -> Zone.SEVERE
        segmentOneBased >= 6 -> Zone.STRONG
        segmentOneBased >= 5 -> Zone.MODERATE
        else -> Zone.MINOR
    }

    /** Relleno de cada segmento: clamp01(kp - i), i en 0..8. */
    fun fills(kp: Double): List<Float> =
        (0 until SEGMENT_COUNT).map { i -> (kp - i).coerceIn(0.0, 1.0).toFloat() }

    /** G-label con coercion silenciosa a 0..5 (el payload manda). */
    fun gLabel(gScale: Int): String = G_LABELS[gScale.coerceIn(0, G_LABELS.lastIndex)]

    /** Valor con 2 decimales, punto fijo US (espejo del pinned). */
    fun format(kp: Double): String = String.format(Locale.US, "%.2f", kp)
}

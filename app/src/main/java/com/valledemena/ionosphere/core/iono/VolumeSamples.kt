package com.valledemena.ionosphere.core.iono

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * Muestreo puntual y picos por columna del volumen, portados de
 * `src/Ionosphere/DensityVolume.h` del oraculo (`sampleVolume`, lineas 146-170,
 * y `extractPeakGrids`, lineas 76-125).
 *
 * R4.5 usa [sampleVolumeAt] para llenar la cortina Ne(h) punto por punto, igual
 * que `App.cpp:3944`. R4.6 reutilizara este muestreador si necesita muestreo
 * CPU; no se bifurca (dictamen `from-glm/0403`, seccion 4.C).
 *
 * Aritmetica en `Float` con el mismo orden de operaciones que el oraculo (all
 * es `float` de extremo a extremo). Los arrays del volumen siguen en `Double`
 * porque asi los entrego R4.4; este slice no los reescribe. La unica frontera
 * es `Double -> Float` al leer una celda.
 *
 * Puro: sin GL, sin UI, sin red.
 */

private const val PEAK_LOG_MIN = 8.0f
private const val PEAK_LOG_RANGE = 4.5f
private const val PEAK_LOG_FLOOR = 8.5f

/**
 * Altura de la muestra [i] en una malla de [n] niveles, en `Float`:
 * `altMin + (altMax - altMin) * (i / (n - 1))^1.5`.
 *
 * Es el `volumeAltKm` del oraculo en precision simple. `powf(f, 1.5f)` se
 * emula con `pow` en doble redondeado a `Float`: el resultado es el `Float`
 * correctamente redondeado del mismo exponente real.
 */
fun volumeAltKmF(i: Int, n: Int, altMinKm: Float, altMaxKm: Float): Float {
    val f = if (n <= 1) 0.0f else i.toFloat() / (n - 1).toFloat()
    return altMinKm + (altMaxKm - altMinKm) * f.toDouble().pow(1.5).toFloat()
}

/** Celda con wrap en longitud y clamp en latitud, como el `at` del oraculo. */
private fun volumeCell(
    volume: DensityVolume,
    xIn: Int,
    yIn: Int,
    altIdx: Int,
    w: Int,
    h: Int,
): Float {
    var x = xIn % w
    if (x < 0) x += w
    val y = if (yIn < 0) 0 else if (yIn >= h) h - 1 else yIn
    return volume.data[volumeIndex(x, y, altIdx, w, h)].toFloat()
}

/**
 * Valor normalizado [0,1] del volumen en `(latDeg, lonDeg, altKm)`.
 *
 * Espejo exacto de `sampleVolume` (oraculo `DensityVolume.h:146-170`):
 * - guardas: volumen invalido, sin datos o cualquier resolucion `< 2` -> `0`;
 * - celda: `gx = (lon + 180) / 360 * W - 0.5`, `gy = (lat + 90) / 180 * H - 0.5`;
 * - **wrap** en longitud (`x % W`, negativos `+W`) y **clamp** en latitud
 *   (`[0, H-1]`);
 * - altitud: bracketing lineal sobre la rejilla `^1.5` con `fa` clampeado
 *   a `[0,1]`;
 * - trilineal: 4 esquinas por nivel, dos niveles, mismo orden de lerps.
 *
 * No muta el volumen.
 */
fun sampleVolumeAt(
    volume: DensityVolume,
    latDeg: Float,
    lonDeg: Float,
    altKm: Float,
): Float {
    val w = volume.lonRes
    val h = volume.latRes
    val levels = volume.altRes
    if (!volume.valid || volume.data.isEmpty() || w < 2 || h < 2 || levels < 2) return 0.0f

    val altMinKm = volume.altMinKm.toFloat()
    val altMaxKm = volume.altMaxKm.toFloat()

    val gx = (lonDeg + 180.0f) / 360.0f * w.toFloat() - 0.5f
    val gy = (latDeg + 90.0f) / 180.0f * h.toFloat() - 0.5f
    val x0 = floor(gx).toInt()
    val y0 = floor(gy).toInt()
    val fx = gx - x0
    val fy = gy - y0

    var a0 = 0
    while (a0 + 1 < levels && volumeAltKmF(a0 + 1, levels, altMinKm, altMaxKm) <= altKm) a0++
    val a1 = if (a0 + 1 < levels) a0 + 1 else a0
    val h0 = volumeAltKmF(a0, levels, altMinKm, altMaxKm)
    val h1 = volumeAltKmF(a1, levels, altMinKm, altMaxKm)
    var fa = if (h1 > h0) (altKm - h0) / (h1 - h0) else 0.0f
    fa = if (fa < 0.0f) 0.0f else if (fa > 1.0f) 1.0f else fa

    val lo = volumeCell(volume, x0, y0, a0, w, h) * (1.0f - fx) * (1.0f - fy) +
        volumeCell(volume, x0 + 1, y0, a0, w, h) * fx * (1.0f - fy) +
        volumeCell(volume, x0, y0 + 1, a0, w, h) * (1.0f - fx) * fy +
        volumeCell(volume, x0 + 1, y0 + 1, a0, w, h) * fx * fy
    val hi = volumeCell(volume, x0, y0, a1, w, h) * (1.0f - fx) * (1.0f - fy) +
        volumeCell(volume, x0 + 1, y0, a1, w, h) * fx * (1.0f - fy) +
        volumeCell(volume, x0, y0 + 1, a1, w, h) * (1.0f - fx) * fy +
        volumeCell(volume, x0 + 1, y0 + 1, a1, w, h) * fx * fy
    return lo * (1.0f - fa) + hi * fa
}

/**
 * Picos por columna del volumen, espejo de `PeakGrids` del oraculo.
 *
 * `logNe[i]` es `log10(Ne)` ya des-normalizado del pico de la columna y
 * `hmKm[i]` la altura del pico (refinada por parabola). Con `logNe < 8.5` la
 * columna es un hueco y `hmKm = 0`.
 *
 * Layout de [logNe] y [hmKm]: `i = y * width + x` (latitud mayor, longitud
 * menor), el mismo del oraculo.
 *
 * `equals`/`hashCode` comparan referencias de los arrays, como en
 * [DensityVolume]; para comparar contenidos usar los tests.
 */
data class PeakGrids(
    val width: Int,
    val height: Int,
    val logNe: FloatArray,
    val hmKm: FloatArray,
    val valid: Boolean,
    val timestamp: Long,
)

/**
 * Extrae los picos por columna (`extractPeakGrids`, oraculo
 * `DensityVolume.h:76-125`).
 *
 * - Guardas: volumen invalido, sin datos, `width/height < 1` o `altRes < 3`
 *   devuelven rejillas del tamano nominal, a cero y `valid = false`.
 *   Se anade una guarda de longitud del array que el oraculo no necesita
 *   (sus arrays siempre son coherentes por construccion); aqui evita un
 *   `IndexOutOfBounds` en vez de un fallo de memoria.
 * - Argmax por columna con `>` estricto: los empates se quedan con el indice
 *   menor (`bi` arranca en 0 y el bucle desde `a = 1`).
 * - Refinamiento sub-muestra con parabola de Lagrange sobre las **alturas
 *   reales** `volumeAltKmF(bi-1/bi/bi+1)`; el espaciado `^1.5` no es uniforme
 *   y la version por indice sesga hacia el lado denso.
 * - `|den| <= 1e-9` deja el pico en el vertice `bi` sin refinar.
 * - `logNe = 8 + 4.5 * peak`; `hmKm = 0` si `logNe < 8.5`.
 * - `valid` y `timestamp` se propagan del volumen.
 */
fun extractPeakGrids(volume: DensityVolume): PeakGrids {
    val w = volume.lonRes
    val h = volume.latRes
    val levels = volume.altRes
    val cells = if (w > 0 && h > 0) w * h else 0
    val logNe = FloatArray(cells)
    val hmKm = FloatArray(cells)
    val invalid = PeakGrids(w, h, logNe, hmKm, valid = false, timestamp = 0L)
    if (!volume.valid || volume.data.isEmpty() || w < 1 || h < 1 || levels < 3) return invalid
    if (volume.data.size < cells * levels) return invalid

    val altMinKm = volume.altMinKm.toFloat()
    val altMaxKm = volume.altMaxKm.toFloat()
    for (y in 0 until h) {
        for (x in 0 until w) {
            var bi = 0
            var peak = volume.data[volumeIndex(x, y, 0, w, h)].toFloat()
            for (level in 1 until levels) {
                val v = volume.data[volumeIndex(x, y, level, w, h)].toFloat()
                if (v > peak) {
                    bi = level
                    peak = v
                }
            }
            var hm = volumeAltKmF(bi, levels, altMinKm, altMaxKm)
            if (bi > 0 && bi + 1 < levels) {
                val x0 = volumeAltKmF(bi - 1, levels, altMinKm, altMaxKm)
                val x1 = hm
                val x2 = volumeAltKmF(bi + 1, levels, altMinKm, altMaxKm)
                val y0 = volume.data[volumeIndex(x, y, bi - 1, w, h)].toFloat()
                val y1 = peak
                val y2 = volume.data[volumeIndex(x, y, bi + 1, w, h)].toFloat()
                val den = x0 * (y1 - y2) + x1 * (y2 - y0) + x2 * (y0 - y1)
                if (abs(den) > 1e-9f) {
                    var xs = 0.5f * (x0 * x0 * (y1 - y2) + x1 * x1 * (y2 - y0) +
                        x2 * x2 * (y0 - y1)) / den
                    if (xs < x0) xs = x0
                    if (xs > x2) xs = x2
                    // Valor en el vertice: t en [-1,1] sobre el tramo local.
                    var t = (xs - x1) / (0.5f * (x2 - x0) + 1e-9f)
                    if (t < -1.0f) t = -1.0f
                    if (t > 1.0f) t = 1.0f
                    hm = xs
                    peak = y1 - 0.25f * (y0 - y2) * t
                }
            }
            val logNeValue = PEAK_LOG_MIN + PEAK_LOG_RANGE * peak
            val cell = y * w + x
            logNe[cell] = logNeValue
            hmKm[cell] = if (logNeValue < PEAK_LOG_FLOOR) 0.0f else hm
        }
    }
    return PeakGrids(w, h, logNe, hmKm, valid = true, timestamp = volume.timestamp)
}

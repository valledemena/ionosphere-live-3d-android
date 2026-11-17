package com.valledemena.ionosphere.render

import com.valledemena.ionosphere.core.iono.SLICE_LVL
import com.valledemena.ionosphere.core.iono.SLICE_SEG
import com.valledemena.ionosphere.core.iono.SliceArc
import com.valledemena.ionosphere.core.iono.sliceAlpha
import com.valledemena.ionosphere.core.iono.sliceAltKm
import com.valledemena.ionosphere.core.iono.sliceDirectionAt
import com.valledemena.ionosphere.core.iono.sliceVertexRadius

/**
 * Honeado de la cortina Ne(h): textura RGBA, vertices e indices (R4.5, P4).
 *
 * Espejo exacto de `SliceLayer::updateSlice` (`src/Ionosphere/SliceLayer.cpp:77-128`).
 * Todo es puro y testeable en JVM; el VAO/VBO, el shader y el dispose son del
 * renderer GL que consume estos arrays.
 *
 * Reglas del oraculo que se conservan tal cual:
 * - Textura de `SEG x LVL` texeles RGBA en el orden `k = j * SEG + i`
 *   (nivel, tramo), el mismo layout que [sliceVertices] y que el volumen.
 * - Color por paleta de **256** entradas del colormap pedido; `"viridis"` es
 *   literal en `App.cpp:3949`.
 * - Indice de color `(int)(d * 255)` con `d = clamp(valor, 0, 1)`: truncado,
 *   no redondeo.
 * - Alfa `clamp(d * 1.2, 0, 1) * 255` (ganancia `SLICE_ALPHA_GAIN`).
 * - Vertice `(dir * r, s, f)` con `r = 1 + altKm / 6371` y alturas de la
 *   cortina (`SLICE_LVL`) sobre los bounds **del volumen**.
 * - Dos triangulos por celda con el mismo orden de vertices que el oraculo:
 *   `(a0, b0, a0+1)` y `(b0, b0+1, a0+1)`.
 */

/** Colormap de la cortina: literal del oraculo (`App.cpp:3949`). */
const val SLICE_COLORMAP = "viridis"

/** Entradas de la paleta del oraculo (`colormap::buildPalette(cmap, 256, pal)`). */
const val SLICE_PALETTE_SIZE = 256

/**
 * Textura RGBA de la cortina: `seg * lvl * 4` bytes listos para
 * `glTexImage2D(GL_RGBA, seg, lvl, GL_UNSIGNED_BYTE)`.
 *
 * Con `values.size != seg * lvl` (o dimensiones no positivas) devuelve un array
 * vacio: el oraculo retorna sin tocar la textura anterior
 * (`SliceLayer.cpp:71`), y quien llama decide conservar lo que hubiera.
 */
fun sliceTextureRgba(
    values: FloatArray,
    seg: Int = SLICE_SEG,
    lvl: Int = SLICE_LVL,
    colormap: String = SLICE_COLORMAP,
): ByteArray {
    if (seg <= 0 || lvl <= 0 || values.size != seg * lvl) return ByteArray(0)
    val palette = Colormaps.buildPalette(colormap, SLICE_PALETTE_SIZE)
    val tex = ByteArray(values.size * 4)
    for (k in values.indices) {
        val d = clampUnit(values[k])
        // (int)(d * 255): truncado, no redondeo, como el oraculo.
        val ci = (d * 255.0f).toInt().coerceIn(0, SLICE_PALETTE_SIZE - 1)
        tex[k * 4 + 0] = paletteByte(palette[ci * 3 + 0])
        tex[k * 4 + 1] = paletteByte(palette[ci * 3 + 1])
        tex[k * 4 + 2] = paletteByte(palette[ci * 3 + 2])
        tex[k * 4 + 3] = paletteByte(sliceAlpha(d).toDouble())
    }
    return tex
}

/**
 * Vertices de la cortina: `seg * lvl` vertices de 5 floats `(x, y, z, s, f)`.
 *
 * `s = i / (seg - 1)` (tramo) y `f = j / (lvl - 1)` (altura); la altura de cada
 * nivel sale de [sliceAltKm] con los bounds del volumen, y el radio de
 * [sliceVertexRadius] (`r = 1 + altKm / 6371`).
 *
 * Con `seg` o `lvl` no positivos devuelve un array vacio.
 */
fun sliceVertices(
    arc: SliceArc,
    altMinKm: Float,
    altMaxKm: Float,
    seg: Int = SLICE_SEG,
    lvl: Int = SLICE_LVL,
): FloatArray {
    if (seg <= 0 || lvl <= 0) return FloatArray(0)
    val out = FloatArray(seg * lvl * 5)
    var n = 0
    for (j in 0 until lvl) {
        val f = if (lvl <= 1) 0.0f else j.toFloat() / (lvl - 1).toFloat()
        val r = sliceVertexRadius(sliceAltKm(j, altMinKm, altMaxKm, lvl))
        for (i in 0 until seg) {
            val s = if (seg <= 1) 0.0f else i.toFloat() / (seg - 1).toFloat()
            val dir = sliceDirectionAt(arc, s)
            out[n++] = dir[0] * r
            out[n++] = dir[1] * r
            out[n++] = dir[2] * r
            out[n++] = s
            out[n++] = f
        }
    }
    return out
}

/**
 * Indices de los triangulos de la cortina: `6 * (seg - 1) * (lvl - 1)` valores
 * `GL_UNSIGNED_INT`.
 *
 * Mismo orden que el oraculo (`SliceLayer.cpp:99-106`): por celda
 * `(a0, b0, a0 + 1)` y `(b0, b0 + 1, a0 + 1)`, con `b0 = a0 + seg`.
 *
 * Con `seg < 2` o `lvl < 2` no hay celdas: devuelve un array vacio.
 */
fun sliceIndices(seg: Int = SLICE_SEG, lvl: Int = SLICE_LVL): IntArray {
    if (seg < 2 || lvl < 2) return IntArray(0)
    val out = IntArray((seg - 1) * (lvl - 1) * 6)
    var n = 0
    for (j in 0 until lvl - 1) {
        for (i in 0 until seg - 1) {
            val a0 = j * seg + i
            val b0 = a0 + seg
            out[n++] = a0
            out[n++] = b0
            out[n++] = a0 + 1
            out[n++] = b0
            out[n++] = b0 + 1
            out[n++] = a0 + 1
        }
    }
    return out
}

/** `clamp(x, 0, 1)`; `NaN` cae a `0` para no envenenar la textura. */
private fun clampUnit(x: Float): Float = if (!(x > 0.0f)) 0.0f else if (x > 1.0f) 1.0f else x

/** Canal de paleta `[0, 1]` -> byte, con el truncado del oraculo. */
private fun paletteByte(channel: Double): Byte =
    (channel * 255.0).toInt().coerceIn(0, 255).toByte()

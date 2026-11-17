package com.valledemena.ionosphere.render

import com.valledemena.ionosphere.core.iono.SLICE_LVL
import com.valledemena.ionosphere.core.iono.SLICE_SEG
import com.valledemena.ionosphere.core.iono.sliceAltKm
import com.valledemena.ionosphere.core.iono.sliceArc
import com.valledemena.ionosphere.core.iono.sliceDirectionAt
import com.valledemena.ionosphere.core.iono.sliceVertexRadius
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * G8: honeado de la cortina (P4). Espejo de `SliceLayer::updateSlice`
 * (`src/Ionosphere/SliceLayer.cpp:77-128`): textura RGBA, vertices e indices.
 */
class SliceBakeTest {

    private fun byteOf(v: Double): Byte = (v * 255.0).toInt().coerceIn(0, 255).toByte()

    // G8_30
    @Test
    fun `G8_30 textura RGBA tiene tamano seg por lvl y orden nivel tramo`() {
        val seg = 3
        val lvl = 2
        val values = floatArrayOf(
            0.0f, 0.0f, 0.0f, // j = 0: tramos 0..2
            1.0f, 1.0f, 1.0f, // j = 1
        )
        val tex = sliceTextureRgba(values, seg, lvl)
        assertEquals(seg * lvl * 4, tex.size)
        // k = j * seg + i: el primer texel de cada nivel es el mismo valor.
        assertEquals(viridisByte(0, 0.0), tex[0])
        assertEquals(viridisByte(1, 0.0), tex[1])
        assertEquals(viridisByte(2, 0.0), tex[2])
        assertEquals(0, tex[3].toInt())
        // nivel 1, tramo 0 -> k = 3
        val k = 3 * 4
        assertEquals(viridisByte(0, 1.0), tex[k])
        assertEquals(viridisByte(1, 1.0), tex[k + 1])
        assertEquals(viridisByte(2, 1.0), tex[k + 2])
        assertEquals(255, tex[k + 3].toInt() and 0xFF)
    }

    // G8_31
    @Test
    fun `G8_31 alfa es clamp de 1_2 por densidad y se trunca igual que el oraculo`() {
        val values = floatArrayOf(0.0f, 0.25f, 0.5f, 1.0f)
        val tex = sliceTextureRgba(values, 4, 1)
        assertEquals(0, tex[0 * 4 + 3].toInt() and 0xFF) // 0
        assertEquals(76, tex[1 * 4 + 3].toInt() and 0xFF) // 0.25 * 1.2 = 0.3 -> 76.5 -> 76
        assertEquals(153, tex[2 * 4 + 3].toInt() and 0xFF) // 0.6 -> 153.0 -> 153
        assertEquals(255, tex[3 * 4 + 3].toInt() and 0xFF) // 1.2 -> clamp 1 -> 255
    }

    // G8_32
    @Test
    fun `G8_32 color sale de la paleta de 256 del colormap`() {
        assertEquals(256, SLICE_PALETTE_SIZE)
        val palette = Colormaps.buildPalette("viridis", SLICE_PALETTE_SIZE)
        // d = k / 255 recorre los 256 indices sin ambiguedad de truncado.
        val values = FloatArray(256) { it.toFloat() / 255.0f }
        val tex = sliceTextureRgba(values, 256, 1)
        for (k in 0 until 256) {
            assertEquals("r en k=$k", byteOf(palette[k * 3 + 0]), tex[k * 4 + 0])
            assertEquals("g en k=$k", byteOf(palette[k * 3 + 1]), tex[k * 4 + 1])
            assertEquals("b en k=$k", byteOf(palette[k * 3 + 2]), tex[k * 4 + 2])
        }
        // El oraculo trunca `(int)(d*255)`, no redondea. Se busca una densidad en
        // la que truncar y redondear den colores distintos y se pinea esa.
        var distinguido = false
        fun triple(ci: Int): ByteArray = byteArrayOf(
            byteOf(palette[ci * 3 + 0]),
            byteOf(palette[ci * 3 + 1]),
            byteOf(palette[ci * 3 + 2]),
        )
        for (num in 1..999) {
            val d = num / 1000.0f
            val truncado = (d * 255.0f).toInt()
            val redondeado = (d * 255.0f + 0.5f).toInt()
            if (truncado == redondeado) continue
            if (triple(truncado).contentEquals(triple(redondeado))) continue
            val tex = sliceTextureRgba(floatArrayOf(d), 1, 1)
            assertEquals("r con d=$d", triple(truncado)[0], tex[0])
            assertEquals("g con d=$d", triple(truncado)[1], tex[1])
            assertEquals("b con d=$d", triple(truncado)[2], tex[2])
            assertTrue("no debe usar el indice redondeado", !triple(redondeado).contentEquals(tex.copyOfRange(0, 3)))
            distinguido = true
            break
        }
        assertTrue("debe existir una densidad que distinga truncar de redondear", distinguido)
    }

    // G8_33
    @Test
    fun `G8_33 entradas fuera de rango o invalidas no revientan`() {
        // El oraculo retorna sin tocar la textura si el tamano no cuadra.
        assertEquals(0, sliceTextureRgba(floatArrayOf(0.5f, 0.5f), 3, 2).size)
        assertEquals(0, sliceTextureRgba(floatArrayOf(0.5f), 0, 1).size)
        assertEquals(0, sliceTextureRgba(floatArrayOf(0.5f), 1, 0).size)

        // d fuera de [0,1] se clampa (color de los extremos), no desborda el indice.
        val tex = sliceTextureRgba(floatArrayOf(-3.0f, 4.0f, Float.NaN), 3, 1)
        assertEquals(viridisByte(0, 0.0), tex[0])
        assertEquals(0, tex[3].toInt() and 0xFF)
        assertEquals(viridisByte(0, 1.0), tex[4])
        assertEquals(255, tex[7].toInt() and 0xFF)
        // NaN cae a 0: alfa 0 y color del extremo bajo, sin envenenar la textura.
        assertEquals(viridisByte(0, 0.0), tex[8])
        assertEquals(0, tex[11].toInt() and 0xFF)
    }

    // G8_34
    @Test
    fun `G8_34 vertices llevan direccion por radio y texcoords s f`() {
        val seg = 5
        val lvl = 3
        val arc = sliceArc(0.0f, 0.0f, 0.0f, 40.0f)
        val altMin = 60.0f
        val altMax = 500.0f
        val v = sliceVertices(arc, altMin, altMax, seg, lvl)
        assertEquals(seg * lvl * 5, v.size)

        for (j in 0 until lvl) {
            val fEsperado = j.toFloat() / (lvl - 1).toFloat()
            val r = sliceVertexRadius(sliceAltKm(j, altMin, altMax, lvl))
            for (i in 0 until seg) {
                val base = (j * seg + i) * 5
                val s = i.toFloat() / (seg - 1).toFloat()
                assertEquals("s en j=$j i=$i", s, v[base + 3], 1e-6f)
                assertEquals("f en j=$j i=$i", fEsperado, v[base + 4], 1e-6f)
                val len = sqrt(v[base] * v[base] + v[base + 1] * v[base + 1] + v[base + 2] * v[base + 2])
                assertEquals("radio en j=$j i=$i", r, len, 1e-5f)
                val dir = sliceDirectionAt(arc, s)
                assertTrue("direccion en j=$j i=$i", abs(dir[0] * r - v[base]) < 1e-5f)
            }
        }
        // Extremos del arco: s = 0 es A y s = 1 es B (no invertidos).
        val dirA = sliceDirectionAt(arc, 0.0f)
        assertTrue(abs(dirA[0] - v[0] / sliceVertexRadius(sliceAltKm(0, altMin, altMax, lvl))) < 1e-5f)
        assertEquals(0.0f, v[3], 0.0f)
    }

    // G8_35
    @Test
    fun `G8_35 indices recorren celdas con dos triangulos y b0 igual a a0 mas seg`() {
        val seg = 4
        val lvl = 3
        val idx = sliceIndices(seg, lvl)
        assertEquals(6 * (seg - 1) * (lvl - 1), idx.size)
        assertArrayEquals(intArrayOf(0, 4, 1, 4, 5, 1), idx.copyOfRange(0, 6))
        assertArrayEquals(intArrayOf(1, 5, 2, 5, 6, 2), idx.copyOfRange(6, 12))
        // Ultima celda: j = lvl-2, i = seg-2 -> a0 = 6, b0 = 10.
        assertArrayEquals(intArrayOf(6, 10, 7, 10, 11, 7), idx.copyOfRange(idx.size - 6, idx.size))
        // Ningun indice se sale del rango de vertices.
        assertTrue(idx.all { it in 0 until seg * lvl })
        // Sin celdas posibles no hay indices.
        assertEquals(0, sliceIndices(1, lvl).size)
        assertEquals(0, sliceIndices(seg, 1).size)
    }

    // G8_36
    @Test
    fun `G8_36 constantes del oraculo y defaults de la cortina`() {
        assertEquals("viridis", SLICE_COLORMAP)
        val tex = sliceTextureRgba(FloatArray(SLICE_SEG * SLICE_LVL) { 0.5f })
        assertEquals(SLICE_SEG * SLICE_LVL * 4, tex.size)
        val v = sliceVertices(sliceArc(10.0f, 10.0f, 20.0f, 30.0f), 60.0f, 700.0f)
        assertEquals(SLICE_SEG * SLICE_LVL * 5, v.size)
        assertEquals(6 * (SLICE_SEG - 1) * (SLICE_LVL - 1), sliceIndices().size)
        // Los bounds son del volumen: 700 km produce un radio mayor que 500 km.
        val alto = sliceVertices(sliceArc(10.0f, 10.0f, 20.0f, 30.0f), 60.0f, 700.0f)
        val bajo = sliceVertices(sliceArc(10.0f, 10.0f, 20.0f, 30.0f), 60.0f, 500.0f)
        val ultimo = (SLICE_SEG * SLICE_LVL - 1) * 5
        val ra = sqrt(alto[ultimo] * alto[ultimo] + alto[ultimo + 1] * alto[ultimo + 1] + alto[ultimo + 2] * alto[ultimo + 2])
        val rb = sqrt(bajo[ultimo] * bajo[ultimo] + bajo[ultimo + 1] * bajo[ultimo + 1] + bajo[ultimo + 2] * bajo[ultimo + 2])
        assertTrue("700 km debe superar a 500 km", ra > rb)
        assertEquals(1.0f + 700.0f / 6371.0f, ra, 1e-5f)
    }

    private fun viridisByte(channel: Int, t: Double): Byte {
        val palette = Colormaps.buildPalette("viridis", SLICE_PALETTE_SIZE)
        val ci = (t * 255.0).toInt().coerceIn(0, SLICE_PALETTE_SIZE - 1)
        return byteOf(palette[ci * 3 + channel])
    }
}

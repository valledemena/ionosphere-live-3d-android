package com.valledemena.ionosphere.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderGlobeTextureTest {

    @Test
    fun `el tier medium es 2048x1024`() {
        assertEquals(2048, PlaceholderGlobeTexture.TIER_MEDIUM_WIDTH)
        assertEquals(1024, PlaceholderGlobeTexture.TIER_MEDIUM_HEIGHT)
    }

    @Test
    fun `genera el numero de pixeles pedido y opaco`() {
        val width = 128
        val height = 64
        val pixels = PlaceholderGlobeTexture.generate(width, height)
        assertEquals(width * height, pixels.size)
        assertTrue(pixels.all { (it ushr 24) and 0xFF == 0xFF })
    }

    @Test
    fun `es determinista`() {
        val a = PlaceholderGlobeTexture.generate(96, 48)
        val b = PlaceholderGlobeTexture.generate(96, 48)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun `polo norte y sur comparten color y el ecuador es distinto`() {
        val north = PlaceholderGlobeTexture.oceanColor(90f)
        val equator = PlaceholderGlobeTexture.oceanColor(0f)
        val south = PlaceholderGlobeTexture.oceanColor(-90f)
        assertTrue("polos distintos", north == south)
        assertTrue("ecuador igual a polo", north != equator)
    }

    @Test
    fun `la retícula se dibuja sobre el fondo`() {
        val width = 256
        val height = 128
        val pixels = PlaceholderGlobeTexture.generate(width, height)

        // Fila fuera de paralelo y columna fuera de meridiano, para aislar cada línea.
        val rowOffParallel = 58
        val colOffMeridian = 131

        val onMeridian = pixels[rowOffParallel * width + 128]
        val offMeridian = pixels[rowOffParallel * width + colOffMeridian]
        assertTrue("meridiano y fondo no difieren", onMeridian != offMeridian)

        val onParallel = pixels[64 * width + colOffMeridian]
        val offParallel = pixels[rowOffParallel * width + colOffMeridian]
        assertTrue("paralelo y fondo no difieren", onParallel != offParallel)
    }

    @Test
    fun `los bytes van en orden RGBA y no BGR`() {
        // 0xFF0A2A4A -> R=0A, G=2A, B=4A, A=FF
        val argb = (0xFF shl 24) or (0x0A shl 16) or (0x2A shl 8) or 0x4A
        val bytes = PlaceholderGlobeTexture.toRgbaBytes(intArrayOf(argb))

        assertEquals(4, bytes.size)
        assertEquals(0x0A.toByte(), bytes[0])
        assertEquals(0x2A.toByte(), bytes[1])
        assertEquals(0x4A.toByte(), bytes[2])
        assertEquals(0xFF.toByte(), bytes[3])
    }

    @Test
    fun `un color mas azul que rojo mantiene el azul en el tercer byte`() {
        // Regresión de H1: escribir Int en un IntBuffer little-endian dejaba B,R,G,A
        // y el driver leía el azul como rojo (globo rojizo).
        val equator = PlaceholderGlobeTexture.oceanColor(0f)
        val bytes = PlaceholderGlobeTexture.toRgbaBytes(intArrayOf(equator))

        val r = bytes[0].toInt() and 0xFF
        val g = bytes[1].toInt() and 0xFF
        val b = bytes[2].toInt() and 0xFF
        assertTrue("se esperaba azul dominante (r=$r g=$g b=$b)", b > r && b > g)
    }

    @Test
    fun `la textura generada mantiene el azul en la banda del ecuador`() {
        val width = 256
        val height = 128
        val bytes = PlaceholderGlobeTexture.toRgbaBytes(PlaceholderGlobeTexture.generate(width, height))

        // fila del ecuador, columna fuera de meridiano
        val index = (64 * width + 131) * 4
        val r = bytes[index].toInt() and 0xFF
        val b = bytes[index + 2].toInt() and 0xFF
        assertTrue("el ecuador debe ser azul, no rojizo (r=$r b=$b)", b > r)
    }

    @Test
    fun `toRgbaBytes conserva el numero de pixeles`() {
        val pixels = PlaceholderGlobeTexture.generate(32, 16)
        val bytes = PlaceholderGlobeTexture.toRgbaBytes(pixels)
        assertEquals(pixels.size * 4, bytes.size)
    }

    @Test
    fun `dimensiones invalidas lanzan excepcion`() {
        var threw = false
        try {
            PlaceholderGlobeTexture.generate(2, 2)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}

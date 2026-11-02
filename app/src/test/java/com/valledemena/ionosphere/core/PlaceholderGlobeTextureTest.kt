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

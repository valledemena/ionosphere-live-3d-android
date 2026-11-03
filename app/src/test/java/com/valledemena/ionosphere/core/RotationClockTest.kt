package com.valledemena.ionosphere.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationClockTest {

    @Test
    fun `la primera creacion fija la base`() {
        val clock = RotationClock()
        clock.onSurfaceCreated(1_000L)
        assertEquals(1, clock.surfaceCreations)
        assertEquals(0f, clock.angleAt(1_000L), 1e-6f)
        assertTrue(clock.angleAt(4_000L) > 0f)
    }

    @Test
    fun `la segunda creacion no reinicia el angulo`() {
        val clock = RotationClock()
        clock.onSurfaceCreated(1_000L)
        val before = clock.angleAt(7_000L) // 6 s → 36°
        clock.onSurfaceCreated(5_000L)       // recreación de superficie (contexto perdido)
        val after = clock.angleAt(8_000L)   // 7 s desde la base → 42°
        assertEquals(2, clock.surfaceCreations)
        assertTrue("el ángulo debe continuar, no saltar a 0° ($before -> $after)", after > before)
        assertEquals(42f, clock.angleAt(8_000L), 1e-4f)
    }

    @Test
    fun `el angulo avanza seis grados por segundo`() {
        val clock = RotationClock()
        clock.onSurfaceCreated(0L)
        assertEquals(6f, clock.angleAt(1_000L), 1e-4f)
        assertEquals(18f, clock.angleAt(3_000L), 1e-4f)
    }
}

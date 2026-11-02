package com.valledemena.ionosphere.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationTest {

    @Test
    fun `angulo en el instante cero es cero`() {
        assertEquals(0f, Rotation.angleDegrees(0L), 1e-6f)
    }

    @Test
    fun `un segundo avanza los grados por segundo`() {
        assertEquals(6f, Rotation.angleDegrees(1_000L), 1e-4f)
        assertEquals(45f, Rotation.angleDegrees(7_500L), 1e-4f)
    }

    @Test
    fun `una vuelta completa vuelve a cero`() {
        assertEquals(0f, Rotation.angleDegrees(60_000L), 1e-4f)
    }

    @Test
    fun `el angulo se mantiene en rango tras varias vueltas`() {
        val angle = Rotation.angleDegrees(123_456L)
        assertTrue("angle=$angle fuera de rango", angle >= 0f && angle < 360f)
    }

    @Test
    fun `elapsed negativo se trata como cero`() {
        assertEquals(0f, Rotation.angleDegrees(-5_000L), 1e-6f)
    }

    @Test
    fun `intervalo de frame para 30 fps`() {
        assertEquals(33L, Rotation.frameIntervalMs(30))
        assertEquals(16L, Rotation.frameIntervalMs(60))
    }

    @Test
    fun `primer frame siempre se dibuja`() {
        assertTrue(Rotation.shouldRenderNow(lastFrameMs = 0L, nowMs = 1L))
    }

    @Test
    fun `no se dibuja antes de cumplir el intervalo`() {
        assertFalse(Rotation.shouldRenderNow(lastFrameMs = 1_000L, nowMs = 1_010L, targetFps = 30))
        assertTrue(Rotation.shouldRenderNow(lastFrameMs = 1_000L, nowMs = 1_034L, targetFps = 30))
    }
}

package com.valledemena.ionosphere.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayTickerTest {

    @Test
    fun `live no avanza aunque el flag de reproduccion este activo`() {
        val settings = ReplaySettings(timeMode = TimeMode.LIVE, replayPlaying = true)
        assertEquals(settings, settings.tick(0.5))
    }

    @Test
    fun `replay pausado no avanza`() {
        val settings = ReplaySettings(
            timeMode = TimeMode.REPLAY,
            replayMinutes = 120.0,
            replayPlaying = false,
        )
        assertEquals(settings, settings.tick(0.5))
    }

    @Test
    fun `replay activo avanza medio segundo a 1x`() {
        val settings = ReplaySettings(
            timeMode = TimeMode.REPLAY,
            replayMinutes = 1000.0,
            replayPlaying = true,
            replaySpeed = 1.0,
        )
        assertEquals(990.0, settings.tick(0.5).replayMinutes, 0.0)
    }

    @Test
    fun `la velocidad se aplica al tick`() {
        val settings = ReplaySettings(
            timeMode = TimeMode.REPLAY,
            replayMinutes = 1000.0,
            replayPlaying = true,
            replaySpeed = 4.0,
        )
        assertEquals(960.0, settings.tick(0.5).replayMinutes, 0.0)
    }

    @Test
    fun `con loop el tick envuelve al fondo`() {
        val settings = ReplaySettings(
            timeMode = TimeMode.REPLAY,
            replayMinutes = 5.0,
            replayPlaying = true,
            replaySpeed = 1.0,
            replayLoop = true,
        )
        assertEquals(REPLAY_MAX_MINUTES, settings.tick(0.5).replayMinutes, 0.0)
    }

    @Test
    fun `sin loop el tick se queda en cero y conserva playing`() {
        val settings = ReplaySettings(
            timeMode = TimeMode.REPLAY,
            replayMinutes = 5.0,
            replayPlaying = true,
            replaySpeed = 1.0,
            replayLoop = false,
        )
        val next = settings.tick(0.5)
        assertEquals(0.0, next.replayMinutes, 0.0)
        assertTrue(next.replayPlaying)
    }

    @Test
    fun `el tick conserva velocidad y loop`() {
        val settings = ReplaySettings(
            timeMode = TimeMode.REPLAY,
            replayMinutes = 500.0,
            replayPlaying = true,
            replaySpeed = 8.0,
            replayLoop = false,
        )
        val next = settings.tick(0.5)
        assertEquals(8.0, next.replaySpeed, 0.0)
        assertFalse(next.replayLoop)
        assertEquals(TimeMode.REPLAY, next.timeMode)
    }
}

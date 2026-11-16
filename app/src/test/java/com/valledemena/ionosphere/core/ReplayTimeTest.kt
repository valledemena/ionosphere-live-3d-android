package com.valledemena.ionosphere.core

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests espejo de `src/lib/iono/replay.test.ts` (pin `8cdc334`).
 *
 * No se porta el bloque de `stationPointSizeClamp`: R3.4 ya lo cerró. Aquí solo
 * viven las puertas de replay, reloj y settings de R4.1.
 */
class ReplayTimeTest {

    private val max = REPLAY_MAX_MINUTES
    private val t0 = Instant.parse("2026-10-19T13:12:56Z").toEpochMilli()

    @Test
    fun `defaults del oraculo`() {
        assertEquals(2.0, ReplaySettings().replaySpeed, 0.0)
        assertEquals(TimeMode.LIVE, ReplaySettings().timeMode)
        assertEquals(0.0, ReplaySettings().replayMinutes, 0.0)
        assertFalse(ReplaySettings().replayPlaying)
        assertTrue(ReplaySettings().replayLoop)
    }

    @Test
    fun `avanza diez minutos por tick a 1x`() {
        assertEquals(990.0, replayAdvance(1000.0, 1.0, 0.5, max, true), 0.0)
    }

    @Test
    fun `diez minutos de un tick 1x son dos grados y medio`() {
        val minutes = 1000.0 - replayAdvance(1000.0, 1.0, 0.5, max, true)
        assertEquals(2.5, minutes * 0.25, 1e-9)
    }

    @Test
    fun `la velocidad escala el avance`() {
        assertEquals(960.0, replayAdvance(1000.0, 4.0, 0.5, max, true), 0.0)
    }

    @Test
    fun `con loop envuelve al fondo`() {
        assertEquals(max, replayAdvance(5.0, 1.0, 0.5, max, true), 0.0)
    }

    @Test
    fun `llegar exactamente a cero tambien envuelve con loop`() {
        assertEquals(max, replayAdvance(10.0, 1.0, 0.5, max, true), 0.0)
    }

    @Test
    fun `sin loop fija en el presente`() {
        assertEquals(0.0, replayAdvance(5.0, 1.0, 0.5, max, false), 0.0)
    }

    @Test
    fun `recorta al maximo`() {
        assertEquals(max, replayAdvance(20_000.0, 1.0, 0.5, max, true), 0.0)
    }

    @Test
    fun `un dt fraccionario no se redondea a un tick entero`() {
        assertEquals(995.0, replayAdvance(1000.0, 1.0, 0.25, max, true), 0.0)
    }

    @Test
    fun `el slider en los extremos es ahora y menos 168 horas`() {
        assertEquals(0.0, replaySliderOffset(max, max), 0.0)
        assertEquals(max, replaySliderOffset(max, 0.0), 0.0)
    }

    @Test
    fun `el caso de dos horas atras no mueve el pulgar`() {
        val raw = 9960.0
        val replay = replaySliderOffset(max, raw)
        assertEquals(120.0, replay, 0.0)
        assertEquals(raw, replaySliderOffset(max, replay), 0.0)
    }

    @Test
    fun `el slider es su propia inversa`() {
        for (position in listOf(0.0, 5.0, 120.0, 5040.0, 9960.0, 10075.0, max)) {
            assertEquals(
                position,
                replaySliderOffset(max, replaySliderOffset(max, position)),
                0.0,
            )
        }
    }

    @Test
    fun `solo replay descuenta minutos`() {
        assertEquals(120.0, timeBarEffectiveMinutes(TimeMode.REPLAY, 120.0), 0.0)
        assertEquals(0.0, timeBarEffectiveMinutes(TimeMode.LIVE, 120.0), 0.0)
    }

    @Test
    fun `reloj sin inyectar devuelve placeholder`() {
        assertEquals("--:--:--", timeBarClockLabel(0L, 0.0))
        assertEquals("--:--:--", timeBarClockLabel(0L, 120.0))
    }

    @Test
    fun `reloj live formatea UTC exacto`() {
        assertEquals("13:12 UTC", timeBarClockLabel(t0, 0.0))
    }

    @Test
    fun `reloj replay resta dos horas`() {
        assertEquals("11:12 UTC", timeBarClockLabel(t0, 120.0))
    }

    @Test
    fun `live ignora un historial largo persistido`() {
        assertEquals(
            "13:12 UTC",
            timeBarClockLabel(t0, timeBarEffectiveMinutes(TimeMode.LIVE, max)),
        )
    }

    @Test
    fun `restaurar vuelve a live sin minutos ni reproduccion`() {
        val restored = ReplaySettings.restored(storedSpeed = 4.0, storedLoop = false)

        assertEquals(TimeMode.LIVE, restored.timeMode)
        assertEquals(0.0, restored.replayMinutes, 0.0)
        assertFalse(restored.replayPlaying)
        assertEquals(4.0, restored.replaySpeed, 0.0)
        assertFalse(restored.replayLoop)
    }

    @Test
    fun `restaurar sin datos usa los defaults`() {
        val restored = ReplaySettings.restored(storedSpeed = null, storedLoop = null)

        assertEquals(2.0, restored.replaySpeed, 0.0)
        assertTrue(restored.replayLoop)
        assertEquals(TimeMode.LIVE, restored.timeMode)
    }

    @Test
    fun `la velocidad se recorta al rango de la web`() {
        assertEquals(0.5, sanitizeReplaySpeed(-1.0), 0.0)
        assertEquals(8.0, sanitizeReplaySpeed(99.0), 0.0)
        assertEquals(3.0, sanitizeReplaySpeed(3.0), 0.0)
    }

    @Test
    fun `la velocidad no finita cae al default`() {
        assertEquals(2.0, sanitizeReplaySpeed(Double.NaN), 0.0)
        assertEquals(2.0, sanitizeReplaySpeed(Double.POSITIVE_INFINITY), 0.0)
        assertEquals(2.0, sanitizeReplaySpeed(null), 0.0)
    }

    @Test
    fun `los minutos se recortan y no finito cae a cero`() {
        assertEquals(0.0, sanitizeReplayMinutes(-1.0), 0.0)
        assertEquals(max, sanitizeReplayMinutes(20_000.0), 0.0)
        assertEquals(42.0, sanitizeReplayMinutes(42.0), 0.0)
        assertEquals(0.0, sanitizeReplayMinutes(Double.NaN), 0.0)
        assertEquals(0.0, sanitizeReplayMinutes(null), 0.0)
    }
}

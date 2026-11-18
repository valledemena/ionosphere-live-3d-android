package com.valledemena.ionosphere.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * R4.6.1: la epoca con la que se hornea el volumen Ne(h) tiene que existir en
 * LIVE. `solarEpochForScene` devuelve `null` cuando el reloj es el de verdad
 * (el renderer lo lee por frame), y tratar ese `null` como "no hay epoca" dejaba
 * la cortina encendida sin volumen: el readout se quedaba en el prompt y la GPU
 * nunca horneaba malla.
 */
class SliceVolumeSolarStepTest {

    private val step = 600_000L

    @Test
    fun `en LIVE la epoca del volumen es el reloj actual`() {
        val nowMs = 1_764_000_000_000L
        assertEquals((nowMs / step) * step, sliceVolumeSolarStepMs(nowMs, solarEpochMs = null))
    }

    @Test
    fun `en REPLAY manda la epoca de escena`() {
        val nowMs = 1_764_000_000_000L
        val sceneMs = nowMs - 3 * 3_600_000L
        assertEquals((sceneMs / step) * step, sliceVolumeSolarStepMs(nowMs, solarEpochMs = sceneMs))
    }

    @Test
    fun `el escalon es estable dentro de los diez minutos y cambia al cruzarlo`() {
        val base = (1_764_000_000_000L / step) * step
        val first = sliceVolumeSolarStepMs(base, solarEpochMs = null)
        val within = sliceVolumeSolarStepMs(base + step - 1, solarEpochMs = null)
        val next = sliceVolumeSolarStepMs(base + step, solarEpochMs = null)
        assertEquals(first, within)
        assertNotEquals(first, next)
        assertEquals(step, next - first)
    }
}

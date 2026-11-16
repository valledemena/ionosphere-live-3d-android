package com.valledemena.ionosphere.core.iono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class DensityVolumeTest {

    private fun testProfile(): LayerProfile = blankProfile().copy(
        nmE = 100.0,
        nmF1 = 200.0,
        nmF2 = 1.24e12,
        hmF2 = 300.0,
        b0 = 100.0,
        b1 = 3.0,
        hasE = true,
        hasF1 = true,
        hasF2 = true,
    )

    @Test
    fun `G7_1 constantes del volumen`() {
        assertEquals(72, VOL_LON_RES)
        assertEquals(72, VOL_LAT_RES)
        assertEquals(48, VOL_ALT_RES)
        assertEquals(60.0, VOL_ALT_MIN_KM, 0.0)
        assertEquals(500.0, VOL_ALT_MAX_KM, 0.0)
        assertEquals(8.0, VOL_LOG_MIN, 0.0)
        assertEquals(12.5, VOL_LOG_MIN + VOL_LOG_RANGE, 1e-12)
        assertEquals(8.5, VOL_PEAK_FLOOR, 0.0)
    }

    @Test
    fun `G7_2 volumeIndex usa layout altitud-latitud-longitud`() {
        val w = 3
        val h = 2
        assertEquals(0, volumeIndex(0, 0, 0, w, h))
        assertEquals(2, volumeIndex(2, 0, 0, w, h))
        assertEquals(3, volumeIndex(0, 1, 0, w, h))
        assertEquals(6, volumeIndex(0, 0, 1, w, h))
        assertEquals(23, volumeIndex(2, 1, 3, w, h))
        assertEquals(3 * 2 * 4, w * h * 4)
    }

    @Test
    fun `G7_3 volumeAltKm pinea extremos y malla densa abajo`() {
        assertEquals(60.0, volumeAltKm(0, 48, 60.0, 500.0), 0.0)
        assertEquals(500.0, volumeAltKm(47, 48, 60.0, 500.0), 1e-12)
        assertEquals(60.0, volumeAltKm(0, 1, 60.0, 500.0), 0.0)
        assertEquals(61.3655446013, volumeAltKm(1, 48, 60.0, 500.0), 1e-9)
        assertEquals(486.0324082504, volumeAltKm(46, 48, 60.0, 500.0), 1e-9)
        val dLo = volumeAltKm(1, 48, 60.0, 500.0) -
            volumeAltKm(0, 48, 60.0, 500.0)
        val dHi = volumeAltKm(47, 48, 60.0, 500.0) -
            volumeAltKm(46, 48, 60.0, 500.0)
        assertTrue(dHi > dLo)
    }

    @Test
    fun `G7_4 densityDir pinea marco del motor y unidad`() {
        val a = densityDir(0.0, 0.0)
        assertEquals(-1.0, a[0], 1e-12)
        assertEquals(0.0, a[1], 1e-12)
        assertEquals(0.0, a[2], 1e-12)

        val b = densityDir(0.0, 90.0)
        assertEquals(0.0, b[0], 1e-12)
        assertEquals(0.0, b[1], 1e-12)
        assertEquals(1.0, b[2], 1e-12)

        val c = densityDir(90.0, 33.0)
        assertEquals(0.0, c[0], 1e-12)
        assertEquals(1.0, c[1], 1e-12)
        assertEquals(0.0, c[2], 1e-12)

        val norm = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
        assertEquals(1.0, norm, 1e-12)
    }

    @Test
    fun `G7_5 applyDayNight subsolar intacto y antisolar apaga E-F1`() {
        val input = listOf(testProfile())
        val day = applyDayNight(input, 1, 1, doubleArrayOf(-1.0, 0.0, 0.0))
        assertNotSame(input, day)
        assertEquals(100.0, day[0].nmE, 0.0)
        assertEquals(200.0, day[0].nmF1, 0.0)
        assertTrue(day[0].hasE)
        assertTrue(day[0].hasF1)

        val night = applyDayNight(input, 1, 1, doubleArrayOf(1.0, 0.0, 0.0))
        assertEquals(0.0, night[0].nmE, 0.0)
        assertEquals(0.0, night[0].nmF1, 0.0)
        assertTrue(!night[0].hasE)
        assertTrue(!night[0].hasF1)
        assertEquals(1.24e12, night[0].nmF2, 0.0)
        assertEquals(300.0, night[0].hmF2, 0.0)

        // La web muta; esta version es funcional y no toca la entrada.
        assertEquals(100.0, input[0].nmE, 0.0)
        assertEquals(200.0, input[0].nmF1, 0.0)
        assertTrue(input[0].hasE)
        assertTrue(input[0].hasF1)
    }

    @Test
    fun `G7_6 terminador usa smoothstep 2-27 con tolerancia 1e-12`() {
        val input = listOf(testProfile())
        val out = applyDayNight(input, 1, 1, doubleArrayOf(0.0, 1.0, 0.0))
        val e = out[0].nmE / 100.0
        assertEquals(2.0 / 27.0, e, 1e-12)
        assertEquals(100.0 * 2.0 / 27.0, out[0].nmE, 1e-12)
        assertTrue(out[0].hasE)
        assertTrue(out[0].hasF1)
        assertEquals(100.0, input[0].nmE, 0.0)
    }

    @Test
    fun `G7_6b rejilla de tamano erroneo es no-op`() {
        val input = listOf(testProfile())
        val out = applyDayNight(input, 2, 2, doubleArrayOf(-1.0, 0.0, 0.0))
        assertSame(input, out)
        assertEquals(100.0, input[0].nmE, 0.0)
        assertEquals(200.0, input[0].nmF1, 0.0)
        assertTrue(input[0].hasE)
    }
}

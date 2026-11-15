package com.valledemena.ionosphere.core

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolarPositionTest {

    @Test
    fun `junio solsticio apunta al hemisferio norte`() {
        val d = SolarPosition.directionAt(1718884800000L) // 2024-06-20T12:00:00Z
        assertEquals(0.917481f, d[0], 5e-4f)
        assertEquals(0.397719f, d[1], 5e-4f)
        assertEquals(-0.006945f, d[2], 5e-4f)
    }

    @Test
    fun `diciembre solsticio apunta al hemisferio sur`() {
        val d = SolarPosition.directionAt(1734782400000L) // 2024-12-21T12:00:00Z
        assertEquals(0.917481f, d[0], 5e-4f)
        assertEquals(-0.397724f, d[1], 5e-4f)
        assertEquals(0.006665f, d[2], 5e-4f)
    }

    @Test
    fun `equinoccio de marzo queda sobre el ecuador`() {
        val d = SolarPosition.directionAt(1710936000000L) // 2024-03-20T12:00:00Z
        assertEquals(0.999484f, d[0], 5e-4f)
        assertEquals(0.002615f, d[1], 5e-4f)
        assertEquals(-0.032004f, d[2], 5e-4f)
    }

    @Test
    fun `el vector es unitario`() {
        val d = SolarPosition.directionAt(1794783600000L) // 2026-11-15T23:00:00Z
        val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
        assertEquals(1f, len, 1e-6f)
    }

    @Test
    fun `anclas de paridad con el oraculo`() {
        assertSubsolar(946728000000L, -23.033f, 0.832f)    // J2000.0
        assertSubsolar(1774008000000L, -0.042f, 1.870f)    // 2026-03-20T12:00Z
        assertSubsolar(1782043200000L, 23.436f, 0.463f)    // 2026-06-21T12:00Z
        assertSubsolar(1791374400000L, -5.615f, -3.040f)   // 2026-10-07T12:00Z
        assertSubsolar(1794778924000L, -18.656f, -149.347f) // captura 2026-11-15T21:42:04Z
        assertSubsolar(1794787200000L, -18.680f, 176.174f) // 2026-11-16T00:00Z
    }

    @Test
    fun `el sol cae sobre su punto subsolar`() {
        val d = SolarPosition.directionAt(1794778924000L)
        val (lat, lon) = subsolar(d)
        val cosLat = cos(Math.toRadians(lat.toDouble())).toFloat()
        val p = floatArrayOf(
            cosLat * cos(Math.toRadians(lon.toDouble())).toFloat(),
            sin(Math.toRadians(lat.toDouble())).toFloat(),
            -cosLat * sin(Math.toRadians(lon.toDouble())).toFloat(),
        )
        val dot = d[0] * p[0] + d[1] * p[1] + d[2] * p[2]
        assertTrue("dot=$dot", dot > 0.999f)
        val anti = d[0] * -p[0] + d[1] * -p[1] + d[2] * -p[2]
        assertTrue("antipoda dot=$anti", anti < -0.999f)
    }

    private fun assertSubsolar(epochMs: Long, expectedLat: Float, expectedLon: Float) {
        val (lat, lon) = subsolar(SolarPosition.directionAt(epochMs))
        assertEquals("lat @$epochMs", expectedLat, lat, 0.01f)
        assertEquals("lon @$epochMs", expectedLon, lon, 0.01f)
    }

    private fun subsolar(d: FloatArray): Pair<Float, Float> {
        val lat = Math.toDegrees(asin(d[1].toDouble())).toFloat()
        var lon = Math.toDegrees(atan2(-d[2].toDouble(), d[0].toDouble())).toFloat()
        if (lon < -180f) lon += 360f
        if (lon > 180f) lon -= 360f
        return lat to lon
    }
}

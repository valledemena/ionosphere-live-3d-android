package com.valledemena.ionosphere.render

import org.junit.Assert.assertEquals
import org.junit.Test

class StationPointSizeClampTest {

    @Test
    fun t3_fronteras_obligatorias() {
        assertEquals(2f, stationPointSizeClamp(2f), 0f)
        assertEquals(30f, stationPointSizeClamp(30f), 0f)
        assertEquals(2f, stationPointSizeClamp(1f), 0f)
        assertEquals(30f, stationPointSizeClamp(31f), 0f)
        assertEquals(2f, stationPointSizeClamp(-1f), 0f)
        assertEquals(2f, stationPointSizeClamp(Float.NaN), 0f)
    }

    @Test
    fun suite_web_y_derivados() {
        assertEquals(2f, stationPointSizeClamp(0f), 0f)
        assertEquals(11f, stationPointSizeClamp(11f), 0f)
        assertEquals(30f, stationPointSizeClamp(99f), 0f)
        assertEquals(30f, stationPointSizeClamp(Float.POSITIVE_INFINITY), 0f)
        assertEquals(2f, stationPointSizeClamp(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(2.5f, stationPointSizeClamp(2.5f), 0f)
    }
}

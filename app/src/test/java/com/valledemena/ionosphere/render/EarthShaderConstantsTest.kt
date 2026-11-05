package com.valledemena.ionosphere.render

import org.junit.Assert.assertEquals
import org.junit.Test

class EarthShaderConstantsTest {
    private fun assertFloat(actual: Float, expected: Float) = assertEquals(expected, actual, 1e-9f)

    @Test
    fun pins_day_night_lights() {
        assertFloat(EarthShaderConstants.DAY_NIGHT_TERMINATOR_LOW, -0.08f)
        assertFloat(EarthShaderConstants.DAY_NIGHT_TERMINATOR_HIGH, 0.25f)
        assertFloat(EarthShaderConstants.NIGHT_BAND_LOW, -0.08f)
        assertFloat(EarthShaderConstants.NIGHT_BAND_MID, 0.02f)
        assertFloat(EarthShaderConstants.NIGHT_BAND_HIGH, 0.35f)
    }

    @Test
    fun pins_night_ambient_and_spec() {
        assertFloat(EarthShaderConstants.NIGHT_MULTIPLIER, 2.5f)
        assertFloat(EarthShaderConstants.AMBIENT_NIGHT_R, 0.004f)
        assertFloat(EarthShaderConstants.AMBIENT_NIGHT_G, 0.007f)
        assertFloat(EarthShaderConstants.AMBIENT_NIGHT_B, 0.016f)
        assertFloat(EarthShaderConstants.SPECULAR_POWER, 36.0f)
        assertFloat(EarthShaderConstants.GLITTER, 0.7f)
    }

    @Test
    fun pins_ocean_thresholds() {
        assertFloat(EarthShaderConstants.OCEAN_THRESHOLD_LOW, 0.008f)
        assertFloat(EarthShaderConstants.OCEAN_THRESHOLD_HIGH, 0.07f)
    }
}

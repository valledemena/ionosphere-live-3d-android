package com.valledemena.ionosphere.render

import org.junit.Assert.assertEquals
import org.junit.Test

class EarthShaderConstantsTest {
    private fun eq(a: Float, b: Float) = assertEquals(b, a, 1e-9f)
    @Test fun pins_day_and_night() {
        eq(EarthShaderConstants.DAY_NIGHT_TERMINATOR_LOW, -0.08f)
        eq(EarthShaderConstants.DAY_NIGHT_TERMINATOR_HIGH, 0.25f)
        eq(EarthShaderConstants.NIGHT_BAND_LOW, -0.08f)
        eq(EarthShaderConstants.NIGHT_BAND_MID, 0.02f)
        eq(EarthShaderConstants.NIGHT_BAND_HIGH, 0.35f)
    }
    @Test fun pins_ambient_and_spec() {
        eq(EarthShaderConstants.NIGHT_MULTIPLIER, 2.5f)
        eq(EarthShaderConstants.AMBIENT_NIGHT_R, 0.004f)
        eq(EarthShaderConstants.AMBIENT_NIGHT_G, 0.007f)
        eq(EarthShaderConstants.AMBIENT_NIGHT_B, 0.016f)
        eq(EarthShaderConstants.SPECULAR_POWER, 36.0f)
        eq(EarthShaderConstants.GLITTER, 0.7f)
    }
    @Test fun pins_night_default() = assertEquals(0.45f, EarthShaderConstants.NIGHT_DEFAULT, 1e-9f)
    @Test fun pins_ocean() {
        eq(EarthShaderConstants.OCEAN_THRESHOLD_LOW, 0.008f)
        eq(EarthShaderConstants.OCEAN_THRESHOLD_HIGH, 0.07f)
    }
}


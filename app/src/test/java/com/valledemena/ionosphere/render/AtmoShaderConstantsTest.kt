package com.valledemena.ionosphere.render

import org.junit.Assert.assertEquals
import org.junit.Test

class AtmoShaderConstantsTest {
    private fun eq(a: Float, b: Float) = assertEquals(b, a, 1e-6f)
    private fun eqi(a: Int, b: Int) = assertEquals(b, a)

    @Test fun pins_volume() {
        eq(AtmoShaderConstants.PLANET_R, 1.0f)
        eq(AtmoShaderConstants.ATM_R, 1.0255f)
        eq(AtmoShaderConstants.H_R, 0.0085f)
        eq(AtmoShaderConstants.H_M, 0.012f)
    }

    @Test fun pins_beta() {
        eq(AtmoShaderConstants.BETA_R_R, 0.0609f)
        eq(AtmoShaderConstants.BETA_R_G, 0.14175f)
        eq(AtmoShaderConstants.BETA_R_B, 0.34755f)
        eq(AtmoShaderConstants.BETA_M, 0.2205f)
    }

    @Test fun pins_mie() {
        eq(AtmoShaderConstants.G_MIE, 0.76f)
        eq(AtmoShaderConstants.EXTINCTION_MIE_SCALE, 1.1f)
        eq(AtmoShaderConstants.INTENSITY_SCALE, 20.0f)
    }

    @Test fun pins_knobs() {
        eqi(AtmoShaderConstants.DEFAULT_STEPS, 12)
        eqi(AtmoShaderConstants.DEFAULT_LIGHT_STEPS, 4)
        eqi(AtmoShaderConstants.STEPS_MIN, 8)
        eqi(AtmoShaderConstants.STEPS_MAX, 16)
        eqi(AtmoShaderConstants.LIGHT_STEPS_MIN, 3)
        eqi(AtmoShaderConstants.LIGHT_STEPS_MAX, 6)
    }
}

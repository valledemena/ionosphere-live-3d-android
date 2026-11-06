package com.valledemena.ionosphere.render

import org.junit.Assert.assertEquals
import org.junit.Test

class AtmoShaderConstantsTest {
    private fun eq(a: Float, b: Float) = assertEquals(b, a, 1e-6f)
    @Test fun pins_r() { eq(AtmoShaderConstants.ATM_R, 1.0255f); eq(AtmoShaderConstants.PLANET_R, 1.0f) }
    @Test fun pins_h() { eq(AtmoShaderConstants.H_R, 0.0085f); eq(AtmoShaderConstants.H_M, 0.012f) }
    @Test fun pins_beta_channels() {
        eq(AtmoShaderConstants.BETA_R_R, 0.0609f)
        eq(AtmoShaderConstants.BETA_R_G, 0.14175f)
        eq(AtmoShaderConstants.BETA_R_B, 0.34755f)
        eq(AtmoShaderConstants.BETA_M, 0.2205f)
    }
    @Test fun pins_mie() { eq(AtmoShaderConstants.G_MIE, 0.76f) }
    @Test fun pins_knobs() {
        eq(AtmoShaderConstants.STEPS_MIN.toFloat(), 8f)
        eq(AtmoShaderConstants.STEPS_MAX.toFloat(), 16f)
        eq(AtmoShaderConstants.LIGHT_STEPS_MIN.toFloat(), 3f)
        eq(AtmoShaderConstants.LIGHT_STEPS_MAX.toFloat(), 6f)
    }
}

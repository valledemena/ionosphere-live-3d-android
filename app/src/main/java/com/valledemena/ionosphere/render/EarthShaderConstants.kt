package com.valledemena.ionosphere.render
object EarthShaderConstants {
    // Parses web 8-coded EARTH_FRAG; interpolations occur in the shader string, not kept separate.
    // Shot: version 8cdc334. Every constant below is consumed by the FRAGMENT_BODY string.
    const val DAY_NIGHT_TERMINATOR_LOW = -0.08f
    const val DAY_NIGHT_TERMINATOR_HIGH = 0.25f
    const val NIGHT_BAND_LOW = -0.08f
    const val NIGHT_BAND_MID = 0.02f
    const val NIGHT_BAND_HIGH = 0.35f
    const val NIGHT_MULTIPLIER = 2.5f
    const val AMBIENT_NIGHT_R = 0.004f
    const val AMBIENT_NIGHT_G = 0.007f
    const val AMBIENT_NIGHT_B = 0.016f
    const val SPECULAR_POWER = 36.0f
    const val GLITTER = 0.7f
    const val OCEAN_THRESHOLD_LOW = 0.008f
    const val OCEAN_THRESHOLD_HIGH = 0.07f
}


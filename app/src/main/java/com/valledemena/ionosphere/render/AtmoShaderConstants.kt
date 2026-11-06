package com.valledemena.ionosphere.render

// Web GLSL 8cdc334 ATMO_FRAG constants (pinned before port).
object AtmoShaderConstants {
    const val ATM_R = 1.0255f
    const val H_R = 0.0085f
    const val H_M = 0.012f
    // BETA_R is a vec3 in the web (channel chromatic).
    const val BETA_R_R = 0.0609f    // 5.8  * 0.0105
    const val BETA_R_G = 0.14175f  // 13.5 * 0.0105
    const val BETA_R_B = 0.34755f  // 33.1 * 0.0105
    const val BETA_M = 0.2205f
    const val G_MIE = 0.76f
    const val PLANET_R = 1.0f
    const val STEPS_MIN = 8
    const val STEPS_MAX = 16
    const val LIGHT_STEPS_MIN = 3
    const val LIGHT_STEPS_MAX = 6
}

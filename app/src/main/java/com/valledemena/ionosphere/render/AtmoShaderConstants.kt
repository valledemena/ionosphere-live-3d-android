package com.valledemena.ionosphere.render

// Pins from the web reference (repo ionosphere-live-3d @ 8cdc334):
//  - physics: src/components/ionosphere/shaders.ts, ATMO_FRAG L130-137
//  - defaults: src/components/ionosphere/builders.ts L130-131 (uSteps 12, uLightSteps 4)
// BETA_R is a vec3 in the web (channel chromatic): Rayleigh (450,550,650nm) / Mie, scaled by 0.0105.
object AtmoShaderConstants {
    const val PLANET_R = 1.0f
    const val ATM_R = 1.0255f     // ~162 km
    const val H_R = 0.0085f       // Rayleigh scale height in planet radii (8.5 km / 6371 km, ajustado)
    const val H_M = 0.012f
    const val BETA_R_R = 0.0609f  // 5.8 * 0.0105
    const val BETA_R_G = 0.14175f // 13.5 * 0.0105
    const val BETA_R_B = 0.34755f // 33.1 * 0.0105
    const val BETA_M = 0.2205f    // 21.0 * 0.0105
    const val G_MIE = 0.76f
    const val EXTINCTION_MIE_SCALE = 1.1f  // web shaders.ts L204 (BETA_M * 1.1)
    const val INTENSITY_SCALE = 20.0f      // web shaders.ts L210
    const val DEFAULT_STEPS = 12           // web builders.ts L130
    const val DEFAULT_LIGHT_STEPS = 4      // web builders.ts L131
    const val STEPS_MIN = 8
    const val STEPS_MAX = 16
    const val LIGHT_STEPS_MIN = 3
    const val LIGHT_STEPS_MAX = 6
}

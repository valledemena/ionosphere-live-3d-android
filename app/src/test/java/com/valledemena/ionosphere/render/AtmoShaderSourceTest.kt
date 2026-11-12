package com.valledemena.ionosphere.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmoShaderSourceTest {
    private val vert = AtmoShaderSource.ATMO_VERT
    private val frag = AtmoShaderSource.ATMO_FRAG

    @Test fun vert_follows_codebase_convention() {
        assertTrue(vert.contains("uniform mat4 uMvp;"))
        assertTrue(vert.contains("uniform mat4 uModel;"))
        assertTrue(vert.contains("in vec3 aPosition;"))
        assertTrue(vert.contains("out vec3 vFragPos;"))
        assertTrue(vert.contains("gl_Position = uMvp * wp;"))
        assertFalse(vert.contains("varying"))
        assertFalse(vert.contains("projectionMatrix"))
        assertFalse(vert.contains("viewMatrix"))
        assertFalse(vert.contains("modelMatrix"))
        assertFalse(vert.contains("in vec3 position;"))
    }

    @Test fun frag_es3_header() {
        assertTrue(frag.contains("precision highp float;"))
        assertTrue(frag.contains("in vec3 vFragPos;"))
        assertTrue(frag.contains("out vec4 fragColor;"))
        assertFalse(frag.contains("varying"))
        assertFalse(frag.contains("gl_FragColor"))
    }

    @Test fun frag_web_raymarch_structure() {
        // view optical depth accumulates before extinction (web shaders.ts L195-197)
        assertTrue(frag.contains("float dR = exp(-h / H_R) * dt;"))
        assertTrue(frag.contains("float dM = exp(-h / H_M) * dt;"))
        assertTrue(frag.contains("odR += dR; odM += dM;"))
        // planet shadow skips the sample before the light march (web L200-201)
        assertTrue(frag.contains("if (tPlanet > 0.0) continue;"))
        // extinction: beta INSIDE the exponential, chromatic vec3 (web L204-205)
        assertTrue(frag.contains("vec3 tau = BETA_R * (odR + lod.x)"))
        assertTrue(frag.contains("vec3 att = exp(-tau);"))
        assertTrue(frag.contains("sumR += dR * att;"))
        assertTrue(frag.contains("sumM += dM * att;"))
        // segment guards (web L176-177)
        assertTrue(frag.contains("if (segLen <= 0.0) discard;"))
        assertTrue(frag.contains("t0 = max(t0, 0.0);"))
        // exact web phase functions (L182-185)
        assertTrue(frag.contains("3.0 / (16.0 * 3.14159265)"))
        assertTrue(frag.contains("3.0 / (8.0 * 3.14159265)"))
        // single combine after the loop (web L210)
        assertTrue(frag.contains("sumR * BETA_R * phaseR + sumM * BETA_M * phaseM"))
    }

    @Test fun frag_renders_pinned_values() {
        // ${AtmoShaderConstants.X} renders these literals into the GLSL string
        assertTrue(frag.contains("1.0255"))
        assertTrue(frag.contains("0.0085"))
        assertTrue(frag.contains("0.012"))
        assertTrue(frag.contains("0.0609"))
        assertTrue(frag.contains("0.14175"))
        assertTrue(frag.contains("0.34755"))
        assertTrue(frag.contains("0.2205"))
        assertTrue(frag.contains("0.76"))
        assertTrue(frag.contains("1.1"))
        assertTrue(frag.contains("20.0"))
        assertTrue(frag.contains("i < 16"))
        assertTrue(frag.contains("i < 6"))
    }

    @Test fun frag_light_od_parity() {
        // web latent quirk kept on purpose: near-root-only raySphere makes lightOD
        // return 0 for samples inside the atmosphere (backlog rider, do not "fix" here)
        assertTrue(frag.contains("if (tExit <= 0.0) return vec2(0.0);"))
        assertTrue(frag.contains("return vec2(odR, odM);"))
    }
}

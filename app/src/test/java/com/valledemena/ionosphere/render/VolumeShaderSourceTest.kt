package com.valledemena.ionosphere.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeShaderSourceTest {

    private val frag: String = VolumeShaderSource.VOLUME_FRAG

    // ---- interfaz de GLES ----

    @Test
    fun `G7c_20 sampler3D en alta precision`() {
        assertTrue(frag.contains("uniform highp sampler3D uDensity;"))
        assertTrue(frag.contains("uniform highp sampler3D uLayer;"))
        assertTrue(frag.contains("uniform highp sampler2D uColormap;"))
    }

    @Test
    fun `G7c_21 modo de volumen y radii`() {
        assertTrue(frag.contains("uniform int uMode;"))
        assertTrue(frag.contains("uniform float uInnerR;"))
        assertTrue(frag.contains("uniform float uOuterR;"))
        assertTrue(frag.contains("uniform float uOpacity;"))
        assertTrue(frag.contains("uniform int uSteps;"))
        assertTrue(frag.contains("uniform ivec3 uVolRes;"))
        assertTrue(frag.contains("uniform vec3 uCamPos;"))
        assertTrue(frag.contains("uniform vec3 uSunDir;"))
        assertTrue(frag.contains("in vec3 vWorldPos;"))
    }

    // ---- bucle: pasos pre-registrados = 64 ----

    @Test
    fun `G7c_22 bucle de marcha de 64 pasos`() {
        assertTrue(frag.contains("for (int i = 0; i < ${VOL_STEPS_MAX}; i++) {"))
        assertEquals(64, VOL_STEPS_REF)
        assertEquals(64, VOL_STEPS_MAX)
    }

    @Test
    fun `G7c_23 wiring funcional del marcha`() {
        assertTrue(frag.contains("vec3 p = ro + rd * t;"))
        assertTrue(frag.contains("float altNorm = clamp((r - uInnerR) / (uOuterR - uInnerR), 0.0, 1.0);"))
        assertTrue(frag.contains("float d = texture(uDensity, vec3(uv, altNorm)).r;"))
        assertTrue(frag.contains("col *= sunShadow;"))
        assertTrue(frag.contains("float a = clamp(d * d * uOpacity * dt * 40.0, 0.0, 1.0) * sunShadow;"))
        assertTrue(frag.contains("accumColor += (1.0 - accumAlpha) * a * col;"))
        assertTrue(frag.contains("accumAlpha += (1.0 - accumAlpha) * a;"))
        assertTrue(frag.contains("fragColor = vec4(accumColor, accumAlpha);"))
    }

    // ---- mapeo de marco del motor (espejo 0334-errata, medido por GLM en 0427) ----

    @Test
    fun `G7c_24 marco del motor del motor`() {
        assertTrue(frag.contains("float phi = -atan(n.z, n.x);"))
        assertTrue(frag.contains("float u = fract(phi / 6.28318530718);"))
        assertTrue(frag.contains("vec2 uv = vec2(u, lat / 3.14159265 + 0.5);"))
        assertTrue(frag.contains("float lat = asin(clamp(n.y, -1.0, 1.0));"))
    }

    // ---- capas (chapman): texelFetch exacto y colores byte-iguales ----

    @Test
    fun `G7c_25 rama chapman tevisa capas y colores`() {
        assertTrue(frag.contains("if (uMode == 1) {"))
        assertTrue(frag.contains("int w = int(texelFetch(uLayer, ip, 0).r * 255.0 + 0.5);"))
        assertTrue(frag.contains("if (w == 0)      col = vec3(1.00, 0.55, 0.10);"))
        assertTrue(frag.contains("else if (w == 1) col = vec3(0.20, 0.85, 0.30);"))
        assertTrue(frag.contains("else if (w == 2) col = vec3(1.00, 0.90, 0.20);"))
        assertTrue(frag.contains("else             col = vec3(0.20, 0.80, 1.00);"))
    }

    // ---- densidad (density): colormap viridis ----

    @Test
    fun `G7c_26 rama density usa colormap`() {
        assertTrue(frag.contains("col = texture(uColormap, vec2(d, 0.5)).rgb;"))
    }

    // ---- sentinelas ----

    @Test
    fun `G7c_27 discarta sin terminado y a cero alpha`() {
        assertTrue(frag.contains("discard"))
        assertTrue(frag.contains("if (accumAlpha > 0.98) break;"))
        assertTrue(frag.contains("if (accumAlpha <= 0.001) discard;"))
    }

    @Test
    fun `G7c_28 sin ES rObsolete ni gl_FragColor`() {
        assertFalse(frag.contains("gl_FragColor"))
        assertFalse(frag.contains("varying "))
        assertFalse(frag.contains("texture2D"))
    }
}

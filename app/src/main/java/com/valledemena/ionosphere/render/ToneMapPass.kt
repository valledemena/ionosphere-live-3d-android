package com.valledemena.ionosphere.render

import kotlin.math.pow

/**
 * Slice B + C (R4 ii): output pass tipo OutputPass.
 *
 * Tone mapping ACES con exposicion 1.12 (pin IonosphereScene.tsx:306-307),
 * composicion aditiva del bloom y codificacion sRGB. El pase se ejecuta en
 * precision highp (misma decision que el pin web).
 *
 * [toneCurve] es la misma curva que el shader, en Kotlin puro, para que el
 * test conductual compare numeros y no texto.
 */
class ToneMapPass {
    companion object {
        private const val ACES_1_12 = 1.12f

        /** Misma constante que el pin: exposicion ACES. */
        const val EXPOSURE = 1.12f

        val FRAGMENT = """
            precision highp float;
            in vec2 vUv;
            out vec4 fragColor;
            uniform sampler2D uScene;
            uniform sampler2D uBloom;
            uniform float uBloomStrength;
            uniform float uExposure; // ACES exposure 1.12
            const float ACES_OUTPUT_EXPOSURE = 1.12;
            vec3 acesFilmic(vec3 x) {
                x *= 1.0 / 0.6;
                const mat3 m1 = mat3(0.59719, 0.07600, 0.02840,
                                     0.35458, 0.90834, 0.13383,
                                     0.04823, 0.01566, 0.83777);
                const mat3 m2 = mat3(1.60475, -0.10208, -0.00327,
                                     -0.53108,  1.10813, -0.07276,
                                     -0.07367, -0.00605, 1.07602);
                vec3 v = m1 * x;
                vec3 a = v * (v + 0.0245786) - 0.000090537;
                vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;
                vec3 y = m2 * (a / b);
                return clamp(y, vec3(0.0), vec3(1.0));
            }
            float toSrgb(float c) {
                if (c <= 0.0031308) return c * 12.92;
                return pow(c, 0.41666) * 1.055 - 0.055;
            }
            void main() {
                vec3 scene = texture(uScene, vUv).rgb;
                vec3 bloom = texture(uBloom, vUv).rgb;
                vec3 c = (scene + bloom * uBloomStrength) * uExposure;
                c = acesFilmic(c);
                fragColor = vec4(toSrgb(c.r), toSrgb(c.g), toSrgb(c.b), 1.0);
            }
        """.trimIndent()

        private val M1 = floatArrayOf(
            0.59719f, 0.07600f, 0.02840f,
            0.35458f, 0.90834f, 0.13383f,
            0.04823f, 0.01566f, 0.83777f,
        )
        private val M2 = floatArrayOf(
            1.60475f, -0.10208f, -0.00327f,
            -0.53108f, 1.10813f, -0.07276f,
            -0.07367f, -0.00605f, 1.07602f,
        )

        /**
         * Curva ACES + sRGB en Kotlin puro (misma matematica que FRAGMENT).
         * Entrada lineal HDR; salida display-referred 0..1.
         */
        fun toneCurve(r: Float, g: Float, b: Float): FloatArray {
            val x0 = r * EXPOSURE / 0.6f
            val x1 = g * EXPOSURE / 0.6f
            val x2 = b * EXPOSURE / 0.6f
            val v0 = M1[0] * x0 + M1[3] * x1 + M1[6] * x2
            val v1 = M1[1] * x0 + M1[4] * x1 + M1[7] * x2
            val v2 = M1[2] * x0 + M1[5] * x1 + M1[8] * x2
            val f0 = (v0 * (v0 + 0.0245786f) - 0.000090537f) / (v0 * (0.983729f * v0 + 0.4329510f) + 0.238081f)
            val f1 = (v1 * (v1 + 0.0245786f) - 0.000090537f) / (v1 * (0.983729f * v1 + 0.4329510f) + 0.238081f)
            val f2 = (v2 * (v2 + 0.0245786f) - 0.000090537f) / (v2 * (0.983729f * v2 + 0.4329510f) + 0.238081f)
            val y0 = (M2[0] * f0 + M2[3] * f1 + M2[6] * f2).coerceIn(0f, 1f)
            val y1 = (M2[1] * f0 + M2[4] * f1 + M2[7] * f2).coerceIn(0f, 1f)
            val y2 = (M2[2] * f0 + M2[5] * f1 + M2[8] * f2).coerceIn(0f, 1f)
            return floatArrayOf(toSrgb(y0), toSrgb(y1), toSrgb(y2))
        }

        /** Convenience for grey input (tests / diagnostics). */
        fun toneCurve(value: Float): Float = toneCurve(value, value, value)[0]

        private fun toSrgb(c: Float): Float =
            if (c <= 0.0031308f) c * 12.92f
            else (c.toDouble().pow(0.41666) * 1.055 - 0.055).toFloat()

        /** The ACES exposure constant, kept referenced to avoid dead constants. */
        fun exposure(): Float = ACES_1_12
    }
}

package com.valledemena.ionosphere.render

/**
 * Slice B (R4 ii): output pass tipo OutputPass.
 * Tone mapping ACES con exposicion 1.12 (pin: IonosphereScene.tsx:306-307)
 * y codificacion sRGB. Sin cambiar geometrica ni atmo.
 */
class ToneMapPass {
    companion object {
        private const val ACES_1_12 = 1.12f

        val FRAGMENT = """
            #version 300 es
            precision mediump float;
            in vec2 vUv;
            out vec4 fragColor;
            uniform sampler2D uScene;
            uniform float uExposure; // ACES exposure 1.12
            const float ACES_OUTPUT_EXPOSURE = 1.12; // exposure 1.12
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
                vec3 c = texture(uScene, vUv).rgb * ACES_OUTPUT_EXPOSURE;
                c = acesFilmic(c);
                fragColor = vec4(toSrgb(c.r), toSrgb(c.g), toSrgb(c.b), 1.0);
            }
        """.trimIndent()
    }
}

package com.valledemena.ionosphere.render

/**
 * Slice C (R4 ii): bloom separable tipo UnrealBloomPass.
 *
 * Pin web: strength 0.55, radius 0.5, threshold 0.72. El pase de umbral extrae
 * las zonas HDR; las dos pasadas separables (horizontal + vertical) aplican una
 * gaussiana de 9 taps; el tone map compone el resultado antes de ACES.
 */
class BloomPass {
    companion object {
        const val STRENGTH = 0.55f
        const val RADIUS = 0.5f
        const val THRESHOLD = 0.72f
        private const val KNEE = 0.5f
        private const val TAP_A = 1.3846f
        private const val TAP_B = 3.2308f
        private const val WEIGHT_CENTER = 0.227027f
        private const val WEIGHT_NEAR = 0.316216f
        private const val WEIGHT_FAR = 0.070270f

        /** Threshold / soft-knee extract (linear HDR). */
        val THRESHOLD_FRAGMENT = """
            precision highp float;
            in vec2 vUv;
            out vec4 fragColor;
            uniform sampler2D uScene;
            uniform float uThreshold;
            void main() {
                vec3 c = texture(uScene, vUv).rgb;
                float l = max(max(c.r, c.g), c.b);
                float knee = uThreshold * ${KNEE};
                float soft = clamp(l - uThreshold + knee, 0.0, 2.0 * knee);
                soft = soft * soft / (4.0 * knee + 0.0001);
                float contribution = max(soft, l - uThreshold) / max(l, 0.0001);
                fragColor = vec4(c * contribution, 1.0);
            }
        """.trimIndent()

        /** Separable horizontal pass. */
        val HORIZONTAL_FRAGMENT = """
            precision highp float;
            in vec2 vUv;
            out vec4 fragColor;
            uniform sampler2D uScene;
            uniform vec2 uDirection;
            void main() {
                vec3 sum = texture(uScene, vUv).rgb * ${WEIGHT_CENTER};
                sum += (texture(uScene, vUv + uDirection * ${TAP_A}).rgb
                      + texture(uScene, vUv - uDirection * ${TAP_A}).rgb) * ${WEIGHT_NEAR};
                sum += (texture(uScene, vUv + uDirection * ${TAP_B}).rgb
                      + texture(uScene, vUv - uDirection * ${TAP_B}).rgb) * ${WEIGHT_FAR};
                fragColor = vec4(sum, 1.0);
            }
        """.trimIndent()

        /** Separable vertical pass. */
        val VERTICAL_FRAGMENT = """
            precision highp float;
            in vec2 vUv;
            out vec4 fragColor;
            uniform sampler2D uScene;
            uniform vec2 uDirection;
            void main() {
                vec3 sum = texture(uScene, vUv).rgb * ${WEIGHT_CENTER};
                sum += (texture(uScene, vUv + uDirection * ${TAP_A}).rgb
                      + texture(uScene, vUv - uDirection * ${TAP_A}).rgb) * ${WEIGHT_NEAR};
                sum += (texture(uScene, vUv + uDirection * ${TAP_B}).rgb
                      + texture(uScene, vUv - uDirection * ${TAP_B}).rgb) * ${WEIGHT_FAR};
                fragColor = vec4(sum, 1.0);
            }
        """.trimIndent()

        /** Direction vector for the horizontal pass, in UV units. */
        fun horizontalDirection(invWidth: Float): FloatArray = floatArrayOf(invWidth * RADIUS, 0f)

        /** Direction vector for the vertical pass, in UV units. */
        fun verticalDirection(invHeight: Float): FloatArray = floatArrayOf(0f, invHeight * RADIUS)
    }
}

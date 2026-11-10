package com.valledemena.ionosphere.render

/**
 * Slice C (R4 ii): god-rays radiales post-ACES.
 *
 * Pin web (godrays.ts:10-15): NTAPS 12, DECAY 0.9, MAX_RADIUS 0.45 (fraccion
 * de alto), GAIN 0.12. El pase es el ultimo de la cadena: recibe la imagen ya
 * tone-mapped y suma la luz radial del sol en espacio de pantalla.
 *
 * CPU gate: si el sol no aporta (detras de camara o fuera del encuadre) el
 * host hace skip del pase completo.
 */
class GodRaysPass {
    companion object {
        const val NTAPS = 12
        const val DECAY = 0.9f
        const val MAX_RADIUS = 0.45f
        const val GAIN = 0.12f
        private const val GATE_MARGIN = 1.15f

        val FRAGMENT = """
            precision highp float;
            in vec2 vUv;
            out vec4 fragColor;
            uniform sampler2D uScene;
            uniform vec2 uSunScreen;
            uniform float uAspect;
            uniform float uDecay;
            uniform float uMaxRadius;
            uniform float uGain;
            void main() {
                vec2 delta = vUv - uSunScreen;
                delta.x *= uAspect;
                delta *= uMaxRadius / float(${NTAPS});
                vec2 uv = vUv;
                float illum = 0.0;
                float w = 1.0;
                for (int i = 0; i < ${NTAPS}; i++) {
                    uv -= delta;
                    vec3 s = texture(uScene, clamp(uv, 0.0, 1.0)).rgb;
                    illum += dot(s, vec3(0.2126, 0.7152, 0.0722)) * w;
                    w *= uDecay;
                }
                illum /= float(${NTAPS});
                vec3 base = texture(uScene, vUv).rgb;
                fragColor = vec4(base + vec3(illum * uGain), 1.0);
            }
        """.trimIndent()

        /**
         * CPU gate. [w] is clip-space w, [ndcX]/[ndcY] the projected sun.
         * Returns false when the host must skip the god-rays pass.
         */
        fun sunContributes(w: Float, ndcX: Float, ndcY: Float): Boolean =
            w > 0f && ndcX >= -GATE_MARGIN && ndcX <= GATE_MARGIN &&
                ndcY >= -GATE_MARGIN && ndcY <= GATE_MARGIN

        fun defaultDecay(): Float = DECAY
        fun defaultMaxRadius(): Float = MAX_RADIUS
        fun defaultGain(): Float = GAIN
    }
}

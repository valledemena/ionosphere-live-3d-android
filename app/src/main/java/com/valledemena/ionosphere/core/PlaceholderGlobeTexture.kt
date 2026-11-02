package com.valledemena.ionosphere.core

/**
 * Textura equirectangular de R1: **procedural**, generada en el dispositivo.
 *
 * No es un asset de terceros ni pretende ser geografía: es un globo de trabajo
 * (gradiente de latitud + retícula de 15°) para validar el pipeline de textura,
 * mipmaps y muestreo. La procedencia es el propio repo (GPL-3.0) y así consta en
 * `NOTICE`. Cuando entre una textura real (Blue Marble u otra con licencia
 * declarada y compresión ASTC/ETC2), sustituye a esta sin tocar el renderer.
 *
 * Puro Kotlin: sin Android, testeable en JVM.
 */
object PlaceholderGlobeTexture {

    /** Tier medium acordado en `ARCHITECTURE_DECISION.md` §4. */
    const val TIER_MEDIUM_WIDTH = 2048
    const val TIER_MEDIUM_HEIGHT = 1024

    private const val GRATICULE_STEP_DEGREES = 15
    private const val GRATICULE_ALPHA = 0.55f

    /**
     * Genera píxeles ARGB8888 empaquetados (formato que espera
     * `GLUtils.texImage2D` / `glTexImage2D` con `GL_RGBA`).
     */
    fun generate(
        width: Int = TIER_MEDIUM_WIDTH,
        height: Int = TIER_MEDIUM_HEIGHT,
    ): IntArray {
        require(width >= 4) { "width debe ser >= 4" }
        require(height >= 2) { "height debe ser >= 2" }

        val pixels = IntArray(width * height)
        val meridianStepPx = (width * GRATICULE_STEP_DEGREES / 360f)
        val parallelStepPx = (height * GRATICULE_STEP_DEGREES / 180f)

        for (y in 0 until height) {
            val v = y.toFloat() / (height - 1)          // 0 = norte, 1 = sur
            val latitude = 90f - v * 180f
            val base = oceanColor(latitude)

            for (x in 0 until width) {
                var color = base

                val onMeridian = isNearMultiple(x.toFloat(), meridianStepPx, 1.6f)
                val onParallel = isNearMultiple(y.toFloat(), parallelStepPx, 1.6f)

                if (onMeridian || onParallel) {
                    val equator = Math.abs(latitude) < 1e-3f
                    color = blend(color, if (equator) 0xFFFFFFFF.toInt() else 0xFFB9D4E8.toInt(), GRATICULE_ALPHA)
                }

                pixels[y * width + x] = color
            }
        }

        return pixels
    }

    /**
     * Convierte ARGB8888 (formato de [generate]) a la secuencia de bytes que
     * `glTexImage2D` espera para `GL_RGBA` y `GL_UNSIGNED_BYTE`: **R, G, B, A**,
     * en ese orden y sin depender del endianness de la máquina.
     *
     * Escribir los `Int` directamente sobre un `IntBuffer` little-endian deja en
     * memoria `B, G, R, A` y el driver lee el azul como rojo: el globo sale
     * rojizo. Este es el único punto donde se decide el orden de canales, y por
     * eso está aquí, en código puro, con test propio.
     */
    fun toRgbaBytes(pixels: IntArray): ByteArray {
        val out = ByteArray(pixels.size * 4)
        var i = 0
        for (argb in pixels) {
            out[i++] = ((argb shr 16) and 0xFF).toByte()   // R
            out[i++] = ((argb shr 8) and 0xFF).toByte()    // G
            out[i++] = (argb and 0xFF).toByte()            // B
            out[i++] = ((argb ushr 24) and 0xFF).toByte()  // A
        }
        return out
    }

    /** Degradado por latitud: azul profundo en el ecuador, gris azulado en los polos. */
    internal fun oceanColor(latitude: Float): Int {        val t = (Math.abs(latitude) / 90f).coerceIn(0f, 1f)
        val r = lerp(0x0A, 0x8A, t).toInt()
        val g = lerp(0x2A, 0x9E, t).toInt()
        val b = lerp(0x4A, 0xBC, t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun isNearMultiple(value: Float, step: Float, tolerancePx: Float): Boolean {
        if (step <= 0f) return false
        val nearest = Math.round(value / step) * step
        return Math.abs(value - nearest) <= tolerancePx
    }

    private fun lerp(from: Int, to: Int, t: Float): Float = from + (to - from) * t

    private fun blend(base: Int, overlay: Int, alpha: Float): Int {
        val a = alpha.coerceIn(0f, 1f)
        val br = (base shr 16) and 0xFF
        val bg = (base shr 8) and 0xFF
        val bb = base and 0xFF
        val or = (overlay shr 16) and 0xFF
        val og = (overlay shr 8) and 0xFF
        val ob = overlay and 0xFF

        val r = (br + (or - br) * a).toInt()
        val g = (bg + (og - bg) * a).toInt()
        val b = (bb + (ob - bb) * a).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

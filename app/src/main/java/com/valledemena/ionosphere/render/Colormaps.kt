package com.valledemena.ionosphere.render

/**
 * Espejo verbatim de `src/lib/iono/colormaps.ts` (sampleColormap + buildPalette).
 * Cinco colormaps científicos: viridis, inferno, plasma, turbo, aurora. Tablas idénticas al web.
 */
object Colormaps {
    enum class Name(val key: String) {
        VIRIDIS("viridis"),
        INFERNO("inferno"),
        PLASMA("plasma"),
        TURBO("turbo"),
        AURORA("aurora")
    }

    private data class Stop(val pos: Double, val r: Double, val g: Double, val b: Double)

    private val VIRIDIS = listOf(
        Stop(0.0, 0.267,0.004,0.329), Stop(0.13,0.282,0.14,0.458), Stop(0.25, 0.253,0.265,0.53),
        Stop(0.38,0.191,0.407,0.557), Stop(0.5,0.127,0.566,0.551), Stop(0.63, 0.206,0.718,0.473),
        Stop(0.75,0.478,0.824,0.318), Stop(0.88,0.741,0.873,0.15), Stop(1.0,0.993,0.906,0.144)
    )
    private val INFERNO = listOf(
        Stop(0.0, 0.0,0.0,0.04), Stop(0.13,0.108,0.031,0.31), Stop(0.25,0.358,0.045,0.451),
        Stop(0.38,0.595,0.083,0.405), Stop(0.5,0.786,0.153,0.203), Stop(0.63,0.932,0.318,0.131),
        Stop(0.75,0.984,0.5,0.147), Stop(0.88,0.988,0.711,0.3), Stop(1.0,0.988,0.998,0.645)
    )
    private val PLASMA = listOf(
        Stop(0.0,0.05,0.03,0.527), Stop(0.13,0.24,0.017,0.678), Stop(0.25,0.471,0.028,0.696),
        Stop(0.38,0.681,0.107,0.594), Stop(0.5,0.838,0.264,0.443), Stop(0.63,0.944,0.446,0.32),
        Stop(0.75,0.986,0.654,0.258), Stop(0.88,0.989,0.852,0.354), Stop(1.0,0.94,0.975,0.131)
    )
    private val TURBO = listOf(
        Stop(0.0,0.19,0.071,0.232), Stop(0.13,0.214,0.357,0.77), Stop(0.25,0.165,0.604,0.843),
        Stop(0.38,0.196,0.784,0.582), Stop(0.5,0.45,0.866,0.314), Stop(0.63,0.762,0.784,0.042),
        Stop(0.75,0.954,0.555,0.036), Stop(0.88,0.966,0.256,0.052), Stop(1.0,0.648,0.033,0.112)
    )
    private val AURORA = listOf(
        Stop(0.0, 0.0,0.01,0.0), Stop(0.25, 0.0,0.25,0.05), Stop(0.5, 0.05,0.65,0.15),
        Stop(0.75, 0.3,1.0,0.35), Stop(1.0, 0.85,1.0,0.55)
    )

    private fun table(name: Name): List<Stop> = when (name) {
        Name.VIRIDIS -> VIRIDIS
        Name.INFERNO -> INFERNO
        Name.PLASMA -> PLASMA
        Name.TURBO -> TURBO
        Name.AURORA -> AURORA
    }
    private fun tableStr(key: String): List<Stop> {
        val nm = Name.values().firstOrNull { it.key == key } ?: Name.VIRIDIS
        return table(nm)
    }

    fun sample(name: String, t: Double): DoubleArray {
        val stops = tableStr(name)
        val tt = maxOf(0.0, minOf(1.0, t))
        var s = 0
        for (j in 0 until (stops.size - 1)) {
            if (tt >= stops[j].pos && tt <= stops[j + 1].pos) { s = j; break }
        }
        val a = stops[s]
        val b = stops.getOrElse(s + 1) { stops[stops.size - 1] }
        val f = (tt - a.pos) / (b.pos - a.pos + 1e-6)
        return doubleArrayOf(a.r + f * (b.r - a.r), a.g + f * (b.g - a.g), a.b + f * (b.b - a.b))
    }

    fun sample(name: Name, t: Double): DoubleArray = sample(name.key, t)

    // Web returns a NaN-palette for n<=1; here invalid input throws (guard only valid input).
    fun buildPalette(name: String, n: Int): DoubleArray {
        require(n >= 2) { "n must be >=2" }
        val stops = tableStr(name)
        val out = DoubleArray(n * 3)
        for (i in 0 until n) {
            val t = i.toDouble() / (n - 1).toDouble()
            var s = 0
            for (j in 0 until (stops.size - 1)) {
                if (t >= stops[j].pos && t <= stops[j + 1].pos) { s = j; break }
            }
            val f = (t - stops[s].pos) / (stops[s + 1].pos - stops[s].pos + 1e-6)
            out[i * 3 + 0] = stops[s].r + f * (stops[s + 1].r - stops[s].r)
            out[i * 3 + 1] = stops[s].g + f * (stops[s + 1].g - stops[s].g)
            out[i * 3 + 2] = stops[s].b + f * (stops[s + 1].b - stops[s].b)
        }
        return out
    }

    fun buildPalette(name: Name, n: Int): DoubleArray = buildPalette(name.key, n)
}

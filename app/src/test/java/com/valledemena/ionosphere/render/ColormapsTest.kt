package com.valledemena.ionosphere.render

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColormapsTest {

    private fun approx(a: DoubleArray, b: DoubleArray, eps: Double = 1e-4) {
        assertEquals(a.size, b.size)
        for (i in a.indices) assertTrue("idx $i expected ${b[i]} got ${a[i]}", Math.abs(a[i] - b[i]) <= eps)
    }

    @Test
    fun sample_viridis_extremos_webpin() {
        approx(Colormaps.sample("viridis", 0.0), doubleArrayOf(0.267, 0.004, 0.329))
        approx(Colormaps.sample("viridis", 1.0), doubleArrayOf(0.993, 0.906, 0.144))
    }

    @Test
    fun sample_clamp_limits_and_mid() {
        // clamp out of bounds and endpoint compare
        approx(Colormaps.sample("viridis", -2.0), Colormaps.sample("viridis", 0.0))
        approx(Colormaps.sample("viridis", 3.0), Colormaps.sample("viridis", 1.0))
        // midpoint between pos=0.25 and 0.38 (f=0.5 on segment)
        val ip = Colormaps.sample("viridis", 0.315)
        assertEquals(0.222, ip[0], 1e-4)
    }

    @Test
    fun unknown_name_and_nullish_fallback_viridis() {
        approx(Colormaps.sample("no-such-map", 0.5), Colormaps.sample("viridis", 0.5))
        approx(Colormaps.sample("inferno", 0.0), doubleArrayOf(0.0, 0.0, 0.04))
    }

    @Test
    fun palette_length_and_first_last_match_sample() {
        for (name in arrayOf("viridis", "inferno", "plasma", "turbo", "aurora")) {
            val n = 11
            val p = Colormaps.buildPalette(name, n)
            assertEquals(n * 3, p.size)
            val s0 = Colormaps.sample(name, 0.0)
            val s1 = Colormaps.sample(name, 1.0)
            approx(doubleArrayOf(p[0], p[1], p[2]), s0, 1e-9)
            approx(doubleArrayOf(p[p.size-3], p[p.size-2], p[p.size-1]), s1, 1e-9)
        }
    }

    @Test
    fun buildPalette_assemble_index() {
        val p = Colormaps.buildPalette("aurora", 5)
        assertEquals(15, p.size)
    }

    @Test
    fun enums_cover_all_web_names() {
        for (n in Colormaps.Name.values()) {
            Colormaps.sample(n, 0.5) // smoke
        }
    }
}

package com.valledemena.ionosphere.render

import org.junit.Assert.assertEquals
import org.junit.Test

class ColormapsPinsTest {

    private data class Pin(
        val name: String, val t: Double,
        val r: Double, val g: Double, val b: Double
    )

    // 16 pines generados ejecutando src/lib/iono/colormaps.ts (8cdc334), t parciales de stop
    // (la guarda 1e-6 altera el interpolador; los valores son los reales del web con 6 decimales).
    // Tarea T5: verificacion contra renders web quedo cerrada con tolerancia 1e-4/canal.
    private val pins = listOf(
        Pin("viridis", 0.25, 0.253, 0.265, 0.530),
        Pin("inferno", 0.25, 0.358, 0.045, 0.451),
        Pin("plasma", 0.25, 0.471, 0.028, 0.696),
        Pin("turbo", 0.25, 0.165, 0.604, 0.843),
        Pin("aurora", 0.25, 0.000, 0.250, 0.050),
        Pin("inferno", 0.5, 0.786, 0.153, 0.203),
        Pin("plasma", 0.5, 0.838, 0.264, 0.443),
        Pin("turbo", 0.5, 0.450, 0.866, 0.314),
        Pin("viridis", 0.5, 0.127, 0.566, 0.551),
        Pin("aurora", 0.5, 0.050, 0.650, 0.150),
        Pin("plasma", 0.75, 0.986, 0.654, 0.258),
        Pin("turbo", 0.75, 0.954, 0.555, 0.036),
        Pin("inferno", 0.75, 0.984, 0.500, 0.147),
        Pin("viridis", 0.75, 0.478, 0.824, 0.318),
        Pin("aurora", 0.75, 0.300, 1.000, 0.350),
        Pin("viridis", 1.0, 0.993, 0.906, 0.144)
    )

    @Test
    fun pines_web_match_kotlin_16() {
        for (p in pins) {
            val out = Colormaps.sample(p.name, p.t)
            assertEquals("name=${p.name} t=${p.t} r", p.r, out[0], 1e-4)
            assertEquals("name=${p.name} t=${p.t} g", p.g, out[1], 1e-4)
            assertEquals("name=${p.name} t=${p.t} b", p.b, out[2], 1e-4)
        }
    }

    @Test
    fun exact_keys_not_case_variants() {
        // Web treats the case-mismatched key as unknown -> viridis. Exact match required (H1).
        val a = Colormaps.sample("turbo", 0.5)
        val b = Colormaps.sample("Turbo", 0.5)
        assertEquals("known", 0.450, a[0], 1e-4)
        assertEquals("unknown fallback", 0.127, b[0], 1e-4)
    }
}

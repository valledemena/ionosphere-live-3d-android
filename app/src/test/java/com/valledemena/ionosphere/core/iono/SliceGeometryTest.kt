package com.valledemena.ionosphere.core.iono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G8 / P3: espejo de `SliceLayer.cpp` y de `App::rebuildSlice`
 * (`App.cpp:3886-3951`).
 *
 * Fija las tres trampas del dictamen `from-glm/0403` seccion 5:
 * convencion `theta = pi - lon`, `LVL = 20` de la cortina frente al `altRes`
 * del volumen y bounds tomados del volumen; ademas las guardas de `omega`, el
 * layout `[j * seg + i]`, el alfa `1.2 * d` y la decision de rebuild.
 */
class SliceGeometryTest {

    private fun testVolume(
        lonRes: Int,
        latRes: Int,
        altRes: Int,
        altMinKm: Double = 60.0,
        altMaxKm: Double = 500.0,
        fill: (x: Int, y: Int, a: Int) -> Double,
    ): DensityVolume {
        val data = DoubleArray(lonRes * latRes * altRes)
        val layer = IntArray(lonRes * latRes * altRes)
        for (a in 0 until altRes) {
            for (y in 0 until latRes) {
                for (x in 0 until lonRes) {
                    data[volumeIndex(x, y, a, lonRes, latRes)] = fill(x, y, a)
                }
            }
        }
        return DensityVolume(lonRes, latRes, altRes, altMinKm, altMaxKm, data, layer, true, 0L)
    }

    @Test
    fun `G8_20 densityDirF pinea el marco del motor con theta igual a pi menos lon`() {
        val a = densityDirF(0.0f, 0.0f)
        assertEquals(-1.0f, a[0], 1e-6f)
        assertEquals(0.0f, a[1], 1e-6f)
        assertEquals(0.0f, a[2], 1e-6f)

        val b = densityDirF(0.0f, 90.0f)
        assertEquals(0.0f, b[0], 1e-6f)
        assertEquals(0.0f, b[1], 1e-6f)
        assertEquals(1.0f, b[2], 1e-6f)

        val c = densityDirF(90.0f, 33.0f)
        assertEquals(0.0f, c[0], 1e-6f)
        assertEquals(1.0f, c[1], 1e-6f)
        assertEquals(0.0f, c[2], 1e-6f)
    }

    @Test
    fun `G8_21 sliceArc calcula omega y las guardas de slerp`() {
        val quarter = sliceArc(0.0f, 0.0f, 0.0f, 90.0f)
        assertEquals(1.5707964f, quarter.omega, 1e-5f)
        assertTrue(quarter.useSlerp)

        val same = sliceArc(10.0f, 20.0f, 10.0f, 20.0f)
        assertEquals(0.0f, same.omega, 1e-5f)
        assertFalse(same.useSlerp)

        val antipodal = sliceArc(0.0f, 0.0f, 0.0f, 180.0f)
        assertEquals(3.1415927f, antipodal.omega, 1e-5f)
        assertFalse(antipodal.useSlerp)
    }

    @Test
    fun `G8_22 sliceDirectionAt interpola el gran circulo y pinea los extremos`() {
        val arc = sliceArc(0.0f, 0.0f, 0.0f, 90.0f)
        val mid = sliceDirectionAt(arc, 0.5f)
        assertEquals(-0.7071068f, mid[0], 1e-5f)
        assertEquals(0.0f, mid[1], 1e-5f)
        assertEquals(0.7071068f, mid[2], 1e-5f)

        val start = sliceDirectionAt(arc, 0.0f)
        assertEquals(-1.0f, start[0], 1e-6f)
        assertEquals(0.0f, start[1], 1e-6f)
        assertEquals(0.0f, start[2], 1e-6f)

        val end = sliceDirectionAt(arc, 1.0f)
        assertEquals(0.0f, end[0], 1e-6f)
        assertEquals(0.0f, end[1], 1e-6f)
        assertEquals(1.0f, end[2], 1e-6f)
    }

    @Test
    fun `G8_23 sliceDirectionAt usa nlerp cuando omega es degenerado`() {
        val same = sliceArc(10.0f, 20.0f, 10.0f, 20.0f)
        val mid = sliceDirectionAt(same, 0.5f)
        assertEquals(1.0f, mid[0] * mid[0] + mid[1] * mid[1] + mid[2] * mid[2], 1e-5f)
        // Antipodal exacto: la guarda devuelve vector nulo en vez de NaN.
        val antipodal = sliceArc(0.0f, 0.0f, 0.0f, 180.0f)
        val zero = sliceDirectionAt(antipodal, 0.5f)
        assertFalse(zero[0].isNaN())
        assertFalse(zero[2].isNaN())
    }

    @Test
    fun `G8_24 sliceAltKm usa LVL de la cortina y bounds del volumen`() {
        assertEquals(60.0f, sliceAltKm(0, 60.0f, 500.0f), 1e-6f)
        assertEquals(500.0f, sliceAltKm(19, 60.0f, 500.0f), 1e-3f)
        // Techo del volumen, no constante de la capa.
        assertEquals(700.0f, sliceAltKm(19, 60.0f, 700.0f), 1e-3f)
        // LVL = 20 no es el altRes del volumen: el mismo indice da otra altura.
        val curtain = sliceAltKm(1, 60.0f, 500.0f)
        val volume48 = volumeAltKmF(1, 48, 60.0f, 500.0f)
        assertTrue(curtain - volume48 > 1.0f)
        // Valores fijos (exponente 1.5 y denominador LVL-1 = 19).
        assertEquals(65.31279f, curtain, 1e-3f)
        assertEquals(60.0f, sliceAltKm(0, 60.0f, 500.0f, lvl = 1), 0.0f)
    }

    @Test
    fun `G8_24b buildSliceValues toma los bounds del volumen no 500`() {
        val volume = testVolume(4, 4, 4, altMinKm = 60.0, altMaxKm = 700.0) { _, _, a ->
            (100 * a).toDouble()
        }
        val arc = sliceArc(0.0f, 0.0f, 0.0f, 90.0f)
        val samples = buildSliceValues(volume, arc, seg = 2, lvl = 4)
        // Nivel superior = 700 del volumen; a 500 el muestreo caeria entre los
        // niveles 2 y 3 y daria ~251.6 en vez de 300.
        assertEquals(300.0f, samples.values[3 * 2], 0.0f)
        assertEquals(300.0f, samples.values[3 * 2 + 1], 0.0f)
        assertEquals(700.0f, sliceAltKm(3, 60.0f, 700.0f, lvl = 4), 1e-3f)
    }

    @Test
    fun `G8_24c buildSliceValues usa LVL de la cortina no altRes del volumen`() {
        // altRes = 6, lvl = 4: el nivel superior de la cortina (700) cae en el
        // ultimo nivel del volumen (500). Si se usara altRes como lvl, el nivel
        // 3 caeria a ~357 km y el valor seria 300.
        val volume = testVolume(4, 4, 6, altMinKm = 60.0, altMaxKm = 700.0) { _, _, a ->
            (100 * a).toDouble()
        }
        val arc = sliceArc(0.0f, 0.0f, 0.0f, 90.0f)
        val samples = buildSliceValues(volume, arc, seg = 1, lvl = 4)
        assertEquals(500.0f, samples.values[3], 0.0f)
        assertEquals(500.0f, samples.peak, 0.0f)
    }

    @Test
    fun `G8_25 buildSliceValues con volumen constante da malla uniforme`() {
        val volume = testVolume(4, 4, 4) { _, _, _ -> 0.5 }
        val arc = sliceArc(0.0f, 0.0f, 0.0f, 90.0f)
        val samples = buildSliceValues(volume, arc)
        assertEquals(SLICE_SEG * SLICE_LVL, samples.values.size)
        samples.values.forEach { assertEquals(0.5f, it, 1e-6f) }
        assertEquals(0.5f, samples.peak, 1e-6f)
        assertEquals(10.25f, samples.peakLogNe, 1e-6f)
    }

    @Test
    fun `G8_26 buildSliceValues muestrea latitud a lo largo del meridiano`() {
        val latRes = 18
        // El volumen depende solo de la fila (latitud).
        val volume = testVolume(4, latRes, 2) { _, y, _ -> y.toDouble() / (latRes - 1) }
        val arc = sliceArc(0.0f, 0.0f, 60.0f, 0.0f)
        val samples = buildSliceValues(volume, arc)

        val level0 = FloatArray(SLICE_SEG) { samples.values[0 * SLICE_SEG + it] }
        assertEquals(0.5f, level0[0], 1e-4f)
        assertEquals(14.5f / 17.0f, level0[SLICE_SEG - 1], 1e-4f)
        for (i in 1 until SLICE_SEG) assertTrue(level0[i] >= level0[i - 1] - 1e-6f)
        assertEquals(samples.peak, samples.values.maxOrNull()!!, 0.0f)
    }

    @Test
    fun `G8_27 slicePeakLog y sliceAlpha`() {
        assertEquals(8.0f, slicePeakLog(0.0f), 0.0f)
        assertEquals(12.5f, slicePeakLog(1.0f), 1e-6f)
        assertEquals(8.9f, slicePeakLog(0.2f), 1e-6f)

        assertEquals(0.0f, sliceAlpha(0.0f), 0.0f)
        assertEquals(0.6f, sliceAlpha(0.5f), 1e-6f)
        assertEquals(1.0f, sliceAlpha(1.0f), 0.0f)
        assertEquals(0.0f, sliceAlpha(-0.5f), 0.0f)
        // 1.2 * 0.9 = 1.08 se clampa a 1.
        assertEquals(1.0f, sliceAlpha(0.9f), 0.0f)
    }

    @Test
    fun `G8_28 opacidad y radio del vertice`() {
        assertEquals(0.9f, clampSliceOpacity(SLICE_OPACITY_DEFAULT), 0.0f)
        assertEquals(1.5f, clampSliceOpacity(5.0f), 0.0f)
        assertEquals(0.0f, clampSliceOpacity(-1.0f), 0.0f)
        assertEquals(1.0f, sliceVertexRadius(0.0f), 0.0f)
        assertEquals(2.0f, sliceVertexRadius(6371.0f), 1e-6f)
    }

    @Test
    fun `G8_29 sliceAction cubre par ausente pronto keep y rebuild`() {
        val pair = SlicePair("EA036", "EB040")
        assertEquals(SliceAction.Hide, sliceAction(null, "EB040", true, null, false))
        assertEquals(SliceAction.Hide, sliceAction("EA036", null, true, null, false))
        assertEquals(SliceAction.Hide, sliceAction("EA036", "EA036", true, null, false))
        assertEquals(SliceAction.Keep, sliceAction("EA036", "EB040", true, pair, false))
        assertEquals(SliceAction.Rebuild, sliceAction("EA036", "EB040", true, pair, true))
        assertEquals(SliceAction.Keep, sliceAction("EA036", "EB040", false, null, false))
        assertEquals(SliceAction.Rebuild, sliceAction("EA036", "EB040", true, null, false))
        assertEquals(
            SliceAction.Rebuild,
            sliceAction("EA036", "EB041", true, pair, false),
        )
        assertEquals(SliceAction.Keep, sliceAction("EA036", "EB040", false, pair, false))
    }
}

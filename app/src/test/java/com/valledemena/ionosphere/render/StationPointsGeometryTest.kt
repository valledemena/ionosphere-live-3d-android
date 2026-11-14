package com.valledemena.ionosphere.render

import com.valledemena.ionosphere.data.model.IonosondeStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.min

class StationPointsGeometryTest {

    @Test
    fun posiciones_radio_1_012_y_lon_cero_en_x() {
        val p = stationPositions(listOf(station("A", 0.0, 0.0)))
        assertEquals(1.012f, p[0], 1e-5f)
        assertEquals(0f, p[1], 1e-5f)
        assertEquals(0f, p[2], 1e-5f)

        val north = stationPositions(listOf(station("N", 90.0, 0.0)))
        assertEquals(0f, north[0], 1e-5f)
        assertEquals(1.012f, north[1], 1e-5f)
        assertEquals(0f, north[2], 1e-5f)

        for (i in p.indices step 3) {
            val len = kotlin.math.sqrt(p[i] * p[i] + p[i + 1] * p[i + 1] + p[i + 2] * p[i + 2])
            assertEquals(1.012f, len, 1e-5f)
        }
    }

    @Test
    fun rango_degenerado_devuelve_span_1_5() {
        val (min, max) = stationColorRange(listOf(5.0, 5.0))
        assertEquals(5f, min, 0f)
        assertEquals(6.5f, max, 0f)
    }

    @Test
    fun rango_vacio_es_seguro() {
        val (min, max) = stationColorRange(emptyList())
        assertEquals(0f, min, 0f)
        assertEquals(1.5f, max, 0f)
    }

    @Test
    fun afo_nulo_invalido_y_fuera_de_rango() {
        val stations = listOf(
            station("A", 0.0, 0.0, foF2 = 5.0),
            station("B", 0.0, 0.0, foF2 = 5.5),
            station("C", 0.0, 0.0, foF2 = null),
            station("D", 0.0, 0.0, foF2 = -1.0),
            station("E", 0.0, 0.0, foF2 = 4.0),
            station("F", 0.0, 0.0, foF2 = 7.0),
            station("G", 0.0, 0.0, foF2 = Double.POSITIVE_INFINITY),
            station("H", 0.0, 0.0, foF2 = Double.NEGATIVE_INFINITY),
        )
        val values = stationFoValues(stations, 5f, 6.5f)
        assertEquals(0f, values[0], 0f)
        assertEquals(1f / 3f, values[1], 1e-6f)
        assertEquals(0f, values[2], 0f)
        assertEquals(0f, values[3], 0f)
        assertEquals(0f, values[4], 0f)
        assertEquals(1f, values[5], 0f)
        assertEquals(1f, values[6], 0f)
        assertEquals(0f, values[7], 0f)
    }

    @Test
    fun formula_kotlin_concuerda_con_constantes_del_shader() {
        val source = shaderSource()
        val numbers = Regex(
            """clamp\(([0-9.]+) \* uPointSize / ([0-9.]+) / dist, ([0-9.]+), min\(([0-9.]+) \* uPointSize / ([0-9.]+), ([0-9.]+)\)\) \* uPixelRatio""",
        ).find(source)
        requireNotNull(numbers) { "no se encontro la formula del vertex shader" }
        val base = numbers.groupValues[1].toFloat()
        val ref = numbers.groupValues[2].toFloat()
        val low = numbers.groupValues[3].toFloat()
        val highBase = numbers.groupValues[4].toFloat()
        val highRef = numbers.groupValues[5].toFloat()
        val highCap = numbers.groupValues[6].toFloat()

        val sizes = floatArrayOf(2f, 3f, 11f, 15f, 30f)
        val dists = floatArrayOf(1f, 1.2f, 1.412f, 2f, 5f)
        val ratios = floatArrayOf(1f, 2f, 2.75f)
        for (u in sizes) for (d in dists) for (r in ratios) {
            val expected = (base * u / ref / d).coerceIn(low, min(highBase * u / highRef, highCap)) * r
            assertEquals("u=$u d=$d r=$r", expected, stationPointPixels(u, d, r), 1e-4f * expected)
        }
    }

    private fun shaderSource(): String {
        val candidates = listOf(
            File("src/main/java/com/valledemena/ionosphere/gl/StationPointsRenderer.kt"),
            File("app/src/main/java/com/valledemena/ionosphere/gl/StationPointsRenderer.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
        assertTrue("fuente del shader no encontrada", file != null)
        return file!!.readText()
    }

    private fun station(code: String, lat: Double, lon: Double, foF2: Double? = null) =
        IonosondeStation(
            code = code,
            name = code,
            lat = lat,
            lon = lon,
            foF2 = foF2,
            muf3000 = null,
            history = emptyList(),
            foE = null,
            foF1 = null,
            hmF2 = null,
            B0 = null,
            B1 = null,
            stale = false,
        )
}

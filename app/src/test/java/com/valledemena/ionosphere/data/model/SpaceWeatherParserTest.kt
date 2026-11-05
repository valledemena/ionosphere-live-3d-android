package com.valledemena.ionosphere.data.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de parser puro del forecast (fixture real NOAA, dominio publico)
 * y de los helpers heredados del pin `de56407f` (route.ts) / parker.ts.
 */
class SpaceWeatherParserTest {

    private val fixture: String by lazy {
        findFixture()?.readText() ?: error("fixture 3-day-forecast-real.txt no encontrado en ningun path esperado")
    }

    /** Patron de la casa (IonosondeParserTest): buscar fixture subiendo directorios. */
    private fun findFixture(): File? {
        var d = File(System.getProperty("user.dir"))
        for (i in 0..10) {
            val c = File(d, "docs/fixtures/spaceweather/3-day-forecast-real.txt")
            if (c.exists()) return c
            val p = d.parentFile ?: break
            d = p
        }
        return null
    }

    // ---- forecast field-by-field ----

    @Test
    fun forecastExtract_maxKp_delTextoReal() {
        val f = SpaceWeatherParser.parseForecast(fixture)
        // "The greatest expected 3 hr Kp for Nov 05-Nov 07 2026 is 5.00 (NOAA Scale G1)."
        assertNotNull(f.maxKp)
        assertEquals(5.0, f.maxKp!!, 1e-9)
    }

    @Test
    fun forecastExtract_note_primeroRationale_yTruncadoA260() {
        val f = SpaceWeatherParser.parseForecast(fixture)
        assertTrue("nota debe empezar con G1", f.note.startsWith("G1 (Minor) geomagnetic storms"))
        assertTrue(f.note.length <= 260)
        assertTrue("sin saltos", !f.note.contains("\n"))
    }

    @Test
    fun forecastEnTextoVacio_noInventa() {
        val f = SpaceWeatherParser.parseForecast("")
        assertNull("maxKp ausente no se inventa", f.maxKp)
        assertEquals("", f.note)
    }

    // ---- xrayClass (espejo route.ts:58-64) ----

    @Test
    fun xrayClass_ABCMX_enUmbralesDeEscala() {
        assertEquals("X1.0", SpaceWeatherParser.xrayClass(1e-4))
        assertEquals("M1.0", SpaceWeatherParser.xrayClass(1e-5))
        assertEquals("C1.0", SpaceWeatherParser.xrayClass(1e-6))
        assertEquals("B1.0", SpaceWeatherParser.xrayClass(1e-7))
        assertEquals("A1.0", SpaceWeatherParser.xrayClass(1e-8))
    }

    @Test
    fun xrayClass_conFraccionNoEntera() {
        assertEquals("M2.5", SpaceWeatherParser.xrayClass(2.5e-5))
        assertEquals("A0.3", SpaceWeatherParser.xrayClass(3e-9))
    }

    // ---- gScale (espejo route.ts:66-73) ----

    @Test
    fun gScale_0a5_enUmbralesDeKp() {
        listOf(-1.0 to 0, 0.0 to 0, 4.9 to 0, 5.0 to 1, 6.0 to 2, 7.0 to 3, 8.0 to 4, 9.0 to 5, 9.9 to 5)
            .forEach { p -> assertEquals("kp=${p.first}", p.second, SpaceWeatherParser.gScale(p.first)) }
    }

    // ---- kpSeries (espejo route.ts:122-137) ----

    @Test
    fun kpSeries_filtra24h_remuestrea_yAnclaExtremoNuevo() {
        val now = 1_772_000_000_000L
        val step = 60_000L
        // feed DESCENDENTE en el stub (0 = mas nuevo): el parser es orden-agnostico
        val rows = (0 until 1000).map { i ->
            SpaceWeatherParser.KpRow(t = now - i * step, estimatedKp = 3.0, kpIndex = null)
        }
        val series = SpaceWeatherParser.kpSeries(rows, now)
        // step = max(1, 1000/96) = 10 -> ~100 pts remuestreados + ancla al final;
        // el cap a 96 es del adapter (takeLast), no del parser (fiel al web: slice(-96)).
        assertEquals("ancla = extremo nuevo", now, series.last().t)
        assertEquals("remuestreo 1/step + ancla", 101, series.size)
    }

    @Test
    fun kpSeries_sinRows_noInventa() {
        assertTrue(SpaceWeatherParser.kpSeries(emptyList(), 1L).isEmpty())
    }

    // ---- selectMagSeries (espejo parker.ts:78-86, feed multilinea) ----

    @Test
    fun selectMagSeries_fuenteActiva_ascendente_ventana3h() {
        val now = 1_772_000_000_000L
        val rows = listOf(
            SpaceWeatherParser.MagRow(t = now + 4 * 3600_000L, bx = null, active = true),     // fuera de ventana
            SpaceWeatherParser.MagRow(t = now, bx = 1.2, active = true),
            SpaceWeatherParser.MagRow(t = now - 3600_000L, bx = -0.5, active = true),         // en ventana
            SpaceWeatherParser.MagRow(t = now - 2 * 3600_000L, bx = null, active = true),     // sin bx
            SpaceWeatherParser.MagRow(t = now - 1_000L, bx = 9.9, active = false),            // inactiva
        )
        val s = SpaceWeatherParser.selectMagSeries(rows, hoursBack = 3, nowMs = now)
        assertEquals(2, s.size)
        assertEquals(-0.5, s[0].bx, 1e-9)
        assertEquals(1.2, s[1].bx, 1e-9)
    }

    @Test
    fun selectMagSeries_sinNavesActivas_caeATodas() {
        val now = 1_772_000_000_000L
        val rows = listOf(
            SpaceWeatherParser.MagRow(t = now, bx = 3.3, active = false),
            SpaceWeatherParser.MagRow(t = now - 60_000L, bx = -1.1, active = false),
        )
        assertEquals(2, SpaceWeatherParser.selectMagSeries(rows, nowMs = now).size)
    }
}

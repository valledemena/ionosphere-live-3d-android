package com.valledemena.ionosphere.core.iono

import com.valledemena.ionosphere.data.model.IonosondeStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * R4.6.1: cadena estaciones -> rejilla de perfiles -> volumen Ne(h), port de
 * `profileGrid.ts`. Cubre lo que el cableado necesita garantizar: distancia,
 * dia del ano, que una estacion viva produce densidad en su vertical y que una
 * muestra stale queda fuera del volumen.
 */
class ProfileGridTest {

    private fun station(
        code: String,
        lat: Double,
        lon: Double,
        foF2: Double?,
        hmF2: Double? = 300.0,
        foE: Double? = null,
        stale: Boolean = false,
    ): IonosondeStation = IonosondeStation(
        code = code,
        name = code,
        lat = lat,
        lon = lon,
        foF2 = foF2,
        muf3000 = null,
        history = emptyList(),
        foE = foE,
        foF1 = null,
        hmF2 = hmF2,
        B0 = null,
        B1 = null,
        stale = stale,
    )

    // Aristas del haversine en grados: cuarto de circulo y medio.
    @Test
    fun `havDeg mide en grados`() {
        assertEquals(0.0, havDeg(10.0, 20.0, 10.0, 20.0), 1e-9)
        assertEquals(90.0, havDeg(0.0, 0.0, 0.0, 90.0), 1e-9)
        assertEquals(180.0, havDeg(0.0, 0.0, 0.0, 180.0), 1e-9)
    }

    // 1..366 estilo gmtime, como el oraculo.
    @Test
    fun `dayOfYearUtc cuenta desde uno y respeta bisiesto`() {
        assertEquals(1, dayOfYearUtc(0L))
        assertEquals(366, dayOfYearUtc(Instant.parse("2024-12-31T12:00:00Z").epochSecond))
        assertEquals(60, dayOfYearUtc(Instant.parse("2024-02-29T00:00:00Z").epochSecond))
    }

    @Test
    fun `una estacion viva deja densidad en su vertical`() {
        val volume = buildVolumeFromStations(
            stations = listOf(station("MAD", 40.4, -3.7, foF2 = 7.0)),
            sunDir = doubleArrayOf(1.0, 0.0, 0.0),
            epochSec = Instant.parse("2026-11-18T12:00:00Z").epochSecond,
            f107 = 150.0,
        )
        assertTrue(volume.valid)
        val peak = sampleVolumeAt(volume, 40.4f, -3.7f, 300f)
        // 7 MHz -> Ne = 6,1e11 -> log10 11,8 -> normalizado ~0,84.
        assertTrue("normalizado en la vertical: $peak", peak > 0.5f)
        // Lejos de la estacion el campo NO es cero: el interpolar mezcla la
        // mediana de las muestras con el modelo climatologico (pin web).
        val far = sampleVolumeAt(volume, -60f, 170f, 300f)
        assertTrue("fondo climatologico: $far", far > 0.0f)

        // Sin ninguna estacion valida no hay rejilla y el volumen queda a cero:
        // la densidad de la vertical la aporta la medicion, no el modelo solo.
        val empty = buildVolumeFromStations(
            stations = emptyList(),
            sunDir = doubleArrayOf(1.0, 0.0, 0.0),
            epochSec = Instant.parse("2026-11-18T12:00:00Z").epochSecond,
            f107 = 150.0,
        )
        assertEquals(0.0f, sampleVolumeAt(empty, 40.4f, -3.7f, 300f), 0.0f)
    }

    @Test
    fun `una muestra stale no entra al volumen`() {
        val volume = buildVolumeFromStations(
            stations = listOf(station("OLD", 10.0, 10.0, foF2 = 9.0, stale = true)),
            sunDir = doubleArrayOf(1.0, 0.0, 0.0),
            epochSec = Instant.parse("2026-11-18T12:00:00Z").epochSecond,
            f107 = 150.0,
        )
        assertTrue(volume.valid)
        assertEquals(0.0f, sampleVolumeAt(volume, 10f, 10f, 300f), 0.0f)
    }
}

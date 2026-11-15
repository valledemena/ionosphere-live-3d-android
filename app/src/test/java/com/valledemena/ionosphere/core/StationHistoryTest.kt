package com.valledemena.ionosphere.core

import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.data.model.IonosondeStation
import com.valledemena.ionosphere.data.model.StationHistoryPoint
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Espejo de `replay.test.ts`: historia dispersa, clamp, lerp y ceros. */
class StationHistoryTest {

    private val t0 = Instant.parse("2026-10-05T12:00:00Z").toEpochMilli()
    private val hour = 3_600_000L

    private fun station(
        history: List<Pair<Double, Double>>,
        foF2: Double? = 7.0,
    ): IonosondeStation = IonosondeStation(
        code = "EB040",
        name = "ROQUETES",
        lat = 40.8,
        lon = 0.5,
        foF2 = foF2,
        muf3000 = null,
        history = history.map { (hours, fo) ->
            StationHistoryPoint(
                t = Instant.ofEpochMilli(t0 + (hours * hour).toLong()).toString(),
                foF2 = fo,
            )
        },
        foE = null,
        foF1 = null,
        hmF2 = null,
        B0 = null,
        B1 = null,
        stale = false,
    )

    private val sparse = station(
        listOf(
            0.0 to 6.0,
            9.0 to 9.0,
            30.0 to 5.0,
            150.0 to 8.0,
        ),
    )

    private fun at(hours: Double): Long = t0 + (hours * hour).toLong()

    @Test
    fun `clamp antes del primer punto`() {
        assertEquals(6.0, sampleStationAt(sparse, at(-1.0))!!, 0.0)
    }

    @Test
    fun `clamp despues del ultimo punto`() {
        assertEquals(8.0, sampleStationAt(sparse, at(200.0))!!, 0.0)
    }

    @Test
    fun `lerp en un hueco grande de nueve a treinta horas`() {
        assertEquals(7.0, sampleStationAt(sparse, at(19.5))!!, 1e-12)
    }

    @Test
    fun `valor exacto en un ionograma`() {
        assertEquals(5.0, sampleStationAt(sparse, at(30.0))!!, 0.0)
    }

    @Test
    fun `sin historia devuelve el foF2 actual`() {
        assertEquals(7.5, sampleStationAt(station(emptyList(), 7.5), at(100.0))!!, 0.0)
    }

    @Test
    fun `sin historia y sin foF2 devuelve null`() {
        assertNull(sampleStationAt(station(emptyList(), null), at(100.0)))
    }

    @Test
    fun `acepta timestamps sin segundos como el payload real`() {
        val s = station(emptyList(), 7.0).copy(
            history = listOf(StationHistoryPoint("2026-10-05T12:00Z", 6.0)),
        )
        assertEquals(6.0, sampleStationAt(s, t0)!!, 0.0)
    }

    @Test
    fun `un extremo cero toma el positivo`() {
        val zero = station(listOf(0.0 to 0.0, 10.0 to 8.0))
        assertEquals(8.0, sampleStationAt(zero, at(5.0))!!, 0.0)
    }

    @Test
    fun `ambos extremos cero no inventan un valor positivo`() {
        val zero = station(listOf(0.0 to 0.0, 10.0 to 0.0))
        assertEquals(0.0, sampleStationAt(zero, at(5.0))!!, 0.0)
    }

    @Test
    fun `sampledAt copia la estacion con el valor de la epoca`() {
        val sampled = sparse.sampledAt(at(19.5))
        assertEquals(7.0, sampled.foF2!!, 1e-12)
        assertEquals(sparse.code, sampled.code)
        assertEquals(sparse.history, sampled.history)
    }

    @Test
    fun `sampledAt sin historia conserva la misma estacion`() {
        val current = station(emptyList(), 7.5)
        assertSame(current, current.sampledAt(at(100.0)))
    }

    @Test
    fun `sampledAt del payload conserva metadatos y muestrea todas`() {
        val second = station(listOf(0.0 to 4.0, 10.0 to 6.0)).copy(code = "JR055")
        val data = IonosondeData(
            updated = 123L,
            stations = listOf(sparse, second),
            total = 2,
            catalogSize = 10,
            source = "test",
        )

        val sampled = data.sampledAt(at(19.5))

        assertEquals(123L, sampled.updated)
        assertEquals(2, sampled.total)
        assertEquals(10, sampled.catalogSize)
        assertEquals("test", sampled.source)
        assertEquals(listOf(7.0, 6.0), sampled.stations.map { it.foF2 })
    }
}

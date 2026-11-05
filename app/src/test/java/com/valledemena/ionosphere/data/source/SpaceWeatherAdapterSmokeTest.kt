package com.valledemena.ionosphere.data.source

import com.valledemena.ionosphere.data.net.HttpResult
import com.valledemena.ionosphere.data.net.HttpClient
import com.valledemena.ionosphere.data.net.InMemoryCache
import com.valledemena.ionosphere.data.net.Outcome
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Smoke test del SpaceWeatherAdapter SIN red (HttpClient stub, como
 * IonosondeAdapterSmokeTest). Verifica el H5 decision C (PR #11):
 *   - Nivel 1 usa exactamente 6 endpoints.
 *   - Nivel 2 (rtsw/sunspots/xray-1d/protones) corre bajo su TTL propio.
 *   - Cache fresca (< 90 s) hace 0 requests.
 *   - Fallo de transporte sin datos: Failed sin cache, Cached con cache.
 */
class SpaceWeatherAdapterSmokeTest {

    /** Stub: cada endpoint devuelve JSON minimo fiel a la forma real (harvest 2026-11-05). */
    private class StubHttp(private val forecastBody: String) : HttpClient {
        val calls = mutableListOf<String>()

        override suspend fun get(url: String, timeoutMs: Int): HttpResult {
            calls.add(url)
            val path = url.substringAfter(".gov")
            if (path.startsWith("/text/3-day-forecast.txt")) return HttpResult(200, forecastBody)
            if (path.startsWith("/json/planetary_k_index_1m.json")) {
                return HttpResult(200, """[{"time_tag":"2026-11-05T03:39:00","kp_index":0,"estimated_kp":0.33,"kp":"0P"}]""")
            }
            if (path.startsWith("/json/f107_cm_flux.json")) {
                return HttpResult(200, """[{"time_tag":"2026-11-05T03:39:00","flux":142.0}]""")
            }
            if (path.startsWith("/products/summary/solar-wind-speed.json")) {
                return HttpResult(200, """[{"proton_speed":333,"time_tag":"2026-11-05T09:36:00.000"}]""")
            }
            if (path.startsWith("/products/summary/solar-wind-mag-field.json")) {
                return HttpResult(200, """[{"bt":5,"bz_gsm":-1,"time_tag":"2026-11-05T09:36:00.000"}]""")
            }
            if (path.startsWith("/json/goes/primary/xrays-6-hour.json")) {
                return HttpResult(
                    200,
                    """[{"time_tag":"2026-11-05T03:40:00Z","satellite":18,"flux":9.2e-9,"energy":"0.05-0.4nm"},{"time_tag":"2026-11-05T03:41:00Z","satellite":18,"flux":9.2e-7,"energy":"0.1-0.8nm"}]""",
                )
            }
            if (path.startsWith("/json/rtsw/rtsw_wind_1m.json")) {
                return HttpResult(200, """[{"time_tag":"2026-11-05T09:36:00.000Z","proton_density":6.7}]""")
            }
            if (path.startsWith("/json/rtsw/rtsw_mag_1m.json")) {
                return HttpResult(
                    200,
                    """[{"time_tag":"2026-11-05T09:35:00.000Z","bx_gse":-0.4,"bt":5.0,"active":false},{"time_tag":"2026-11-05T09:36:00.000Z","bx_gse":1.1,"bt":5.0,"active":true}]""",
                )
            }
            if (path.startsWith("/json/goes/primary/xrays-1-day.json") ||
                path.startsWith("/json/goes/primary/integral-protons-1-day.json") ||
                path.startsWith("/json/sunspot_report.json")
            ) {
                return HttpResult(200, "") // sin bloques -> source honestamente "unavailable"
            }
            throw AssertionError("URL inesperada en el stub: $url")
        }
    }

    private fun fixtureText(): String {
        var d = File(System.getProperty("user.dir"))
        for (i in 0..10) {
            val c = File(d, "docs/fixtures/spaceweather/3-day-forecast-real.txt")
            if (c.exists()) return c.readText()
            val p = d.parentFile ?: break
            d = p
        }
        error("fixture 3-day-forecast-real.txt no encontrado en ningun path esperado")
    }

    private val nivel2Paths = listOf("rtsw", "sunspot", "integral-protons", "xrays-1-day")

    @Test
    fun ciclo1_parseFixtureReal_yNivel1SixEndpoints() = runBlocking {
        val http = StubHttp(fixtureText())
        val out = SpaceWeatherAdapter(http, InMemoryCache()).fetch()
        assertTrue("esperaba Live, salio ${out::class.simpleName}", out is Outcome.Live)
        val v = (out as Outcome.Live).value
        assertEquals(5.0, v.forecast.maxKp!!, 1e-9)
        assertEquals("NOAA 3-day (live)", v.forecast.source)
        assertTrue("nota del rationale G1", v.forecast.note.startsWith("G1 (Minor)"))
        assertFalse("sin saltos en la nota", v.forecast.note.contains("\n"))
        assertEquals(0.33, v.kp.now, 1e-9)
        val n1Calls = http.calls.count { c -> nivel2Paths.none { c.contains(it) } }
        assertEquals("Nivel 1 debe usar exactamente 6 endpoints", 6, n1Calls)
    }

    @Test
    fun cacheFresca_haceCeroRequests() = runBlocking {
        val http = StubHttp(fixtureText())
        val cache = InMemoryCache()
        val adapter = SpaceWeatherAdapter(http, cache)
        val first = adapter.fetch()
        assertTrue("primera lectura Live", first is Outcome.Live)
        val callsAfterFirst = http.calls.size
        val second = adapter.fetch() // cache fresca (<90 s): cero requests
        assertTrue("segunda lectura Cached por TTL fresco", second is Outcome.Cached)
        assertFalse("fresca no stale", (second as Outcome.Cached).stale)
        assertEquals("cache fresca = 0 requests", callsAfterFirst, http.calls.size)
        assertEquals((first as Outcome.Live).value.updated, second.value.updated)
    }

    @Test
    fun falloTransporte_failedSinCache_cachedConCache() = runBlocking {
        val failing = object : HttpClient {
            override suspend fun get(url: String, timeoutMs: Int): HttpResult =
                throw IllegalStateException("sin red simulada")
        }
        assertTrue(
            "sin cache y sin datos = Failed",
            SpaceWeatherAdapter(failing, InMemoryCache()).fetch() is Outcome.Failed,
        )

        val http = StubHttp(fixtureText())
        val cache = InMemoryCache()
        assertTrue(SpaceWeatherAdapter(http, cache).fetch() is Outcome.Live)
        val cached = SpaceWeatherAdapter(failing, cache).fetch()
        assertTrue("con cache previa el fallo debe degradar a Cached", cached is Outcome.Cached)
    }
}

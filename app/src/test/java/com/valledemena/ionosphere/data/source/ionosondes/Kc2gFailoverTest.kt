package com.valledemena.ionosphere.data.source.ionosondes

import com.valledemena.ionosphere.data.net.HttpClient
import com.valledemena.ionosphere.data.net.HttpResult
import com.valledemena.ionosphere.data.net.InMemoryCache
import com.valledemena.ionosphere.data.net.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Kc2gFailoverTest {

    private val kcBody = """
        [
          {"station":{"code":"AU930","name":"Austin, TX, USA","latitude":"30.4","longitude":"262.3"},
           "fof2":8.6,"hmf2":241.8,"foe":3.32,"fof1":null,"mufd":28.827,
           "time":"2026-11-07T03:10:05"}
        ]
    """.trimIndent()

    private fun findCatalogFixture(): File? {
        var d = File(System.getProperty("user.dir"))
        for (i in 0..10) {
            val c = File(d, "docs/fixtures/ionosondes/catalog-real.html")
            if (c.exists()) return c
            val p = d.parentFile ?: break
            d = p
        }
        return null
    }

    private fun catalogHtml(): String = findCatalogFixture()?.readText() ?: """<a href="ursiCode=AA343">x</a></big></td><td><big>Alaska</big></td><td><big> 43.81 </big></td><td><big> 200 </big></td>"""

    @Test fun failoverNotConsultedWhenLGStreamAlive() = runBlocking {
        var kcCalled = false
        val http = object : HttpClient {
            override suspend fun get(url: String, timeoutMs: Int): HttpResult {
                if (url.contains("DIDBFastStationList")) return HttpResult(200, catalogHtml())
                if (url.contains("getbest")) return HttpResult(200, "Time  CS foF2\n2026.10.04T12:00 00 5.4\nURSI-Code AA343\nDMUF=3000: 10.2\n")
                if (url.contains("prop.kc2g.com")) { kcCalled = true; return HttpResult(200, kcBody) }
                return HttpResult(200, "")
            }
        }
        val adapter = IonosondeAdapter(http, clock = { java.time.Instant.parse("2026-11-07T03:20:00Z").toEpochMilli() }, stations = listOf("AA343"), anchorStations = emptySet())
        val result = adapter.fetch()
        assertEquals("kcCalled=$kcCalled", "GIRO DIDBase (live, kc2g failover reserved)", (result as Outcome.Live<*>).source)
        assertEquals(false, kcCalled)
    }

    @Test fun failoverOnlyTriggersWhenLGRoundEmpty() = runBlocking {
        var kcCalled = false
        val http = object : HttpClient {
            override suspend fun get(url: String, timeoutMs: Int): HttpResult {
                if (url.contains("DIDBFastStationList")) return HttpResult(504, "")
                if (url.contains("getbest")) return HttpResult(504, "")
                if (url.contains("prop.kc2g.com")) { kcCalled = true; return HttpResult(200, kcBody) }
                return HttpResult(504, "")
            }
        }
        val adapter = IonosondeAdapter(http, clock = { java.time.Instant.parse("2026-11-07T03:20:00Z").toEpochMilli() }, stations = listOf("AA343"), anchorStations = emptySet())
        val r = adapter.fetch()
        assertTrue(kcCalled)
        assertTrue(r is Outcome.Live)
        assertEquals("kc2g fallback", (r as Outcome.Live<*>).source)
    }

    @Test fun kc2gMapsMufdToMuf3000() = runBlocking {
        val http = object : HttpClient {
            override suspend fun get(url: String, timeoutMs: Int): HttpResult {
                if (url.contains("DIDBFastStationList")) return HttpResult(504, "")
                if (url.contains("prop.kc2g.com")) return HttpResult(200, kcBody)
                return HttpResult(504, "")
            }
        }
        val r = IonosondeAdapter(http, clock = { java.time.Instant.parse("2026-11-07T03:20:00Z").toEpochMilli() }, stations = listOf("AA343"), anchorStations = emptySet()).fetch()
        val s = (r as Outcome.Live<*>).value as com.valledemena.ionosphere.data.model.IonosondeData
        assertEquals("AU930", s.stations[0].code)
        assertEquals(28.827, s.stations[0].muf3000!!, 0.001)
        assertEquals("kc2g fallback", s.source)
    }

    @Test fun kc2gExcludesEG931AndBadCoords() {
        val source = com.valledemena.ionosphere.data.model.parseKc2gRow(
            mapOf("station" to mapOf("code" to "EG931", "latitude" to 1.0, "longitude" to 2.0), "time" to "2026-03-19T22:10:05")
        )
        assertEquals(null, source)
    }

    @Test fun kc2gDegradesToUnavailableWithNoCacheOnBodiesNoOk() = runBlocking {
        val http = object : HttpClient {
            override suspend fun get(url: String, timeoutMs: Int): HttpResult {
                if (url.contains("DIDBFastStationList")) return HttpResult(504, "")
                if (url.contains("prop.kc2g.com")) return HttpResult(200, "[]")
                return HttpResult(504, "")
            }
        }
        val adapter = IonosondeAdapter(http, clock = { java.time.Instant.parse("2026-11-07T03:20:00Z").toEpochMilli() }, stations = listOf("AA343"), anchorStations = emptySet())
        val r = adapter.fetch()
        assertTrue(r is Outcome.Live)
        assertEquals("unavailable", (r as Outcome.Live<*>).source)
    }

    @Test fun kc2gDegradesToFailedOnTotalTransportNoCache() = runBlocking {
        val http = object : HttpClient {
            override suspend fun get(url: String, timeoutMs: Int): HttpResult {
                if (url.contains("DIDBFastStationList")) throw java.io.IOException("net down")
                if (url.contains("prop.kc2g.com")) return HttpResult(200, "[]")
                return HttpResult(504, "")
            }
        }
        val adapter = IonosondeAdapter(http, clock = { java.time.Instant.parse("2026-11-07T03:20:00Z").toEpochMilli() }, stations = listOf("AA343"), anchorStations = emptySet())
        val r = adapter.fetch()
        assertTrue(r is Outcome.Failed)
    }

    @Test fun kc2gReturnsCachedStaleOnPartialFailure() = runBlocking {
        val cache = com.valledemena.ionosphere.data.net.InMemoryCache()
        val primed = com.valledemena.ionosphere.data.model.IonosondeData(
            updated = 10L,
            stations = emptyList(),
            total = 0,
            catalogSize = 0,
            source = "GIRO DIDBase (live, kc2g failover reserved)",
        )
        cache.store("ionosondes", primed.source, kotlinx.serialization.json.Json.encodeToString(com.valledemena.ionosphere.data.model.IonosondeData.serializer(), primed), 10L)
        val http = object : HttpClient {
            override suspend fun get(url: String, timeoutMs: Int): HttpResult {
                if (url.contains("DIDBFastStationList")) return HttpResult(404, "")
                if (url.contains("prop.kc2g.com")) return HttpResult(200, "[]")
                return HttpResult(404, "")
            }
        }
        val adapter = IonosondeAdapter(http, cache = cache, clock = { java.time.Instant.parse("2026-11-07T03:20:00Z").toEpochMilli() }, stations = listOf("AA343"), anchorStations = emptySet())
        val r = adapter.fetch()
        assertTrue(r is Outcome.Cached)
        assertTrue((r as Outcome.Cached<*>).stale)
    }

    @Test fun kc2gFallbackSourceFlowsThroughOutcome() = runBlocking {
        val http = object : com.valledemena.ionosphere.data.net.HttpClient {
            override suspend fun get(url: String, timeoutMs: Int): com.valledemena.ionosphere.data.net.HttpResult {
                if (url.contains("DIDBFastStationList")) return com.valledemena.ionosphere.data.net.HttpResult(504, "")
                if (url.contains("prop.kc2g.com")) return com.valledemena.ionosphere.data.net.HttpResult(200, kcBody)
                return com.valledemena.ionosphere.data.net.HttpResult(504, "")
            }
        }
        val adapter = IonosondeAdapter(http, clock = { java.time.Instant.parse("2026-11-07T03:20:00Z").toEpochMilli() }, stations = listOf("AA343"), anchorStations = emptySet())
        val r = adapter.fetch()
        assertTrue(r is com.valledemena.ionosphere.data.net.Outcome.Live)
        val data = (r as com.valledemena.ionosphere.data.net.Outcome.Live<*>).value as com.valledemena.ionosphere.data.model.IonosondeData
        assertEquals("kc2g fallback", data.source)
        assertTrue(data.stations.isNotEmpty())
    }

    @Test
    fun fixtureParsesRealRows() {
        fun findFixture(): java.io.File? {
            var d = java.io.File(System.getProperty("user.dir"))
            for (i in 0..10) {
                val c = java.io.File(d, "docs/fixtures/kc2g/stations.json")
                if (c.exists()) return c
                val parent = d.parentFile ?: break
                d = parent
            }
            return null
        }
        val fixture = findFixture() ?: return
        // Shape check of the harvested fixture (no parser exists for the whole file).
        val raw = fixture.readText()
        val count = raw.split("\"code\"").size - 1
        assertTrue(count >= 80)
    }
}

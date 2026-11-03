package com.valledemena.ionosphere.data.source

import com.valledemena.ionosphere.data.model.AuroraData
import com.valledemena.ionosphere.data.net.HttpClient
import com.valledemena.ionosphere.data.net.HttpResult
import com.valledemena.ionosphere.data.net.InMemoryCache
import com.valledemena.ionosphere.data.net.Outcome
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraAdapterCacheTest {
    @Test
    fun `cache disponible y http 503 devuelve cached sin stale`() = runBlocking {
        val raw = """{"Observation Time":"o","Forecast Time":"f","coordinates":[[0,60,50]]}"""
        val clock = AtomicClock(initial = 123456789L)
        val cache = InMemoryCache()
        // pre-carga (el adaptador almacena via fetch live)
        val firstHttp = object : HttpClient { override suspend fun get(url: String, timeoutMs: Int) = HttpResult(200, raw) }
        val seed = AuroraAdapter(firstHttp, cache, { clock.get() }).fetch() // clock fixed = 123456789
        assertTrue(seed is Outcome.Live)
        // avanza un minuto (menos de 5) -> fresco
        clock.set(123456789L + 60_000L)
        val failHttp = object : HttpClient { override suspend fun get(url: String, timeoutMs: Int) = HttpResult(503, null) }
        val out = AuroraAdapter(failHttp, cache, { clock.get() }).fetch()
        when (out) {
            is Outcome.Cached -> { assertEquals(false, out.stale); org.junit.Assert.assertTrue(out.value.cells.isNotEmpty()) }
            else -> throw AssertionError("esperaba Cached, obtuvo $out")
        }
    }

    @Test
    fun `cache caducada y http falla devuelve cached stale`() = runBlocking {
        val raw = """{"Observation Time":"o","Forecast Time":"f","coordinates":[[0,60,50]]}"""
        val clock = AtomicClock(initial = 123456789L)
        val cache = InMemoryCache()
        val seed = AuroraAdapter(object : HttpClient { override suspend fun get(url: String, timeoutMs: Int) = HttpResult(200, raw) }, cache, { clock.get() }).fetch()
        assertTrue(seed is Outcome.Live)
        // avanza 10 min (>5 min stale)
        clock.set(123456789L + 10 * 60_000L)
        val out = AuroraAdapter(object : HttpClient { override suspend fun get(url: String, timeoutMs: Int) = HttpResult(503, null) }, cache, { clock.get() }).fetch()
        when (out) {
            is Outcome.Cached -> assertEquals(true, out.stale)
            else -> throw AssertionError("esperaba Cached stale, obtuvo $out")
        }
    }

    class AtomicClock(initial: Long) {
        var value: Long = initial
        fun set(v: Long) { value = v }
        fun get(): Long = value
    }
}

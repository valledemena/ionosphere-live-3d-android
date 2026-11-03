package com.valledemena.ionosphere.data.source

import com.valledemena.ionosphere.data.net.HttpClient
import com.valledemena.ionosphere.data.net.HttpResult
import com.valledemena.ionosphere.data.net.InMemoryCache
import com.valledemena.ionosphere.data.net.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class DstAdapterDegradationTest {
    companion object {
        private val sample = """DST2610*04RRX020   0  16  29  23  23  14  22  21  -4 -27 -18 -15 -22 -36 -47 -53 -60 -56 -56 -51 -49 -44 -36 -37 -37 -21"""
    }

    @Test fun `http 404 deja source unavailable (web parity)`() = runBlocking {
        val http = object : HttpClient { override suspend fun get(url: String, timeoutMs: Int) = HttpResult(404, "missing") }
        val r = DstAdapter(http).fetch()
        assertTrue(r is Outcome.Live && (r as Outcome.Live).source == "unavailable")
    }

    @Test fun `http 200 con WDC parsea como live`() = runBlocking {
        val http = object : HttpClient { override suspend fun get(url: String, timeoutMs: Int) = HttpResult(200, sample) }
        val r = DstAdapter(http).fetch()
        assertTrue(r is Outcome.Live && (r as Outcome.Live).source == "Kyoto WDC Dst realtime (live)")
    }

    @Test fun `cliente que lanza suspende resultado cacheado si hay cache`() = runBlocking {
        val cache = InMemoryCache()
        val good = object : HttpClient { override suspend fun get(url: String, timeoutMs: Int) = HttpResult(200, sample) }
        DstAdapter(good, cache).fetch()
        val throwing = object : HttpClient { override suspend fun get(url: String, timeoutMs: Int): HttpResult { throw java.io.IOException("net") } }
        val r = DstAdapter(throwing, cache).fetch()
        assertTrue(r is Outcome.Cached)
    }

    @Test fun `cliente que lanza sin cache deriva a failed`() = runBlocking {
        val throwing = object : HttpClient { override suspend fun get(url: String, timeoutMs: Int): HttpResult { throw java.io.IOException("net") } }
        val r = DstAdapter(throwing).fetch()
        assertTrue(r is Outcome.Failed)
    }
}

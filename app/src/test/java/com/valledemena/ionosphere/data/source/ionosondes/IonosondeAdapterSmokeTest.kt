package com.valledemena.ionosphere.data.source.ionosondes

import com.valledemena.ionosphere.data.net.HttpClient
import com.valledemena.ionosphere.data.net.HttpResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IonosondeAdapterSmokeTest {
    @Test fun `fetch Station 200 OK`() = runBlocking {
        val cat = """
            <a href="ursiCode=AA343">x</a></big></td><td><big>Alaska</big></td><td><big> 43.81 </big></td><td><big> 200 </big></td>
        """.trimIndent()
        val getbest = "Time  CS foF2\n2026.10.04T12:00 00 5.4\nURSI-Code AA343\nDMUF=3000: 10.2\n"
        val http = object : HttpClient {
            override suspend fun get(url: String, timeoutMs: Int): HttpResult {
                return if (url.contains("DIDBFastStationList")) HttpResult(200, cat)
                else HttpResult(200, getbest)
            }
        }
        val adapter = IonosondeAdapter(http, stations = listOf("AA343"), anchorStations = emptySet())
        assertTrue(adapter.fetch() is com.valledemena.ionosphere.data.net.Outcome.Live)
    }
}

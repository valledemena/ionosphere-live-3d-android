package com.valledemena.ionosphere.data.source

import com.valledemena.ionosphere.data.model.AuroraData
import com.valledemena.ionosphere.data.net.HttpClient
import com.valledemena.ionosphere.data.net.HttpResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraAdapterTest {

    @Test
    fun `parsea y downsamplea la ovation canonica`() = runBlockingish {
        val raw = """
            {
              "Observation Time": "2026-11-03T00:00Z",
              "Forecast Time": "2026-11-03T03:00Z",
              "coordinates": [
                [0, 60, 50], [1, 60, 40], [2, 61, 80],
                [10, 0, 5]
              ]
            }
        """.trimIndent()
        val http = object : HttpClient {
            override suspend fun get(url: String, timeoutMs: Int) = HttpResult(200, raw)
        }
        val r = AuroraAdapter(http).fetch()
        when (r) {
            is com.valledemena.ionosphere.data.net.Outcome.Live -> {
                val data: AuroraData = r.value
                assertEquals("2026-11-03T00:00Z", data.observationTime)
                assertTrue("debe haber al menos una celda auroral", data.cells.isNotEmpty())
                assertTrue(data.cells.all { it.p > 8 })
            }
            else -> throw AssertionError("esperaba Live, obtuvo $r")
        }
    }

    @Test
    fun `fallo http degrada a failure`() = runBlockingish {
        val http = object : HttpClient { override suspend fun get(url: String, timeoutMs: Int) = HttpResult(503, null) }
        val r = AuroraAdapter(http).fetch()
        assertTrue(r is com.valledemena.ionosphere.data.net.Outcome.Failed)
    }

    private fun runBlockingish(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}

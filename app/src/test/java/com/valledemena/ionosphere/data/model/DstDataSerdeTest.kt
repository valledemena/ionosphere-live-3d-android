package com.valledemena.ionosphere.data.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DstDataSerdeTest {
    @Test
    fun `round-trip con payload forma-real`() {
        val json = """
            {
              "now": -12.0,
              "min7d": -45.0,
              "series": [ { "t": 1700000000000, "dst": -10.0 } ],
              "count": 1,
              "gst": [ { "id": "g", "start": null, "kpMax": 4.0, "link": null } ],
              "checkedAt": 1700000100000,
              "source": "kyoto-wdc"
            }
        """.trimIndent()
        val parsed = Json.decodeFromString<DstData>(json)
        assertEquals("kyoto-wdc", parsed.source)
        val out = Json.encodeToString(DstData.serializer(), parsed)
        val again = Json.decodeFromString<DstData>(out)
        assertEquals(parsed, again)
    }

    @Test
    fun `payload forma-real con gst nullables presentes`() {
        val json = """
            { "now": 0.0, "min7d": 0.0, "series": [], "count": 0,
              "gst": [ { "id": "x", "start": null, "kpMax": null, "link": null } ],
              "checkedAt": 1, "source": "s" }
        """.trimIndent()
        val parsed = Json.decodeFromString<DstData>(json)
        assertEquals(null, parsed.gst[0].start)
        assertEquals(null, parsed.gst[0].kpMax)
        assertEquals(null, parsed.gst[0].link)
    }

    @Test
    fun `clave desconocida rechazada`() {
        val json = """{"now":0,"min7d":0,"series":[],"count":0,"gst":[],"checkedAt":1,"source":"s","foo":1}"""
        assertTrue(Json.runCatching { Json.decodeFromString<DstData>(json) }.exceptionOrNull() is SerializationException)
    }

    @Test
    fun `clave requerida faltante rechazada`() {
        val json = """{"now":0,"min7d":0,"series":[],"count":0,"gst":[]}"""  // checkedAt, source faltan
        assertTrue(Json.runCatching { Json.decodeFromString<DstData>(json) }.exceptionOrNull() is SerializationException)
    }
}

package com.valledemena.ionosphere.data.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IonosondeDataSerdeTest {
    @Test
    fun `round-trip con payload forma-real (nullables en null, claves presentes)`() {
        val json = """
            {
              "updated": 1700000000000,
              "stations": [ {
                "code": "E040", "name": "Ebre", "lat": 40.06, "lon": -0.25,
                "foF2": 5.5, "muf3000": 10.0,
                "history": [ { "t": "2026-11-03T00:00Z", "foF2": 5.4 } ],
                "foE": null, "foF1": null, "hmF2": null, "B0": null, "B1": null,
                "stale": false
              } ],
              "total": 1, "catalogSize": 1, "source": "giro"
            }
        """.trimIndent()
        val parsed = Json.decodeFromString<IonosondeData>(json)
        assertEquals("E040", parsed.stations[0].code)
        assertEquals(null, parsed.stations[0].foE)
        val out = Json.encodeToString(IonosondeData.serializer(), parsed)
        val again = Json.decodeFromString<IonosondeData>(out)
        assertEquals(parsed, again)
    }

    @Test
    fun `clave desconocida en estacion rechazada`() {
        val json = """
            { "updated":1, "total":1, "catalogSize":1, "source":"s", "stations":[
              { "code":"E040","name":"E","lat":0,"lon":0,"foF2":null,"muf3000":null,
                "history":[],"foE":null,"foF1":null,"hmF2":null,"B0":null,"B1":null,"stale":false,"foo":7 } ] }
        """.trimIndent()
        assertTrue(Json.runCatching { Json.decodeFromString<IonosondeData>(json) }.exceptionOrNull() is SerializationException)
    }

    @Test
    fun `clave extra ausente rechazada (foE obligatorio)`() {
        val json = """
            { "updated":1, "total":1, "catalogSize":1, "source":"s", "stations":[
              { "code":"E040","name":"E","lat":0,"lon":0,"foF2":null,"muf3000":null,
                "history":[],"stale":false } ] }
        """.trimIndent()
        assertTrue(Json.runCatching { Json.decodeFromString<IonosondeData>(json) }.exceptionOrNull() is SerializationException)
    }
}

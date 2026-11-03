package com.valledemena.ionosphere.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceWeatherDataSerdeTest {

    @Test
    fun `round-trip de un payload minimo`() {
        val json = """
            {
              "updated": 1700000000000,
              "utc": "2026-11-03T00:00:00Z",
              "kp": { "now": 3.5, "gScale": 1, "series": [ { "t": 1, "kp": 2.0 } ], "source": "swpc" },
              "f107": { "now": 150.0, "series": [ { "t": 2, "f": 149.0 } ], "source": "swpc" },
              "wind": { "speed": 1, "bt": 2, "bz": 3, "bx": 4, "density": 5, "t": null, "source": "s" },
              "mag": { "series": [ { "t": 3, "bx": 0.5 } ], "source": "s" },
              "xray": { "now": 1.0, "class": "B2", "series": [ { "t": 4, "f": 1.1 } ], "seriesDay": [ { "t": 5, "f": 1.2 } ], "source": "s" },
              "protons": { "now": 0.1, "series": [ { "t": 6, "f": 0.2 } ], "satellite": 18, "source": "s" },
              "sunspots": { "regions": [ { "region": 1, "lat": 10.0, "lon": 20.0, "loc": "N10W20" } ], "source": "s" },
              "forecast": { "maxKp": 4.0, "note": "n", "source": "s" }
            }
        """.trimIndent()
        val parsed = Json.decodeFromString<SpaceWeatherData>(json)
        assertEquals("B2", parsed.xray.xrayClass)
        val out = Json.encodeToString(SpaceWeatherData.serializer(), parsed)
        val again = Json.decodeFromString<SpaceWeatherData>(out)
        assertEquals(parsed, again)
    }
}

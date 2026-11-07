package com.valledemena.ionosphere.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Kc2gDataTest {

    @Test
    fun normalizeLonWrapsZeroThreeSixtyToNegative() {
        assertEquals(-6.7, normalizeLon("353.3"), 0.0001)
    }

    @Test
    fun excludedUrsiEg931IsRejected() {
        val row = mapOf(
            "station" to mapOf("code" to "EG931", "latitude" to 40.0, "longitude" to 3.0, "name" to "fake"),
            "fof2" to 8.6,
        )
        assertEquals(null, parseKc2gRow(row))
    }

    @Test
    fun validRowIsParsedWithExpectedFields() {
        val row = mapOf(
            "station" to mapOf("code" to "EA036", "latitude" to 40.3, "longitude" to 3.98, "name" to "Roquetes"),
            "fof2" to 8.6,
            "hmf2" to 250.0,
            "mufd" to 28.8,
            "time" to "2026-03-19T22:10:05",
        )
        val station = parseKc2gRow(row)
        assertEquals("EA036", station!!.code)
        assertEquals(8.6, station.foF2!!, 0.0001)
        assertEquals(250.0, station.hmF2!!, 0.0001)
        assertEquals(28.8, station.mufd!!, 0.0001)
        assertEquals(24 * 60, KC2G_CACHE_MAX_AGE_MIN)
    }

    @Test
    fun nullLatitudeIsRejected() {
        val row = mapOf(
            "station" to mapOf("code" to "PQ052", "latitude" to null, "longitude" to 14.0),
        )
        assertEquals(null, parseKc2gRow(row))
    }

    @Test
    fun futureStationIsFresh() {
        val station = Kc2gStation(code = "JR055", name = "Juliusruh", lat = 54.6, lon = 13.4, timeMs = 1000.0)
        val fresh = filterFresh(listOf(station), nowMs = 500L, maxAgeMs = 60_000L)
        assertEquals(1, fresh.size)
    }

    @Test
    fun staleStationIsFilteredOut() {
        val station = Kc2gStation(code = "JR055", name = "Juliusruh", lat = 54.6, lon = 13.4, timeMs = 1000.0)
        val fresh = filterFresh(listOf(station), nowMs = 600_000L, maxAgeMs = 60_000L)
        assertEquals(0, fresh.size)
    }

    @Test
    fun resolveSourceFollowsPrecedence() {
        assertEquals("live", resolveSource(live = 1, kc2g = 5, cacheAgeMin = null))
        assertEquals("kc2g", resolveSource(live = 0, kc2g = 5, cacheAgeMin = null))
        assertEquals("cache", resolveSource(live = 0, kc2g = 0, cacheAgeMin = 30))
        assertEquals("model", resolveSource(live = 0, kc2g = 0, cacheAgeMin = null))
    }

    @Test
    fun isCacheUsableRespectsTwentyFourHours() {
        assertTrue(isCacheUsable(24 * 60))
        assertFalse(isCacheUsable(24 * 60 + 1))
        assertFalse(isCacheUsable(null))
    }

    @Test
    fun cacheableOnlyWhenNonEmpty() {
        assertTrue(cacheable(1))
        assertFalse(cacheable(0))
    }

    @Test
    fun timeMsAcceptsMissingZ() {
        val ms = kc2gTimeMs("2026-03-19T22:10:05")
        assertTrue(ms.isFinite())
    }
}

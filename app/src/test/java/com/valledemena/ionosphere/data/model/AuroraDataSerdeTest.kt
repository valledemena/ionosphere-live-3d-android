package com.valledemena.ionosphere.data.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraDataSerdeTest {
    @Test
    fun `round-trip con payload forma-real`() {
        val json = """
            {
              "updated": 1700000000000,
              "observationTime": "2026-11-03T00:00:00Z",
              "forecastTime": "2026-11-03T03:00:00Z",
              "cells": [ { "lat": 60.0, "lon": -150.0, "p": 50 } ],
              "source": "ovation"
            }
        """.trimIndent()
        val parsed = Json.decodeFromString<AuroraData>(json)
        assertEquals(1, parsed.cells.size)
        val out = Json.encodeToString(AuroraData.serializer(), parsed)
        val again = Json.decodeFromString<AuroraData>(out)
        assertEquals(parsed, again)
    }

    @Test
    fun `clave desconocida rechazada`() {
        val json = """{"updated":1,"observationTime":"o","forecastTime":"f","cells":[],"source":"s","foo":1}"""
        assertTrue(Json.runCatching { Json.decodeFromString<AuroraData>(json) }.exceptionOrNull() is SerializationException)
    }

    @Test
    fun `clave requerida faltante rechazada`() {
        val json = """{"updated":1,"observationTime":"o","forecastTime":"f","cells":[]}""" // source faltante
        assertTrue(Json.runCatching { Json.decodeFromString<AuroraData>(json) }.exceptionOrNull() is SerializationException)
    }
}

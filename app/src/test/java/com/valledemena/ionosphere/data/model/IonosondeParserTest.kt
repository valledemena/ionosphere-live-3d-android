package com.valledemena.ionosphere.data.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IonosondeParserTest {
    @Test
    fun parseCatalog_real_html() {
        // Fixture dorado: HTML real capturado de lgdc.uml.edu (datos academicos, license note).
        fun findFixture(): File? {
            var d = File(System.getProperty("user.dir"))
            for (i in 0..10) {
                val c = File(d, "docs/fixtures/ionosondes/catalog-real.html")
                if (c.exists()) return c
                val p = d.parentFile ?: break
                d = p
            }
            return null
        }
        val f = findFixture() ?: error("fixture catalog-real.html no encontrado en ningun path esperado")
        val html = f.readText()
        val out = IonosondeParser.parseCatalog(html)
        assertTrue("regex debe casar al menos 100 estaciones del catalogo real", out.size >= 100)
        assertEquals("AA343", out[0].code)  // primer orden ursiCode
        val starbuck = out.firstOrNull { it.code == "AC843" }
        assertTrue("AC843 debe estar en el catalogo (IDAHO LAB)", starbuck != null)
        assertEquals(43.81, starbuck!!.lat, 1e-2)
        assertEquals(-112.68, starbuck.lon, 1e-2)
    }

    @Test
    fun parseGetBest_history() {
        val text = """
            # header
            Time CS foF2
            2026.10.04T12:00 00 5.4
            2026.10.04T13:00 00 5.5
            URSI-Code AA343
            DMUF=3000: 10.2
        """.trimIndent()
        val d = IonosondeParser.parseGetBest(text)
        assertEquals("AA343", d?.code)
        assertEquals(5.5, d?.foF2)
        assertEquals(10.2, d?.muf3000)
        assertEquals(2, d?.history?.size)
    }

    @Test
    fun parseCharValue() {
        val t = "Time x\n2026.10.04T00:00 00 4.2\n2026.10.04T01:00 00 5.1\n"
        assertEquals(5.1, IonosondeParser.parseCharValue(t))
    }
}

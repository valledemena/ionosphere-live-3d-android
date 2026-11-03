package com.valledemena.ionosphere.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DstParserTest {
    private val raw = """
        DST2610*04RRX020   0  16  29  23  23  14  22  21  -4 -27 -18 -15 -22 -36 -47 -53 -60 -56 -56 -51 -49 -44 -36 -37 -37 -21
        DST2610*05RRX020   0 -36 -44 -47 -55 -55 -44 -35 -35 -36 -44 -41 -31 -26 -23 -22 -31 -34 -37 -36 -38 -40 -41 -31 -29 -37
    """.trimIndent()

    @Test
    fun `extrae 48 puntos`() = assertEquals(48, DstParser.parseDstWdc(raw, 2026, 10).size)

    @Test
    fun `primeros 3 puntos canonicos`() {
        val pts = DstParser.parseDstWdc(raw, 2026, 10)
        assertEquals(java.util.Date.UTC(2026, 9, 4, 0), pts[0].t)
        assertEquals(0.0, pts[0].dst, 1e-9)
        assertEquals(java.util.Date.UTC(2026, 9, 4, 16), pts[16].t)
        assertEquals(-60.0, pts[16].dst, 1e-9)
        assertEquals(java.util.Date.UTC(2026, 9, 5, 0), pts[24].t)
        assertEquals(0.0, pts[24].dst, 1e-9)
    }

    @Test
    fun `parseGst maneja null y vacio`() = assertEquals(emptyList<DstStormData>(), DstParser.parseGst(""))

    @Test
    fun `dstBand clasifica bandas`() {
        assertEquals("quiet", DstParser.dstBand(0))
        assertEquals("intense", DstParser.dstBand(-150))
    }
}

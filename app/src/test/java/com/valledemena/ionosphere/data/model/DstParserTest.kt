package com.valledemena.ionosphere.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DstParserTest {
    companion object {
        const val E04T00 = 1791072000000L
        const val E04T16 = 1791129600000L
        const val E05T00 = 1791158400000L
        const val GST_START = 1791644400000L
    }

    private val raw = """
        DST2610*04RRX020   0  16  29  23  23  14  22  21  -4 -27 -18 -15 -22 -36 -47 -53 -60 -56 -56 -51 -49 -44 -36 -37 -37 -21
        DST2610*05RRX020   0 -36 -44 -47 -55 -55 -44 -35 -35 -36 -44 -41 -31 -26 -23 -22 -31 -34 -37 -36 -38 -40 -41 -31 -29 -37
    """.trimIndent()

    @Test fun extrae_48_puntos() = assertEquals(48, DstParser.parseDstWdc(raw, 2026, 10).size)

    @Test
    fun primeros_3_puntos_canonicos() {
        val pts = DstParser.parseDstWdc(raw, 2026, 10)
        assertEquals(E04T00, pts[0].t)
        assertEquals(0.0, pts[0].dst, 1e-9)
        assertEquals(E04T16, pts[16].t)
        assertEquals(-60.0, pts[16].dst, 1e-9)
        assertEquals(E05T00, pts[24].t)
        assertEquals(0.0, pts[24].dst, 1e-9)
    }

    @Test
    fun basura_y_values_ignorados() {
        val mix = raw + "\nBASURA\nDST2610*03RRX020  1  2\n"
        assertEquals(48, DstParser.parseDstWdc(mix, 2026, 10).size)
    }

    private val gstRaw = """
        [
          {"gstID":"2026-10-10T15:00:00-GST-001","startTime":"2026-10-10T15:00:00Z",
           "allKpIndex":[{"kpIndex":7.0},{"kpIndex":6.67}],
           "link":"https://ccmc.gsfc.nasa.gov/DONKI/view/GST/49066/-1"},
          {"gstID":"b","startTime":"2026-10-11T00:00:00","allKpIndex":[{"kpIndex":3.5}],"link":"https://x/y"}
        ]
    """.trimIndent()

    @Test
    fun gst_canonico_ts() {
        val gst = DstParser.parseGst(gstRaw)
        assertEquals(2, gst.size)
        assertEquals(GST_START, gst[0].start)
        assertEquals(7.0, gst[0].kpMax!!, 1e-9)
        assertTrue(gst[0].link!!.contains("ccmc.gsfc.nasa.gov/DONKI/view/GST"))
    }

    @Test fun gst_vacio() = assertEquals(emptyList<DstStormData>(), DstParser.parseGst(""))
    @Test fun gst_no_array() = assertEquals(emptyList<DstStormData>(), DstParser.parseGst("{ \"foo\": 1 }"))

    @Test fun dstBand_standargs() {
        assertEquals("quiet", DstParser.dstBand(0))
        assertEquals("quiet", DstParser.dstBand(-30))
        assertEquals("unsettled", DstParser.dstBand(-31))
        assertEquals("unsettled", DstParser.dstBand(-50))
        assertEquals("storm", DstParser.dstBand(-51))
        assertEquals("storm", DstParser.dstBand(-100))
        assertEquals("intense", DstParser.dstBand(-101))
    }
}

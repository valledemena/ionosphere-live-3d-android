package com.valledemena.ionosphere.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice C / W7: behavioral test of the ACES + sRGB curve.
 *
 * Compares numeric values produced by the same math the pass runs on device,
 * instead of checking source strings.
 */
class ToneMapPassTest {

    @Test
    fun blackStaysBlack() {
        assertEquals(0.0, ToneMapPass.toneCurve(0f).toDouble(), 0.0001)
    }

    @Test
    fun midGreyMatchesAcesCurve() {
        assertEquals(0.5319, ToneMapPass.toneCurve(0.18f).toDouble(), 0.001)
        assertEquals(0.7956, ToneMapPass.toneCurve(0.5f).toDouble(), 0.001)
    }

    @Test
    fun highlightsCompressTowardsWhite() {
        assertEquals(0.9008, ToneMapPass.toneCurve(1f).toDouble(), 0.001)
        assertTrue(ToneMapPass.toneCurve(10f) > 0.99f)
    }

    @Test
    fun curveIsMonotonic() {
        assertTrue(ToneMapPass.toneCurve(0.1f) < ToneMapPass.toneCurve(0.3f))
        assertTrue(ToneMapPass.toneCurve(0.3f) < ToneMapPass.toneCurve(0.8f))
    }

    @Test
    fun exposureConstantMatchesPin() {
        assertEquals(1.12, ToneMapPass.exposure().toDouble(), 0.0001)
    }
}

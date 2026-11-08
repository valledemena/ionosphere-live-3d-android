package com.valledemena.ionosphere.render

import org.junit.Assert.assertTrue
import org.junit.Test

class ToneMapPassTest {
    @Test
    fun referencesAcesAndSrgbWithExposure() {
        val source = ToneMapPass.FRAGMENT
        assertTrue(source.contains("ACES Filmic") || source.contains("acesFilmic"))
        assertTrue(source.contains("uExposure"))
        assertTrue(source.contains("1.12"))
        assertTrue(source.contains("12.92") || source.contains("2.2") || source.contains("sRGB"))
    }
}

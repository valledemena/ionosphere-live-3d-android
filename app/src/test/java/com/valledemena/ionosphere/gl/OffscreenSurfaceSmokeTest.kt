package com.valledemena.ionosphere.gl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Slice C / A6: real RenderTarget size and lifecycle checks.
 *
 * The GL calls stay out of the JVM test; the pure size/sample allocation and
 * the initial state are exercised, including the resize realloc contract
 * (a new instance per surface size, old instance released by the host).
 */
class OffscreenSurfaceTest {

    @Test
    fun exposesRequestedSize() {
        val surface = OffscreenSurface(640, 480)
        assertEquals(640, surface.width())
        assertEquals(480, surface.height())
    }

    @Test
    fun isNotReadyBeforeInitAndAfterRelease() {
        val surface = OffscreenSurface(320, 240)
        assertFalse(surface.isReady())
        assertFalse(surface.resolvedReady())
        surface.release()
        assertFalse(surface.isReady())
        assertEquals(0, surface.resolvedTexture())
    }

    @Test
    fun reallocSizeChangeKeepsNewDimensions() {
        val first = OffscreenSurface(320, 240)
        first.release()
        val resized = OffscreenSurface(1280, 720)
        assertEquals(1280, resized.width())
        assertEquals(720, resized.height())
        assertEquals(320, first.width())
    }

    @Test
    fun sampleTargetClampsToDriverMaximum() {
        assertEquals(4, OffscreenSurface.targetSamples(maxSamples = 8, requested = 4))
        assertEquals(2, OffscreenSurface.targetSamples(maxSamples = 2, requested = 4))
        assertEquals(1, OffscreenSurface.targetSamples(maxSamples = 0, requested = 4))
    }

    @Test
    fun halfFloatGuardAcceptsBothExtensions() {
        val surface = OffscreenSurface(16, 16)
        org.junit.Assert.assertTrue(surface.supportsHalfFloatExtension("GL_OES_texture_float EXT_color_buffer_half_float"))
        org.junit.Assert.assertTrue(surface.supportsHalfFloatExtension("EXT_color_buffer_float"))
        assertFalse(surface.supportsHalfFloatExtension("GL_OES_texture_float"))
    }
}

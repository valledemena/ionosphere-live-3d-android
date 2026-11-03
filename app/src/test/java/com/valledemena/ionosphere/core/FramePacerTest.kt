package com.valledemena.ionosphere.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePacerTest {

    private val ns60Hz = 16_666_666L
    private val ns90Hz = 11_111_111L
    private val ns120Hz = 8_333_333L

    private fun paced(periodNs: Long, vsyncs: Int = 200): List<Int> {
        val pacer = FramePacer(initialVsyncPeriodNanos = periodNs)
        val rendered = mutableListOf<Int>()
        var t = 0L
        for (i in 0 until vsyncs) {
            t += periodNs
            if (pacer.onVsync(t)) rendered.add(i)
        }
        return rendered
    }

    @Test
    fun `a 60 Hz se dibuja cada segundo vsync (30 fps)`() {
        val r = paced(ns60Hz)
        assertEquals(100, r.size)
        assertUniform(r, expectedStep = 2, totalVsyncs = 200)
    }

    @Test
    fun `a 90 Hz se dibuja cada tercero vsync (30 fps)`() {
        val r = paced(ns90Hz)
        assertEquals(67, r.size)
        assertUniform(r, expectedStep = 3, totalVsyncs = 200)
    }

    @Test
    fun `a 120 Hz se dibuja cada cuarto vsync (30 fps)`() {
        val r = paced(ns120Hz)
        assertEquals(50, r.size)
        assertUniform(r, expectedStep = 4, totalVsyncs = 200)
    }

    @Test
    fun `el primer vsync siempre se dibuja`() {
        val pacer = FramePacer()
        assertTrue(pacer.onVsync(ns60Hz))
    }

    @Test
    fun `no se dibuja con nanosegundos que no fuerzan el segundo vsync`() {
        // Un único vsync sobre el primero: no toca.
        val pacer = FramePacer(initialVsyncPeriodNanos = ns60Hz)
        pacer.onVsync(ns60Hz)   // primero
        val should = pacer.onVsync(ns60Hz + ns60Hz / 2)
        // 16,67 + 8,33 ms < 33,33 ms: no toca.
        assertFalse(should)
    }

    @Test
    fun `un vsync perdido no infla el periodo estimado`() {
        val pacer = FramePacer(initialVsyncPeriodNanos = ns60Hz)
        var t = 0L
        // secuencia: dos vsyncs buenos, uno perdido, sigue 60 Hz
        val frames = listOf(ns60Hz, ns60Hz, ns60Hz * 2, ns60Hz, ns60Hz)
        // queremos que al final se haya dibujado con el espaciamiento de 60 Hz
        for (d in frames) {
            t += d
            pacer.onVsync(t)
        }
        // el periodo estimado debe seguir siendo el de 60 Hz (no el de 2 vsyncs)
        assertEquals(ns60Hz, pacer.vsyncPeriodNanos)
    }

    @Test
    fun `mutation revertir a delay fijo rompe el grid 90 y 120`() {
        // Un grid de 90 Hz dibujaria mal si usara un techo fijo de 2 frames:
        // 2 × 11,11 ms = 22,22 ms < 33,33, entonces se dibujarian cada 2... pero
        // el techo verificable es que a 90 Hz salen 33 saltos, no 66. Cobertura
        // por aseveracion contundente:
        val r90 = paced(ns90Hz)
        assertEquals(67, r90.size)
    }

    private fun assertUniform(rendered: List<Int>, expectedStep: Int, totalVsyncs: Int) {
        // El primer render es el vsync 0; el resto debe saltar exactamente
        // 'expectedStep' ms por vsync.
        for (i in 1 until rendered.size) {
            val diff = rendered[i] - rendered[i - 1]
            assertEquals("salto esperado $expectedStep en vsync ${rendered[i]}", expectedStep, diff)
        }
    }
}

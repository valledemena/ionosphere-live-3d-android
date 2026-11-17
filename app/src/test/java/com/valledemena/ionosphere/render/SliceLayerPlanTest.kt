package com.valledemena.ionosphere.render

import com.valledemena.ionosphere.core.iono.DensityVolume
import com.valledemena.ionosphere.core.iono.SLICE_LVL
import com.valledemena.ionosphere.core.iono.SLICE_SEG
import com.valledemena.ionosphere.core.iono.SliceAction
import com.valledemena.ionosphere.core.iono.SlicePair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G8: plan de frame de la cortina (P4b). Superficie testeable en JVM del
 * contrato de la opcion A: wiring (capa apagada no dibuja), toggles, guarda de
 * volumen nulo y dispose idempotente. Espejo de `App::rebuildSlice`
 * (`App.cpp:3909-3930`) y de `SliceLayer::render` (`SliceLayer.cpp:132-146`).
 */
class SliceLayerPlanTest {

    /** Volumen pequeno y constante: el muestreo devuelve 0,5 en toda la malla. */
    private fun volume(valid: Boolean = true): DensityVolume {
        val celdas = 4 * 4 * 2
        return DensityVolume(
            lonRes = 4,
            latRes = 4,
            altRes = 2,
            altMinKm = 60.0,
            altMaxKm = 500.0,
            data = DoubleArray(celdas) { 0.5 },
            layer = IntArray(celdas),
            valid = valid,
        )
    }

    private val parMadRoa = SliceEndpoints(
        codeA = "MAD",
        latA = 40.4f,
        lonA = -3.7f,
        codeB = "ROA",
        latB = 40.3f,
        lonB = -6.3f,
    )

    // G8_38
    @Test
    fun `G8_38 capa apagada no dibuja ni sube nada a la GPU`() {
        assertFalse("el toggle por defecto es OFF (SliceLayer.h:41)", SLICE_VISIBLE_DEFAULT)

        val plan = planSliceFrame(showSlice = false, volume = volume(), endpoints = parMadRoa, current = null)
        assertEquals(SliceAction.Hide, plan.action)
        assertFalse(plan.hasUpload)
        assertNull(plan.vertices)
        assertNull(plan.indices)
        assertNull(plan.texels)

        // Apagada gana incluso con volumen y par validos y con force.
        val forzado = planSliceFrame(false, volume(), parMadRoa, null, force = true)
        assertEquals(SliceAction.Hide, forzado.action)
        assertFalse(forzado.hasUpload)
    }

    // G8_39
    @Test
    fun `G8_39 sin volumen o sin par valido no se rehornea nada`() {
        // Guarda de volumen nulo: es el estado real de R4.5 (nadie cablea el volumen).
        val sinVolumen = planSliceFrame(true, null, parMadRoa, null)
        assertEquals(SliceAction.Keep, sinVolumen.action)
        assertFalse(sinVolumen.hasUpload)
        assertNull(sinVolumen.vertices)
        assertNull(sinVolumen.texels)

        // Volumen presente pero invalido: el oraculo retorna sin tocar la textura.
        val invalido = planSliceFrame(true, volume(valid = false), parMadRoa, null)
        assertEquals(SliceAction.Keep, invalido.action)
        assertFalse(invalido.hasUpload)

        // Sin extremos no hay cortina (D3).
        val sinPar = planSliceFrame(true, volume(), null, null)
        assertEquals(SliceAction.Hide, sinPar.action)
        assertFalse(sinPar.hasUpload)
        assertNull(sinPar.vertices)

        // Mismo codigo en A y B no es un par valido (oraculo `A->code == B->code`).
        val mismo = SliceEndpoints("MAD", 40.0f, -4.0f, "MAD", 41.0f, -5.0f)
        val degenerado = planSliceFrame(true, volume(), mismo, null)
        assertEquals(SliceAction.Hide, degenerado.action)
        assertFalse(degenerado.hasUpload)
    }

    // G8_40
    @Test
    fun `G8_40 con volumen y par hornea mallas del tamano de la cortina y su pico`() {
        val plan = planSliceFrame(true, volume(), parMadRoa, null)
        assertEquals(SliceAction.Rebuild, plan.action)
        assertTrue(plan.hasUpload)

        val vertices = plan.vertices
        val indices = plan.indices
        val texels = plan.texels
        assertTrue(vertices != null && indices != null && texels != null)
        if (vertices == null || indices == null || texels == null) return
        assertEquals(SLICE_SEG * SLICE_LVL * 5, vertices.size)
        assertEquals(6 * (SLICE_SEG - 1) * (SLICE_LVL - 1), indices.size)
        assertEquals(SLICE_SEG * SLICE_LVL * 4, texels.size)
        assertEquals(SlicePair("MAD", "ROA"), plan.pair)

        // Volumen constante 0,5: pico 0,5 -> 8 + 4,5 * 0,5 = 10,25 exacto en Float.
        assertEquals(10.25f, plan.peakLogNe, 0.0f)

        // Mismo par y sin force: no se rehornea (early-out del oraculo).
        val segundo = planSliceFrame(true, volume(), parMadRoa, plan.pair)
        assertEquals(SliceAction.Keep, segundo.action)
        assertFalse(segundo.hasUpload)

        // `force` (volumen nuevo) rehornea aunque el par sea el mismo.
        val forzado = planSliceFrame(true, volume(), parMadRoa, plan.pair, force = true)
        assertEquals(SliceAction.Rebuild, forzado.action)
        assertTrue(forzado.hasUpload)
    }

    // G8_41
    @Test
    fun `G8_41 dispose es idempotente solo la primera llamada libera`() {
        val guarda = SliceDisposeGuard()
        assertFalse(guarda.disposed)
        assertTrue("primera llamada libera", guarda.markDisposed())
        assertTrue(guarda.disposed)
        assertFalse("segunda llamada no libera", guarda.markDisposed())
        assertFalse("tercera tampoco", guarda.markDisposed())
    }
}

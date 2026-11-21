package com.valledemena.ionosphere.gl

import com.valledemena.ionosphere.render.VolumeShaderSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R4.6.5 — regression del binding del atributo de vertice del volumen.
 *
 * El defecto de `f306730` era estatico y el test JVM anterior solo validaba
 * el texto del GLSL: `VOLUME_VERT` declara `in vec3 aPosition`, pero el
 * renderer consultaba `glGetAttribLocation(program, "aPos")` -> -1 ->
 * atributo (0,0,0) -> triangulos degenerados, cero fragmentos, sin crash.
 * Este test caza el desacuerdo en JVM, sin GL.
 */
class VolumeAttributeBindingTest {

    private fun source(relative: String): String {
        var dir = File(".").absoluteFile
        repeat(8) {
            val candidate = File(dir, "app/src/main/java/com/valledemena/ionosphere/$relative")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile ?: return@repeat
        }
        throw AssertionError("No se encontro la fuente $relative desde ${File(".").absolutePath}")
    }

    private fun declaredVec3(vertSource: String): Set<String> =
        Regex("""in\s+vec[234]\s+(\w+)\s*;""").findAll(vertSource).map { it.groupValues[1] }.toSet()

    /** Solo el bloque VOLUME_VERT: VOLUME_FRAG declara `in vec3 vWorldPos`, que no es un atributo. */
    private fun volumeVertSource(fileText: String): String {
        val start = fileText.indexOf("VOLUME_VERT")
        check(start >= 0) { "VOLUME_VERT no encontrado" }
        val open = fileText.indexOf("\"\"\"", start)
        val close = fileText.indexOf("\"\"\"", open + 3)
        check(open >= 0 && close > open) { "Bloque VOLUME_VERT mal delimitado" }
        return fileText.substring(open + 3, close)
    }

    private fun lookedUp(source: String): Set<String> =
        Regex("""glGetAttribLocation\([^,]+,\s*"(\w+)"\)""").findAll(source).map { it.groupValues[1] }.toSet()

    @Test
    fun `G7c_29 el vertex del volumen declara un unico atributo y es aPosition`() {
        val declared = declaredVec3(VolumeShaderSource.VOLUME_VERT)
        assertEquals(setOf("aPosition"), declared)
        assertTrue(VolumeShaderSource.VOLUME_VERT.contains("vWorldPos = aPosition;"))
    }

    @Test
    fun `G7c_30 el renderer busca el mismo nombre que declara el shader`() {
        val shader = source("render/VolumeShaderSource.kt")
        val renderer = source("gl/VolumeLayerRenderer.kt")
        val declared = declaredVec3(volumeVertSource(shader))
        val lookup = lookedUp(renderer)
        assertEquals("El renderer debe consultar exactamente el atributo declarado", declared, lookup)
        assertTrue(!renderer.contains("glGetAttribLocation(program, \"aPos\")"))
        assertTrue(renderer.contains("glEnableVertexAttribArray(aPosition)"))
        assertTrue(renderer.contains("glVertexAttribPointer(aPosition,"))
    }

    @Test
    fun `G7c_31 la cortina es el control sano del mismo patron`() {
        val slice = source("gl/SliceLayerRenderer.kt")
        val declared = declaredVec3(slice) // incluye el bloque companion con SLICE_VERT/SLICE_FRAG
        val lookup = lookedUp(slice)
        assertTrue("La cortina declara aPos", declared.contains("aPos"))
        assertTrue("Los lookups de la cortina estan entre sus declaraciones", lookup.all { it in declared })
    }
}

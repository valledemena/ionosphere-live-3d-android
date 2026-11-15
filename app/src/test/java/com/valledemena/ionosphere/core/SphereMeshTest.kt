package com.valledemena.ionosphere.core

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SphereMeshTest {

    private val mesh = SphereMesh.unitSphere(rings = 8, segments = 16)

    @Test
    fun `cuenta de vertices y triangulos`() {
        assertEquals((8 + 1) * (16 + 1), mesh.vertexCount)
        assertEquals(8 * 16 * 2, mesh.triangleCount)
        assertEquals(mesh.vertexCount * 3, mesh.positions.size)
        assertEquals(mesh.vertexCount * 3, mesh.normals.size)
        assertEquals(mesh.vertexCount * 2, mesh.uvs.size)
    }

    @Test
    fun `todos los vertices estan en la esfera unitaria`() {
        var i = 0
        while (i < mesh.positions.size) {
            val x = mesh.positions[i]
            val y = mesh.positions[i + 1]
            val z = mesh.positions[i + 2]
            val length = sqrt(x * x + y * y + z * z)
            assertEquals("vértice ${i / 3} fuera de la esfera", 1f, length, 1e-4f)
            i += 3
        }
    }

    @Test
    fun `u medio cae en +X y u cero en -X`() {
        val equator = 4 * (16 + 1)
        val seam = equator + 0
        val center = equator + 8
        assertEquals(-1f, mesh.positions[seam * 3], 1e-6f)
        assertEquals(0f, mesh.positions[seam * 3 + 2], 1e-6f)
        assertEquals(1f, mesh.positions[center * 3], 1e-6f)
        assertEquals(0f, mesh.positions[center * 3 + 2], 1e-6f)
    }

    @Test
    fun `u tres cuartos cae en -Z (lon 90E)`() {
        val equator = 4 * (16 + 1)
        val east = equator + 12
        assertEquals(0f, mesh.positions[east * 3], 1e-6f)
        assertEquals(-1f, mesh.positions[east * 3 + 2], 1e-6f)
    }

    @Test
    fun `el winding exterior apunta hacia fuera`() {
        val ring = 4
        val segment = 0
        val top = ring * (16 + 1) + segment
        val bottom = top + (16 + 1)
        val topRight = top + 1

        fun point(index: Int) = floatArrayOf(
            mesh.positions[index * 3],
            mesh.positions[index * 3 + 1],
            mesh.positions[index * 3 + 2],
        )

        // Mismo orden que el primer triangulo del indice de la malla:
        // top, bottom, topRight.
        val a = point(top)
        val b = point(bottom)
        val c = point(topRight)
        val u = floatArrayOf(b[0] - a[0], b[1] - a[1], b[2] - a[2])
        val v = floatArrayOf(c[0] - a[0], c[1] - a[1], c[2] - a[2])
        val normal = floatArrayOf(
            u[1] * v[2] - u[2] * v[1],
            u[2] * v[0] - u[0] * v[2],
            u[0] * v[1] - u[1] * v[0],
        )
        val centroid = floatArrayOf(
            (a[0] + b[0] + c[0]) / 3f,
            (a[1] + b[1] + c[1]) / 3f,
            (a[2] + b[2] + c[2]) / 3f,
        )
        val outward = normal[0] * centroid[0] + normal[1] * centroid[1] + normal[2] * centroid[2]
        assertTrue("winding invertido: dot=$outward", outward > 0f)
    }

    @Test
    fun `la normal coincide con la posicion en una esfera unitaria`() {
        for (i in mesh.positions.indices) {
            assertEquals(mesh.positions[i], mesh.normals[i], 1e-6f)
        }
    }

    @Test
    fun `uv cubre el rango completo y la costura esta duplicada`() {
        var minU = Float.MAX_VALUE
        var maxU = -Float.MAX_VALUE
        var i = 0
        while (i < mesh.uvs.size) {
            val u = mesh.uvs[i]
            val v = mesh.uvs[i + 1]
            if (u < minU) minU = u
            if (u > maxU) maxU = u
            assertTrue("v fuera de rango: $v", v >= 0f && v <= 1f)
            i += 2
        }
        assertEquals(0f, minU, 1e-6f)
        assertEquals(1f, maxU, 1e-6f)

        // costura del ecuador (ring 4 de 8): posición idéntica, u 0 vs 1
        val firstOfRing = (4 * (16 + 1) + 0)
        val lastOfRing = (4 * (16 + 1) + 16)
        for (component in 0..2) {
            assertEquals(
                mesh.positions[firstOfRing * 3 + component],
                mesh.positions[lastOfRing * 3 + component],
                1e-6f,
            )
        }
        assertEquals(0f, mesh.uvs[firstOfRing * 2], 1e-6f)
        assertEquals(1f, mesh.uvs[lastOfRing * 2], 1e-6f)
    }

    @Test
    fun `todos los indices apuntan a vertices existentes`() {
        for (index in mesh.indices) {
            val i = index.toInt()
            assertTrue("índice $i fuera de rango", i in 0 until mesh.vertexCount)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rings invalido lanza excepcion`() {
        SphereMesh.unitSphere(rings = 1, segments = 8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `segments invalido lanza excepcion`() {
        SphereMesh.unitSphere(rings = 8, segments = 2)
    }
}

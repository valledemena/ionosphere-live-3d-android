package com.valledemena.ionosphere.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrbitCameraTest {

    @Test
    fun `arranca a 3_3 R_E sobre +Z y con pan deshabilitado`() {
        val camera = OrbitCamera()
        val eye = FloatArray(3)
        camera.writeEye(eye)

        assertEquals(0f, eye[0], 1e-6f)
        assertEquals(0f, eye[1], 1e-6f)
        assertEquals(3.3f, eye[2], 1e-5f)
        assertEquals(3.3f, camera.distance(), 1e-5f)
        assertFalse(camera.panEnabled)
    }

    @Test
    fun `el arrastre cambia yaw y pitch`() {
        val camera = OrbitCamera()
        val before = FloatArray(3)
        camera.writeEye(before)

        camera.dragFromPixels(100f, 50f, 1000)

        val after = FloatArray(3)
        camera.writeEye(after)
        assertTrue("yaw no cambió", after[0] < before[0])
        assertTrue("pitch no cambió", after[1] < before[1])
    }

    @Test
    fun `el pitch se limita a 80 grados`() {
        val camera = OrbitCamera()
        camera.dragFromPixels(0f, 10_000f, 1000)

        assertEquals(-80f, camera.pitchDegrees(), 1e-4f)
    }

    @Test
    fun `el pinch respeta minDistance y maxDistance`() {
        val camera = OrbitCamera()

        camera.scaleBy(0.5f)
        assertEquals(6.6f, camera.distance(), 1e-4f)

        camera.scaleBy(100f)
        assertEquals(1.12f, camera.distance(), 1e-5f)

        camera.scaleBy(0.01f)
        assertEquals(30f, camera.distance(), 1e-5f)
    }

    @Test
    fun `la inercia decae con damping 0_06`() {
        val camera = OrbitCamera()
        val before = FloatArray(3)
        camera.writeEye(before)
        camera.flingFromPixels(1_000f, 0f, 1000)

        val velocityBefore = abs(camera.yawVelocity())
        camera.update(1f / 60f)

        val after = FloatArray(3)
        camera.writeEye(after)
        val velocityAfter = abs(camera.yawVelocity())

        assertTrue("la inercia no movió la cámara", abs(after[0] - before[0]) > 1e-4f)
        assertEquals(velocityBefore * 0.94f, velocityAfter, 1e-4f)
    }
}

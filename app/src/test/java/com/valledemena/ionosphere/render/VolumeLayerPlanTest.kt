package com.valledemena.ionosphere.render

import com.valledemena.ionosphere.core.iono.DensityVolume
import com.valledemena.ionosphere.core.iono.VOL_ALT_MAX_KM
import com.valledemena.ionosphere.core.iono.VOL_ALT_MIN_KM
import com.valledemena.ionosphere.core.iono.VOL_ALT_RES
import com.valledemena.ionosphere.core.iono.VOL_LAT_RES
import com.valledemena.ionosphere.core.iono.VOL_LON_RES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeLayerPlanTest {

    private fun testVolume(valid: Boolean = true): DensityVolume {
        val n = VOL_LON_RES * VOL_LAT_RES * VOL_ALT_RES
        val data = DoubleArray(n) { 0.25 }
        data[0] = 1.0
        data[1] = 0.0
        data[5] = 0.5
        val layer = IntArray(n) { 2 }
        layer[0] = 0
        layer[7] = 3
        return DensityVolume(
            lonRes = VOL_LON_RES,
            latRes = VOL_LAT_RES,
            altRes = VOL_ALT_RES,
            altMinKm = VOL_ALT_MIN_KM,
            altMaxKm = VOL_ALT_MAX_KM,
            data = data,
            layer = layer,
            valid = valid,
        )
    }

    // ---- constantes / pines (D-R4.6.3) ----

    @Test
    fun `G7c_01 OFF es plan cero coste`() {
        val v = testVolume()
        val plan = planVolumeFrame(
            showVolume = false, volume = v, uploadedKey = null,
            hasData = false, mode = VolumeMode.DENSITY, quality = VolumeQuality.HIGH,
        )
        assertNull(plan.upload)
        assertNull(plan.draw)
        assertTrue(!plan.hasUpload)
        assertTrue(!plan.hasDraw)
    }

    @Test
    fun `G7c_02 volumen valido se sube una vez y dibuja`() {
        val v = testVolume()
        val plan = planVolumeFrame(
            showVolume = true, volume = v, uploadedKey = null,
            hasData = false, mode = VolumeMode.DENSITY, quality = VolumeQuality.HIGH,
        )
        val upload = plan.upload
        assertTrue(upload != null)
        assertEquals(VOL_LON_RES, upload!!.lonRes)
        assertEquals(VOL_LAT_RES, upload.latRes)
        assertEquals(VOL_ALT_RES, upload.altRes)
        assertEquals(volumeFingerprint(v), upload.fingerprint)
        assertTrue(plan.draw != null)
        assertEquals(VolumeQuality.HIGH.steps, plan.draw!!.steps)
        assertEquals(VolumeMode.DENSITY, plan.draw.mode)
    }

    @Test
    fun `G7c_03 la textura no sube dos veces y con OFF no hay upload ni draw`() {
        val v = testVolume()
        val first = planVolumeFrame(
            showVolume = true, volume = v, uploadedKey = null,
            hasData = false, mode = VolumeMode.DENSITY, quality = VolumeQuality.HIGH,
        )
        val second = planVolumeFrame(
            showVolume = true, volume = v, uploadedKey = first.upload!!.fingerprint,
            hasData = true, mode = VolumeMode.DENSITY, quality = VolumeQuality.HIGH,
        )
        assertNull(second.upload)
        assertTrue(second.draw != null)

        val off = planVolumeFrame(
            showVolume = false, volume = v, uploadedKey = first.upload?.fingerprint,
            hasData = true, mode = VolumeMode.DENSITY, quality = VolumeQuality.HIGH,
        )
        assertNull(off.upload)
        assertNull(off.draw)
    }

    @Test
    fun `G7c_04 volumen nuevo o invalido no dibuja`() {
        val invalid = planVolumeFrame(
            showVolume = true, volume = testVolume(valid = false), uploadedKey = null,
            hasData = true, mode = VolumeMode.DENSITY, quality = VolumeQuality.HIGH,
        )
        assertNull(invalid.upload)
        assertNull(invalid.draw)

        val nullVolume = planVolumeFrame(
            showVolume = true, volume = null, uploadedKey = "x",
            hasData = true, mode = VolumeMode.DENSITY, quality = VolumeQuality.HIGH,
        )
        assertNull(nullVolume.upload)
        assertNull(nullVolume.draw)
    }

    @Test
    fun `G7c_05 comprobacion de distinta altura no conmuta`() {
        val v = testVolume().copy(altMaxKm = 250.0)
        val plan = planVolumeFrame(
            showVolume = true, volume = v, uploadedKey = null,
            hasData = false, mode = VolumeMode.DENSITY, quality = VolumeQuality.HIGH,
        )
        assertNull(plan.upload)
        assertNull(plan.draw)
    }

    @Test
    fun `G7c_06 re-horneo solo con volumen nuevo`() {
        val a = testVolume()
        val b = a.copy(data = DoubleArray(a.data.size) { 0.75 })
        assertNotEquals(volumeFingerprint(a), volumeFingerprint(b))
        assertNotEquals(a.data.contentToString(), b.data.contentToString())
    }

    // ---- calidad / pasos ----

    @Test
    fun `G7c_07 calidad mapea pasos y ref es 64`() {
        assertEquals(64, VOL_STEPS_REF)
        assertEquals(64, VolumeQuality.HIGH.steps)
        assertEquals(32, VolumeQuality.MEDIUM.steps)
        assertEquals(16, VolumeQuality.LOW.steps)
        val pLow = planVolumeFrame(
            showVolume = true, volume = testVolume(), uploadedKey = null,
            hasData = false, mode = VolumeMode.DENSITY, quality = VolumeQuality.LOW,
        )
        assertEquals(16, pLow.draw?.steps)
    }

    @Test
    fun `G7c_08 cambio de modo no rehornea conmuta en runtime`() {
        val v = testVolume()
        val first = planVolumeFrame(
            showVolume = true, volume = v, uploadedKey = null,
            hasData = false, mode = VolumeMode.DENSITY, quality = VolumeQuality.HIGH,
        )
        val second = planVolumeFrame(
            showVolume = true, volume = v, uploadedKey = first.upload!!.fingerprint,
            hasData = true, mode = VolumeMode.CHAPMAN, quality = VolumeQuality.HIGH,
        )
        assertNull(second.upload)
        assertEquals(VolumeMode.CHAPMAN, second.draw!!.mode)
        assertEquals(1, volumeModeIndex(second.draw.mode))
    }

    // ---- opacidad y radios ----

    @Test
    fun `G7c_09 clamp de opacidad`() {
        assertEquals(0f, volumeOpacityClamp(-1f), 0f)
        assertEquals(0f, volumeOpacityClamp(0f), 0f)
        assertEquals(1f, volumeOpacityClamp(1f), 0f)
        assertEquals(3f, volumeOpacityClamp(5f), 0f)
    }

    @Test
    fun `G7c_10 radios del proxy (espejo C++ VolumeRenderer)`() {
        // Radio exterior: 500 km sobre el planeta (espejo C++ L256).
        assertEquals(AtmoShaderConstants.PLANET_R + 500.0 / PLANET_R_KM, volumeOuterRadius(500.0).toDouble(), 1e-5)
        // Radio interior: planeta + altMin 60 km (espejo C++ L257, explode_=1):
        // altNorm=0 <=> r = uInnerR, o sea que el volumen cubre alt 60..500 km
        // sin desplazamiento vertical (R1).
        assertEquals(AtmoShaderConstants.PLANET_R + 60.0 / PLANET_R_KM, volumeInnerRadius(60.0).toDouble(), 1e-6)
        assertEquals(1.00942, volumeInnerRadius(60.0).toDouble(), 1e-5)
        assertEquals(1.07848, volumeOuterRadius(500.0).toDouble(), 1e-5)
    }

    // ---- packing (byte-identico a densityVolume.ts / builders.ts) ----

    @Test
    fun `G7c_11 packDensityTrunc 0a1 x255`() {
        val v = testVolume()
        val tex = packDensityTexels(v)
        assertEquals(VOL_LON_RES * VOL_LAT_RES * VOL_ALT_RES, tex.size)
        assertEquals(255.toByte(), tex[0]) // 1.0 * 255
        assertEquals(0.toByte(), tex[1]) // 0.0
        assertEquals(127.toByte(), tex[5]) // trunc(0.5*255) = 127, sin redondeo
        assertEquals(63.toByte(), tex[1 + 4 * VOL_LON_RES * VOL_LAT_RES]) // trunc(0.25*255)=63
    }

    @Test
    fun `G7c_12 packLayer valores`() {
        val v = testVolume()
        val tex = packLayerTexels(v)
        assertEquals(0.toByte(), tex[0])
        assertEquals(3.toByte(), tex[7])
        assertEquals(2.toByte(), tex[8])
    }

    // ---- pines de busqueda----

    @Test
    fun `G7c_13 colores chapman byte-iguales al oraculo web`() {
        assertEquals(listOf(1.00f, 0.55f, 0.10f), CHAPMAN_WINNER_COLORS[0].toList())
        assertEquals(listOf(0.20f, 0.85f, 0.30f), CHAPMAN_WINNER_COLORS[1].toList())
        assertEquals(listOf(1.00f, 0.90f, 0.20f), CHAPMAN_WINNER_COLORS[2].toList())
        assertEquals(listOf(0.20f, 0.80f, 1.00f), CHAPMAN_WINNER_COLORS[3].toList())
    }

    @Test
    fun `G7c_14 modos de shader index`() {
        assertEquals(0, volumeModeIndex(VolumeMode.DENSITY))
        assertEquals(1, volumeModeIndex(VolumeMode.CHAPMAN))
    }
}

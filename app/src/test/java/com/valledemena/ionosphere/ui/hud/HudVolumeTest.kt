package com.valledemena.ionosphere.ui.hud

import com.valledemena.ionosphere.render.VolumeMode
import com.valledemena.ionosphere.render.VolumeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HudVolumeTest {

    @Test
    fun `G7c_30 hint apagado sin volumen`() {
        assertNull(HudVolume.hint(false, true))
        assertNull(HudVolume.hint(false, false))
    }

    @Test
    fun `G7c_31 hint en on solo cuando hay textura`() {
        assertEquals("volumen on", HudVolume.hint(true, true))
        assertNull(HudVolume.hint(true, false))
    }

    @Test
    fun `G7c_32 estado deshabilita modos sin volumen`() {
        val off = HudVolume.state(VolumeStatus(showVolume = false, hasData = true), volumeOn = false)
        assertNull(off.hint)
        assertEquals(false, off.modeEnabled)

        val on = HudVolume.state(VolumeStatus(showVolume = true, hasData = true), volumeOn = true)
        assertEquals("volumen on", on.hint)
        assertEquals(true, on.modeEnabled)
    }

    @Test
    fun `G7c_33 labels de modo`() {
        assertEquals("densidad", VolumeMode.DENSITY.hudLabel())
        assertEquals("capas", VolumeMode.CHAPMAN.hudLabel())
    }
}

package com.valledemena.ionosphere.ui.hud

import com.valledemena.ionosphere.render.VolumeMode
import com.valledemena.ionosphere.render.VolumeStatus

/**
 * Estado puro del control de volumen del HUD (R4.6.3 density + chapman).
 *
 * Vigila `HudSlice.kt`: el estado es puro, la vista lo consumes.
 */
object HudVolume {

    /** Texto del hint de la fila, vacio cuando no hay nada que mostrar. */
    fun hint(volumeOn: Boolean, hasVolume: Boolean): String? =
        if (volumeOn && hasVolume) "volumen on" else null

    /** Estado derivado que consume la vista (testeable en JVM). */
    data class State(
        val hint: String?,
        val modeEnabled: Boolean,
    )

    fun state(status: VolumeStatus, volumeOn: Boolean): State = State(
        hint = hint(volumeOn, status.hasData),
        modeEnabled = volumeOn,
    )
}

/** Labels de las dos pestañas de modo; sincronizar con la vista. */
fun VolumeMode.hudLabel(): String = when (this) {
    VolumeMode.DENSITY -> "densidad"
    VolumeMode.CHAPMAN -> "capas"
}

package com.valledemena.ionosphere.core

/**
 * Un tick de reproducción automática, espejo del `setInterval(..., 500)` de la
 * web: solo avanza en REPLAY y con `replayPlaying`; el resto del estado no se
 * toca.
 *
 * El `dt` de la web es 0.5 s. Al llegar a 0 sin loop, `replayAdvance` devuelve
 * 0 y `replayPlaying` sigue activo, igual que el oráculo; la UI puede seguir
 * mostrando el estado de reproducción sin inventar una transición.
 */
fun ReplaySettings.tick(dtSec: Double): ReplaySettings {
    if (timeMode != TimeMode.REPLAY || !replayPlaying) return this
    return copy(
        replayMinutes = replayAdvance(
            minutesBack = replayMinutes,
            speed = replaySpeed,
            dtSec = dtSec,
            maxMin = REPLAY_MAX_MINUTES,
            loop = replayLoop,
        ),
    )
}

/**
 * Época efectiva de la escena: en LIVE es «ahora»; en REPLAY resta los
 * minutos de historia. Es la misma cuenta que usa el reloj del TimeBar.
 */
fun replayEpochMs(nowMs: Long, settings: ReplaySettings): Long {
    val effectiveMinutes = timeBarEffectiveMinutes(settings.timeMode, settings.replayMinutes)
    return nowMs - (effectiveMinutes * 60_000.0).toLong()
}

/**
 * Epoca solar que debe usar la escena.
 *
 * En LIVE devuelve `null`: el renderer lee el reloj en cada frame para que el
 * terminador se mueva de forma continua. En REPLAY devuelve la misma epoca de
 * escena que usan HUD y estaciones.
 */
fun solarEpochForScene(nowMs: Long, settings: ReplaySettings): Long? =
    if (settings.timeMode == TimeMode.REPLAY) replayEpochMs(nowMs, settings) else null

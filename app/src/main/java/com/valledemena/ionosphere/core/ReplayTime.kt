package com.valledemena.ionosphere.core

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Núcleo puro de replay de R4.1, espejo de `replay.test.ts` de la web.
 *
 * La fuente de verdad es el pin web `8cdc334`:
 * `src/components/ionosphere/types.ts:349-399` fija las funciones y
 * `src/lib/iono/replay.test.ts` fija sus valores. Este fichero no conoce
 * Android, Compose, GL ni la red: solo aritmética de tiempo y settings.
 *
 * 1x de replay avanza 20 minutos de historia por segundo real
 * (`dt * speed * 20`), como `App.cpp:2139-2141`. El máximo es 168 h.
 */

/** 168 h de historia, en minutos. */
const val REPLAY_MAX_MINUTES = 10_080.0

/** Velocidad por defecto del oráculo (`DEFAULT_SETTINGS.replaySpeed`). */
const val REPLAY_DEFAULT_SPEED = 2.0

/** Rango de persistencia de la velocidad en la web (`readNumber`). */
const val REPLAY_MIN_SPEED = 0.5
const val REPLAY_MAX_SPEED = 8.0

enum class TimeMode {
    LIVE,
    REPLAY,
}

/**
 * Avanza el replay en minutos-atrás.
 *
 * `next = minutesBack - dtSec * speed * 20`. Si el resultado llega a 0 o lo
 * cruza, con loop vuelve al fondo (`maxMin`) y sin loop se queda en el
 * presente (`0`). Si no, se recorta al máximo. La comparación es `<= 0`, no
 * `< 0`: llegar exactamente al presente también dispara el wrap.
 */
fun replayAdvance(
    minutesBack: Double,
    speed: Double,
    dtSec: Double,
    maxMin: Double,
    loop: Boolean,
): Double {
    val next = minutesBack - dtSec * speed * 20.0
    if (next <= 0.0) return if (loop) maxMin else 0.0
    return minOf(maxMin, next)
}

/**
 * Mapeo espejo del slider del TimeBar.
 *
 * La posición va de 0 (izquierda, `-max`) a `maxReplayMin` (derecha, «ahora»).
 * La función es su propia inversa, de modo que `value` y `onChange` no hacen
 * doble conversión (el quirk 326/328 del oráculo).
 */
fun replaySliderOffset(maxReplayMin: Double, position: Double): Double =
    maxReplayMin - position

/**
 * Guard de época: los minutos de replay solo descuentan en modo REPLAY; en
 * LIVE la época efectiva es «ahora» aunque quede un valor persistido.
 */
fun timeBarEffectiveMinutes(timeMode: TimeMode, replayMinutes: Double): Double =
    if (timeMode == TimeMode.REPLAY) replayMinutes else 0.0

private val CLOCK_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/**
 * Etiqueta del reloj del TimeBar en UTC.
 *
 * `nowMs == 0` es el placeholder de SSR/primer render de la web y devuelve
 * `--:--:--` sin horas horneadas. Con reloj inyectado, resta `effMin` minutos
 * y formatea `HH:mm UTC`. El reloj nunca se lee dentro de esta función.
 */
fun timeBarClockLabel(nowMs: Long, effMin: Double): String {
    if (nowMs == 0L) return "--:--:--"
    val epochMs = (nowMs - effMin * 60_000.0).toLong()
    val label = Instant.ofEpochMilli(epochMs)
        .atOffset(ZoneOffset.UTC)
        .format(CLOCK_FORMATTER)
    return "$label UTC"
}

/**
 * Estado de replay que la UI y el tick comparten.
 *
 * Los defaults son los del oráculo: LIVE, 0 minutos, pausado, 2x y loop. Al
 * restaurar una sesión se resetean las tres claves que la web **no** persiste
 * (`timeMode`, `replayMinutes`, `replayPlaying`); solo sobreviven velocidad y
 * loop.
 */
data class ReplaySettings(
    val timeMode: TimeMode = TimeMode.LIVE,
    val replayMinutes: Double = 0.0,
    val replayPlaying: Boolean = false,
    val replaySpeed: Double = REPLAY_DEFAULT_SPEED,
    val replayLoop: Boolean = true,
) {
    companion object {
        fun restored(storedSpeed: Double?, storedLoop: Boolean?): ReplaySettings =
            ReplaySettings(
                replaySpeed = sanitizeReplaySpeed(storedSpeed),
                replayLoop = storedLoop ?: true,
            )
    }
}

/**
 * Saneado de minutos para la UI: no finito o ausente -> 0; fuera de rango ->
 * borde. La web recorta con `readNumber` al leer números, y el slider ya nace
 * limitado a `0..10080`.
 */
fun sanitizeReplayMinutes(value: Double?): Double {
    if (value == null || !value.isFinite()) return 0.0
    return value.coerceIn(0.0, REPLAY_MAX_MINUTES)
}

/**
 * Saneado de velocidad espejo de `readNumber(r, "replaySpeed", 0.5, 8, 2)`:
 * no finito o ausente -> 2; fuera de rango -> borde. La lista de saltos de la
 * UI es `[0.5, 1, 2, 4, 8]`, pero un valor intermedio persistido sigue siendo
 * válido, como en la web.
 */
fun sanitizeReplaySpeed(value: Double?): Double {
    if (value == null || !value.isFinite()) return REPLAY_DEFAULT_SPEED
    return value.coerceIn(REPLAY_MIN_SPEED, REPLAY_MAX_SPEED)
}

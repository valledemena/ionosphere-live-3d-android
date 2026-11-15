package com.valledemena.ionosphere.core

import com.valledemena.ionosphere.data.model.IonosondeData
import com.valledemena.ionosphere.data.model.IonosondeStation
import com.valledemena.ionosphere.data.model.StationHistoryPoint
import java.time.DateTimeException
import java.time.OffsetDateTime

/**
 * Muestreo de historia de una estación, espejo de `sampleStationAt` del pin
 * web `8cdc334` (`types.ts:401-425`).
 *
 * La historia es dispersa (ionogramas, no cadencia fija), así que se busca el
 * intervalo por bisección y se interpola linealmente. Antes del primer punto
 * se devuelve el primero; después del último, el último. Sin historia se
 * devuelve el `foF2` actual. Si un extremo vale `<= 0`, se toma el positivo:
 * el hueco no se convierte en un cero inventado.
 *
 * Una historia con timestamps inválidos cae al `foF2` actual: preferimos no
 * romper el render por un payload mal formado, y no inventar una lectura.
 */
fun sampleStationAt(station: IonosondeStation, epochMs: Long): Double? {
    val history: List<StationHistoryPoint> = station.history
    if (history.isEmpty()) return station.foF2

    val times = LongArray(history.size)
    try {
        for (i in history.indices) {
            times[i] = parseHistoryEpochMs(history[i].t)
        }
    } catch (_: DateTimeException) {
        return station.foF2
    }

    if (epochMs <= times[0]) return history[0].foF2
    if (epochMs >= times[times.size - 1]) return history[history.size - 1].foF2

    var lo = 0
    var hi = times.size - 1
    while (hi - lo > 1) {
        val mid = (lo + hi) ushr 1
        if (times[mid] <= epochMs) lo = mid else hi = mid
    }

    val span = times[hi] - times[lo]
    val fraction = (epochMs - times[lo]).toDouble() / (if (span != 0L) span else 1L)
    val a = history[lo].foF2
    val b = history[hi].foF2
    if (a <= 0.0) return b
    if (b <= 0.0) return a
    return a + fraction * (b - a)
}

/** Copia de la estación con el `foF2` muestreado en [epochMs]. */
fun IonosondeStation.sampledAt(epochMs: Long): IonosondeStation =
    if (history.isEmpty()) this else copy(foF2 = sampleStationAt(this, epochMs))

/** Copia del payload con todas las estaciones muestreadas en [epochMs]. */
fun IonosondeData.sampledAt(epochMs: Long): IonosondeData =
    if (stations.isEmpty()) this else copy(stations = stations.map { it.sampledAt(epochMs) })

private fun parseHistoryEpochMs(raw: String): Long {
    val normalized = if (raw.endsWith("Z")) raw else raw + "Z"
    return OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
}

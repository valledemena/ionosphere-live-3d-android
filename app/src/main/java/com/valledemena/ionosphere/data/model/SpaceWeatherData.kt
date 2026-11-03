package com.valledemena.ionosphere.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Espejo de `SpaceWeatherData` (web) y de `/api/space-weather`.
 *
 * Todo nullable se marca tal cual: el contrato web nullifica ausencias en vez de
 * inventar valores. El `source` de cada sub-bloque es dinámico (DATA_CONTRACT §4).
 */
@Serializable
data class KpPoint(
    val t: Long,
    val kp: Double,
)

@Serializable
data class TimeSeriesNumber(
    val t: Long,
    val f: Double,
)

@Serializable
data class BxSeriesPoint(
    val t: Long,
    val bx: Double,
)

@Serializable
data class SunspotRegion(
    val region: Int,
    val lat: Double,
    val lon: Double,
    val loc: String,
)

@Serializable
data class SpaceWeatherWind(
    val speed: Double,
    val bt: Double,
    val bz: Double,
    val bx: Double,
    val density: Double,
    val t: String?,
    val source: String,
)

@Serializable
data class SpaceWeatherKp(
    val now: Double,
    val gScale: Int,
    val series: List<KpPoint>,
    val source: String,
)

@Serializable
data class SpaceWeatherF107(
    val now: Double,
    val series: List<TimeSeriesNumber>,
    val source: String,
)

@Serializable
data class SpaceWeatherMag(
    val series: List<BxSeriesPoint>,
    val source: String,
)

@Serializable
data class SpaceWeatherXray(
    val now: Double,
    @SerialName("class") val xrayClass: String,
    val series: List<TimeSeriesNumber>,
    val seriesDay: List<TimeSeriesNumber>,
    val source: String,
)

@Serializable
data class SpaceWeatherProtons(
    val now: Double,
    val series: List<TimeSeriesNumber>,
    val satellite: Int,
    val source: String,
)

@Serializable
data class SpaceWeatherSunspots(
    val regions: List<SunspotRegion>,
    val source: String,
)

@Serializable
data class SpaceWeatherForecast(
    val maxKp: Double?,
    val note: String,
    val source: String,
)

@Serializable
data class SpaceWeatherData(
    val updated: Long,
    val utc: String,
    val kp: SpaceWeatherKp,
    val f107: SpaceWeatherF107,
    val wind: SpaceWeatherWind,
    val mag: SpaceWeatherMag,
    val xray: SpaceWeatherXray,
    val protons: SpaceWeatherProtons,
    val sunspots: SpaceWeatherSunspots,
    val forecast: SpaceWeatherForecast,
)

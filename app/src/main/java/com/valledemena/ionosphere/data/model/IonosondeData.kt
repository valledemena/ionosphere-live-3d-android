package com.valledemena.ionosphere.data.model

import kotlinx.serialization.Serializable

@Serializable
data class StationHistoryPoint(
    val t: String,
    val foF2: Double,
)

@Serializable
data class IonosondeStation(
    val code: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val foF2: Double?,
    val muf3000: Double?,
    val history: List<StationHistoryPoint>,
    val foE: Double?,
    val foF1: Double?,
    val hmF2: Double?,
    val B0: Double?,
    val B1: Double?,
    val stale: Boolean,
)

@Serializable
data class IonosondeData(
    val updated: Long,
    val stations: List<IonosondeStation>,
    val total: Int,
    val catalogSize: Int,
    val source: String,
)

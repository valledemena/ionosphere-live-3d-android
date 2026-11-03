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
    val foE: Double? = null,
    val foF1: Double? = null,
    val hmF2: Double? = null,
    val B0: Double? = null,
    val B1: Double? = null,
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

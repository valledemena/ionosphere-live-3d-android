package com.valledemena.ionosphere.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AuroraCell(
    val lat: Double,
    val lon: Double,
    val p: Int, // 0-100
)

@Serializable
data class AuroraData(
    val updated: Long,
    val observationTime: String,
    val forecastTime: String,
    val cells: List<AuroraCell>,
    val source: String,
)

package com.valledemena.ionosphere.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DstStormData(
    val id: String,
    val start: Long?,
    val kpMax: Double?,
    val link: String?,
)

@Serializable
data class DstSeriesPoint(
    val t: Long,
    val dst: Double,
)

@Serializable
data class DstData(
    val now: Double,
    val min7d: Double,
    val series: List<DstSeriesPoint>,
    val count: Int,
    val gst: List<DstStormData>,
    val checkedAt: Long,
    val source: String,
)

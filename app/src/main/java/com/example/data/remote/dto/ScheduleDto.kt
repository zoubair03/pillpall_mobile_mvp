package com.example.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleDto(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    val dose: DoseType,
    // Postgres "time" comes over the wire as "HH:mm:ss" — see DoseTimeFormatter.
    @SerialName("time_of_day") val timeOfDay: String,
    @SerialName("missed_timeout_min") val missedTimeoutMin: Int,
    @SerialName("updated_at") val updatedAt: String,
)

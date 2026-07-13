package com.example.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DoseEventDto(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    val dose: DoseType,
    val status: DoseStatus,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
    val source: String,
)

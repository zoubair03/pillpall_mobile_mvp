package com.example.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlertDto(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    // Free text on the backend: "missed_dose" | "low_battery" | "motor_fault" | "offline".
    val kind: String,
    val message: String? = null,
    val acknowledged: Boolean,
    @SerialName("created_at") val createdAt: String,
)

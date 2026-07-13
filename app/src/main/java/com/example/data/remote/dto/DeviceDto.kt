package com.example.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceDto(
    val id: String,
    @SerialName("device_uid") val deviceUid: String,
    @SerialName("patient_id") val patientId: String? = null,
    @SerialName("pairing_code") val pairingCode: String? = null,
    @SerialName("claimed_at") val claimedAt: String? = null,
    @SerialName("firmware_version") val firmwareVersion: String? = null,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

package com.example.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Params for the create_patient_and_claim(p_patient_name, p_timezone, p_pairing_code) RPC.
@Serializable
data class ClaimDeviceParams(
    @SerialName("p_patient_name") val patientName: String,
    @SerialName("p_timezone") val timezone: String,
    @SerialName("p_pairing_code") val pairingCode: String,
)

@Serializable
data class ClaimResultDto(
    @SerialName("patient_id") val patientId: String,
    @SerialName("device_id") val deviceId: String,
)

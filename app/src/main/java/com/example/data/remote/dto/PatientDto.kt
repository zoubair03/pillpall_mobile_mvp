package com.example.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PatientDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val timezone: String,
    val age: Int? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val conditions: String? = null,
    @SerialName("profile_completed") val profileCompleted: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

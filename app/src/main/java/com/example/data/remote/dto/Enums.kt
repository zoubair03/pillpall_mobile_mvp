package com.example.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors the dose_type Postgres enum (backend/pillpal_schema.sql).
@Serializable
enum class DoseType {
    @SerialName("morning") MORNING,
    @SerialName("midday") MIDDAY,
    @SerialName("night") NIGHT,
}

// Mirrors the dose_status Postgres enum.
@Serializable
enum class DoseStatus {
    @SerialName("pending") PENDING,
    @SerialName("dispensed") DISPENSED,
    @SerialName("acknowledged") ACKNOWLEDGED,
    @SerialName("missed") MISSED,
}

// Mirrors the caregiver_role Postgres enum.
@Serializable
enum class CaregiverRole {
    @SerialName("owner") OWNER,
    @SerialName("viewer") VIEWER,
}

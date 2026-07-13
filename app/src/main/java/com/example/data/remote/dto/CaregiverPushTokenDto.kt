package com.example.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CaregiverPushTokenUpsert(
    // caregiver_id is omitted on insert: the column defaults to auth.uid()
    // server-side, and RLS wouldn't allow claiming a different caregiver_id
    // anyway.
    @SerialName("fcm_token") val fcmToken: String,
)

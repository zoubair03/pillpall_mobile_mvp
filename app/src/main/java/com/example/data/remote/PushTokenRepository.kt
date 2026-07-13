package com.example.data.remote

import com.example.data.remote.dto.CaregiverPushTokenUpsert
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

// caregiver_id is the table's primary key and defaults to auth.uid()
// server-side, so upserting with just an fcm_token naturally lands on
// "this caregiver's row" — one row per caregiver, last-registered token
// wins (see backend/supabase/migrations/20260711000000_caregiver_push_tokens.sql).
@Singleton
class PushTokenRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    suspend fun upsertToken(fcmToken: String) {
        supabaseClient.from("caregiver_push_tokens").upsert(CaregiverPushTokenUpsert(fcmToken))
    }
}

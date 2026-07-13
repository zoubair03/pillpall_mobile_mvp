package com.example.data.remote

import com.example.data.remote.dto.DeviceDto
import com.example.data.remote.dto.PatientDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

// Resolves the signed-in caregiver's one patient and one device. A caregiver
// has exactly one patient (create_patient_and_claim enforces this at
// creation time, and there's no invite/sharing flow) — .limit(1) enforces
// the same single-patient/single-device assumption at the query layer,
// rather than trusting the UI to only ever show one.
@Singleton
class PatientDeviceRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    suspend fun getPatient(): PatientDto? =
        supabaseClient.from("patients")
            .select { limit(1) }
            .decodeList<PatientDto>()
            .firstOrNull()

    suspend fun getDeviceForPatient(patientId: String): DeviceDto? =
        supabaseClient.from("devices")
            .select {
                filter { eq("patient_id", patientId) }
                limit(1)
            }
            .decodeList<DeviceDto>()
            .firstOrNull()

    // RLS-permitted via "update linked patients" (pillpal_schema.sql) — the
    // caregiver can rename their own patient. Used by ControlsView's
    // profile-edit dialog, post-onboarding.
    suspend fun updateFullName(patientId: String, fullName: String) {
        supabaseClient.from("patients").update({
            set("full_name", fullName)
        }) {
            filter { eq("id", patientId) }
        }
    }

    // Onboarding's Patient Profile screen — first real save of patient
    // details, replacing the caregiver-name placeholder claimDevice() used.
    // Marks profile_completed so routeAfterAuthentication() knows this step
    // is done.
    suspend fun updateProfile(
        patientId: String,
        fullName: String,
        age: Int?,
        dateOfBirth: String?,
        conditions: String?,
    ) {
        supabaseClient.from("patients").update({
            set("full_name", fullName)
            set("age", age)
            set("date_of_birth", dateOfBirth)
            set("conditions", conditions)
            set("profile_completed", true)
        }) {
            filter { eq("id", patientId) }
        }
    }

    // Onboarding's Schedule Setup screen, final step — gates entry to the
    // main hub via routeAfterAuthentication().
    suspend fun markOnboardingComplete(deviceId: String) {
        supabaseClient.from("devices").update({
            set("onboarding_completed", true)
        }) {
            filter { eq("id", deviceId) }
        }
    }
}

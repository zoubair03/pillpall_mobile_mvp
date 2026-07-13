package com.example.data.remote

import com.example.data.remote.dto.ClaimDeviceParams
import com.example.data.remote.dto.ClaimResultDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

sealed class ClaimDeviceResult {
    data class Success(val result: ClaimResultDto) : ClaimDeviceResult()
    data class Error(val message: String) : ClaimDeviceResult()
}

@Singleton
class OnboardingRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    // Calls create_patient_and_claim — the only way a patients/devices row
    // gets created client-side (security definer, validates the pairing
    // code server-side, creates patient + caregiver_patients + claims the
    // device atomically). See backend/pillpal_schema.sql.
    suspend fun claimDevice(patientName: String, timezone: String, pairingCode: String): ClaimDeviceResult = try {
        val result = supabaseClient.postgrest.rpc(
            "create_patient_and_claim",
            ClaimDeviceParams(patientName, timezone, pairingCode),
        ).decodeList<ClaimResultDto>().firstOrNull()

        if (result == null) {
            ClaimDeviceResult.Error("Le serveur n'a renvoyé aucune donnée")
        } else {
            ClaimDeviceResult.Success(result)
        }
    } catch (e: Exception) {
        val message = if (e.message?.contains("invalid_or_already_claimed_pairing_code") == true) {
            "Code de jumelage invalide ou déjà utilisé"
        } else {
            e.message ?: "Échec du jumelage de l'appareil"
        }
        ClaimDeviceResult.Error(message)
    }
}

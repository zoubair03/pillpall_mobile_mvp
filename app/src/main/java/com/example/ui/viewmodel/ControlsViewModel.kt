package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthRepository
import com.example.data.remote.CommandResult
import com.example.data.remote.CommandsRepository
import com.example.data.remote.PatientDeviceRepository
import com.example.data.remote.dto.DoseType
import com.example.data.session.HubSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

@HiltViewModel
class ControlsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val patientDeviceRepository: PatientDeviceRepository,
    private val commandsRepository: CommandsRepository,
    private val hub: HubSessionStore,
) : ViewModel() {
    val patient = hub.patient
    val isDeviceOnline = hub.isDeviceOnline
    val devicePowerStatus = hub.devicePowerStatus
    val deviceBatteryLevel = hub.deviceBatteryLevel

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    // Dispenser Core overrides — all three are real publish-command calls.
    // Firmware doesn't consume MQTT commands yet (backend/plan.md), so
    // these succeed at the HTTP/broker layer with no physical effect yet —
    // no optimistic local state is mutated to pretend otherwise.
    fun forceDispense(dose: DoseType) {
        val deviceId = hub.device.value?.id ?: return
        viewModelScope.launch {
            val payload = buildJsonObject { put("dose", dose.name.lowercase()) }
            val result = commandsRepository.publish(deviceId, "force_dispense", payload)
            if (result is CommandResult.Error) _actionError.value = result.message
        }
    }

    fun runCalibration() {
        val deviceId = hub.device.value?.id ?: return
        viewModelScope.launch {
            val result = commandsRepository.publish(deviceId, "calibrate")
            if (result is CommandResult.Error) _actionError.value = result.message
        }
    }

    fun runRestart() {
        val deviceId = hub.device.value?.id ?: return
        viewModelScope.launch {
            val result = commandsRepository.publish(deviceId, "restart")
            if (result is CommandResult.Error) _actionError.value = result.message
        }
    }

    // RLS-permitted direct Postgrest update ("update linked patients" in
    // pillpal_schema.sql) — age/DOB/conditions are edited on this profile
    // dialog too, but only the name is here today; see PatientDto.
    fun renamePatient(fullName: String) {
        val patient = hub.patient.value ?: return
        val trimmed = fullName.trim()
        if (trimmed.isEmpty() || trimmed == patient.fullName) return
        viewModelScope.launch {
            try {
                patientDeviceRepository.updateFullName(patient.id, trimmed)
                hub.setPatientLocally(patient.copy(fullName = trimmed))
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Échec de la mise à jour du profil"
            }
        }
    }

    // One-shot: once true, the composable navigates to sign_in with a full
    // back-stack clear, popping (and destroying) this ViewModel along with
    // the rest of the hub graph — no "consume" reset needed.
    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
            hub.clear()
            _loggedOut.value = true
        }
    }
}

package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.CommandResult
import com.example.data.remote.CommandsRepository
import com.example.data.remote.DoseTimeFormatter
import com.example.data.remote.ScheduleRepository
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
class ScheduleViewModel @Inject constructor(
    private val hub: HubSessionStore,
    private val scheduleRepository: ScheduleRepository,
    private val commandsRepository: CommandsRepository,
) : ViewModel() {
    val realSchedules = hub.realSchedules

    // Selected tab inside Schedule page ("Heures de Distribution" vs "Plan de Recharge")
    private val _scheduleTab = MutableStateFlow("Heures de Distribution")
    val scheduleTab: StateFlow<String> = _scheduleTab.asStateFlow()
    fun setScheduleTab(tab: String) { _scheduleTab.value = tab }

    // Refill Blueprint tab: no pills_remaining concept exists anywhere in
    // the schema — this stays a local-only, non-persisted affordance, not
    // backed by any real inventory tracking.
    private val _pillsRemaining = MutableStateFlow(21)
    val pillsRemaining: StateFlow<Int> = _pillsRemaining.asStateFlow()
    fun refillDispenser() { _pillsRemaining.value = 21 }

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    // Direct Postgrest update (RLS-permitted for the schedule's owner),
    // then a publish-command call so the device picks up the change —
    // matches the architecture: the app is the source of truth for
    // schedules, the command is just a nudge to sync.
    fun updateScheduleTime(dose: DoseType, hour: Int, minute: Int) {
        val deviceId = hub.device.value?.id ?: return
        viewModelScope.launch {
            val timeOfDay = DoseTimeFormatter.toPostgres(hour, minute)
            try {
                scheduleRepository.updateTime(deviceId, dose, timeOfDay)
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Échec de la mise à jour de l'horaire"
                return@launch
            }
            val payload = buildJsonObject { put(dose.name.lowercase(), timeOfDay) }
            val result = commandsRepository.publish(deviceId, "update_schedule", payload)
            if (result is CommandResult.Error) _actionError.value = result.message
        }
    }
}

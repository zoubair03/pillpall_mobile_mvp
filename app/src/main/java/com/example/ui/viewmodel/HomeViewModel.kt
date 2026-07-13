package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.CommandResult
import com.example.data.remote.CommandsRepository
import com.example.data.remote.dto.DoseType
import com.example.data.session.HubSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val hub: HubSessionStore,
    private val commandsRepository: CommandsRepository,
) : ViewModel() {
    val patient = hub.patient
    val todayLabel = hub.todayLabel
    val weekDays = hub.weekDays
    val countdownTimer = hub.countdownTimer

    // Calendar day selector — defaults to today. Tapping a different day in
    // the strip swaps doseCards below to that day's history instead of
    // today's actionable list.
    private val _selectedDay = MutableStateFlow(LocalDate.now())
    val selectedDay: StateFlow<LocalDate> = _selectedDay.asStateFlow()
    fun selectCalendarDay(date: LocalDate) { _selectedDay.value = date }

    val doseCards = hub.doseCardsFor(_selectedDay)

    // Acknowledging only makes sense for today — a past day is history, a
    // future day hasn't happened. Non-today selections render read-only.
    val isSelectedDayToday: StateFlow<Boolean> = _selectedDay
        .map { it == LocalDate.now() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    // Sends ack_dose for a dose card tap. No optimistic local mutation of
    // adherence state — the card stays "En attente" until Realtime reflects
    // a real dose_events row (or the firmware, once it exists, never acts on
    // the command and it just stays pending — that's the honest state).
    fun acknowledgeDose(dose: DoseType) {
        if (!isSelectedDayToday.value) return
        val deviceId = hub.device.value?.id ?: return
        viewModelScope.launch {
            val payload = buildJsonObject { put("dose", dose.name.lowercase()) }
            val result = commandsRepository.publish(deviceId, "ack_dose", payload)
            if (result is CommandResult.Error) {
                _actionError.value = result.message
            }
        }
    }
}

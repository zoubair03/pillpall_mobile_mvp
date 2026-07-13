package com.example.data.session

import com.example.data.remote.AlertsRepository
import com.example.data.remote.DeviceStatusRepository
import com.example.data.remote.DoseEventRepository
import com.example.data.remote.DoseTimeFormatter
import com.example.data.remote.PatientDeviceRepository
import com.example.data.remote.ScheduleRepository
import com.example.data.remote.dto.AlertDto
import com.example.data.remote.dto.DeviceDto
import com.example.data.remote.dto.DeviceStatusDto
import com.example.data.remote.dto.DoseEventDto
import com.example.data.remote.dto.DoseStatus
import com.example.data.remote.dto.DoseType
import com.example.data.remote.dto.PatientDto
import com.example.data.remote.dto.ScheduleDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// Shared read-state for everything keyed off the caregiver's one patient/
// device, backing all four hub tabs plus the tail end of onboarding
// (PatientProfileScreen/ScheduleSetupScreen need patient/schedules before
// ever reaching the hub). A plain singleton rather than a ViewModel — Hilt
// ViewModels can't constructor-inject other ViewModels, and Home/Horaires/
// Historique/Contrôles all read overlapping slices of this same data, so
// duplicating the loading + Realtime-subscription logic per tab would mean
// four independent subscriptions to the same rows instead of one shared set.
@Singleton
class HubSessionStore @Inject constructor(
    private val patientDeviceRepository: PatientDeviceRepository,
    private val deviceStatusRepository: DeviceStatusRepository,
    private val scheduleRepository: ScheduleRepository,
    private val doseEventRepository: DoseEventRepository,
    private val alertsRepository: AlertsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _patient = MutableStateFlow<PatientDto?>(null)
    val patient: StateFlow<PatientDto?> = _patient.asStateFlow()

    private val _device = MutableStateFlow<DeviceDto?>(null)
    val device: StateFlow<DeviceDto?> = _device.asStateFlow()

    private val _deviceStatus = MutableStateFlow<DeviceStatusDto?>(null)
    val deviceStatus: StateFlow<DeviceStatusDto?> = _deviceStatus.asStateFlow()

    val isDeviceOnline: StateFlow<Boolean> = _deviceStatus.map { it?.wifiConnected == true }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    val devicePowerStatus: StateFlow<String> = _deviceStatus.map {
        when {
            it == null -> "Statut inconnu"
            it.rtcOk == true -> "Horloge synchronisée"
            else -> "Horloge non synchronisée"
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), "Statut inconnu")

    val deviceBatteryLevel: StateFlow<Int> = _deviceStatus.map { it?.batteryPct ?: 0 }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    private val _realSchedules = MutableStateFlow<List<ScheduleDto>>(emptyList())
    val realSchedules: StateFlow<List<ScheduleDto>> = _realSchedules.asStateFlow()

    private val _doseEvents = MutableStateFlow<List<DoseEventDto>>(emptyList())
    val doseEvents: StateFlow<List<DoseEventDto>> = _doseEvents.asStateFlow()

    private val _alerts = MutableStateFlow<List<AlertDto>>(emptyList())
    val alerts: StateFlow<List<AlertDto>> = _alerts.asStateFlow()

    private val _hubLoading = MutableStateFlow(false)
    val hubLoading: StateFlow<Boolean> = _hubLoading.asStateFlow()

    private var hubDataJob: Job? = null

    // Loads the caregiver's patient/device once, then keeps device_status/
    // schedules/dose_events/alerts live via Realtime for as long as the
    // store lives (the whole app process — it's a singleton) — cheap
    // enough for a single-device app, and simpler than tearing subscriptions
    // down per tab.
    fun ensureLoaded() {
        if (hubDataJob?.isActive == true) return
        hubDataJob = scope.launch {
            _hubLoading.value = true
            val patient = try {
                patientDeviceRepository.getPatient()
            } catch (e: Exception) {
                null
            }
            _patient.value = patient

            val device = patient?.let {
                try {
                    patientDeviceRepository.getDeviceForPatient(it.id)
                } catch (e: Exception) {
                    null
                }
            }
            _device.value = device
            _hubLoading.value = false

            if (device == null) return@launch

            launch { deviceStatusRepository.observeStatus(device.id).collect { _deviceStatus.value = it } }
            launch { scheduleRepository.observeSchedules(device.id).collect { _realSchedules.value = it } }
            launch { doseEventRepository.observeEvents(device.id).collect { _doseEvents.value = it } }
            launch { alertsRepository.observeAlerts(device.id).collect { _alerts.value = it } }
        }
    }

    // What the top bar's manual sync button calls.
    fun refresh() {
        hubDataJob?.cancel()
        hubDataJob = null
        ensureLoaded()
    }

    // Called on logout — clears everything so a stale patient/device from
    // the previous session can never flash before the next sign-in's real
    // data (or lack thereof) loads in.
    fun clear() {
        hubDataJob?.cancel()
        hubDataJob = null
        _patient.value = null
        _device.value = null
        _deviceStatus.value = null
        _realSchedules.value = emptyList()
        _doseEvents.value = emptyList()
        _alerts.value = emptyList()
    }

    // Local-state mirrors for mutations made elsewhere (OnboardingViewModel's
    // savePatientProfile/completeOnboarding, ControlsViewModel's
    // renamePatient) — the DB write already happened by the time these are
    // called; this just keeps the store's cached copy in sync without a
    // redundant re-fetch.
    fun setPatientLocally(patient: PatientDto) {
        _patient.value = patient
    }

    fun markOnboardingCompletedLocally() {
        _device.value = _device.value?.copy(onboardingCompleted = true)
    }

    private fun patientZoneId(): ZoneId =
        _patient.value?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

    data class DoseCardState(
        val dose: DoseType,
        val label: String,
        val timeLabel: String,
        val status: DoseStatus?,
    )

    private fun doseStatusOn(dose: DoseType, events: List<DoseEventDto>, zone: ZoneId, date: LocalDate): DoseStatus? =
        events
            .filter { it.dose == dose }
            .filter { event ->
                runCatching { Instant.parse(event.occurredAt).atZone(zone).toLocalDate() == date }.getOrDefault(false)
            }
            .maxByOrNull { it.occurredAt }
            ?.status

    val doseCards: StateFlow<List<DoseCardState>> = combine(_realSchedules, _doseEvents, _patient) { schedules, events, _ ->
        val zone = patientZoneId()
        val today = LocalDate.now(zone)
        val labels = mapOf(DoseType.MORNING to "Matin", DoseType.MIDDAY to "Midi", DoseType.NIGHT to "Soir")
        DoseType.entries.map { dose ->
            val schedule = schedules.find { it.dose == dose }
            DoseCardState(
                dose = dose,
                label = labels.getValue(dose),
                timeLabel = schedule?.let { DoseTimeFormatter.toDisplay(it.timeOfDay) } ?: "--:--",
                status = doseStatusOn(dose, events, zone, today),
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Same card shape as doseCards above, but for an arbitrary caregiver-
    // selected date instead of always today — backs the Home screen's
    // calendar strip so tapping a different day actually shows that day's
    // dose history instead of just highlighting the tapped day.
    fun doseCardsFor(selectedDate: StateFlow<LocalDate>): StateFlow<List<DoseCardState>> =
        combine(_realSchedules, _doseEvents, selectedDate) { schedules, events, date ->
            val zone = patientZoneId()
            val labels = mapOf(DoseType.MORNING to "Matin", DoseType.MIDDAY to "Midi", DoseType.NIGHT to "Soir")
            DoseType.entries.map { dose ->
                val schedule = schedules.find { it.dose == dose }
                DoseCardState(
                    dose = dose,
                    label = labels.getValue(dose),
                    timeLabel = schedule?.let { DoseTimeFormatter.toDisplay(it.timeOfDay) } ?: "--:--",
                    status = doseStatusOn(dose, events, zone, date),
                )
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Ticks every second so the countdown display stays live; recomputed
    // from real schedule/event data each tick rather than a free-running
    // fake timer.
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(1000)
        }
    }

    val countdownTimer: StateFlow<String> = combine(ticker, _realSchedules, _doseEvents) { _, schedules, events ->
        computeNextDoseCountdown(schedules, events)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), "--:--:--")

    private fun computeNextDoseCountdown(schedules: List<ScheduleDto>, events: List<DoseEventDto>): String {
        if (schedules.isEmpty()) return "--:--:--"
        val zone = patientZoneId()
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()

        val next = schedules.mapNotNull { schedule ->
            val time = runCatching { LocalTime.parse(schedule.timeOfDay.take(8)) }.getOrNull() ?: return@mapNotNull null
            val scheduledToday = ZonedDateTime.of(today, time, zone)
            val handledToday = doseStatusOn(schedule.dose, events, zone, today) != null
            if (scheduledToday.isAfter(now) && !handledToday) scheduledToday else scheduledToday.plusDays(1)
        }.minOrNull() ?: return "--:--:--"

        val remaining = Duration.between(now, next)
        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            remaining.toHours(),
            remaining.toMinutesPart(),
            remaining.toSecondsPart(),
        )
    }

    val todayLabel: StateFlow<String> = _patient.map {
        val formatted = LocalDate.now(patientZoneId()).format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
        formatted.replaceFirstChar { c -> c.titlecase(Locale.FRENCH) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), "")

    data class CalendarDay(val label: String, val dayOfMonth: Int, val date: LocalDate)

    // Real dates (today ± 2 days) so this row can't drift out of sync with
    // todayLabel above the way a hardcoded "23, 24, 25..." would. Carries
    // the full date (not just dayOfMonth) so selection stays correct across
    // month boundaries.
    val weekDays: StateFlow<List<CalendarDay>> = _patient.map {
        val today = LocalDate.now(patientZoneId())
        (-2..2).map { offset ->
            val date = today.plusDays(offset.toLong())
            val label = date.dayOfWeek
                .getDisplayName(TextStyle.NARROW, Locale.FRENCH)
                .uppercase(Locale.FRENCH)
            CalendarDay(label, date.dayOfMonth, date)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
}

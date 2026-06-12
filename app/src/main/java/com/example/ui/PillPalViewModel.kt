package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HistoryLog
import com.example.data.PillPalDatabase
import com.example.data.PillPalRepository
import com.example.data.ScheduleItem
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PillPalViewModel(application: Application) : AndroidViewModel(application) {

    // Initialize Database & Repository safely
    private val database = PillPalDatabase.getDatabase(application, viewModelScope)
    private val repository = PillPalRepository(database.pillPalDao())

    // Enums for Navigation & Tabs
    enum class Screen {
        SIGN_IN,
        CREATE_PROFILE,
        ENTER_CODE,
        NO_DEVICE,
        CONNECT_WIFI,
        PAIRED_SUCCESS,
        MAIN_HUB
    }

    enum class Tab {
        HOME,
        SCHEDULE,
        HISTORY,
        CONTROLS
    }

    // Navigation and Tab flows
    private val _currentScreen = MutableStateFlow(Screen.SIGN_IN)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _currentTab = MutableStateFlow(Tab.HOME)
    val currentTab: StateFlow<Tab> = _currentTab.asStateFlow()

    // Onboarding flow Form states
    private val _signInEmail = MutableStateFlow("caregiver@pillpal.com")
    val signInEmail: StateFlow<String> = _signInEmail.asStateFlow()

    private val _signInPassword = MutableStateFlow("••••••••••")
    val signInPassword: StateFlow<String> = _signInPassword.asStateFlow()

    private val _profileName = MutableStateFlow("Aidant d'Ahmed")
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    private val _profileEmail = MutableStateFlow("caregiver@pillpal.com")
    val profileEmail: StateFlow<String> = _profileEmail.asStateFlow()

    private val _profilePassword = MutableStateFlow("12345678")
    val profilePassword: StateFlow<String> = _profilePassword.asStateFlow()

    private val _agreeTerms = MutableStateFlow(true)
    val agreeTerms: StateFlow<Boolean> = _agreeTerms.asStateFlow()

    // 6 Digit verification Code Entry states
    private val _verificationCode = MutableStateFlow(listOf("", "", "", "", "", ""))
    val verificationCode: StateFlow<List<String>> = _verificationCode.asStateFlow()

    private val _verificationResendTimer = MutableStateFlow(58)
    val verificationResendTimer: StateFlow<Int> = _verificationResendTimer.asStateFlow()

    // Bluetooth linking simulation states
    private val _bluetoothScanning = MutableStateFlow(false)
    val bluetoothScanning: StateFlow<Boolean> = _bluetoothScanning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<String>>(emptyList())
    val scannedDevices: StateFlow<List<String>> = _scannedDevices.asStateFlow()

    // Wifi provision simulation states
    private val _selectedSSID = MutableStateFlow("")
    val selectedSSID: StateFlow<String> = _selectedSSID.asStateFlow()

    private val _wifiPassword = MutableStateFlow("")
    val wifiPassword: StateFlow<String> = _wifiPassword.asStateFlow()

    private val _wifiSending = MutableStateFlow(false)
    val wifiSending: StateFlow<Boolean> = _wifiSending.asStateFlow()

    // Main App Real-Time states
    val schedules: StateFlow<List<ScheduleItem>> = repository.allSchedules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val historyLogs: StateFlow<List<HistoryLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Syncing status indicator state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Filter chip configuration on History page
    private val _historyFilter = MutableStateFlow("Tout") // "Tout", "Doses", "Statut Matériel"
    val historyFilter: StateFlow<String> = _historyFilter.asStateFlow()

    // Device diagnostic states
    private val _isDeviceOnline = MutableStateFlow(true)
    val isDeviceOnline: StateFlow<Boolean> = _isDeviceOnline.asStateFlow()

    private val _devicePowerStatus = MutableStateFlow("USB-C branché")
    val devicePowerStatus: StateFlow<String> = _devicePowerStatus.asStateFlow()

    private val _deviceBatteryLevel = MutableStateFlow(88)
    val deviceBatteryLevel: StateFlow<Int> = _deviceBatteryLevel.asStateFlow()

    // Current countdown timing state for next dose
    private val _countdownTimer = MutableStateFlow("01:12:45")
    val countdownTimer: StateFlow<String> = _countdownTimer.asStateFlow()

    // Selected Date on Calendar selector row
    private val _selectedDay = MutableStateFlow(25) // Wednesday 25th in screenshot
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    // Active Dose Batch completion states
    private val _morningDoseDispensed = MutableStateFlow(true)
    val morningDoseDispensed: StateFlow<Boolean> = _morningDoseDispensed.asStateFlow()

    private val _middayDoseDispensed = MutableStateFlow(false)
    val middayDoseDispensed: StateFlow<Boolean> = _middayDoseDispensed.asStateFlow()

    private val _nightDoseDispensed = MutableStateFlow(false)
    val nightDoseDispensed: StateFlow<Boolean> = _nightDoseDispensed.asStateFlow()

    // Dispenser Refill blueprint detail state (Image 2 - Refill Blueprint)
    private val _pillsRemaining = MutableStateFlow(21) // out of 21 max
    val pillsRemaining: StateFlow<Int> = _pillsRemaining.asStateFlow()

    // Selected tab inside Schedule page ("Heures de Distribution" vs "Plan de Recharge")
    private val _scheduleTab = MutableStateFlow("Heures de Distribution")
    val scheduleTab: StateFlow<String> = _scheduleTab.asStateFlow()

    // Form screen helper updates
    fun updateSignInEmail(v: String) { _signInEmail.value = v }
    fun updateSignInPassword(v: String) { _signInPassword.value = v }
    fun updateProfileName(v: String) { _profileName.value = v }
    fun updateProfileEmail(v: String) { _profileEmail.value = v }
    fun updateProfilePassword(v: String) { _profilePassword.value = v }
    fun updateAgreeTerms(v: Boolean) { _agreeTerms.value = v }

    init {
        // Run verification resend countdown ticker
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_verificationResendTimer.value > 0) {
                    _verificationResendTimer.value--
                }
            }
        }

        // Run Next Dose real-time countdown timer ticker
        viewModelScope.launch {
            var secondsLeft = 4365 // 1 hour, 12 minutes, 45 seconds as in screens
            while (true) {
                delay(1000)
                if (secondsLeft > 0) {
                    secondsLeft--
                } else {
                    secondsLeft = 8 * 60 * 60 // Reset to 8 hours
                    // Add missed event if not actioned
                    recordEvent(
                        type = "dose_missed",
                        title = "Dose du Midi Manquée",
                        description = "Le patient n'a pas pris ses médicaments dans la plage horaire prévue.",
                        category = "Doses"
                    )
                }
                val hours = secondsLeft / 3600
                val minutes = (secondsLeft % 3600) / 60
                val secs = secondsLeft % 60
                _countdownTimer.value = String.format(Locale.US, "%02d:%02d:%01d%01d", hours, minutes, secs / 10, secs % 10)
            }
        }
    }

    // Set screen navigation helper
    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // Set tab selection helper
    fun selectTab(tab: Tab) {
        _currentTab.value = tab
    }

    // Set interactive verify digit entries
    fun updateCodeDigit(index: Int, digit: String) {
        val newList = _verificationCode.value.toMutableList()
        if (index in newList.indices) {
            newList[index] = digit
            _verificationCode.value = newList
        }
    }

    fun resendVerificationCode() {
        _verificationResendTimer.value = 59
    }

    // Simulate Bluetooth Device search
    fun startBluetoothScanning() {
        _bluetoothScanning.value = true
        _scannedDevices.value = emptyList()
        viewModelScope.launch {
            delay(1500) // Realistic delay
            _scannedDevices.value = listOf("PillPal-SN8824", "PillPal-Alpha", "SmartDispenser_B9")
            _bluetoothScanning.value = false
        }
    }

    // WiFi Provisioning
    fun selectSSID(ssid: String) {
        _selectedSSID.value = ssid
    }

    fun updateWifiPassword(p: String) {
        _wifiPassword.value = p
    }

    fun sendWifiCredentials() {
        _wifiSending.value = true
        viewModelScope.launch {
            delay(2000) // Connect simulation
            _wifiSending.value = false
            recordEvent(
                type = "calibrated",
                title = "Connexion Wifi Réussie",
                description = "Distributeur connecté avec succès au SSID : ${_selectedSSID.value}",
                category = "Statut Matériel"
            )
            navigateTo(Screen.PAIRED_SUCCESS)
        }
    }

    // Synchronize Trigger (Top-Right refreshing circle icon)
    fun triggerSync() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        viewModelScope.launch {
            delay(1500) // Simulate wireless transmission
            _isSyncing.value = false
            // Refresh counts or generate healthy logging update
            _deviceBatteryLevel.value = (80..98).random()
            recordEvent(
                type = "dose_dispensed",
                title = "Synchronisation Santé Appareil",
                description = "Base de données et horaires de distribution synchronisés en temps réel avec le distributeur.",
                category = "Statut Matériel"
            )
        }
    }

    // Save and synchronization of schedules (Image 2 - Schedule page)
    fun saveScheduleTime(morningTime: String, middayTime: String, nightTime: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateScheduleTime("morning", morningTime)
            repository.updateScheduleTime("midday", middayTime)
            repository.updateScheduleTime("night", nightTime)
            
            // Add history event
            recordEvent(
                type = "calibrated",
                title = "Horaires Synchronisés",
                description = "Minuteurs de l'appareil mis à jour : Matin=$morningTime, Midi=$middayTime, Soir=$nightTime.",
                category = "Statut Matériel"
            )
        }
    }

    fun setScheduleActive(id: String, active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateScheduleActive(id, active)
        }
    }

    fun setScheduleTab(tab: String) {
        _scheduleTab.value = tab
    }

    // Reset local state logs history
    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
            repository.insertLog(
                HistoryLog(
                    type = "calibrated",
                    title = "Historique Effacé",
                    description = "L'historique des événements a été archivé par l'aidant.",
                    time = currentTimeString(),
                    dateLabel = "Aujourd'hui",
                    category = "Statut Matériel"
                )
            )
        }
    }

    // Calendar Day helper
    fun selectCalendarDay(day: Int) {
        _selectedDay.value = day
    }

    // Filter Chips on History Page
    fun setHistoryFilter(filter: String) {
        _historyFilter.value = filter
    }

    // Toggle dose buttons
    fun toggleMorningDose() {
        _morningDoseDispensed.value = !_morningDoseDispensed.value
    }
    fun toggleMiddayDose() {
        _middayDoseDispensed.value = !_middayDoseDispensed.value
    }
    fun toggleNightDose() {
        _nightDoseDispensed.value = !_nightDoseDispensed.value
    }

    // Dispenser Core overrides (Active Control Wheel - Image 5)
    fun forceDispense(batch: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val title = when (batch.lowercase()) {
                "morning" -> {
                    _morningDoseDispensed.value = true
                    "Dose du Matin"
                }
                "midday" -> {
                    _middayDoseDispensed.value = true
                    "Dose du Midi"
                }
                else -> {
                    _nightDoseDispensed.value = true
                    "Dose du Soir"
                }
            }

            // Decrement remaining drug tablets in carousel
            if (_pillsRemaining.value > 0) {
                _pillsRemaining.value--
            }

            // Create log event
            recordEvent(
                type = "manual_drop",
                title = "Distribution forcée : $title",
                description = "L'aidant a forcé la distribution à distance. Le carrousel du distributeur a tourné avec succès.",
                category = "Doses"
            )
        }
    }

    // Maintenance overrides
    fun runCalibration() {
        viewModelScope.launch(Dispatchers.IO) {
            recordEvent(
                type = "calibrated",
                title = "Auto-calibration Matérielle",
                description = "Alignement physique du carrousel, capteur d'origine et index de vitesse calibrés avec succès.",
                category = "Statut Matériel"
            )
        }
    }

    fun runRestart() {
        viewModelScope.launch(Dispatchers.IO) {
            delay(1000)
            _deviceBatteryLevel.value = 88
            recordEvent(
                type = "restart",
                title = "Appareil Intelligent Redémarré",
                description = "Le système de micro-contrôleur physique PillPal a redémarré et s'est reconnecté au serveur cloud.",
                category = "Statut Matériel"
            )
        }
    }

    fun refillDispenser() {
        _pillsRemaining.value = 21 // Max standard count
        viewModelScope.launch(Dispatchers.IO) {
            recordEvent(
                type = "calibrated",
                title = "Recharge du Carrousel Terminée",
                description = "L'aidant a rechargé la cartouche rotative de 21 compartiments (7 par roue) avec les médicaments programmés.",
                category = "Doses"
            )
        }
    }

    fun logout() {
        _currentScreen.value = Screen.SIGN_IN
        _verificationCode.value = listOf("", "", "", "")
        viewModelScope.launch(Dispatchers.IO) {
            recordEvent(
                type = "system",
                title = "Aidant Déconnecté",
                description = "L'aidant s'est déconnecté manuellement du centre de contrôle de l'appareil.",
                category = "Système"
            )
        }
    }

    // Internal helper to insert logs
    private suspend fun recordEvent(type: String, title: String, description: String, category: String) {
        repository.insertLog(
            HistoryLog(
                type = type,
                title = title,
                description = description,
                time = currentTimeString(),
                dateLabel = "Aujourd'hui",
                category = category
            )
        )
    }

    private fun currentTimeString(): String {
        return try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf.format(Date())
        } catch (e: Exception) {
            "12:00 PM"
        }
    }
}

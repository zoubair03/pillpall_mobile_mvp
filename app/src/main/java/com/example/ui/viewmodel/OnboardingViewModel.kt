package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Network
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthResult
import com.example.data.local.DeviceConnectResult
import com.example.data.local.DeviceWifiConnector
import com.example.data.local.LocalDeviceApi
import com.example.data.remote.ClaimDeviceResult
import com.example.data.remote.CommandResult
import com.example.data.remote.CommandsRepository
import com.example.data.remote.DoseTimeFormatter
import com.example.data.remote.OnboardingRepository
import com.example.data.remote.PatientDeviceRepository
import com.example.data.remote.ScheduleRepository
import com.example.data.remote.dto.DoseType
import com.example.data.session.HubSessionStore
import com.example.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.TimeZone
import javax.inject.Inject

// Covers everything from SIGN_IN through SCHEDULE_SETUP — sign-in/up, email
// OTP, the mocked WiFi hand-off, pairing-code claim, patient profile, and
// schedule setup are genuinely one linear wizard (nothing in it is
// reachable out of order), and claimDevice() reuses the caregiver's own
// name — captured at sign-up — as a temporary patient-name placeholder, so
// splitting sign-in/up and onboarding into two ViewModels would just mean
// threading that value through nav arguments for no real benefit. Scoped
// to the "onboarding" nested nav graph (and to the flat sign-in/create-
// profile/verify-otp routes before it) so it survives across all of these
// screens and is torn down once the caregiver reaches the hub or signs out.
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val onboardingRepository: OnboardingRepository,
    private val patientDeviceRepository: PatientDeviceRepository,
    private val scheduleRepository: ScheduleRepository,
    private val commandsRepository: CommandsRepository,
    private val hub: HubSessionStore,
    private val deviceWifiConnector: DeviceWifiConnector,
    private val localDeviceApi: LocalDeviceApi,
) : ViewModel() {

    // One-time navigation events — the graph-level LaunchedEffect observing
    // this decides the right popUpTo/back-stack policy per destination
    // (e.g. entering MAIN_HUB always clears the onboarding back stack).
    // Same consume-once pattern as otpResendConfirmation below.
    private val _navigationEvent = MutableStateFlow<String?>(null)
    val navigationEvent: StateFlow<String?> = _navigationEvent.asStateFlow()
    fun consumeNavigationEvent() { _navigationEvent.value = null }

    private val _signInEmail = MutableStateFlow("")
    val signInEmail: StateFlow<String> = _signInEmail.asStateFlow()

    private val _signInPassword = MutableStateFlow("")
    val signInPassword: StateFlow<String> = _signInPassword.asStateFlow()

    private val _profileName = MutableStateFlow("")
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    private val _profileEmail = MutableStateFlow("")
    val profileEmail: StateFlow<String> = _profileEmail.asStateFlow()

    private val _profilePassword = MutableStateFlow("")
    val profilePassword: StateFlow<String> = _profilePassword.asStateFlow()

    private val _agreeTerms = MutableStateFlow(false)
    val agreeTerms: StateFlow<Boolean> = _agreeTerms.asStateFlow()

    // Shared by sign-in, sign-up, OTP, wifi-mock, claim, profile and
    // schedule-setup screens — all part of the same linear wizard.
    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _emailOtpCode = MutableStateFlow("")
    val emailOtpCode: StateFlow<String> = _emailOtpCode.asStateFlow()

    private val _otpResendConfirmation = MutableStateFlow<String?>(null)
    val otpResendConfirmation: StateFlow<String?> = _otpResendConfirmation.asStateFlow()
    fun consumeOtpResendConfirmation() { _otpResendConfirmation.value = null }

    private val _claimedDeviceUid = MutableStateFlow<String?>(null)
    val claimedDeviceUid: StateFlow<String?> = _claimedDeviceUid.asStateFlow()

    // Connecting to the device's own setup hotspot (NoDeviceScreen) — see
    // connectToDevice() below.
    private val _deviceConnecting = MutableStateFlow(false)
    val deviceConnecting: StateFlow<Boolean> = _deviceConnecting.asStateFlow()

    // Captured from the device's own /api/status once connected — the
    // pairing code is never typed by the caregiver (see design notes on
    // connectToDevice()). deviceNetwork is held only for the lifetime of
    // the connect → send-wifi-credentials → claim sequence.
    private val _capturedDeviceUid = MutableStateFlow<String?>(null)
    private val _capturedPairingCode = MutableStateFlow<String?>(null)
    private var deviceNetwork: Network? = null

    private val _selectedSSID = MutableStateFlow("")
    val selectedSSID: StateFlow<String> = _selectedSSID.asStateFlow()

    private val _wifiPassword = MutableStateFlow("")
    val wifiPassword: StateFlow<String> = _wifiPassword.asStateFlow()

    private val _wifiSending = MutableStateFlow(false)
    val wifiSending: StateFlow<Boolean> = _wifiSending.asStateFlow()

    // Exposed for PatientProfileScreen/ScheduleSetupScreen — populated by
    // hub.ensureLoaded(), called as soon as claimDevice() succeeds.
    val patient = hub.patient
    val device = hub.device
    val realSchedules = hub.realSchedules

    fun updateSignInEmail(v: String) { _signInEmail.value = v; _authError.value = null }
    fun updateSignInPassword(v: String) { _signInPassword.value = v; _authError.value = null }
    fun updateProfileName(v: String) { _profileName.value = v }
    fun updateProfileEmail(v: String) { _profileEmail.value = v; _authError.value = null }
    fun updateProfilePassword(v: String) { _profilePassword.value = v; _authError.value = null }
    fun updateAgreeTerms(v: Boolean) { _agreeTerms.value = v }

    // Resolves the boot-gate destination once Supabase Auth's session
    // restoration settles. Called from a LaunchedEffect at the nav graph's
    // start destination instead of an init{} block writing straight to
    // navigation state, now that navigation lives in the NavController.
    suspend fun resolveStartDestination(): String {
        val status = authRepository.sessionStatus.first { it !is SessionStatus.Initializing }
        return if (status is SessionStatus.Authenticated) routeAfterAuthentication() else Routes.SIGN_IN
    }

    // Shared by signIn()'s success path and the boot gate. A network hiccup
    // shouldn't force an already-onboarded caregiver back through pairing —
    // only route forward on a confirmed missing step, not on a failed
    // lookup (hence the failure fallback routing onward to MAIN_HUB rather
    // than back to NO_DEVICE).
    private suspend fun routeAfterAuthentication(): String {
        val patientLookup = try {
            Result.success(patientDeviceRepository.getPatient())
        } catch (e: Exception) {
            Result.failure(e)
        }
        val patient = patientLookup.getOrNull() ?: run {
            if (patientLookup.isFailure) {
                hub.ensureLoaded()
                return Routes.MAIN_HUB
            }
            return Routes.NO_DEVICE
        }

        if (!patient.profileCompleted) {
            hub.ensureLoaded()
            return Routes.PATIENT_PROFILE
        }

        val device = try {
            patientDeviceRepository.getDeviceForPatient(patient.id)
        } catch (e: Exception) {
            null
        }
        if (device !== null && !device.onboardingCompleted) {
            hub.ensureLoaded()
            return Routes.SCHEDULE_SETUP
        }

        hub.ensureLoaded()
        return Routes.MAIN_HUB
    }

    fun signIn() {
        val email = _signInEmail.value.trim()
        val password = _signInPassword.value
        if (email.isBlank() || password.isBlank()) {
            _authError.value = "Veuillez entrer l'adresse email et le mot de passe"
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            when (val result = authRepository.signIn(email, password)) {
                is AuthResult.SignedIn -> {
                    _authError.value = null
                    _navigationEvent.value = routeAfterAuthentication()
                }
                is AuthResult.Error -> _authError.value = result.message
                is AuthResult.AwaitingEmailConfirmation -> Unit // not reachable from sign-in
            }
            _authLoading.value = false
        }
    }

    fun signUp() {
        val name = _profileName.value.trim()
        val email = _profileEmail.value.trim()
        val password = _profilePassword.value
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _authError.value = "Veuillez remplir tous les champs demandés"
            return
        }
        if (!_agreeTerms.value) {
            _authError.value = "Vous devez approuver le partage de vos données"
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            when (val result = authRepository.signUp(email, password)) {
                is AuthResult.SignedIn -> {
                    _authError.value = null
                    _navigationEvent.value = Routes.NO_DEVICE
                }
                is AuthResult.AwaitingEmailConfirmation -> {
                    _authError.value = null
                    _emailOtpCode.value = ""
                    _navigationEvent.value = Routes.VERIFY_EMAIL_OTP
                }
                is AuthResult.Error -> _authError.value = result.message
            }
            _authLoading.value = false
        }
    }

    fun updateEmailOtpCode(v: String) {
        _emailOtpCode.value = v.filter { it.isDigit() }.take(6)
        _authError.value = null
    }

    fun verifyEmailOtp() {
        val code = _emailOtpCode.value.trim()
        if (code.isBlank()) {
            _authError.value = "Veuillez entrer le code reçu par email"
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            when (val result = authRepository.verifyEmailOtp(_profileEmail.value.trim(), code)) {
                is AuthResult.SignedIn -> {
                    _authError.value = null
                    _navigationEvent.value = Routes.NO_DEVICE
                }
                is AuthResult.Error -> _authError.value = result.message
                is AuthResult.AwaitingEmailConfirmation -> Unit // not reachable from verify
            }
            _authLoading.value = false
        }
    }

    fun resendEmailOtp() {
        viewModelScope.launch {
            _authLoading.value = true
            when (val result = authRepository.resendSignupOtp(_profileEmail.value.trim())) {
                is AuthResult.AwaitingEmailConfirmation -> {
                    _authError.value = null
                    _otpResendConfirmation.value = "Un nouveau code a été envoyé."
                }
                is AuthResult.Error -> _authError.value = result.message
                is AuthResult.SignedIn -> Unit // not reachable from resend
            }
            _authLoading.value = false
        }
    }

    // Calls create_patient_and_claim — the only way patients/devices rows
    // get created client-side. Called automatically once WiFi credentials
    // are sent successfully (see sendWifiCredentialsLocal below), using
    // the pairing code captured from the device's own /api/status during
    // connectToDevice() — the caregiver never types it.
    private suspend fun claimDevice(code: String) {
        _authLoading.value = true
        when (val result = onboardingRepository.claimDevice(_profileName.value.trim(), TimeZone.getDefault().id, code)) {
            is ClaimDeviceResult.Success -> {
                _authError.value = null
                // Warms hub.patient/device/realSchedules now rather than
                // waiting for MAIN_HUB — PatientProfileScreen and
                // ScheduleSetupScreen need them (updateScheduleTime()
                // no-ops without a loaded device).
                _claimedDeviceUid.value = _capturedDeviceUid.value ?: try {
                    patientDeviceRepository.getDeviceForPatient(result.result.patientId)?.deviceUid
                } catch (e: Exception) {
                    null
                }
                hub.ensureLoaded()
                _navigationEvent.value = Routes.PAIRED_SUCCESS
            }
            is ClaimDeviceResult.Error -> _authError.value = result.message
        }
        _authLoading.value = false
    }

    // Patient Profile onboarding screen — first real save of patient
    // details, replacing the caregiver-name placeholder claimDevice() used.
    fun savePatientProfile(fullName: String, age: Int?, dateOfBirth: String?, conditions: String?) {
        val currentPatient = hub.patient.value
        val trimmedName = fullName.trim()
        if (trimmedName.isBlank()) {
            _authError.value = "Veuillez entrer le nom complet du patient"
            return
        }
        if (currentPatient == null) {
            _authError.value = "Profil patient introuvable. Veuillez réessayer."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            try {
                val trimmedConditions = conditions?.trim()?.ifBlank { null }
                patientDeviceRepository.updateProfile(currentPatient.id, trimmedName, age, dateOfBirth, trimmedConditions)
                hub.setPatientLocally(
                    currentPatient.copy(
                        fullName = trimmedName,
                        age = age,
                        dateOfBirth = dateOfBirth,
                        conditions = trimmedConditions,
                        profileCompleted = true,
                    )
                )
                _authError.value = null
                _navigationEvent.value = Routes.SCHEDULE_SETUP
            } catch (e: Exception) {
                _authError.value = e.message ?: "Échec de l'enregistrement du profil"
            }
            _authLoading.value = false
        }
    }

    // Schedule Setup onboarding screen, final step — the doses themselves
    // are saved live per-edit via updateScheduleTime() below; this just
    // marks onboarding done and enters the hub.
    fun completeOnboarding() {
        val deviceId = hub.device.value?.id
        if (deviceId == null) {
            _authError.value = "Appareil introuvable. Veuillez réessayer."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            try {
                patientDeviceRepository.markOnboardingComplete(deviceId)
                hub.markOnboardingCompletedLocally()
                _authError.value = null
                _navigationEvent.value = Routes.MAIN_HUB
            } catch (e: Exception) {
                _authError.value = e.message ?: "Échec de la finalisation de la configuration"
            }
            _authLoading.value = false
        }
    }

    // Same repository call ScheduleHub uses — Schedule Setup needs it too,
    // to save each dose time as the caregiver picks it.
    fun updateScheduleTime(dose: DoseType, hour: Int, minute: Int) {
        val deviceId = hub.device.value?.id ?: return
        viewModelScope.launch {
            val timeOfDay = DoseTimeFormatter.toPostgres(hour, minute)
            try {
                scheduleRepository.updateTime(deviceId, dose, timeOfDay)
            } catch (e: Exception) {
                _authError.value = e.message ?: "Échec de la mise à jour de l'horaire"
                return@launch
            }
            val payload = buildJsonObject { put(dose.name.lowercase(), timeOfDay) }
            val result = commandsRepository.publish(deviceId, "update_schedule", payload)
            if (result is CommandResult.Error) _authError.value = result.message
        }
    }

    // Connects directly to the PillPal device's own setup hotspot
    // (DeviceWifiConnector — WifiNetworkSpecifier, fixed SSID, no scanning:
    // every unit broadcasts the same network name) and reads its
    // device_uid/pairing_code from /api/status while still connected. This
    // has to happen here, before sendWifiCredentialsLocal(): once home WiFi
    // credentials are sent the device reboots and the hotspot — and this
    // connection — goes away.
    fun connectToDevice() {
        _deviceConnecting.value = true
        _authError.value = null
        viewModelScope.launch {
            when (val result = deviceWifiConnector.connect()) {
                is DeviceConnectResult.Success -> {
                    deviceNetwork = result.network
                    try {
                        val status = localDeviceApi.getStatus(result.network)
                        if (status.pairingCode.isBlank()) {
                            _authError.value = "Cet appareil n'est pas encore provisionné."
                            deviceWifiConnector.release()
                            deviceNetwork = null
                        } else {
                            _capturedDeviceUid.value = status.deviceUid.ifBlank { null }
                            _capturedPairingCode.value = status.pairingCode
                            _navigationEvent.value = Routes.CONNECT_WIFI
                        }
                    } catch (e: Exception) {
                        _authError.value = "Impossible de lire l'état de l'appareil : ${e.message}"
                        deviceWifiConnector.release()
                        deviceNetwork = null
                    }
                }
                is DeviceConnectResult.Error -> _authError.value = result.message
            }
            _deviceConnecting.value = false
        }
    }

    fun selectSSID(ssid: String) {
        _selectedSSID.value = ssid
    }

    fun updateWifiPassword(p: String) {
        _wifiPassword.value = p
    }

    // Sends the home WiFi credentials to the device over the hotspot
    // connection captured by connectToDevice(), then — on success — claims
    // it directly using the pairing code read in that same step.
    fun sendWifiCredentialsLocal(ssid: String, password: String) {
        val network = deviceNetwork
        val code = _capturedPairingCode.value
        if (network == null || code == null) {
            _authError.value = "Connexion à l'appareil perdue. Veuillez recommencer."
            return
        }
        if (ssid.isBlank()) {
            _authError.value = "Veuillez entrer le nom du réseau WiFi"
            return
        }
        _wifiSending.value = true
        _authError.value = null
        viewModelScope.launch {
            val sendResult = runCatching { localDeviceApi.sendWifiCredentials(network, ssid, password) }
            deviceWifiConnector.release()
            deviceNetwork = null

            if (sendResult.isFailure) {
                _authError.value = "Échec de l'envoi des identifiants WiFi : ${sendResult.exceptionOrNull()?.message}"
                _wifiSending.value = false
                return@launch
            }

            _wifiSending.value = false
            claimDevice(code)
        }
    }

    override fun onCleared() {
        // Safety net if the caregiver abandons the flow mid-connection —
        // otherwise the OS keeps the hotspot network request open.
        deviceWifiConnector.release()
    }
}

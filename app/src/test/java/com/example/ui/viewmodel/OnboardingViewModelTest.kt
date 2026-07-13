package com.example.ui.viewmodel

import android.net.Network
import com.example.MainDispatcherRule
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthResult
import com.example.data.local.DeviceConnectResult
import com.example.data.local.DeviceWifiConnector
import com.example.data.local.LocalDeviceApi
import com.example.data.local.LocalStatusResponse
import com.example.data.remote.ClaimDeviceResult
import com.example.data.remote.CommandResult
import com.example.data.remote.CommandsRepository
import com.example.data.remote.OnboardingRepository
import com.example.data.remote.PatientDeviceRepository
import com.example.data.remote.ScheduleRepository
import com.example.data.remote.dto.ClaimResultDto
import com.example.data.remote.dto.DeviceDto
import com.example.data.remote.dto.DoseType
import com.example.data.remote.dto.PatientDto
import com.example.data.session.HubSessionStore
import com.example.navigation.Routes
import io.github.jan.supabase.auth.status.SessionStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = mockk<AuthRepository>()
    private val onboardingRepository = mockk<OnboardingRepository>()
    private val patientDeviceRepository = mockk<PatientDeviceRepository>()
    private val scheduleRepository = mockk<ScheduleRepository>()
    private val commandsRepository = mockk<CommandsRepository>()
    private val hub = mockk<HubSessionStore>()
    private val deviceWifiConnector = mockk<DeviceWifiConnector>()
    private val localDeviceApi = mockk<LocalDeviceApi>()

    private val patientFlow = MutableStateFlow<PatientDto?>(null)
    private val deviceFlow = MutableStateFlow<DeviceDto?>(null)

    @Before
    fun setUp() {
        every { hub.patient } returns patientFlow
        every { hub.device } returns deviceFlow
        every { hub.realSchedules } returns MutableStateFlow(emptyList())
        every { hub.ensureLoaded() } just Runs
        every { hub.setPatientLocally(any()) } just Runs
        every { hub.markOnboardingCompletedLocally() } just Runs
        every { deviceWifiConnector.release() } just Runs
    }

    private fun viewModel() = OnboardingViewModel(
        authRepository,
        onboardingRepository,
        patientDeviceRepository,
        scheduleRepository,
        commandsRepository,
        hub,
        deviceWifiConnector,
        localDeviceApi,
    )

    private fun fakePatient(profileCompleted: Boolean = true) = PatientDto(
        id = "patient-1",
        fullName = "Jeanne Dupont",
        timezone = "Europe/Paris",
        profileCompleted = profileCompleted,
        createdAt = "2026-01-01T00:00:00Z",
    )

    private fun fakeDevice(onboardingCompleted: Boolean = true) = DeviceDto(
        id = "device-1",
        deviceUid = "pillpal-abc123",
        onboardingCompleted = onboardingCompleted,
        createdAt = "2026-01-01T00:00:00Z",
    )

    // ── resolveStartDestination() ────────────────────────────────

    @Test
    fun `resolveStartDestination returns SIGN_IN when not authenticated`() = runTest(mainDispatcherRule.testDispatcher) {
        every { authRepository.sessionStatus } returns MutableStateFlow(mockk<SessionStatus.NotAuthenticated>(relaxed = true))

        val destination = viewModel().resolveStartDestination()

        assertEquals(Routes.SIGN_IN, destination)
    }

    @Test
    fun `resolveStartDestination returns NO_DEVICE when authenticated with no patient`() = runTest(mainDispatcherRule.testDispatcher) {
        every { authRepository.sessionStatus } returns MutableStateFlow(mockk<SessionStatus.Authenticated>(relaxed = true))
        coEvery { patientDeviceRepository.getPatient() } returns null

        val destination = viewModel().resolveStartDestination()

        assertEquals(Routes.NO_DEVICE, destination)
    }

    @Test
    fun `resolveStartDestination falls forward to MAIN_HUB when patient lookup fails`() = runTest(mainDispatcherRule.testDispatcher) {
        every { authRepository.sessionStatus } returns MutableStateFlow(mockk<SessionStatus.Authenticated>(relaxed = true))
        coEvery { patientDeviceRepository.getPatient() } throws RuntimeException("network hiccup")

        val destination = viewModel().resolveStartDestination()

        assertEquals(Routes.MAIN_HUB, destination)
        every { hub.ensureLoaded() }
    }

    @Test
    fun `resolveStartDestination returns PATIENT_PROFILE when profile incomplete`() = runTest(mainDispatcherRule.testDispatcher) {
        every { authRepository.sessionStatus } returns MutableStateFlow(mockk<SessionStatus.Authenticated>(relaxed = true))
        coEvery { patientDeviceRepository.getPatient() } returns fakePatient(profileCompleted = false)

        val destination = viewModel().resolveStartDestination()

        assertEquals(Routes.PATIENT_PROFILE, destination)
    }

    @Test
    fun `resolveStartDestination returns SCHEDULE_SETUP when device onboarding incomplete`() = runTest(mainDispatcherRule.testDispatcher) {
        every { authRepository.sessionStatus } returns MutableStateFlow(mockk<SessionStatus.Authenticated>(relaxed = true))
        coEvery { patientDeviceRepository.getPatient() } returns fakePatient(profileCompleted = true)
        coEvery { patientDeviceRepository.getDeviceForPatient("patient-1") } returns fakeDevice(onboardingCompleted = false)

        val destination = viewModel().resolveStartDestination()

        assertEquals(Routes.SCHEDULE_SETUP, destination)
    }

    @Test
    fun `resolveStartDestination returns MAIN_HUB when fully onboarded`() = runTest(mainDispatcherRule.testDispatcher) {
        every { authRepository.sessionStatus } returns MutableStateFlow(mockk<SessionStatus.Authenticated>(relaxed = true))
        coEvery { patientDeviceRepository.getPatient() } returns fakePatient(profileCompleted = true)
        coEvery { patientDeviceRepository.getDeviceForPatient("patient-1") } returns fakeDevice(onboardingCompleted = true)

        val destination = viewModel().resolveStartDestination()

        assertEquals(Routes.MAIN_HUB, destination)
    }

    @Test
    fun `resolveStartDestination returns MAIN_HUB when profile complete but device missing`() = runTest(mainDispatcherRule.testDispatcher) {
        every { authRepository.sessionStatus } returns MutableStateFlow(mockk<SessionStatus.Authenticated>(relaxed = true))
        coEvery { patientDeviceRepository.getPatient() } returns fakePatient(profileCompleted = true)
        coEvery { patientDeviceRepository.getDeviceForPatient("patient-1") } returns null

        val destination = viewModel().resolveStartDestination()

        assertEquals(Routes.MAIN_HUB, destination)
    }

    // ── signIn() ──────────────────────────────────────────────────

    @Test
    fun `signIn with blank fields sets error without calling repository`() {
        val vm = viewModel()
        vm.updateSignInEmail("")
        vm.updateSignInPassword("")

        vm.signIn()

        assertEquals("Veuillez entrer l'adresse email et le mot de passe", vm.authError.value)
        coVerify(exactly = 0) { authRepository.signIn(any(), any()) }
    }

    @Test
    fun `signIn success navigates via routeAfterAuthentication`() = runTest(mainDispatcherRule.testDispatcher) {
        every { authRepository.sessionStatus } returns MutableStateFlow(mockk<SessionStatus.Authenticated>(relaxed = true))
        coEvery { authRepository.signIn("user@example.com", "hunter2") } returns AuthResult.SignedIn
        coEvery { patientDeviceRepository.getPatient() } returns null

        val vm = viewModel()
        vm.updateSignInEmail("user@example.com")
        vm.updateSignInPassword("hunter2")
        vm.signIn()

        assertEquals(Routes.NO_DEVICE, vm.navigationEvent.value)
        assertNull(vm.authError.value)
    }

    @Test
    fun `signIn error surfaces the message`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.signIn(any(), any()) } returns AuthResult.Error("Adresse email ou mot de passe incorrect")

        val vm = viewModel()
        vm.updateSignInEmail("user@example.com")
        vm.updateSignInPassword("wrong")
        vm.signIn()

        assertEquals("Adresse email ou mot de passe incorrect", vm.authError.value)
        assertNull(vm.navigationEvent.value)
    }

    // ── signUp() ──────────────────────────────────────────────────

    @Test
    fun `signUp with blank fields sets error without calling repository`() {
        val vm = viewModel()
        vm.updateProfileName("")
        vm.signUp()

        assertEquals("Veuillez remplir tous les champs demandés", vm.authError.value)
        coVerify(exactly = 0) { authRepository.signUp(any(), any()) }
    }

    @Test
    fun `signUp without agreeing to terms sets error`() {
        val vm = viewModel()
        vm.updateProfileName("Jeanne Dupont")
        vm.updateProfileEmail("jeanne@example.com")
        vm.updateProfilePassword("hunter2")
        vm.updateAgreeTerms(false)

        vm.signUp()

        assertEquals("Vous devez approuver le partage de vos données", vm.authError.value)
        coVerify(exactly = 0) { authRepository.signUp(any(), any()) }
    }

    @Test
    fun `signUp awaiting confirmation navigates to OTP screen and resets code`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.signUp(any(), any()) } returns AuthResult.AwaitingEmailConfirmation

        val vm = viewModel()
        vm.updateProfileName("Jeanne Dupont")
        vm.updateProfileEmail("jeanne@example.com")
        vm.updateProfilePassword("hunter2")
        vm.updateAgreeTerms(true)
        vm.updateEmailOtpCode("123456")
        vm.signUp()

        assertEquals(Routes.VERIFY_EMAIL_OTP, vm.navigationEvent.value)
        assertEquals("", vm.emailOtpCode.value)
    }

    @Test
    fun `signUp immediate sign-in navigates to NO_DEVICE`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.signUp(any(), any()) } returns AuthResult.SignedIn

        val vm = viewModel()
        vm.updateProfileName("Jeanne Dupont")
        vm.updateProfileEmail("jeanne@example.com")
        vm.updateProfilePassword("hunter2")
        vm.updateAgreeTerms(true)
        vm.signUp()

        assertEquals(Routes.NO_DEVICE, vm.navigationEvent.value)
    }

    // ── verifyEmailOtp() / resendEmailOtp() ──────────────────────

    @Test
    fun `verifyEmailOtp with blank code sets error`() {
        val vm = viewModel()
        vm.verifyEmailOtp()

        assertEquals("Veuillez entrer le code reçu par email", vm.authError.value)
        coVerify(exactly = 0) { authRepository.verifyEmailOtp(any(), any()) }
    }

    @Test
    fun `verifyEmailOtp success navigates to NO_DEVICE`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.verifyEmailOtp(any(), any()) } returns AuthResult.SignedIn

        val vm = viewModel()
        vm.updateProfileEmail("jeanne@example.com")
        vm.updateEmailOtpCode("123456")
        vm.verifyEmailOtp()

        assertEquals(Routes.NO_DEVICE, vm.navigationEvent.value)
    }

    @Test
    fun `resendEmailOtp sets confirmation message on success`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.resendSignupOtp(any()) } returns AuthResult.AwaitingEmailConfirmation

        val vm = viewModel()
        vm.resendEmailOtp()

        assertEquals("Un nouveau code a été envoyé.", vm.otpResendConfirmation.value)
    }

    // ── connectToDevice() ─────────────────────────────────────────

    @Test
    fun `connectToDevice surfaces connector error`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { deviceWifiConnector.connect() } returns DeviceConnectResult.Error("Impossible de se connecter à l'appareil PillPal.")

        val vm = viewModel()
        vm.connectToDevice()

        assertEquals("Impossible de se connecter à l'appareil PillPal.", vm.authError.value)
        assertEquals(false, vm.deviceConnecting.value)
    }

    @Test
    fun `connectToDevice rejects an unprovisioned device`() = runTest(mainDispatcherRule.testDispatcher) {
        val network = mockk<Network>()
        coEvery { deviceWifiConnector.connect() } returns DeviceConnectResult.Success(network)
        coEvery { localDeviceApi.getStatus(network) } returns LocalStatusResponse(deviceUid = "", pairingCode = "")

        val vm = viewModel()
        vm.connectToDevice()

        assertEquals("Cet appareil n'est pas encore provisionné.", vm.authError.value)
        coVerify { deviceWifiConnector.release() }
    }

    @Test
    fun `connectToDevice captures identity and navigates to CONNECT_WIFI`() = runTest(mainDispatcherRule.testDispatcher) {
        val network = mockk<Network>()
        coEvery { deviceWifiConnector.connect() } returns DeviceConnectResult.Success(network)
        coEvery { localDeviceApi.getStatus(network) } returns LocalStatusResponse(deviceUid = "pillpal-xyz", pairingCode = "ABC123")

        val vm = viewModel()
        vm.connectToDevice()

        assertEquals(Routes.CONNECT_WIFI, vm.navigationEvent.value)
        assertNull(vm.authError.value)
    }

    @Test
    fun `connectToDevice handles getStatus failure`() = runTest(mainDispatcherRule.testDispatcher) {
        val network = mockk<Network>()
        coEvery { deviceWifiConnector.connect() } returns DeviceConnectResult.Success(network)
        coEvery { localDeviceApi.getStatus(network) } throws RuntimeException("timeout")

        val vm = viewModel()
        vm.connectToDevice()

        assertEquals("Impossible de lire l'état de l'appareil : timeout", vm.authError.value)
        coVerify { deviceWifiConnector.release() }
    }

    // ── sendWifiCredentialsLocal() → claimDevice() ───────────────

    @Test
    fun `sendWifiCredentialsLocal without a captured connection sets error`() {
        val vm = viewModel()
        vm.sendWifiCredentialsLocal("HomeWifi", "password")

        assertEquals("Connexion à l'appareil perdue. Veuillez recommencer.", vm.authError.value)
        coVerify(exactly = 0) { localDeviceApi.sendWifiCredentials(any(), any(), any()) }
    }

    @Test
    fun `sendWifiCredentialsLocal success claims the device and navigates to PAIRED_SUCCESS`() = runTest(mainDispatcherRule.testDispatcher) {
        val network = mockk<Network>()
        coEvery { deviceWifiConnector.connect() } returns DeviceConnectResult.Success(network)
        coEvery { localDeviceApi.getStatus(network) } returns LocalStatusResponse(deviceUid = "pillpal-xyz", pairingCode = "ABC123")
        coEvery { localDeviceApi.sendWifiCredentials(network, "HomeWifi", "password") } just Runs
        coEvery { onboardingRepository.claimDevice("", any(), "ABC123") } returns
            ClaimDeviceResult.Success(ClaimResultDto(patientId = "patient-1", deviceId = "device-1"))

        val vm = viewModel()
        vm.connectToDevice()
        vm.sendWifiCredentialsLocal("HomeWifi", "password")

        assertEquals(Routes.PAIRED_SUCCESS, vm.navigationEvent.value)
        assertEquals("pillpal-xyz", vm.claimedDeviceUid.value)
        coVerify { deviceWifiConnector.release() }
    }

    @Test
    fun `sendWifiCredentialsLocal failure surfaces error and never claims`() = runTest(mainDispatcherRule.testDispatcher) {
        val network = mockk<Network>()
        coEvery { deviceWifiConnector.connect() } returns DeviceConnectResult.Success(network)
        coEvery { localDeviceApi.getStatus(network) } returns LocalStatusResponse(deviceUid = "pillpal-xyz", pairingCode = "ABC123")
        coEvery { localDeviceApi.sendWifiCredentials(network, "HomeWifi", "password") } throws RuntimeException("HTTP 500")

        val vm = viewModel()
        vm.connectToDevice()
        vm.sendWifiCredentialsLocal("HomeWifi", "password")

        assertEquals("Échec de l'envoi des identifiants WiFi : HTTP 500", vm.authError.value)
        assertEquals(false, vm.wifiSending.value)
        coVerify(exactly = 0) { onboardingRepository.claimDevice(any(), any(), any()) }
    }

    @Test
    fun `claimDevice error surfaces the message`() = runTest(mainDispatcherRule.testDispatcher) {
        val network = mockk<Network>()
        coEvery { deviceWifiConnector.connect() } returns DeviceConnectResult.Success(network)
        coEvery { localDeviceApi.getStatus(network) } returns LocalStatusResponse(deviceUid = "pillpal-xyz", pairingCode = "ABC123")
        coEvery { localDeviceApi.sendWifiCredentials(network, "HomeWifi", "password") } just Runs
        coEvery { onboardingRepository.claimDevice(any(), any(), "ABC123") } returns
            ClaimDeviceResult.Error("Code de jumelage invalide ou déjà utilisé")

        val vm = viewModel()
        vm.connectToDevice()
        // connectToDevice() already set navigationEvent to CONNECT_WIFI on
        // its own success — a later claim failure has no reason to touch
        // it, so it's expected to still read CONNECT_WIFI here, not null.
        vm.sendWifiCredentialsLocal("HomeWifi", "password")

        assertEquals("Code de jumelage invalide ou déjà utilisé", vm.authError.value)
        assertEquals(Routes.CONNECT_WIFI, vm.navigationEvent.value)
    }

    // ── savePatientProfile() ──────────────────────────────────────

    @Test
    fun `savePatientProfile with blank name sets error`() {
        val vm = viewModel()
        vm.savePatientProfile("  ", null, null, null)

        assertEquals("Veuillez entrer le nom complet du patient", vm.authError.value)
    }

    @Test
    fun `savePatientProfile with no current patient sets error`() {
        val vm = viewModel()
        vm.savePatientProfile("Jeanne Dupont", null, null, null)

        assertEquals("Profil patient introuvable. Veuillez réessayer.", vm.authError.value)
    }

    @Test
    fun `savePatientProfile success updates hub and navigates to SCHEDULE_SETUP`() = runTest(mainDispatcherRule.testDispatcher) {
        patientFlow.value = fakePatient(profileCompleted = false)
        coEvery { patientDeviceRepository.updateProfile(any(), any(), any(), any(), any()) } just Runs

        val vm = viewModel()
        vm.savePatientProfile("Jeanne Dupont", 72, "1954-01-01", "Diabète")

        assertEquals(Routes.SCHEDULE_SETUP, vm.navigationEvent.value)
        coVerify { hub.setPatientLocally(match { it.fullName == "Jeanne Dupont" && it.profileCompleted }) }
    }

    // ── completeOnboarding() ──────────────────────────────────────

    @Test
    fun `completeOnboarding with no device sets error`() {
        val vm = viewModel()
        vm.completeOnboarding()

        assertEquals("Appareil introuvable. Veuillez réessayer.", vm.authError.value)
    }

    @Test
    fun `completeOnboarding success navigates to MAIN_HUB`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        coEvery { patientDeviceRepository.markOnboardingComplete("device-1") } just Runs

        val vm = viewModel()
        vm.completeOnboarding()

        assertEquals(Routes.MAIN_HUB, vm.navigationEvent.value)
        coVerify { hub.markOnboardingCompletedLocally() }
    }

    // ── updateScheduleTime() ──────────────────────────────────────

    @Test
    fun `updateScheduleTime does nothing without a device`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.updateScheduleTime(DoseType.MORNING, 8, 0)

        coVerify(exactly = 0) { scheduleRepository.updateTime(any(), any(), any()) }
    }

    @Test
    fun `updateScheduleTime success publishes a command`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        coEvery { scheduleRepository.updateTime("device-1", DoseType.MORNING, "08:00:00") } just Runs
        coEvery { commandsRepository.publish("device-1", "update_schedule", any()) } returns CommandResult.Success

        val vm = viewModel()
        vm.updateScheduleTime(DoseType.MORNING, 8, 0)

        coVerify { commandsRepository.publish("device-1", "update_schedule", any()) }
        assertNull(vm.authError.value)
    }

    @Test
    fun `updateScheduleTime surfaces repository failure without publishing`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        coEvery { scheduleRepository.updateTime(any(), any(), any()) } throws RuntimeException("offline")

        val vm = viewModel()
        vm.updateScheduleTime(DoseType.MORNING, 8, 0)

        assertEquals("offline", vm.authError.value)
        coVerify(exactly = 0) { commandsRepository.publish(any(), any(), any()) }
    }
}

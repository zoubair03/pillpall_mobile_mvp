package com.example.ui.viewmodel

import com.example.MainDispatcherRule
import com.example.data.auth.AuthRepository
import com.example.data.remote.CommandResult
import com.example.data.remote.CommandsRepository
import com.example.data.remote.PatientDeviceRepository
import com.example.data.remote.dto.DeviceDto
import com.example.data.remote.dto.DoseType
import com.example.data.remote.dto.PatientDto
import com.example.data.session.HubSessionStore
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

class ControlsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = mockk<AuthRepository>()
    private val patientDeviceRepository = mockk<PatientDeviceRepository>()
    private val commandsRepository = mockk<CommandsRepository>()
    private val hub = mockk<HubSessionStore>()
    private val patientFlow = MutableStateFlow<PatientDto?>(null)
    private val deviceFlow = MutableStateFlow<DeviceDto?>(null)

    @Before
    fun setUp() {
        every { hub.patient } returns patientFlow
        every { hub.device } returns deviceFlow
        every { hub.isDeviceOnline } returns MutableStateFlow(false)
        every { hub.devicePowerStatus } returns MutableStateFlow("Statut inconnu")
        every { hub.deviceBatteryLevel } returns MutableStateFlow(0)
        every { hub.setPatientLocally(any()) } just Runs
        every { hub.clear() } just Runs
    }

    private fun viewModel() = ControlsViewModel(authRepository, patientDeviceRepository, commandsRepository, hub)

    private fun fakeDevice() = DeviceDto(
        id = "device-1",
        deviceUid = "pillpal-abc123",
        createdAt = "2026-01-01T00:00:00Z",
    )

    private fun fakePatient(fullName: String = "Jeanne Dupont") = PatientDto(
        id = "patient-1",
        fullName = fullName,
        timezone = "Europe/Paris",
        createdAt = "2026-01-01T00:00:00Z",
    )

    @Test
    fun `forceDispense does nothing without a device`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel().forceDispense(DoseType.MORNING)

        coVerify(exactly = 0) { commandsRepository.publish(any(), any(), any()) }
    }

    @Test
    fun `forceDispense success clears any error`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        coEvery { commandsRepository.publish("device-1", "force_dispense", any()) } returns CommandResult.Success

        val vm = viewModel()
        vm.forceDispense(DoseType.MORNING)

        coVerify { commandsRepository.publish("device-1", "force_dispense", any()) }
        assertNull(vm.actionError.value)
    }

    @Test
    fun `forceDispense surfaces a command error`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        coEvery { commandsRepository.publish(any(), any(), any()) } returns CommandResult.Error("L'appareil n'a pas pu être contacté")

        val vm = viewModel()
        vm.forceDispense(DoseType.MORNING)

        assertEquals("L'appareil n'a pas pu être contacté", vm.actionError.value)
    }

    @Test
    fun `runCalibration publishes calibrate`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        coEvery { commandsRepository.publish("device-1", "calibrate") } returns CommandResult.Success

        viewModel().runCalibration()

        coVerify { commandsRepository.publish("device-1", "calibrate") }
    }

    @Test
    fun `runRestart publishes restart`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        coEvery { commandsRepository.publish("device-1", "restart") } returns CommandResult.Success

        viewModel().runRestart()

        coVerify { commandsRepository.publish("device-1", "restart") }
    }

    @Test
    fun `renamePatient does nothing with a blank name`() = runTest(mainDispatcherRule.testDispatcher) {
        patientFlow.value = fakePatient()

        viewModel().renamePatient("   ")

        coVerify(exactly = 0) { patientDeviceRepository.updateFullName(any(), any()) }
    }

    @Test
    fun `renamePatient does nothing when the name is unchanged`() = runTest(mainDispatcherRule.testDispatcher) {
        patientFlow.value = fakePatient("Jeanne Dupont")

        viewModel().renamePatient("Jeanne Dupont")

        coVerify(exactly = 0) { patientDeviceRepository.updateFullName(any(), any()) }
    }

    @Test
    fun `renamePatient does nothing without a current patient`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel().renamePatient("New Name")

        coVerify(exactly = 0) { patientDeviceRepository.updateFullName(any(), any()) }
    }

    @Test
    fun `renamePatient success updates the repository and hub`() = runTest(mainDispatcherRule.testDispatcher) {
        patientFlow.value = fakePatient("Jeanne Dupont")
        coEvery { patientDeviceRepository.updateFullName("patient-1", "Jeanne Martin") } just Runs

        viewModel().renamePatient("Jeanne Martin")

        coVerify { patientDeviceRepository.updateFullName("patient-1", "Jeanne Martin") }
        coVerify { hub.setPatientLocally(match { it.fullName == "Jeanne Martin" }) }
    }

    @Test
    fun `renamePatient surfaces a repository failure`() = runTest(mainDispatcherRule.testDispatcher) {
        patientFlow.value = fakePatient("Jeanne Dupont")
        coEvery { patientDeviceRepository.updateFullName(any(), any()) } throws RuntimeException("offline")

        val vm = viewModel()
        vm.renamePatient("Jeanne Martin")

        assertEquals("offline", vm.actionError.value)
    }

    @Test
    fun `logout signs out, clears the hub, and flips loggedOut`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.signOut() } just Runs

        val vm = viewModel()
        vm.logout()

        coVerify { authRepository.signOut() }
        coVerify { hub.clear() }
        assertEquals(true, vm.loggedOut.value)
    }
}

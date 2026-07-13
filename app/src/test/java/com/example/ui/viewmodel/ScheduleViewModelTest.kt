package com.example.ui.viewmodel

import com.example.MainDispatcherRule
import com.example.data.remote.CommandResult
import com.example.data.remote.CommandsRepository
import com.example.data.remote.ScheduleRepository
import com.example.data.remote.dto.DeviceDto
import com.example.data.remote.dto.DoseType
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

class ScheduleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hub = mockk<HubSessionStore>()
    private val scheduleRepository = mockk<ScheduleRepository>()
    private val commandsRepository = mockk<CommandsRepository>()
    private val deviceFlow = MutableStateFlow<DeviceDto?>(null)

    @Before
    fun setUp() {
        every { hub.realSchedules } returns MutableStateFlow(emptyList())
        every { hub.device } returns deviceFlow
    }

    private fun viewModel() = ScheduleViewModel(hub, scheduleRepository, commandsRepository)

    private fun fakeDevice() = DeviceDto(
        id = "device-1",
        deviceUid = "pillpal-abc123",
        createdAt = "2026-01-01T00:00:00Z",
    )

    @Test
    fun `setScheduleTab updates the selected tab`() {
        val vm = viewModel()
        vm.setScheduleTab("Plan de Recharge")

        assertEquals("Plan de Recharge", vm.scheduleTab.value)
    }

    @Test
    fun `refillDispenser resets pills remaining to 21`() {
        val vm = viewModel()
        vm.refillDispenser()

        assertEquals(21, vm.pillsRemaining.value)
    }

    @Test
    fun `updateScheduleTime does nothing without a device`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.updateScheduleTime(DoseType.MORNING, 8, 0)

        coVerify(exactly = 0) { scheduleRepository.updateTime(any(), any(), any()) }
    }

    @Test
    fun `updateScheduleTime success publishes update_schedule`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        coEvery { scheduleRepository.updateTime("device-1", DoseType.MORNING, "08:00:00") } just Runs
        coEvery { commandsRepository.publish("device-1", "update_schedule", any()) } returns CommandResult.Success

        val vm = viewModel()
        vm.updateScheduleTime(DoseType.MORNING, 8, 0)

        coVerify { commandsRepository.publish("device-1", "update_schedule", any()) }
        assertNull(vm.actionError.value)
    }

    @Test
    fun `updateScheduleTime repository failure skips the command publish`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        coEvery { scheduleRepository.updateTime(any(), any(), any()) } throws RuntimeException("offline")

        val vm = viewModel()
        vm.updateScheduleTime(DoseType.MORNING, 8, 0)

        assertEquals("offline", vm.actionError.value)
        coVerify(exactly = 0) { commandsRepository.publish(any(), any(), any()) }
    }

    @Test
    fun `updateScheduleTime surfaces a command error`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        coEvery { scheduleRepository.updateTime(any(), any(), any()) } just Runs
        coEvery { commandsRepository.publish(any(), any(), any()) } returns CommandResult.Error("L'appareil n'a pas pu être contacté")

        val vm = viewModel()
        vm.updateScheduleTime(DoseType.MORNING, 8, 0)

        assertEquals("L'appareil n'a pas pu être contacté", vm.actionError.value)
    }
}

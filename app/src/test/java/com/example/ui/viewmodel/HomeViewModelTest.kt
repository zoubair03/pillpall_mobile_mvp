package com.example.ui.viewmodel

import com.example.MainDispatcherRule
import com.example.data.remote.CommandResult
import com.example.data.remote.CommandsRepository
import com.example.data.remote.dto.DeviceDto
import com.example.data.remote.dto.DoseType
import com.example.data.session.HubSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hub = mockk<HubSessionStore>()
    private val commandsRepository = mockk<CommandsRepository>()
    private val deviceFlow = MutableStateFlow<DeviceDto?>(null)

    @Before
    fun setUp() {
        every { hub.patient } returns MutableStateFlow(null)
        every { hub.todayLabel } returns MutableStateFlow("")
        every { hub.weekDays } returns MutableStateFlow(emptyList())
        every { hub.countdownTimer } returns MutableStateFlow("--:--:--")
        every { hub.doseCardsFor(any()) } returns MutableStateFlow(emptyList())
        every { hub.device } returns deviceFlow
    }

    private fun viewModel() = HomeViewModel(hub, commandsRepository)

    private fun fakeDevice() = DeviceDto(
        id = "device-1",
        deviceUid = "pillpal-abc123",
        createdAt = "2026-01-01T00:00:00Z",
    )

    @Test
    fun `selectCalendarDay updates selectedDay immediately`() {
        val vm = viewModel()
        val yesterday = LocalDate.now().minusDays(1)

        vm.selectCalendarDay(yesterday)

        assertEquals(yesterday, vm.selectedDay.value)
    }

    @Test
    fun `isSelectedDayToday reflects whether the selected date is today`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.isSelectedDayToday.collect {} }

        assertEquals(true, vm.isSelectedDayToday.value)

        vm.selectCalendarDay(LocalDate.now().minusDays(1))
        assertEquals(false, vm.isSelectedDayToday.value)

        vm.selectCalendarDay(LocalDate.now())
        assertEquals(true, vm.isSelectedDayToday.value)
    }

    @Test
    fun `acknowledgeDose is a no-op for a non-today selection`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        val vm = viewModel()
        backgroundScope.launch { vm.isSelectedDayToday.collect {} }
        vm.selectCalendarDay(LocalDate.now().minusDays(1))

        vm.acknowledgeDose(DoseType.MORNING)

        coVerify(exactly = 0) { commandsRepository.publish(any(), any(), any()) }
    }

    @Test
    fun `acknowledgeDose is a no-op without a device`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.isSelectedDayToday.collect {} }

        vm.acknowledgeDose(DoseType.MORNING)

        coVerify(exactly = 0) { commandsRepository.publish(any(), any(), any()) }
    }

    @Test
    fun `acknowledgeDose publishes ack_dose for today`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        coEvery { commandsRepository.publish("device-1", "ack_dose", any()) } returns CommandResult.Success
        val vm = viewModel()
        backgroundScope.launch { vm.isSelectedDayToday.collect {} }

        vm.acknowledgeDose(DoseType.MORNING)

        coVerify { commandsRepository.publish("device-1", "ack_dose", any()) }
        assertNull(vm.actionError.value)
    }

    @Test
    fun `acknowledgeDose surfaces a command error`() = runTest(mainDispatcherRule.testDispatcher) {
        deviceFlow.value = fakeDevice()
        coEvery { commandsRepository.publish(any(), any(), any()) } returns CommandResult.Error("L'appareil n'a pas pu être contacté")
        val vm = viewModel()
        backgroundScope.launch { vm.isSelectedDayToday.collect {} }

        vm.acknowledgeDose(DoseType.MORNING)

        assertEquals("L'appareil n'a pas pu être contacté", vm.actionError.value)
    }
}

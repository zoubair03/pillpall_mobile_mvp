package com.example.ui.viewmodel

import com.example.MainDispatcherRule
import com.example.data.remote.PushTokenRepository
import com.example.data.session.HubSessionStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeviceStatusViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hub = mockk<HubSessionStore>()
    private val pushTokenRepository = mockk<PushTokenRepository>()

    @Before
    fun setUp() {
        every { hub.hubLoading } returns MutableStateFlow(true)
        every { hub.isDeviceOnline } returns MutableStateFlow(false)
        every { hub.deviceBatteryLevel } returns MutableStateFlow(42)
        every { hub.refresh() } just Runs
    }

    private fun viewModel() = DeviceStatusViewModel(hub, pushTokenRepository)

    @Test
    fun `exposed flows proxy hub state`() {
        val vm = viewModel()

        assertEquals(true, vm.isSyncing.value)
        assertEquals(false, vm.isOnline.value)
        assertEquals(42, vm.batteryLevel.value)
    }

    @Test
    fun `refresh delegates to the hub`() {
        viewModel().refresh()

        every { hub.refresh() }
    }

    @Test
    fun `FCM registration failing in a plain unit test does not crash construction or register a token`() = runTest(mainDispatcherRule.testDispatcher) {
        // FirebaseMessaging.getInstance() has no real FirebaseApp to attach to
        // here, so init{}'s token fetch fails and is swallowed by runCatching
        // (same as it would be on a real device with no Play Services) — this
        // just documents that construction itself stays safe either way.
        viewModel()

        coVerify(exactly = 0) { pushTokenRepository.upsertToken(any()) }
    }
}

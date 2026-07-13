package com.example.ui.viewmodel

import com.example.data.session.HubSessionStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HistoryViewModelTest {

    private val hub = mockk<HubSessionStore>()

    @Before
    fun setUp() {
        every { hub.doseEvents } returns MutableStateFlow(emptyList())
        every { hub.alerts } returns MutableStateFlow(emptyList())
    }

    @Test
    fun `default filter is Tout`() {
        val vm = HistoryViewModel(hub)

        assertEquals("Tout", vm.historyFilter.value)
    }

    @Test
    fun `setHistoryFilter updates the selected filter`() {
        val vm = HistoryViewModel(hub)

        vm.setHistoryFilter("Doses")

        assertEquals("Doses", vm.historyFilter.value)
    }
}

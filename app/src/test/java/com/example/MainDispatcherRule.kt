package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// Every ViewModel here launches work via viewModelScope, which needs
// Dispatchers.Main available — unconfined so queued coroutines (auth
// calls, repository writes) complete synchronously within the test body
// instead of requiring explicit advanceUntilIdle() calls everywhere.
//
// testDispatcher is public so it can be passed into runTest(testDispatcher)
// { ... } — without that, runTest builds its own separate TestScheduler,
// and a stateIn(viewModelScope, WhileSubscribed, ...) flow (viewModelScope
// runs on *this* dispatcher) never gets nudged by that other scheduler,
// so collectors started via runTest's backgroundScope silently never see
// updates.
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

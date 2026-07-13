package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.PushTokenRepository
import com.example.data.session.HubSessionStore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject

// Backs PillPalTopStatusBar, shown across all four hub tabs. HubSessionStore
// is a @Singleton, so this doesn't need any special backstack-entry scoping
// to "share" state with the tab ViewModels — they all read the same
// underlying store regardless of how many ViewModel instances wrap it.
@HiltViewModel
class DeviceStatusViewModel @Inject constructor(
    private val hub: HubSessionStore,
    private val pushTokenRepository: PushTokenRepository,
) : ViewModel() {
    val isSyncing: StateFlow<Boolean> = hub.hubLoading
    val isOnline: StateFlow<Boolean> = hub.isDeviceOnline
    val batteryLevel: StateFlow<Int> = hub.deviceBatteryLevel

    init {
        // PillPalMessagingService.onNewToken only fires on (re)issuance —
        // a caregiver signing in with an already-cached token from a prior
        // install would never hit that callback, so register the current
        // token here too, once per hub landing.
        viewModelScope.launch {
            val token = runCatching { fetchFcmToken() }.getOrNull() ?: return@launch
            runCatching { pushTokenRepository.upsertToken(token) }
        }
    }

    fun refresh() = hub.refresh()

    private suspend fun fetchFcmToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
            .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    }
}

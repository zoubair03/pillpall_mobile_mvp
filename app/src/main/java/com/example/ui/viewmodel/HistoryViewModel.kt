package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.session.HubSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    hub: HubSessionStore,
) : ViewModel() {
    val doseEvents = hub.doseEvents
    val alerts = hub.alerts

    // Filter chip configuration ("Tout" / "Doses" / "Statut Matériel")
    private val _historyFilter = MutableStateFlow("Tout")
    val historyFilter: StateFlow<String> = _historyFilter.asStateFlow()
    fun setHistoryFilter(filter: String) { _historyFilter.value = filter }
}

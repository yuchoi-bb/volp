package com.volp.travelbudget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.data.settings.VolpSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: AppSettings) : ViewModel() {

    val state: StateFlow<VolpSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VolpSettings())

    fun setOwnerName(value: String) {
        viewModelScope.launch { settings.setOwnerName(value) }
    }

    fun setCaptureEnabled(value: Boolean) {
        viewModelScope.launch { settings.setCaptureEnabled(value) }
    }

    fun setAutoAssignEnabled(value: Boolean) {
        viewModelScope.launch { settings.setAutoAssignEnabled(value) }
    }

    fun setBudgetAlertsEnabled(value: Boolean) {
        viewModelScope.launch { settings.setBudgetAlertsEnabled(value) }
    }

    fun setAutoBackupEnabled(value: Boolean) {
        viewModelScope.launch { settings.setAutoBackupEnabled(value) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

package com.volp.travelbudget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.data.settings.VolpSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    fun setRainAlertsEnabled(value: Boolean) {
        viewModelScope.launch { settings.setRainAlertsEnabled(value) }
    }

    fun setAutoBackupEnabled(value: Boolean) {
        viewModelScope.launch { settings.setAutoBackupEnabled(value) }
    }

    fun setPrepRemindersEnabled(value: Boolean) {
        viewModelScope.launch { settings.setPrepRemindersEnabled(value) }
    }

    /** 여권 만료일. 비우면 확인하지 않는다. */
    fun setPassportExpiry(value: LocalDate?) {
        viewModelScope.launch { settings.setPassportExpiry(value?.toString().orEmpty()) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

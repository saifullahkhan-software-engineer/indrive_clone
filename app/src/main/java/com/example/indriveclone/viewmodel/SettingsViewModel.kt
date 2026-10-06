package com.example.indriveclone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.indriveclone.BuildConfig
import com.example.indriveclone.InDriveApplication
import com.example.indriveclone.ServiceLocator
import com.example.indriveclone.data.model.AdminSettings
import com.example.indriveclone.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Admin settings (no login in the demo). Everything is written to DataStore, so the fare rules and the
 * optional ORS key survive a restart — and the key from local.properties is only used while the field
 * here is empty.
 */
class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AdminSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminSettings())

    /** True when `ORS_API_KEY` was found in local.properties (BuildConfig), i.e. a key is available. */
    val buildConfigKeyPresent: Boolean = BuildConfig.ORS_API_KEY.isNotBlank()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun updateBaseFare(value: Double) = update { it.copy(baseFare = value.coerceIn(0.0, MAX_MONEY)) }

    fun updatePerKmRate(value: Double) = update { it.copy(perKmRate = value.coerceIn(0.0, MAX_MONEY)) }

    fun updateMinimumFare(value: Double) = update { it.copy(minimumFare = value.coerceIn(0.0, MAX_MONEY)) }

    /** The multiplier must stay at 1x or above, otherwise the adjuster's ceiling would be below the suggestion. */
    fun updateMaxFareMultiplier(value: Double) =
        update { it.copy(maxFareMultiplier = value.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)) }

    fun updateOrsKey(value: String) = update { it.copy(orsApiKey = value.trim()) }

    fun clearOrsKey() {
        update { it.copy(orsApiKey = "") }
        _message.value = "ORS key cleared — the app falls back to the public OSRM server."
    }

    fun resetToDefaults() {
        update { AdminSettings() }
        _message.value = "Fare settings restored to defaults."
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun update(transform: (AdminSettings) -> AdminSettings) {
        viewModelScope.launch { settingsRepository.updateSettings(transform) }
    }

    companion object {
        private const val MIN_MULTIPLIER = 1.0
        private const val MAX_MULTIPLIER = 10.0
        private const val MAX_MONEY = 1_000_000.0

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as InDriveApplication
                SettingsViewModel(ServiceLocator.from(application).settingsRepository)
            }
        }
    }
}

package com.example.indriveclone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.indriveclone.InDriveApplication
import com.example.indriveclone.ServiceLocator
import com.example.indriveclone.data.model.UserRole
import com.example.indriveclone.data.repository.RideRepository
import com.example.indriveclone.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds the app-wide role selection so the first screen and the menu agree. */
class AppViewModel(
    private val settingsRepository: SettingsRepository,
    private val rideRepository: RideRepository,
) : ViewModel() {

    /** null until the user has picked a role once (or restored from DataStore). */
    val role: StateFlow<UserRole?> = settingsRepository.role
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectRole(role: UserRole) {
        viewModelScope.launch { settingsRepository.setRole(role) }
    }

    /** Demo helper behind the overflow menu; also wipes rides so screens start clean. */
    fun resetDemoData() {
        viewModelScope.launch { rideRepository.clear() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as InDriveApplication
                val services = ServiceLocator.from(application)
                AppViewModel(services.settingsRepository, services.rideRepository)
            }
        }
    }
}

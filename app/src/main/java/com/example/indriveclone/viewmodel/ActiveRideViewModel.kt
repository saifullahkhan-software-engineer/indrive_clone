package com.example.indriveclone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.indriveclone.InDriveApplication
import com.example.indriveclone.ServiceLocator
import com.example.indriveclone.data.model.DriverOffer
import com.example.indriveclone.data.model.OfferStatus
import com.example.indriveclone.data.model.RideRequest
import com.example.indriveclone.data.repository.RideRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveRideUiState(
    val ride: RideRequest? = null,
    val acceptedOffer: DriverOffer? = null,
    /** True until the repository has produced its first value — "no ride yet" is not "ride gone". */
    val loading: Boolean = true,
) {
    /** True when the ride was cancelled (or wiped) while this screen was open. */
    val isFinished: Boolean get() = !loading && ride == null
}

/** The rider's "your driver is on the way" screen. */
class ActiveRideViewModel(
    private val rideId: String,
    private val rideRepository: RideRepository,
) : ViewModel() {

    val uiState: StateFlow<ActiveRideUiState> = combine(
        rideRepository.rideRequest(rideId),
        rideRepository.offersForRide(rideId),
    ) { ride, offers ->
        ActiveRideUiState(
            ride = ride,
            acceptedOffer = offers.firstOrNull { it.status == OfferStatus.ACCEPTED },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveRideUiState())

    fun cancelRide() {
        viewModelScope.launch { rideRepository.cancelRide(rideId) }
    }

    companion object {
        fun factory(rideId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as InDriveApplication
                ActiveRideViewModel(rideId, ServiceLocator.from(application).rideRepository)
            }
        }
    }
}

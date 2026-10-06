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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OffersUiState(
    val ride: RideRequest? = null,
    val offers: List<DriverOffer> = emptyList(),
    val busyOfferId: String? = null,
) {
    val acceptedOffer: DriverOffer? get() = offers.firstOrNull { it.status == OfferStatus.ACCEPTED }
}

sealed interface OffersEvent {
    data class OfferAccepted(val rideId: String) : OffersEvent
}

/** Rider's incoming offers: live list, accept / decline. */
class OffersViewModel(
    private val rideId: String,
    private val rideRepository: RideRepository,
) : ViewModel() {

    private val _events = MutableSharedFlow<OffersEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<OffersEvent> = _events.asSharedFlow()

    private val busyOfferId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OffersUiState> = combine(
        rideRepository.rideRequest(rideId),
        rideRepository.offersForRide(rideId),
        busyOfferId,
    ) { ride, offers, busy ->
        // Newest offer first: a driver that just answered should be at the top.
        OffersUiState(ride = ride, offers = offers.sortedByDescending { it.createdAtMillis }, busyOfferId = busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OffersUiState())

    fun accept(offerId: String) {
        viewModelScope.launch {
            busyOfferId.value = offerId
            val accepted = rideRepository.acceptOffer(offerId)
            busyOfferId.value = null
            if (accepted) _events.emit(OffersEvent.OfferAccepted(rideId))
        }
    }

    fun decline(offerId: String) {
        viewModelScope.launch {
            busyOfferId.value = offerId
            rideRepository.declineOffer(offerId)
            busyOfferId.value = null
        }
    }

    fun cancelRide() {
        viewModelScope.launch { rideRepository.cancelRide(rideId) }
    }

    companion object {
        fun factory(rideId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as InDriveApplication
                OffersViewModel(rideId, ServiceLocator.from(application).rideRepository)
            }
        }
    }
}

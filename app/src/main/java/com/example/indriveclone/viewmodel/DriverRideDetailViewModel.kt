package com.example.indriveclone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.indriveclone.InDriveApplication
import com.example.indriveclone.ServiceLocator
import com.example.indriveclone.data.model.AdminSettings
import com.example.indriveclone.data.model.DriverOffer
import com.example.indriveclone.data.model.OfferStatus
import com.example.indriveclone.data.model.RideRequest
import com.example.indriveclone.data.repository.RideRepository
import com.example.indriveclone.data.settings.SettingsRepository
import com.example.indriveclone.domain.demo.DemoDriver
import com.example.indriveclone.domain.fare.FareBounds
import com.example.indriveclone.domain.fare.FareCalculator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DriverRideDetailUiState(
    val ride: RideRequest? = null,
    /** True until the first repository value arrives. */
    val loading: Boolean = true,
    val myOffers: List<DriverOffer> = emptyList(),
    /** Window for the counter-offer — identical to the rider's adjuster. */
    val counterBounds: FareBounds? = null,
    val counterFare: Double = 0.0,
    val counterFareText: String = "",
    val sending: Boolean = false,
) {
    val latestOffer: DriverOffer? get() = myOffers.lastOrNull()
    val alreadyAccepted: Boolean get() = myOffers.any { it.status == OfferStatus.ACCEPTED }
}

sealed interface DriverRideEvent {
    /** Emitted once the driver's bid is placed so the screen can confirm it. */
    data class OfferPlaced(val isCounterOffer: Boolean) : DriverRideEvent
}

/**
 * Driver's view of one request: the stored route, "accept at rider's fare" and a counter-offer whose
 * bounds come from [FareCalculator] — the same pure function the rider's adjuster uses, so both sides
 * always agree on what is allowed.
 */
class DriverRideDetailViewModel(
    private val rideId: String,
    private val rideRepository: RideRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverRideDetailUiState())
    val uiState: StateFlow<DriverRideDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DriverRideEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<DriverRideEvent> = _events.asSharedFlow()

    private var settings: AdminSettings = AdminSettings()
    private var counterTouched = false

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { updated ->
                settings = updated
                refreshBounds()
            }
        }

        viewModelScope.launch {
            combine(
                rideRepository.rideRequest(rideId),
                rideRepository.offersForRide(rideId),
            ) { ride, offers -> ride to offers.filter { it.driverName == DemoDriver.NAME } }
                .collect { (ride, myOffers) ->
                    _uiState.update { current ->
                        current.copy(ride = ride, myOffers = myOffers, loading = false)
                    }
                    refreshBounds()
                }
        }
    }

    /** Keeps the counter-offer inside the current window and defaults it to the rider's fare. */
    private suspend fun refreshBounds() {
        val ride = _uiState.value.ride ?: rideRepository.rideRequest(rideId).first() ?: return
        val bounds = FareCalculator.boundsFor(ride.suggestedFare, settings, settings.fareStep)
        _uiState.update { current ->
            val fare = when {
                !counterTouched -> ride.offeredFare
                else -> FareCalculator.clampToBounds(current.counterFare, bounds)
            }
            current.copy(
                ride = ride,
                counterBounds = bounds,
                counterFare = fare,
                // Never overwrite what the driver is currently typing.
                counterFareText = if (counterTouched) current.counterFareText else formatFare(fare),
            )
        }
    }

    fun setCounterFare(fare: Double) {
        val bounds = _uiState.value.counterBounds ?: return
        counterTouched = true
        val clamped = FareCalculator.clampToBounds(fare, bounds)
        _uiState.update { it.copy(counterFare = clamped, counterFareText = formatFare(clamped)) }
    }

    fun adjustCounterFare(direction: Int) {
        val state = _uiState.value
        val bounds = state.counterBounds ?: return
        counterTouched = true
        val fare = FareCalculator.stepFare(state.counterFare, direction, bounds)
        _uiState.update { it.copy(counterFare = fare, counterFareText = formatFare(fare)) }
    }

    fun onCounterFareTextChange(text: String) {
        counterTouched = true
        _uiState.update { it.copy(counterFareText = text) }
        text.replace(',', '.').toDoubleOrNull()?.let { parsed ->
            val bounds = _uiState.value.counterBounds ?: return@let
            _uiState.update { it.copy(counterFare = FareCalculator.clampToBounds(parsed, bounds)) }
        }
    }

    /** Places the driver's bid: the typed counter-offer, or exactly the rider's fare. */
    fun submitOffer(isCounterOffer: Boolean) {
        val state = _uiState.value
        val ride = state.ride ?: return
        val bounds = state.counterBounds ?: return

        val typedFare = state.counterFareText.replace(',', '.').toDoubleOrNull() ?: state.counterFare
        val fare = if (isCounterOffer) {
            FareCalculator.driverCounterOffer(typedFare, bounds) ?: return
        } else {
            ride.offeredFare
        }

        viewModelScope.launch {
            _uiState.update { it.copy(sending = true) }
            rideRepository.placeOffer(
                DemoDriver.offer(
                    ride = ride,
                    fare = fare,
                    isCounterOffer = isCounterOffer && fare > ride.offeredFare,
                ),
            )
            _uiState.update { it.copy(sending = false) }
            _events.emit(DriverRideEvent.OfferPlaced(isCounterOffer = isCounterOffer))
        }
    }

    private fun formatFare(fare: Double): String = Math.round(fare).toString()

    companion object {
        fun factory(rideId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as InDriveApplication
                val services = ServiceLocator.from(application)
                DriverRideDetailViewModel(rideId, services.rideRepository, services.settingsRepository)
            }
        }
    }
}

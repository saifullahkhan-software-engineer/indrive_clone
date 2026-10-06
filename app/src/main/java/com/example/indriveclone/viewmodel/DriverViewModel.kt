package com.example.indriveclone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.indriveclone.InDriveApplication
import com.example.indriveclone.ServiceLocator
import com.example.indriveclone.data.model.DriverOffer
import com.example.indriveclone.data.model.RideRequest
import com.example.indriveclone.data.model.RideStatus
import com.example.indriveclone.data.repository.RideRepository
import com.example.indriveclone.domain.demo.DemoDriver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DriverHomeUiState(
    /** SEARCHING + OFFERED requests — the ones a driver can still act on. */
    val openRequests: List<RideRequest> = emptyList(),
    /** This driver's own bids, keyed by ride id, so the list can show their status inline. */
    val myOffersByRide: Map<String, DriverOffer> = emptyMap(),
)

/** Driver home: every open ride request in the (in-memory) system. */
class DriverViewModel(private val rideRepository: RideRepository) : ViewModel() {

    val uiState: StateFlow<DriverHomeUiState> = combine(
        rideRepository.rideRequests,
        rideRepository.offers,
    ) { requests, offers ->
        val mine = offers
            .filter { it.driverName == DemoDriver.NAME }
            .associateBy { it.rideId }
        DriverHomeUiState(
            openRequests = requests.filter { it.status != RideStatus.ACCEPTED },
            myOffersByRide = mine,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriverHomeUiState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as InDriveApplication
                DriverViewModel(ServiceLocator.from(application).rideRepository)
            }
        }
    }
}

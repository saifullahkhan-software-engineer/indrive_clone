package com.example.indriveclone.data.repository

import com.example.indriveclone.data.model.DriverOffer
import com.example.indriveclone.data.model.NewOffer
import com.example.indriveclone.data.model.OfferStatus
import com.example.indriveclone.data.model.RideDraft
import com.example.indriveclone.data.model.RideRequest
import com.example.indriveclone.data.model.RideStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Frontend-only implementation: everything lives in memory for the lifetime of the process.
 *
 * Swapping this for a backend-backed implementation is the whole "add a real backend" task — the
 * interface, not the screens, is the seam.
 */
class InMemoryRideRepository(
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: (String) -> String = { prefix -> "$prefix-${UUID.randomUUID()}" },
) : RideRepository {

    private val rideRequestsFlow = MutableStateFlow<List<RideRequest>>(emptyList())
    private val offersFlow = MutableStateFlow<List<DriverOffer>>(emptyList())
    private val sequence = AtomicLong(0)

    override val rideRequests: StateFlow<List<RideRequest>> = rideRequestsFlow.asStateFlow()
    override val offers: StateFlow<List<DriverOffer>> = offersFlow.asStateFlow()

    override fun rideRequest(id: String): Flow<RideRequest?> =
        rideRequestsFlow.map { rides -> rides.firstOrNull { it.id == id } }

    override fun offersForRide(rideId: String): Flow<List<DriverOffer>> =
        offersFlow.map { all -> all.filter { it.rideId == rideId }.sortedBy { it.createdAtMillis } }

    override fun offer(id: String): Flow<DriverOffer?> =
        offersFlow.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun createRideRequest(draft: RideDraft): RideRequest {
        val ride = RideRequest(
            id = idFactory("ride"),
            pickup = draft.pickup,
            pickupLabel = draft.pickupLabel,
            destination = draft.destination,
            destinationLabel = draft.destinationLabel,
            distanceMeters = draft.distanceMeters,
            durationSeconds = draft.durationSeconds,
            routePolyline = draft.routePolyline,
            routeSource = draft.routeSource,
            offeredFare = draft.offeredFare,
            suggestedFare = draft.suggestedFare,
            status = RideStatus.SEARCHING,
            createdAtMillis = clock(),
        )
        rideRequestsFlow.update { current -> (current + ride).sortedByDescending { it.createdAtMillis } }
        return ride
    }

    override suspend fun placeOffer(draft: NewOffer): DriverOffer {
        require(draft.fare > 0) { "Offer fare must be positive" }
        val offer = DriverOffer(
            id = idFactory("offer"),
            rideId = draft.rideId,
            driverName = draft.driverName,
            driverRating = draft.driverRating,
            carModel = draft.carModel,
            carPlate = draft.carPlate,
            fare = draft.fare,
            etaMinutes = draft.etaMinutes,
            status = OfferStatus.PENDING,
            isCounterOffer = draft.isCounterOffer,
            createdAtMillis = clock() + sequence.incrementAndGet(),
        )
        offersFlow.update { current -> (current + offer).sortedByDescending { it.createdAtMillis } }
        updateRide(draft.rideId) { ride ->
            if (ride.status == RideStatus.SEARCHING) ride.copy(status = RideStatus.OFFERED) else ride
        }
        return offer
    }

    override suspend fun acceptOffer(offerId: String): Boolean {
        val accepted = offersFlow.value.firstOrNull { it.id == offerId } ?: return false
        val ride = rideRequestsFlow.value.firstOrNull { it.id == accepted.rideId } ?: return false

        offersFlow.update { current ->
            current.map { offer ->
                when {
                    offer.id == offerId -> offer.copy(status = OfferStatus.ACCEPTED)
                    offer.rideId == ride.id && offer.status == OfferStatus.PENDING ->
                        offer.copy(status = OfferStatus.DECLINED)
                    else -> offer
                }
            }
        }
        updateRide(ride.id) {
            it.copy(
                status = RideStatus.ACCEPTED,
                acceptedOfferId = offerId,
                acceptedDriverName = accepted.driverName,
            )
        }
        return true
    }

    override suspend fun declineOffer(offerId: String) {
        val declined = offersFlow.value.firstOrNull { it.id == offerId } ?: return
        offersFlow.update { current ->
            current.map { offer ->
                if (offer.id == offerId && offer.status == OfferStatus.PENDING) {
                    offer.copy(status = OfferStatus.DECLINED)
                } else {
                    offer
                }
            }
        }
        // If that was the last pending offer, the ride goes back to SEARCHING.
        val stillPending = offersFlow.value.any {
            it.rideId == declined.rideId && it.status == OfferStatus.PENDING
        }
        if (!stillPending) {
            updateRide(declined.rideId) { ride ->
                if (ride.status == RideStatus.OFFERED) ride.copy(status = RideStatus.SEARCHING) else ride
            }
        }
    }

    override suspend fun cancelRide(rideId: String) {
        rideRequestsFlow.update { current -> current.filterNot { it.id == rideId } }
        offersFlow.update { current -> current.filterNot { it.rideId == rideId } }
    }

    override suspend fun clear() {
        rideRequestsFlow.value = emptyList()
        offersFlow.value = emptyList()
    }

    private fun updateRide(rideId: String, transform: (RideRequest) -> RideRequest) {
        rideRequestsFlow.update { current ->
            current.map { ride -> if (ride.id == rideId) transform(ride) else ride }
        }
    }
}

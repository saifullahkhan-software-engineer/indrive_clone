package com.example.indriveclone.data.repository

import com.example.indriveclone.data.model.DriverOffer
import com.example.indriveclone.data.model.NewOffer
import com.example.indriveclone.data.model.RideDraft
import com.example.indriveclone.data.model.RideRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's single source of truth for rides and offers.
 *
 * The demo ships [InMemoryRideRepository]; a real backend would be another implementation of this
 * interface (REST call + WebSocket feed) and nothing above it would change: the rider screen and the
 * driver screen both observe the same flows, which is why a request created in rider mode shows up
 * in driver mode instantly.
 */
interface RideRepository {

    /** All ride requests, newest first. */
    val rideRequests: StateFlow<List<RideRequest>>

    /** All offers (every ride), newest first. */
    val offers: StateFlow<List<DriverOffer>>

    fun rideRequest(id: String): Flow<RideRequest?>

    fun offersForRide(rideId: String): Flow<List<DriverOffer>>

    fun offer(id: String): Flow<DriverOffer?>

    /** Creates a request in [com.example.indriveclone.data.model.RideStatus.SEARCHING]. */
    suspend fun createRideRequest(draft: RideDraft): RideRequest

    /** Driver accepts at the rider's fare or counters. Moves the ride to OFFERED. */
    suspend fun placeOffer(draft: NewOffer): DriverOffer

    /** Rider accepts: ride -> ACCEPTED, chosen offer ACCEPTED, every other offer DECLINED. */
    suspend fun acceptOffer(offerId: String): Boolean

    /** Rider declines a single offer; the ride stays OFFERED unless no offers are left. */
    suspend fun declineOffer(offerId: String)

    suspend fun cancelRide(rideId: String)

    /** Demo helper: wipes rides and offers (used by "Reset demo data"). */
    suspend fun clear()
}

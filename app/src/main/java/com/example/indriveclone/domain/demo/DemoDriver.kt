package com.example.indriveclone.domain.demo

import com.example.indriveclone.data.model.NewOffer
import com.example.indriveclone.data.model.RideRequest
import kotlin.math.roundToInt

/**
 * The identity used by the driver side of the demo (there is no login and no backend, so "the driver"
 * is a constant). Offers carry the name, which is how the driver screens recognise their own bids.
 */
object DemoDriver {

    const val NAME: String = "You (demo driver)"
    const val RATING: Double = 5.0
    const val CAR_MODEL: String = "Chevrolet Cobalt"
    const val CAR_PLATE: String = "001 YOU 02"

    /** Mock pickup ETA: roughly half the trip time, at least 2 minutes. */
    fun etaMinutes(ride: RideRequest): Int =
        (ride.durationSeconds / 120.0).roundToInt().coerceAtLeast(2)

    /** "Accept at the rider's fare" and the counter-offer share this constructor. */
    fun offer(ride: RideRequest, fare: Double, isCounterOffer: Boolean): NewOffer = NewOffer(
        rideId = ride.id,
        driverName = NAME,
        driverRating = RATING,
        carModel = CAR_MODEL,
        carPlate = CAR_PLATE,
        fare = fare,
        etaMinutes = etaMinutes(ride),
        isCounterOffer = isCounterOffer,
    )
}

package com.example.indriveclone.domain.demo

import com.example.indriveclone.data.model.AdminSettings
import com.example.indriveclone.data.model.NewOffer
import com.example.indriveclone.data.model.RideRequest
import com.example.indriveclone.domain.fare.FareCalculator
import com.example.indriveclone.domain.fare.FareBounds
import kotlin.random.Random

/** Source of the simulated driver offers the demo rider receives. */
interface DemoOfferSource {
    fun offersFor(ride: RideRequest, settings: AdminSettings): List<NewOffer>
}

/**
 * Frontend-only stand-in for real drivers: 2–3 offers with a name, rating, car and mock ETA.
 *
 * Most drivers simply take the rider's fare; the others counter-offer inside the exact same window the
 * rider's adjuster used ([FareCalculator.boundsFor] on the ride's stored suggestion), which is what
 * makes the accept/decline/counter flow meaningful. The *timing* of the offers is owned by the
 * ViewModel, since it also has to stop when the ride is accepted.
 */
class MockDriverFleet(private val random: Random = Random.Default) : DemoOfferSource {

    override fun offersFor(ride: RideRequest, settings: AdminSettings): List<NewOffer> {
        val bounds = FareCalculator.boundsFor(ride.suggestedFare, settings, settings.fareStep)
        val driverCount = MIN_DRIVERS + random.nextInt(MAX_DRIVERS - MIN_DRIVERS + 1)

        return DRIVERS.shuffled(random).take(driverCount).mapIndexed { index, driver ->
            val requestedFare = when (index) {
                0 -> ride.offeredFare                                   // accepts what the rider asked
                1 -> ride.offeredFare * (1.06 + random.nextDouble() * 0.08) // counters a bit higher
                else -> if (random.nextBoolean()) {
                    ride.offeredFare                                     // matches
                } else {
                    ride.offeredFare * (1.0 - (0.03 + random.nextDouble() * 0.05)) // undercuts slightly
                }
            }
            val fare = FareCalculator.clampToBounds(requestedFare, bounds)

            NewOffer(
                rideId = ride.id,
                driverName = driver.name,
                driverRating = driver.rating,
                carModel = driver.carModel,
                carPlate = driver.carPlate,
                fare = fare,
                etaMinutes = MIN_ETA_MINUTES + random.nextInt(MAX_ETA_MINUTES - MIN_ETA_MINUTES + 1),
                isCounterOffer = fare > ride.offeredFare,
            )
        }
    }

    /** Mock drivers currently working the city. */
    private data class MockDriver(
        val name: String,
        val rating: Double,
        val carModel: String,
        val carPlate: String,
    )

    companion object {
        const val MIN_DRIVERS = 2
        const val MAX_DRIVERS = 3
        private const val MIN_ETA_MINUTES = 3
        private const val MAX_ETA_MINUTES = 9

        private val DRIVERS = listOf(
            MockDriver("Azamat T.", 4.9, "Toyota Camry", "123 ABC 02"),
            MockDriver("Dana K.", 4.8, "Hyundai Elantra", "456 DEF 02"),
            MockDriver("Ruslan M.", 4.7, "Kia Rio", "789 GHJ 02"),
            MockDriver("Gulnara S.", 5.0, "Toyota Corolla", "012 KLM 02"),
            MockDriver("Timur A.", 4.6, "Lada Vesta", "345 NPQ 02"),
            MockDriver("Yerlan B.", 4.9, "Honda Accord", "678 RST 02"),
        )

        /** Bounds helper re-exported for the driver screens (same window for offers and counters). */
        fun boundsFor(ride: RideRequest, settings: AdminSettings): FareBounds =
            FareCalculator.boundsFor(ride.suggestedFare, settings, settings.fareStep)
    }
}

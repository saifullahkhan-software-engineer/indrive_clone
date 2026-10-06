package com.example.indriveclone.domain.demo

import com.example.indriveclone.Fixtures
import com.example.indriveclone.data.model.AdminSettings
import com.example.indriveclone.domain.fare.FareCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The simulated drivers must bid inside the rider's window, otherwise accepting would break the rules. */
class MockDriverFleetTest {

    private val settings = AdminSettings()
    private val ride = Fixtures.rideRequest(offeredFare = 220.0, suggestedFare = 220.0)
    private val bounds = FareCalculator.boundsFor(ride.suggestedFare, settings)

    @Test
    fun `two or three drivers answer`() {
        repeat(20) { seed ->
            val offers = MockDriverFleet(Random(seed)).offersFor(ride, settings)
            assertTrue(offers.size in MockDriverFleet.MIN_DRIVERS..MockDriverFleet.MAX_DRIVERS)
            assertEquals(offers.size, offers.map { it.driverName }.toSet().size)
        }
    }

    @Test
    fun `every offer stays inside the rider's fare window`() {
        repeat(20) { seed ->
            MockDriverFleet(Random(seed)).offersFor(ride, settings).forEach { offer ->
                assertTrue(
                    "Offer ${offer.fare} outside ${bounds.minimum}..${bounds.maximum}",
                    offer.fare in bounds.minimum..bounds.maximum,
                )
                assertEquals(ride.id, offer.rideId)
                assertTrue(offer.etaMinutes >= 3)
                assertTrue(offer.driverRating in 4.0..5.0)
            }
        }
    }

    @Test
    fun `the first driver simply accepts the rider's fare`() {
        val offers = MockDriverFleet(Random(7)).offersFor(ride, settings)

        assertEquals(ride.offeredFare, offers.first().fare, 0.001)
        assertTrue(!offers.first().isCounterOffer)
    }

    @Test
    fun `the driver identity used by the demo driver app is distinct from the simulated fleet`() {
        val fleetNames = MockDriverFleet(Random(3)).offersFor(ride, settings).map { it.driverName }

        assertTrue(DemoDriver.NAME !in fleetNames)
    }

    @Test
    fun `the driver's own offer reuses the ride's fare window`() {
        val offer = DemoDriver.offer(ride, fare = bounds.maximum, isCounterOffer = true)

        assertEquals(ride.id, offer.rideId)
        assertEquals(DemoDriver.NAME, offer.driverName)
        assertEquals(bounds.maximum, offer.fare, 0.001)
        assertTrue(offer.etaMinutes >= 2)
    }
}

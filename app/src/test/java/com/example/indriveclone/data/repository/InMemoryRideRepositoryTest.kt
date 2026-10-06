package com.example.indriveclone.data.repository

import com.example.indriveclone.Fixtures
import com.example.indriveclone.data.model.NewOffer
import com.example.indriveclone.data.model.OfferStatus
import com.example.indriveclone.data.model.RideStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The in-memory repository is the stand-in for the backend, so its state machine is worth testing. */
class InMemoryRideRepositoryTest {

    private val repository = InMemoryRideRepository(clock = { 1_000L }, idFactory = { prefix -> "$prefix-1" })

    private fun offer(rideId: String, fare: Double, driver: String = "Driver A", counter: Boolean = false) = NewOffer(
        rideId = rideId,
        driverName = driver,
        driverRating = 4.8,
        carModel = "Toyota Camry",
        carPlate = "123 ABC",
        fare = fare,
        etaMinutes = 5,
        isCounterOffer = counter,
    )

    @Test
    fun `a new request starts as SEARCHING`() = runTest {
        val ride = repository.createRideRequest(Fixtures.rideDraft())

        assertEquals(RideStatus.SEARCHING, ride.status)
        assertEquals(1, repository.rideRequests.value.size)
        assertTrue(repository.offers.value.isEmpty())
    }

    @Test
    fun `the first offer moves the ride to OFFERED and stays pending`() = runTest {
        val ride = repository.createRideRequest(Fixtures.rideDraft())

        val placed = repository.placeOffer(offer(ride.id, fare = 200.0))

        assertEquals(OfferStatus.PENDING, placed.status)
        assertEquals(RideStatus.OFFERED, repository.rideRequest(ride.id).first()?.status)
        assertEquals(1, repository.offersForRide(ride.id).first().size)
    }

    @Test
    fun `accepting an offer accepts it and declines every other offer`() = runTest {
        val ride = repository.createRideRequest(Fixtures.rideDraft())
        val first = repository.placeOffer(offer(ride.id, fare = 210.0, driver = "Azamat"))
        repository.placeOffer(offer(ride.id, fare = 220.0, driver = "Dana", counter = true))

        val accepted = repository.acceptOffer(first.id)

        assertTrue(accepted)
        val offers = repository.offersForRide(ride.id).first()
        assertEquals(OfferStatus.ACCEPTED, offers.first { it.id == first.id }.status)
        assertEquals(OfferStatus.DECLINED, offers.first { it.driverName == "Dana" }.status)
        val updatedRide = repository.rideRequest(ride.id).first()!!
        assertEquals(RideStatus.ACCEPTED, updatedRide.status)
        assertEquals(first.id, updatedRide.acceptedOfferId)
        assertEquals("Azamat", updatedRide.acceptedDriverName)
    }

    @Test
    fun `declining the only offer puts the ride back to SEARCHING`() = runTest {
        val ride = repository.createRideRequest(Fixtures.rideDraft())
        val placed = repository.placeOffer(offer(ride.id, fare = 200.0))

        repository.declineOffer(placed.id)

        assertEquals(OfferStatus.DECLINED, repository.offersForRide(ride.id).first().single().status)
        assertEquals(RideStatus.SEARCHING, repository.rideRequest(ride.id).first()?.status)
    }

    @Test
    fun `declining one of two offers keeps the ride OFFERED`() = runTest {
        val ride = repository.createRideRequest(Fixtures.rideDraft())
        val first = repository.placeOffer(offer(ride.id, fare = 200.0))
        repository.placeOffer(offer(ride.id, fare = 250.0, driver = "Dana"))

        repository.declineOffer(first.id)

        assertEquals(RideStatus.OFFERED, repository.rideRequest(ride.id).first()?.status)
    }

    @Test
    fun `cancelling a ride removes it together with its offers`() = runTest {
        val ride = repository.createRideRequest(Fixtures.rideDraft())
        repository.placeOffer(offer(ride.id, fare = 200.0))

        repository.cancelRide(ride.id)

        assertNull(repository.rideRequest(ride.id).first())
        assertTrue(repository.offersForRide(ride.id).first().isEmpty())
    }

    @Test
    fun `driver screens see requests created on the rider side`() = runTest {
        val ride = repository.createRideRequest(Fixtures.rideDraft())

        val open = repository.rideRequests.first()

        assertEquals(listOf(ride.id), open.map { it.id })
        assertFalse(open.single().routePolyline.isEmpty())
    }

    @Test
    fun `resetting the demo data clears rides and offers`() = runTest {
        val ride = repository.createRideRequest(Fixtures.rideDraft())
        repository.placeOffer(offer(ride.id, fare = 200.0))

        repository.clear()

        assertTrue(repository.rideRequests.value.isEmpty())
        assertTrue(repository.offers.value.isEmpty())
    }
}

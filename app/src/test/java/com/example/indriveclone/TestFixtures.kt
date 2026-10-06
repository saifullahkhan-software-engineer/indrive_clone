package com.example.indriveclone

import com.example.indriveclone.data.model.LatLng
import com.example.indriveclone.data.model.RideDraft
import com.example.indriveclone.data.model.RideRequest
import com.example.indriveclone.data.model.RideStatus
import com.example.indriveclone.data.model.RouteSource

object Fixtures {

    val pickup = LatLng(latitude = 43.238949, longitude = 76.889709)
    val destination = LatLng(latitude = 43.256670, longitude = 76.928610)

    fun rideDraft(
        offeredFare: Double = 200.0,
        suggestedFare: Double = 200.0,
        distanceMeters: Double = 2_000.0,
        durationSeconds: Double = 240.0,
    ) = RideDraft(
        pickup = pickup,
        pickupLabel = "Panfilov St 100",
        destination = destination,
        destinationLabel = "Abay Ave 20",
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        routePolyline = listOf(pickup, destination),
        routeSource = RouteSource.OSRM,
        offeredFare = offeredFare,
        suggestedFare = suggestedFare,
    )

    fun rideRequest(
        id: String = "ride-1",
        offeredFare: Double = 200.0,
        suggestedFare: Double = 200.0,
        status: RideStatus = RideStatus.SEARCHING,
        createdAtMillis: Long = 1_000L,
    ) = RideRequest(
        id = id,
        pickup = pickup,
        pickupLabel = "Panfilov St 100",
        destination = destination,
        destinationLabel = "Abay Ave 20",
        distanceMeters = 2_000.0,
        durationSeconds = 240.0,
        routePolyline = listOf(pickup, destination),
        routeSource = RouteSource.OSRM,
        offeredFare = offeredFare,
        suggestedFare = suggestedFare,
        status = status,
        createdAtMillis = createdAtMillis,
    )
}

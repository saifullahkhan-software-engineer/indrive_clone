package com.example.indriveclone.data.model

/**
 * Coordinate pair used across the app. The order is deliberately (latitude, longitude) — the
 * opposite of the wire format used by OSRM / OpenRouteService, which is longitude first. The
 * conversion lives in exactly two places: [com.example.indriveclone.domain.route.OsrmRouteService]
 * and [com.example.indriveclone.domain.route.OpenRouteServiceRouteService].
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

/** Lifecycle of a ride request, from the rider's point of view. */
enum class RideStatus {
    /** Created by the rider, waiting for driver offers. */
    SEARCHING,

    /** At least one driver offer exists. */
    OFFERED,

    /** The rider accepted an offer; a driver is on the way. */
    ACCEPTED,
}

/** Lifecycle of a single driver offer. */
enum class OfferStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
}

/** Where a [RouteResult]'s geometry came from. Drives the small provider label in the UI. */
enum class RouteSource {
    /** OpenRouteService (requires an API key). */
    ORS,

    /** Public or self-hosted OSRM (no key needed). */
    OSRM,

    /** Offline straight-line estimate. */
    HAVERSINE,
}

/** Result of a routing request, always in metric units, always oriented from -> to. */
data class RouteResult(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val polyline: List<LatLng>,
    val source: RouteSource,
)

/** Admin-editable fare configuration. Defaults are the demo values. */
data class AdminSettings(
    val baseFare: Double = 100.0,
    val perKmRate: Double = 45.0,
    val minimumFare: Double = 50.0,
    val maxFareMultiplier: Double = 2.0,
    /** Runtime override of BuildConfig.ORS_API_KEY; blank means "use BuildConfig / no key". */
    val orsApiKey: String = "",
)

/** The app's role, chosen on the first screen and changeable from the menu. */
enum class UserRole {
    RIDER,
    DRIVER,
}

/**
 * A ride requested by a rider. The route (distance, duration, polyline) is stored with the request
 * so drivers never need to re-route anything and the fare bounds stay stable.
 */
data class RideRequest(
    val id: String,
    val pickup: LatLng,
    val pickupLabel: String,
    val destination: LatLng,
    val destinationLabel: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val routePolyline: List<LatLng>,
    val routeSource: RouteSource,
    /** What the rider is willing to pay. */
    val offeredFare: Double,
    /** The calculated suggestion the rider started from (kept for the driver's counter-offer bounds). */
    val suggestedFare: Double,
    val status: RideStatus,
    val createdAtMillis: Long,
    val acceptedOfferId: String? = null,
    val acceptedDriverName: String? = null,
)

/** What the rider screen hands to the repository when "Request Ride" is tapped. */
data class RideDraft(
    val pickup: LatLng,
    val pickupLabel: String,
    val destination: LatLng,
    val destinationLabel: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val routePolyline: List<LatLng>,
    val routeSource: RouteSource,
    val offeredFare: Double,
    val suggestedFare: Double,
)

/** A driver's bid on a ride: "accept at the rider's fare" or a counter-offer. */
data class DriverOffer(
    val id: String,
    val rideId: String,
    val driverName: String,
    val driverRating: Double,
    val carModel: String,
    val carPlate: String,
    val fare: Double,
    /** Mock ETA in minutes, as shown to the rider. */
    val etaMinutes: Int,
    val status: OfferStatus,
    val isCounterOffer: Boolean,
    val createdAtMillis: Long,
)

/** Something the driver app can send. */
data class NewOffer(
    val rideId: String,
    val driverName: String,
    val driverRating: Double,
    val carModel: String,
    val carPlate: String,
    val fare: Double,
    val etaMinutes: Int,
    val isCounterOffer: Boolean,
)

/** Best-effort place label from reverse geocoding; falls back to a pretty-printed coordinate. */
data class AddressSuggestion(
    val label: String,
    val position: LatLng,
)

/** Navigation target for the rider's menu. */
enum class DriveStep(val isPickup: Boolean) {
    PICKUP(true),
    DESTINATION(false),
}

internal fun LatLng.prettyCoords(): String =
    "%.5f, %.5f".format(latitude, longitude)

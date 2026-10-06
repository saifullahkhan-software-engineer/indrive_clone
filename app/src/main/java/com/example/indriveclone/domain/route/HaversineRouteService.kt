package com.example.indriveclone.domain.route

import com.example.indriveclone.data.model.LatLng
import com.example.indriveclone.data.model.RouteResult
import com.example.indriveclone.data.model.RouteSource
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline fallback: great-circle distance inflated by a road factor, with the duration estimated at a
 * city average speed, and a straight two-point polyline. It never fails, so it is always the last
 * element of the provider chain.
 */
class HaversineRouteService(
    private val roadFactor: Double = ROAD_FACTOR,
    private val averageSpeedKmh: Double = AVERAGE_SPEED_KMH,
) : RouteService {

    override val source: RouteSource = RouteSource.HAVERSINE

    override suspend fun getRoute(from: LatLng, to: LatLng): RouteResult {
        val straightLineMeters = haversineMeters(from, to)
        val distanceMeters = straightLineMeters * roadFactor
        val durationSeconds = distanceMeters / (averageSpeedKmh / 3.6)
        return RouteResult(
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            polyline = listOf(from, to),
            source = source,
        )
    }

    companion object {
        /** Straight-line distance is multiplied by this to approximate a road distance. */
        const val ROAD_FACTOR: Double = 1.3

        /** Assumed average speed of the estimate. */
        const val AVERAGE_SPEED_KMH: Double = 30.0

        private const val EARTH_RADIUS_METERS = 6_371_000.0

        fun haversineMeters(from: LatLng, to: LatLng): Double {
            val fromLatRad = Math.toRadians(from.latitude)
            val toLatRad = Math.toRadians(to.latitude)
            val deltaLat = Math.toRadians(to.latitude - from.latitude)
            val deltaLon = Math.toRadians(to.longitude - from.longitude)
            val a = sin(deltaLat / 2).let { it * it } +
                cos(fromLatRad) * cos(toLatRad) * sin(deltaLon / 2).let { it * it }
            return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
        }
    }
}

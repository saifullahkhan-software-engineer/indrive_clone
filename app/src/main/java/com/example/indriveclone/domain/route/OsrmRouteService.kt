package com.example.indriveclone.domain.route

import com.example.indriveclone.data.model.LatLng
import com.example.indriveclone.data.model.RouteResult
import com.example.indriveclone.data.model.RouteSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Public OSRM demo server — **no API key required**. Default provider of the app.
 *
 * Request: `GET {base}/route/v1/driving/{lon},{lat};{lon},{lat}?overview=full&geometries=geojson`
 * Response: `routes[0].distance` (m), `routes[0].duration` (s), `routes[0].geometry.coordinates`
 * (GeoJSON, **longitude first**).
 *
 * The public server is for demos only: it is rate limited and has no uptime guarantee. Point
 * [baseUrl] at a self-hosted OSRM (`docker run -p 5000:5000 osrm/osrm-backend`) for anything real.
 */
class OsrmRouteService(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val backend: RouteBackend = OkHttpRouteBackend(),
) : RouteService {

    override val source: RouteSource = RouteSource.OSRM

    override suspend fun getRoute(from: LatLng, to: LatLng): RouteResult {
        val url = "$baseUrl/route/v1/driving/" +
            "${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
            "?overview=full&geometries=geojson"

        val response = backend.get(url)
        check(response.isSuccessful) { "OSRM returned HTTP ${response.code}" }

        val body = json.decodeFromString<OsrmResponse>(response.body)
        check(body.code == "Ok") { "OSRM error: ${body.code} ${body.message.orEmpty()}" }
        val route = body.routes?.firstOrNull() ?: error("OSRM returned no route")

        val polyline = route.geometry?.coordinates.orEmpty()
            .mapNotNull { pair -> pair.toLatLngOrNull() }
        check(polyline.size >= 2) { "OSRM returned an unusable geometry" }

        return RouteResult(
            distanceMeters = route.distance,
            durationSeconds = route.duration,
            polyline = polyline,
            source = source,
        )
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://router.project-osrm.org"

        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class OsrmResponse(
    val code: String = "",
    val message: String? = null,
    val routes: List<OsrmRoute>? = null,
)

@Serializable
private data class OsrmRoute(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val geometry: OsrmGeometry? = null,
)

@Serializable
private data class OsrmGeometry(
    /** GeoJSON order: [longitude, latitude]. */
    val coordinates: List<List<Double>> = emptyList(),
)

/** Wire format is longitude-first; the app model is latitude-first. Convert here and nowhere else. */
private fun List<Double>.toLatLngOrNull(): LatLng? {
    if (size < 2) return null
    val latitude = getOrNull(1) ?: return null
    if (!latitude.isFinite() || !this[0].isFinite()) return null
    return LatLng(latitude = latitude, longitude = this[0])
}

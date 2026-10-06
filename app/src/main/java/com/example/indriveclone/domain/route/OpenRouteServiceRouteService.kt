package com.example.indriveclone.domain.route

import com.example.indriveclone.data.model.LatLng
import com.example.indriveclone.data.model.RouteResult
import com.example.indriveclone.data.model.RouteSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenRouteService — used **only when an API key is available** (Settings screen, else
 * `ORS_API_KEY` from local.properties via BuildConfig).
 *
 * Request: `POST {base}/v2/directions/{profile}/geojson`, header `Authorization: <key>`,
 * body `{"coordinates": [[lon,lat],[lon,lat]]}`
 * Response: `features[0].properties.summary.distance` (m) / `.duration` (s),
 * `features[0].geometry.coordinates` (GeoJSON, **longitude first**).
 *
 * The free tier has daily request limits — that is why the app debounces, caches and keeps OSRM as a
 * fallback instead of calling ORS on every map tap.
 */
class OpenRouteServiceRouteService(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val profile: String = PROFILE_DRIVING_CAR,
    private val backend: RouteBackend = OkHttpRouteBackend(),
) : RouteService {

    override val source: RouteSource = RouteSource.ORS

    override suspend fun getRoute(from: LatLng, to: LatLng): RouteResult {
        require(apiKey.isNotBlank()) { "OpenRouteService requires an API key" }

        val requestBody = buildString {
            append("""{"coordinates":[""")
            append("[${from.longitude},${from.latitude}]")
            append(",")
            append("[${to.longitude},${to.latitude}]")
            append("]}")
        }

        val response = backend.postJson(
            url = "$baseUrl/v2/directions/$profile/geojson",
            jsonBody = requestBody,
            headers = mapOf("Authorization" to apiKey),
        )
        check(response.isSuccessful) { "OpenRouteService returned HTTP ${response.code}" }

        val body = json.decodeFromString<OrsResponse>(response.body)
        val feature = body.features?.firstOrNull() ?: error("OpenRouteService returned no route")
        val summary = feature.properties?.summary ?: error("OpenRouteService returned no summary")

        val polyline = feature.geometry?.coordinates.orEmpty()
            .mapNotNull { pair -> pair.toLatLngOrNull() }
        check(polyline.size >= 2) { "OpenRouteService returned an unusable geometry" }

        return RouteResult(
            distanceMeters = summary.distance,
            durationSeconds = summary.duration,
            polyline = polyline,
            source = source,
        )
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.openrouteservice.org"
        const val PROFILE_DRIVING_CAR: String = "driving-car"

        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class OrsResponse(
    val features: List<OrsFeature>? = null,
)

@Serializable
private data class OrsFeature(
    val properties: OrsProperties? = null,
    val geometry: OrsGeometry? = null,
)

@Serializable
private data class OrsProperties(
    val summary: OrsSummary? = null,
)

@Serializable
private data class OrsSummary(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
)

@Serializable
private data class OrsGeometry(
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

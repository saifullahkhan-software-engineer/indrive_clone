package com.example.indriveclone.domain.route

import com.example.indriveclone.data.model.AddressSuggestion
import com.example.indriveclone.data.model.LatLng
import com.example.indriveclone.data.network.urlEncoded
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Optional place search. The core flow (tap the map) works without it. */
interface GeocodingService {
    /** Forward search; [near] biases the results if the backend supports it. */
    suspend fun search(query: String, near: LatLng? = null): List<AddressSuggestion>

    /** Reverse lookup used to label tapped pins. Returns null when nothing useful came back. */
    suspend fun reverse(position: LatLng): String?
}

/**
 * Nominatim (OpenStreetMap geocoding). Free, no key, but its usage policy requires an identifying
 * User-Agent (set in [com.example.indriveclone.data.network.Http]) and **at most one request per
 * second**, which [RateLimiter] enforces. Failures are silent by design: a missing label must never
 * break the tap-to-pick flow.
 */
class NominatimGeocodingService(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val backend: RouteBackend = OkHttpRouteBackend(),
    private val rateLimiter: RateLimiter = RateLimiter(MIN_INTERVAL_MS),
) : GeocodingService {

    override suspend fun search(query: String, near: LatLng?): List<AddressSuggestion> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return emptyList()
        rateLimiter.awaitTurn()
        return try {
            val response = backend.get(
                "$baseUrl/search?q=${trimmed.urlEncoded()}&format=jsonv2&limit=$SEARCH_LIMIT&addressdetails=0",
            )
            if (!response.isSuccessful) return emptyList()
            json.decodeFromString<List<NominatimPlace>>(response.body)
                .mapNotNull { place -> place.toSuggestion() }
        } catch (failure: Exception) {
            emptyList()
        }
    }

    override suspend fun reverse(position: LatLng): String? {
        rateLimiter.awaitTurn()
        return try {
            val response = backend.get(
                "$baseUrl/reverse?lat=${position.latitude}&lon=${position.longitude}&format=jsonv2&zoom=17",
            )
            if (!response.isSuccessful) return null
            json.decodeFromString<NominatimPlace>(response.body).displayName?.shortLabel()
        } catch (failure: Exception) {
            null
        }
    }

    private fun NominatimPlace.toSuggestion(): AddressSuggestion? {
        val latitude = latitude?.toDoubleOrNull() ?: return null
        val longitude = longitude?.toDoubleOrNull() ?: return null
        return AddressSuggestion(
            label = displayName?.shortLabel() ?: return null,
            position = LatLng(latitude = latitude, longitude = longitude),
        )
    }

    /** Nominatim returns "Street 12, District, City, Region, Country" — keep the useful head. */
    private fun String.shortLabel(): String =
        split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(LABEL_PARTS)
            .joinToString(", ")

    companion object {
        const val DEFAULT_BASE_URL: String = "https://nominatim.openstreetmap.org"
        const val MIN_INTERVAL_MS: Long = 1_100L

        private const val MIN_QUERY_LENGTH = 3
        private const val SEARCH_LIMIT = 6
        private const val LABEL_PARTS = 3

        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class NominatimPlace(
    @kotlinx.serialization.SerialName("display_name") val displayName: String? = null,
    @kotlinx.serialization.SerialName("lat") val latitude: String? = null,
    @kotlinx.serialization.SerialName("lon") val longitude: String? = null,
)

/** Serialises calls so Nominatim's 1 req/s policy is respected even if the UI fires quickly. */
class RateLimiter(private val minIntervalMillis: Long) {
    private val mutex = Mutex()
    private var lastCallMillis = 0L

    suspend fun awaitTurn() = mutex.withLock {
        val elapsed = System.currentTimeMillis() - lastCallMillis
        if (elapsed < minIntervalMillis) {
            delay(minIntervalMillis - elapsed)
        }
        lastCallMillis = System.currentTimeMillis()
    }
}

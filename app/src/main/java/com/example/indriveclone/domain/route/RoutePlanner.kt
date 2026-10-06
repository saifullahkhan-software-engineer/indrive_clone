package com.example.indriveclone.domain.route

import com.example.indriveclone.data.model.LatLng
import com.example.indriveclone.data.model.RouteResult
import com.example.indriveclone.data.model.RouteSource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest

/** What the rider screen knows about the current routing attempt. */
sealed interface RouteState {
    /** Pickup and/or destination not set yet. */
    data object Idle : RouteState

    data object Loading : RouteState

    data class Success(val result: RouteResult, val fromCache: Boolean) : RouteState

    /** Only reachable if even the offline estimate fails; the UI then offers manual distance entry. */
    data class Failure(val message: String) : RouteState
}

/**
 * The one place that turns "pickup + destination" into a route. It owns the request policy so that no
 * ViewModel has to:
 *
 * - routes only when **both** endpoints are set;
 * - **debounces 500 ms** (map taps and marker drags produce a burst of updates);
 * - **cancels the in-flight request** when a newer one arrives (`mapLatest` cancels the previous
 *   collector, which cancels the underlying OkHttp call);
 * - **caches** the last result per (from, to) pair, so re-selecting the same pins costs nothing;
 * - drops the cache when the ORS key changes.
 */
class RoutePlanner(
    private val factory: RouteServiceFactory = RouteServiceFactory(),
    private val cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
) {

    private val cache = RouteCache(cacheCapacity)

    @Volatile
    private var activeKey: String? = null

    @Volatile
    private var routeService: RouteService = factory.createForResolvedKey(null)

    /**
     * Called by the ViewModel when the settings change. [resolvedKey] is already resolved
     * (Settings screen first, then `BuildConfig.ORS_API_KEY`, blank == none) — the ViewModel does that
     * because `BuildConfig` lives outside the domain layer.
     */
    fun updateKey(resolvedKey: String?) {
        val normalised = resolvedKey?.trim()?.takeIf { it.isNotEmpty() }
        if (normalised == activeKey) return
        activeKey = normalised
        routeService = factory.createForResolvedKey(normalised)
        cache.clear()
    }

    fun clearCache() = cache.clear()

    /** The chain currently in use, e.g. `[ORS, OSRM, HAVERSINE]` — used by the provider label + tests. */
    fun currentChain(): List<RouteSource> =
        (routeService as? RouteServiceProvider)?.services?.map { it.source } ?: emptyList()

    /**
     * Cold flow of the route between [from] and [to]. Collect it once per screen; it re-emits on every
     * endpoint change (debounced) and cancels superseded requests.
     */
    @OptIn(FlowPreview::class)
    fun route(
        from: Flow<LatLng?>,
        to: Flow<LatLng?>,
        debounceMillis: Long = DEBOUNCE_MILLIS,
    ): Flow<RouteState> = combine(from, to) { start, end -> start to end }
        .distinctUntilChanged()
        .debounce(debounceMillis)
        .mapLatest { (start, end) -> resolveRoute(start, end) }

    private suspend fun resolveRoute(start: LatLng?, end: LatLng?): RouteState {
        if (start == null || end == null) return RouteState.Idle

        val pair = RouteCache.Key(start, end)
        cache[pair]?.let { cached -> return RouteState.Success(cached, fromCache = true) }

        return try {
            val result = routeService.getRoute(start, end)
            cache[pair] = result
            RouteState.Success(result, fromCache = false)
        } catch (failure: Throwable) {
            // The chain already falls back to the offline estimate, so this is genuinely exceptional.
            RouteState.Failure(failure.message ?: "Routing failed")
        }
    }

    /** One-off lookup, e.g. a future driver-to-pickup ETA. */
    suspend fun plan(from: LatLng, to: LatLng): RouteResult = routeService.getRoute(from, to)

    companion object {
        const val DEBOUNCE_MILLIS: Long = 500L
        const val DEFAULT_CACHE_CAPACITY: Int = 16
    }
}

/**
 * Tiny LRU keyed by rounded coordinates. Rounded to ~1 m so map-library float noise does not defeat
 * the cache, while a deliberately moved marker still produces a new entry.
 */
private class RouteCache(private val capacity: Int) {

    class Key(start: LatLng, end: LatLng) {
        private val values = doubleArrayOf(
            start.latitude.roundedForCache(),
            start.longitude.roundedForCache(),
            end.latitude.roundedForCache(),
            end.longitude.roundedForCache(),
        )

        override fun equals(other: Any?): Boolean = other is Key && values.contentEquals(other.values)

        override fun hashCode(): Int = values.contentHashCode()
    }

    private val entries = object : LinkedHashMap<Key, RouteResult>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, RouteResult>?): Boolean =
            size > capacity
    }

    @Synchronized
    operator fun get(key: Key): RouteResult? = entries[key]

    @Synchronized
    operator fun set(key: Key, value: RouteResult) {
        entries[key] = value
    }

    @Synchronized
    fun clear() = entries.clear()
}

private fun Double.roundedForCache(): Double = Math.round(this * 10_000.0) / 10_000.0

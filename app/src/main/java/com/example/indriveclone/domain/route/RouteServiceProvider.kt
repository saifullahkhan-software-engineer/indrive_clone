package com.example.indriveclone.domain.route

import com.example.indriveclone.data.model.LatLng
import com.example.indriveclone.data.model.RouteResult
import com.example.indriveclone.data.model.RouteSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout

/**
 * The fallback chain. Tries every service in order and falls through to the next one on **any**
 * error, timeout ([attemptTimeoutMillis], 8 s by default) or non-2xx response. Only if every service
 * fails does it propagate the last error — with [HaversineRouteService] as the last element that
 * practically never happens, so the demo keeps working offline.
 *
 * Cancellation is never swallowed: if the *calling* coroutine is cancelled (the user dragged a marker
 * again), the cancellation is rethrown instead of being treated as a provider failure.
 */
class RouteServiceProvider(
    val services: List<RouteService>,
    private val attemptTimeoutMillis: Long = DEFAULT_ATTEMPT_TIMEOUT_MS,
) : RouteService {

    /** Source of the last element of the chain, i.e. the worst case. The actual one is in the result. */
    override val source: RouteSource = services.lastOrNull()?.source ?: RouteSource.HAVERSINE

    override suspend fun getRoute(from: LatLng, to: LatLng): RouteResult {
        require(services.isNotEmpty()) { "RouteServiceProvider needs at least one service" }
        var lastFailure: Throwable? = null

        for (service in services) {
            try {
                return withTimeout(attemptTimeoutMillis) { service.getRoute(from, to) }
            } catch (cancellation: CancellationException) {
                // A per-attempt timeout means "this provider is too slow" -> try the next one.
                // Anything else means our caller was cancelled -> propagate immediately.
                if (cancellation is TimeoutCancellationException && currentCoroutineContext().isActive) {
                    lastFailure = cancellation
                } else {
                    throw cancellation
                }
            } catch (failure: Throwable) {
                lastFailure = failure
            }
        }
        throw RoutingException(
            message = "All routing providers failed (tried ${services.joinToString { it.source.name }})",
            cause = lastFailure,
        )
    }

    companion object {
        const val DEFAULT_ATTEMPT_TIMEOUT_MS: Long = 8_000L
    }
}

/** Thrown only when every provider in the chain failed. */
class RoutingException(message: String, cause: Throwable?) : Exception(message, cause)

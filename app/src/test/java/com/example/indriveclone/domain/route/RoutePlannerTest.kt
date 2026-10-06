package com.example.indriveclone.domain.route

import com.example.indriveclone.data.model.LatLng
import com.example.indriveclone.data.model.RouteSource
import com.example.indriveclone.data.network.HttpResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The request policy: debounce a burst of marker updates, cancel superseded lookups, cache per
 * (from, to) pair, and drop the cache when the ORS key changes.
 */
class RoutePlannerTest {

    private val draggedPoint = LatLng(latitude = 43.30, longitude = 76.95)

    @Test
    fun `a burst of marker drags results in a single route request for the final pair`() = runTest {
        val backend = FakeRouteBackend()
        val planner = RoutePlanner(RouteServiceFactory(backend = backend))
        val from = MutableStateFlow<LatLng?>(A)
        val to = MutableStateFlow<LatLng?>(B)
        val states = mutableListOf<RouteState>()

        val job = launch { planner.route(from, to, debounceMillis = 500L).collect { states += it } }
        runCurrent()

        to.value = LatLng(latitude = 43.24, longitude = 76.90)
        advanceTimeBy(100)
        to.value = LatLng(latitude = 43.25, longitude = 76.91)
        advanceTimeBy(100)
        to.value = draggedPoint
        advanceUntilIdle()

        assertEquals("Superseded lookups must be collapsed", 1, backend.requestedUrls.size)
        assertTrue("The final pair must be the one that was routed", backend.requestedUrls.single().contains("76.95,43.3"))
        assertTrue(states.last() is RouteState.Success)
        job.cancel()
    }

    @Test
    fun `nothing is fetched until both endpoints exist`() = runTest {
        val backend = FakeRouteBackend()
        val planner = RoutePlanner(RouteServiceFactory(backend = backend))
        val from = MutableStateFlow<LatLng?>(A)
        val to = MutableStateFlow<LatLng?>(null)
        val states = mutableListOf<RouteState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            planner.route(from, to, debounceMillis = 0L).collect { states += it }
        }
        advanceUntilIdle()

        assertEquals(0, backend.requestedUrls.size)
        assertEquals(listOf<RouteState>(RouteState.Idle), states)

        to.value = B
        advanceUntilIdle()

        assertEquals(1, backend.requestedUrls.size)
        job.cancel()
    }

    @Test
    fun `the same pair is served from the cache`() = runTest {
        val backend = FakeRouteBackend()
        val planner = RoutePlanner(RouteServiceFactory(backend = backend))
        val from = MutableStateFlow<LatLng?>(A)
        val to = MutableStateFlow<LatLng?>(B)
        val states = mutableListOf<RouteState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            planner.route(from, to, debounceMillis = 0L).collect { states += it }
        }
        advanceUntilIdle()
        assertEquals(1, backend.requestedUrls.size)

        to.value = draggedPoint
        advanceUntilIdle()
        assertEquals(2, backend.requestedUrls.size)

        to.value = B
        advanceUntilIdle()

        assertEquals("The third lookup must be answered by the cache", 2, backend.requestedUrls.size)
        val last = states.last() as RouteState.Success
        assertTrue(last.fromCache)
        job.cancel()
    }

    @Test
    fun `changing the ORS key clears the cache and rebuilds the chain`() = runTest {
        val backend = FakeRouteBackend()
        val planner = RoutePlanner(RouteServiceFactory(backend = backend))
        val from = MutableStateFlow<LatLng?>(A)
        val to = MutableStateFlow<LatLng?>(B)

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            planner.route(from, to, debounceMillis = 0L).collect { }
        }
        advanceUntilIdle()

        assertEquals(listOf(RouteSource.OSRM, RouteSource.HAVERSINE), planner.currentChain())
        assertEquals(1, backend.requestedUrls.size)

        planner.updateKey("brand-new-key")

        assertEquals(
            listOf(RouteSource.ORS, RouteSource.OSRM, RouteSource.HAVERSINE),
            planner.currentChain(),
        )

        // A fresh chain also means a cold cache: the same pair is fetched again.
        to.value = draggedPoint
        advanceUntilIdle()
        to.value = B
        advanceUntilIdle()

        val osrmCalls = backend.requestedUrls.count { it.contains("76.92861") }
        assertEquals(3, osrmCalls)
        job.cancel()
    }

    @Test
    fun `a dead network still produces a route thanks to the offline fallback`() = runTest {
        val offlineBackend = object : RouteBackend {
            override suspend fun get(url: String, headers: Map<String, String>) = HttpResult(500, "nope")

            override suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String>) =
                HttpResult(500, "nope")
        }
        val planner = RoutePlanner(RouteServiceFactory(backend = offlineBackend))
        val states = mutableListOf<RouteState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            planner.route(MutableStateFlow<LatLng?>(A), MutableStateFlow<LatLng?>(B), 0L)
                .collect { states += it }
        }
        advanceUntilIdle()

        val success = states.last() as RouteState.Success
        assertEquals(RouteSource.HAVERSINE, success.result.source)
        assertTrue(success.result.distanceMeters > 0)
        job.cancel()
    }
}

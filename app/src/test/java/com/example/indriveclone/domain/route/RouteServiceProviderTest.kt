package com.example.indriveclone.domain.route

import com.example.indriveclone.data.model.RouteSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fallback chain is the heart of "the demo never breaks": with a key ORS goes first, without a key
 * OSRM goes first, and Haversine always catches the ball.
 */
class RouteServiceProviderTest {

    @Test
    fun `no key - the chain is OSRM then the offline estimate`() = runTest {
        val provider = RouteServiceFactory(backend = FakeRouteBackend()).createForResolvedKey(null)

        assertEquals(
            listOf(RouteSource.OSRM, RouteSource.HAVERSINE),
            provider.services.map { it.source },
        )

        val result = provider.getRoute(A, B)
        assertEquals(RouteSource.OSRM, result.source)
        assertEquals(1_500.0, result.distanceMeters, 1e-6)
    }

    @Test
    fun `with a key - the chain is ORS, OSRM then the offline estimate`() {
        val provider = RouteServiceFactory().createForResolvedKey("test-key")

        assertEquals(
            listOf(RouteSource.ORS, RouteSource.OSRM, RouteSource.HAVERSINE),
            provider.services.map { it.source },
        )
    }

    @Test
    fun `with a key - when ORS fails OSRM is used`() = runTest {
        val ors = FakeRouteService.fail(RouteSource.ORS, message = "HTTP 429")
        val osrm = FakeRouteService.succeed(RouteSource.OSRM)
        val haversine = FakeRouteService.succeed(RouteSource.HAVERSINE)
        val provider = RouteServiceProvider(listOf(ors, osrm, haversine))

        val result = provider.getRoute(A, B)

        assertEquals(RouteSource.OSRM, result.source)
        assertEquals(1, ors.callCount)
        assertEquals(1, osrm.callCount)
        assertEquals(0, haversine.callCount)
    }

    @Test
    fun `without a key - OSRM failure falls back to the offline estimate`() = runTest {
        val osrm = FakeRouteService.fail(RouteSource.OSRM, message = "HTTP 500")
        val haversine = FakeRouteService.succeed(RouteSource.HAVERSINE)
        val provider = RouteServiceProvider(listOf(osrm, haversine))

        val result = provider.getRoute(A, B)

        assertEquals(RouteSource.HAVERSINE, result.source)
        assertEquals(1, osrm.callCount)
        assertEquals(1, haversine.callCount)
    }

    @Test
    fun `a slow provider times out and the next one answers`() = runTest {
        val slowOrs = FakeRouteService.slow(RouteSource.ORS, delayMillis = 30_000L)
        val osrm = FakeRouteService.succeed(RouteSource.OSRM)
        val provider = RouteServiceProvider(listOf(slowOrs, osrm), attemptTimeoutMillis = 100L)

        val result = provider.getRoute(A, B)

        assertEquals(RouteSource.OSRM, result.source)
    }

    @Test
    fun `using the real Haversine service as the last resort never fails`() = runTest {
        val provider = RouteServiceProvider(
            listOf(
                FakeRouteService.fail(RouteSource.ORS),
                FakeRouteService.fail(RouteSource.OSRM),
                HaversineRouteService(),
            ),
        )

        val result = provider.getRoute(A, B)

        assertEquals(RouteSource.HAVERSINE, result.source)
        assertTrue(result.distanceMeters > 0)
        assertEquals(2, result.polyline.size)
    }

    @Test
    fun `when every provider fails the chain reports a routing error naming them`() = runTest {
        val provider = RouteServiceProvider(
            listOf(
                FakeRouteService.fail(RouteSource.OSRM),
                FakeRouteService.fail(RouteSource.HAVERSINE),
            ),
        )

        val thrown = runCatching { provider.getRoute(A, B) }.exceptionOrNull()

        assertTrue(thrown is RoutingException)
        assertTrue(thrown!!.message!!.contains("OSRM"))
        assertTrue(thrown.message!!.contains("HAVERSINE"))
    }

    @Test
    fun `cancelling the caller is never mistaken for a provider failure`() = runTest {
        val never = FakeRouteService(RouteSource.ORS, neverReturns = true)
        val fallback = RecordingRouteService(RouteSource.OSRM)
        val provider = RouteServiceProvider(listOf(never, fallback))

        var cancelled = false
        val job = launch {
            try {
                provider.getRoute(A, B)
            } catch (cancellation: CancellationException) {
                cancelled = true
            }
        }
        runCurrent()
        job.cancel()
        job.join()

        assertTrue("Cancellation must propagate", cancelled)
        assertFalse("A cancelled request must not fall through", fallback.callCount > 0)
    }

    @Test
    fun `ORS is skipped when both the settings key and the build config key are blank`() {
        val factory = RouteServiceFactory()

        val provider = factory.create(settingsKey = "   ", buildConfigKey = "")

        assertEquals(
            listOf(RouteSource.OSRM, RouteSource.HAVERSINE),
            provider.services.map { it.source },
        )
    }

    @Test
    fun `the settings key wins over the build config key`() {
        val factory = RouteServiceFactory()

        assertEquals("from-settings", factory.resolveOrsKey("from-settings", "from-local-properties"))
        assertEquals("from-local-properties", factory.resolveOrsKey("  ", "from-local-properties"))
        assertEquals(null, factory.resolveOrsKey("", "   "))
        assertEquals(null, factory.resolveOrsKey(null, null))
    }

    @Test
    fun `a self hosted OSRM base url is honoured and needs no key`() = runTest {
        val backend = FakeRouteBackend()
        val provider = RouteServiceFactory(osrmBaseUrl = "https://osrm.example.com", backend = backend)
            .createForResolvedKey(null)

        provider.getRoute(A, B)

        assertTrue(backend.requestedUrls.single().startsWith("https://osrm.example.com/route/v1/driving/"))
    }
}

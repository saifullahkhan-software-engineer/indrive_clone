package com.example.indriveclone.domain.route

import com.example.indriveclone.data.model.RouteSource
import com.example.indriveclone.data.network.HttpResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-format tests: the request URL/body and the (longitude first!) coordinate order are exactly what
 * the public APIs expect, and a bad response is an exception rather than a silently wrong route.
 */
class RouteServiceParsingTest {

    @Test
    fun `OSRM request uses the documented path, query and lon-lat order`() = runTest {
        val backend = FakeRouteBackend()
        val service = OsrmRouteService(backend = backend)

        service.getRoute(A, B)

        val url = backend.requestedUrls.single()
        assertTrue(url.startsWith("https://router.project-osrm.org/route/v1/driving/"))
        assertTrue(url.contains("76.889709,43.238949;76.92861,43.25667"))
        assertTrue(url.contains("overview=full"))
        assertTrue(url.contains("geometries=geojson"))
    }

    @Test
    fun `OSRM response maps to distance, duration and a lat-lng polyline`() = runTest {
        val service = OsrmRouteService(backend = FakeRouteBackend())

        val result = service.getRoute(A, B)

        assertEquals(1_500.0, result.distanceMeters, 1e-6)
        assertEquals(300.0, result.durationSeconds, 1e-6)
        assertEquals(RouteSource.OSRM, result.source)
        assertEquals(2, result.polyline.size)
        // GeoJSON is [lon, lat]; the app model is LatLng(lat, lng) — this assertion guards the swap.
        assertEquals(43.238949, result.polyline.first().latitude, 1e-9)
        assertEquals(76.889709, result.polyline.first().longitude, 1e-9)
    }

    @Test
    fun `OSRM non-2xx response throws so the chain can fall through`() = runTest {
        val backend = FakeRouteBackend().apply { stub("route/v1", HttpResult(429, "slow down")) }
        val service = OsrmRouteService(backend = backend)

        val thrown = runCatching { service.getRoute(A, B) }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        assertTrue(thrown!!.message!!.contains("429"))
    }

    @Test
    fun `OSRM response without a route throws`() = runTest {
        val backend = FakeRouteBackend().apply {
            stub("route/v1", HttpResult(200, FakeRouteBackend.OSRM_EMPTY_BODY))
        }

        val thrown = runCatching { OsrmRouteService(backend = backend).getRoute(A, B) }.exceptionOrNull()

        assertTrue(thrown != null)
    }

    @Test
    fun `OSRM geometry with fewer than two points is rejected`() = runTest {
        val backend = FakeRouteBackend().apply {
            stub(
                "route/v1",
                HttpResult(200, """{"code":"Ok","routes":[{"distance":10,"duration":3,"geometry":{"coordinates":[[76.889709,43.238949]]}}]}"""),
            )
        }

        val thrown = runCatching { OsrmRouteService(backend = backend).getRoute(A, B) }.exceptionOrNull()

        assertTrue(thrown != null)
    }

    @Test
    fun `ORS posts lon-lat coordinates with the key in the Authorization header`() = runTest {
        val backend = FakeRouteBackend().apply { stub("openrouteservice", HttpResult(200, ORS_OK_BODY)) }
        val service = OpenRouteServiceRouteService(apiKey = "secret-key", backend = backend)

        val result = service.getRoute(A, B)

        assertTrue(backend.requestedUrls.single().endsWith("/v2/directions/driving-car/geojson"))
        assertEquals("secret-key", backend.requestedHeaders.single()["Authorization"])
        assertEquals(
            """{"coordinates":[[76.889709,43.238949],[76.92861,43.25667]]}""",
            backend.lastPostBody,
        )
        assertEquals(2_400.0, result.distanceMeters, 1e-6)
        assertEquals(420.0, result.durationSeconds, 1e-6)
        assertEquals(RouteSource.ORS, result.source)
        assertEquals(43.238949, result.polyline.first().latitude, 1e-9)
        assertEquals(76.889709, result.polyline.first().longitude, 1e-9)
    }

    @Test
    fun `ORS error responses throw`() = runTest {
        val backend = FakeRouteBackend().apply { stub("openrouteservice", HttpResult(403, """{"error":"quota"}""")) }

        val thrown = runCatching {
            OpenRouteServiceRouteService(apiKey = "secret-key", backend = backend).getRoute(A, B)
        }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        assertTrue(thrown!!.message!!.contains("403"))
    }

    @Test
    fun `Haversine estimates a road distance and a 30 km per hour duration`() = runTest {
        val service = HaversineRouteService()

        val result = service.getRoute(A, B)

        assertEquals(RouteSource.HAVERSINE, result.source)
        assertEquals(listOf(A, B), result.polyline)
        val straightLine = HaversineRouteService.haversineMeters(A, B)
        assertEquals(straightLine * 1.3, result.distanceMeters, 1e-6)
        assertEquals(result.distanceMeters / (30.0 / 3.6), result.durationSeconds, 1e-6)
    }

    private companion object {
        /** Minimal but realistic ORS GeoJSON response. */
        const val ORS_OK_BODY = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "summary": { "distance": 2400.0, "duration": 420.0 } },
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[76.889709, 43.238949], [76.928610, 43.256670]]
                  }
                }
              ]
            }
        """
    }
}

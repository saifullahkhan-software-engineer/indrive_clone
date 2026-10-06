package com.example.indriveclone.domain.route

import com.example.indriveclone.data.model.LatLng
import com.example.indriveclone.data.model.RouteResult
import com.example.indriveclone.data.model.RouteSource
import com.example.indriveclone.data.network.HttpResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/** A [RouteBackend] that answers from a canned table and records what was asked. */
class FakeRouteBackend(private val defaultResponse: HttpResult = HttpResult(200, OSRM_OK_BODY)) : RouteBackend {

    private val stubs = mutableMapOf<String, HttpResult>()

    val requestedUrls = mutableListOf<String>()
    val requestedHeaders = mutableListOf<Map<String, String>>()

    var lastPostBody: String? = null
        private set

    /** Registers a response for every URL containing [urlSubstring]. */
    fun stub(urlSubstring: String, response: HttpResult) {
        stubs[urlSubstring] = response
    }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResult = answer(url, headers)

    override suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String>): HttpResult {
        lastPostBody = jsonBody
        return answer(url, headers)
    }

    private fun answer(url: String, headers: Map<String, String>): HttpResult {
        requestedUrls += url
        requestedHeaders += headers
        return stubs.entries.firstOrNull { (needle, _) -> url.contains(needle) }?.value ?: defaultResponse
    }

    companion object {
        /** Minimal but realistic OSRM response: two GeoJSON positions in (lon, lat) order. */
        const val OSRM_OK_BODY: String = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": 1500.0,
                  "duration": 300.0,
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[76.889709, 43.238949], [76.928610, 43.256670]]
                  }
                }
              ]
            }
        """

        const val OSRM_EMPTY_BODY: String = """{"code":"NoRoute","routes":[]}"""
    }
}

/** A [RouteService] with programmable behaviour. */
class FakeRouteService(
    override val source: RouteSource,
    private val result: RouteResult? = null,
    private val failure: Throwable? = null,
    private val delayMillis: Long = 0L,
    private val neverReturns: Boolean = false,
) : RouteService {

    var callCount = 0
        private set

    override suspend fun getRoute(from: LatLng, to: LatLng): RouteResult {
        callCount++
        if (delayMillis > 0) delay(delayMillis)
        if (neverReturns) awaitCancellation()
        failure?.let { throw it }
        return result ?: error("FakeRouteService without a result")
    }

    companion object {
        fun succeed(
            source: RouteSource,
            distanceMeters: Double = 1_000.0,
            polyline: List<LatLng> = listOf(A, B),
        ) = FakeRouteService(
            source = source,
            result = RouteResult(
                distanceMeters = distanceMeters,
                durationSeconds = distanceMeters / 10.0,
                polyline = polyline,
                source = source,
            ),
        )

        fun fail(source: RouteSource, message: String = "boom") =
            FakeRouteService(source = source, failure = IllegalStateException(message))

        fun slow(source: RouteSource, delayMillis: Long) =
            FakeRouteService(source = source, delayMillis = delayMillis)
    }
}

/** A [RouteService] that only counts how often it was reached. */
class RecordingRouteService(
    override val source: RouteSource,
    private val result: RouteResult = RouteResult(500.0, 60.0, listOf(A, B), RouteSource.HAVERSINE),
) : RouteService {
    var callCount = 0
        private set

    override suspend fun getRoute(from: LatLng, to: LatLng): RouteResult {
        callCount++
        return result.copy(source = source)
    }
}

val A = LatLng(latitude = 43.238949, longitude = 76.889709)
val B = LatLng(latitude = 43.256670, longitude = 76.928610)

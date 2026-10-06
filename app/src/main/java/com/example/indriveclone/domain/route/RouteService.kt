package com.example.indriveclone.domain.route

import com.example.indriveclone.data.model.LatLng
import com.example.indriveclone.data.model.RouteResult
import com.example.indriveclone.data.model.RouteSource

/**
 * One routing provider. Implementations must either return a [RouteResult] whose `source` describes
 * themselves, or throw — the provider chain in [RouteServiceProvider] turns a throw into a fallback.
 */
interface RouteService {
    /** Which provider this is, also written into [RouteResult.source]. */
    val source: RouteSource

    suspend fun getRoute(from: LatLng, to: LatLng): RouteResult
}

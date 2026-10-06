package com.example.indriveclone.domain.route

import com.example.indriveclone.data.network.Http
import com.example.indriveclone.data.network.HttpResult
import com.example.indriveclone.data.network.getCancellable
import com.example.indriveclone.data.network.postJsonCancellable
import okhttp3.OkHttpClient

/**
 * The HTTP seam used by every route service. Production uses [OkHttpRouteBackend]; tests inject a
 * fake, so no unit test ever touches the network.
 */
interface RouteBackend {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult

    suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String> = emptyMap()): HttpResult
}

/** Production backend: OkHttp with the timeouts configured in [Http]. */
class OkHttpRouteBackend(
    private val client: OkHttpClient = Http.client,
) : RouteBackend {

    override suspend fun get(url: String, headers: Map<String, String>): HttpResult =
        client.getCancellable(url, headers)

    override suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String>): HttpResult =
        client.postJsonCancellable(url, jsonBody, headers)
}

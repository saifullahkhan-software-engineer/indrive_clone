package com.example.indriveclone.data.network

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Single OkHttp client for the whole app.
 *
 * Timeouts are the first line of defence: the route provider chain also wraps every attempt in an
 * 8 s [kotlinx.coroutines.withTimeout], but a fast socket/read timeout keeps a dead server from
 * stalling the UI in the first place.
 */
object Http {

    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        // Nominatim asks for an identifying client; tile requests set their own UA in osmdroid.
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "inDriveClone-Demo/1.0 (Android; demo app)")
                    .header("Accept", "application/json")
                    .build(),
            )
        }
        .build()
}

/** Body as text plus the HTTP code, so callers can fail loudly on 4xx/5xx. */
class HttpResult(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/**
 * Suspending GET that is **cancellable**: cancelling the coroutine cancels the OkHttp call, which is
 * what stops a superseded route request when the user drags a marker again.
 */
suspend fun OkHttpClient.getCancellable(url: String, headers: Map<String, String> = emptyMap()): HttpResult =
    callCancellable {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        builder.build()
    }

/** Suspending POST with a JSON body, cancellable in the same way. */
suspend fun OkHttpClient.postJsonCancellable(
    url: String,
    jsonBody: String,
    headers: Map<String, String> = emptyMap(),
): HttpResult = callCancellable {
    val builder = Request.Builder()
        .url(url)
        .post(jsonBody.toRequestBody(Http.jsonMediaType))
    headers.forEach { (name, value) -> builder.header(name, value) }
    builder.build()
}

private suspend fun OkHttpClient.callCancellable(requestFactory: () -> Request): HttpResult =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(requestFactory())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use { HttpResult(it.code, it.body?.string().orEmpty()) }
                val self: CancellableContinuation<HttpResult> = continuation
                if (self.isActive) self.resume(result)
            }
        })
    }

/** URL-encodes a query parameter value (Nominatim search). */
internal fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

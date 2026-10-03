package com.personal.budget.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

val RemoteJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
    isLenient = false
}

fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .callTimeout(60, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

/** Network unreachable, DNS failure, timeout… The caller should treat this as "offline". */
class OfflineException(cause: Throwable) : IOException(cause.message ?: "Network unavailable", cause)

/** The server answered with an error status. */
class HttpException(val code: Int, override val message: String, val body: String? = null) : IOException("HTTP $code: $message")

/** The refresh token is no longer valid: the user must sign in again (local data is kept). */
class SessionExpiredException(message: String = "Session expired. Sign in again to sync.") : IOException(message)

/** A malformed response body. */
class MalformedResponseException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class HttpResult(val code: Int, val body: String, val headers: okhttp3.Headers)

/** Executes on the IO dispatcher, cancelling the call if the coroutine is cancelled. */
suspend fun OkHttpClient.send(request: Request): HttpResult = withContext(Dispatchers.IO) {
    try {
        suspendCancellableCoroutine { cont ->
            val call = newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(OfflineException(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { HttpResult(it.code, it.body.string(), it.headers) }
                    } catch (e: IOException) {
                        if (cont.isActive) cont.resumeWithException(OfflineException(e))
                        return
                    }
                    if (cont.isActive) cont.resume(result)
                }
            })
        }
    } catch (e: OfflineException) {
        throw e
    }
}

/** Pulls a human message out of GoTrue / PostgREST error bodies. */
fun errorMessage(body: String, fallback: String): String {
    val obj = runCatching { RemoteJson.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return fallback
    for (key in listOf("error_description", "msg", "message", "error", "hint", "details")) {
        val v = obj[key]?.let { el -> runCatching { el.jsonPrimitive.contentOrNull }.getOrNull() }
        if (!v.isNullOrBlank()) return v
    }
    return fallback
}

fun parseJson(body: String): JsonElement = try {
    RemoteJson.parseToJsonElement(body)
} catch (e: Exception) {
    throw MalformedResponseException("Unexpected response from server", e)
}

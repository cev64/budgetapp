package com.personal.budget.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Supplies a valid access token, refreshing when needed (see AuthRepository). */
interface TokenProvider {
    suspend fun accessToken(): String

    /** Called after a 401 made with [rejectedToken]; returns a fresh token or throws. */
    suspend fun refreshAfterUnauthorized(rejectedToken: String): String
}

/** Minimal PostgREST client: /rest/v1/{table}. */
class RestApi(
    private val config: SupabaseConfig,
    private val http: OkHttpClient,
    private val tokens: TokenProvider,
) {
    /** Bulk upsert. Returns the rows as stored (with the server's updated_at). */
    suspend fun upsert(table: String, onConflict: String, rows: JsonArray): List<JsonObject> {
        val url = base(table).newBuilder().addQueryParameter("on_conflict", onConflict).build()
        val body = rows.toString()
        return authorized { token ->
            Request.Builder()
                .url(url)
                .header("apikey", config.publishableKey)
                .header("Authorization", "Bearer $token")
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
        }
    }

    /** GET with PostgREST query parameters, e.g. updated_at=gt.<ts>, order, limit. */
    suspend fun select(table: String, params: List<Pair<String, String>>): List<JsonObject> {
        val url = base(table).newBuilder().apply {
            addQueryParameter("select", "*")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        return authorized { token ->
            Request.Builder()
                .url(url)
                .header("apikey", config.publishableKey)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        }
    }

    /** POST /rest/v1/rpc/{name} with a JSON body; returns the result rows (object results are wrapped). */
    suspend fun rpc(name: String, body: JsonObject = JsonObject(emptyMap())): List<JsonObject> {
        val url = "${config.url}/rest/v1/rpc/$name".toHttpUrl()
        return authorized(allowObject = true) { token ->
            Request.Builder()
                .url(url)
                .header("apikey", config.publishableKey)
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
        }
    }

    private fun base(table: String): HttpUrl = "${config.url}/rest/v1/$table".toHttpUrl()

    private suspend fun authorized(allowObject: Boolean = false, build: (String) -> Request): List<JsonObject> {
        val token = tokens.accessToken()
        var res = http.send(build(token))
        if (res.code == 401) {
            val fresh = tokens.refreshAfterUnauthorized(token)
            res = http.send(build(fresh))
        }
        if (res.code !in 200..299) {
            val fallback = when (res.code) {
                401, 403 -> "Not authorized"
                429 -> "Rate limited by the server"
                in 500..599 -> "Server error (${res.code})"
                else -> "Request failed (${res.code})"
            }
            throw HttpException(res.code, errorMessage(res.body, fallback), res.body)
        }
        if (res.body.isBlank()) return emptyList()
        val parsed = parseJson(res.body)
        if (allowObject && parsed is JsonObject) return listOf(parsed)
        if (allowObject && parsed !is JsonArray) return emptyList()
        val arr = parsed as? JsonArray ?: throw MalformedResponseException("Expected a JSON array from the server")
        return arr.map { it as? JsonObject ?: throw MalformedResponseException("Expected JSON objects") }
    }
}

package com.personal.budget.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class GoTrueUser(
    val id: String,
    val email: String? = null,
    @SerialName("confirmation_sent_at") val confirmationSentAt: String? = null,
    @SerialName("email_confirmed_at") val emailConfirmedAt: String? = null,
)

@Serializable
data class GoTrueSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val user: GoTrueUser,
)

sealed interface SignUpResult {
    data class SignedIn(val session: GoTrueSession) : SignUpResult
    /** Email confirmation is required before the first sign-in. */
    data class ConfirmationRequired(val email: String) : SignUpResult
}

/** Minimal GoTrue (Supabase Auth) client: /auth/v1/... */
class AuthApi(private val config: SupabaseConfig, private val http: OkHttpClient) {

    suspend fun signIn(email: String, password: String): GoTrueSession =
        session(post("token?grant_type=password", buildJsonObject { put("email", email); put("password", password) }))

    suspend fun refresh(refreshToken: String): GoTrueSession =
        session(post("token?grant_type=refresh_token", buildJsonObject { put("refresh_token", refreshToken) }))

    suspend fun signUp(email: String, password: String): SignUpResult {
        val obj = post("signup", buildJsonObject { put("email", email); put("password", password) })
        return if (obj.containsKey("access_token")) {
            SignUpResult.SignedIn(session(obj))
        } else {
            SignUpResult.ConfirmationRequired(email)
        }
    }

    suspend fun recover(email: String) {
        post("recover", buildJsonObject { put("email", email) })
    }

    /** Best effort: revokes the refresh token server-side. */
    suspend fun logout(accessToken: String) {
        runCatching {
            val req = Request.Builder()
                .url("${config.url}/auth/v1/logout")
                .header("apikey", config.publishableKey)
                .header("Authorization", "Bearer $accessToken")
                .post("{}".toRequestBody(JSON_MEDIA))
                .build()
            http.send(req)
        }
    }

    private fun session(obj: JsonObject): GoTrueSession = try {
        RemoteJson.decodeFromJsonElement(GoTrueSession.serializer(), obj)
    } catch (e: Exception) {
        throw MalformedResponseException("Unexpected sign-in response", e)
    }

    private suspend fun post(path: String, body: JsonObject): JsonObject {
        check(config.isConfigured) { "Backend not configured" }
        val req = Request.Builder()
            .url("${config.url}/auth/v1/$path")
            // New-style publishable keys (sb_publishable_…) are not JWTs: send them only as
            // `apikey`. Authorization: Bearer is reserved for the user's access token.
            .header("apikey", config.publishableKey)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        val res = http.send(req)
        if (res.code !in 200..299) {
            val fallback = when (res.code) {
                400 -> "Invalid email or password"
                422 -> "That request was rejected"
                429 -> "Too many attempts. Wait a minute and try again."
                else -> "Sign-in service error (${res.code})"
            }
            throw HttpException(res.code, errorMessage(res.body, fallback), res.body)
        }
        if (res.body.isBlank()) return JsonObject(emptyMap())
        return parseJson(res.body) as? JsonObject ?: JsonObject(emptyMap())
    }
}

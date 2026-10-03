package com.personal.budget.data.repository

import com.personal.budget.data.local.AuthStore
import com.personal.budget.data.local.PrefsStore
import com.personal.budget.data.local.StoredSession
import com.personal.budget.data.remote.AuthApi
import com.personal.budget.data.remote.GoTrueSession
import com.personal.budget.data.remote.HttpException
import com.personal.budget.data.remote.SessionExpiredException
import com.personal.budget.data.remote.SignUpResult
import com.personal.budget.data.remote.SupabaseConfig
import com.personal.budget.data.remote.TokenProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState

    /**
     * Local data belongs to [userId]. [sessionValid] = false means the tokens are gone or the
     * refresh token was rejected: the app keeps working offline and asks to sign in again to sync.
     */
    data class SignedIn(val userId: String, val email: String, val sessionValid: Boolean) : AuthState
}

/**
 * Owns the GoTrue session and hands out access tokens.
 *
 * Refresh is single-flight: every token request and every 401 retry goes through [mutex], so
 * two concurrent requests that both see an expired token (or both get a 401) trigger exactly one
 * refresh. Supabase rotates refresh tokens, so a second refresh with the old token would fail.
 * [refreshAfterUnauthorized] compares the rejected token with the current one: if another caller
 * already refreshed, the new token is returned without another round-trip.
 */
class AuthRepository(
    private val config: SupabaseConfig,
    private val api: AuthApi,
    private val store: AuthStore,
    private val prefs: PrefsStore,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : TokenProvider {
    private val mutex = Mutex()
    @Volatile private var cached: StoredSession? = null
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val isConfigured: Boolean get() = config.isConfigured

    private val ready = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** Suspends until [init] has loaded the stored session. */
    suspend fun awaitReady() = ready.await()

    suspend fun init() {
        try {
            load()
        } finally {
            ready.complete(Unit)
        }
    }

    private suspend fun load() {
        val s = store.load()
        cached = s
        val p = prefs.current()
        _state.value = when {
            s != null -> AuthState.SignedIn(s.userId, s.email, sessionValid = true)
            p.ownerUserId != null -> AuthState.SignedIn(p.ownerUserId, p.ownerEmail.orEmpty(), sessionValid = false)
            else -> AuthState.SignedOut
        }
    }

    fun currentUserId(): String? = cached?.userId
    fun currentEmail(): String? = cached?.email
    fun hasSession(): Boolean = cached != null

    suspend fun signIn(email: String, password: String): GoTrueSession = api.signIn(email.trim(), password)

    suspend fun signUp(email: String, password: String): SignUpResult = api.signUp(email.trim(), password)

    suspend fun sendPasswordReset(email: String) = api.recover(email.trim())

    /** Stores a fresh session (after sign-in/sign-up) and marks the user signed in. */
    suspend fun adopt(session: GoTrueSession) = mutex.withLock {
        val stored = session.toStored()
        store.save(stored)
        cached = stored
        _state.value = AuthState.SignedIn(stored.userId, stored.email, sessionValid = true)
    }

    /** Forgets the session. Local data handling is the caller's job (see AccountManager). */
    suspend fun signOut() {
        val token = mutex.withLock { cached?.accessToken }
        if (token != null && config.isConfigured) api.logout(token)
        mutex.withLock {
            store.clear()
            cached = null
            _state.value = AuthState.SignedOut
        }
    }

    override suspend fun accessToken(): String = mutex.withLock {
        val s = cached ?: throw SessionExpiredException("Not signed in")
        if (s.expiresAt - EXPIRY_MARGIN_SECONDS > nowSeconds()) s.accessToken else refreshLocked(s).accessToken
    }

    override suspend fun refreshAfterUnauthorized(rejectedToken: String): String = mutex.withLock {
        val s = cached ?: throw SessionExpiredException("Not signed in")
        if (s.accessToken != rejectedToken) s.accessToken else refreshLocked(s).accessToken
    }

    /** Must hold [mutex]. Network errors propagate (offline); a rejected refresh token ends the session. */
    private suspend fun refreshLocked(s: StoredSession): StoredSession {
        val fresh = try {
            api.refresh(s.refreshToken)
        } catch (e: HttpException) {
            if (e.code in 400..403) {
                // Refresh token revoked/expired/reused: keep local data, ask the user to sign in again.
                store.clear()
                cached = null
                _state.value = AuthState.SignedIn(s.userId, s.email, sessionValid = false)
                throw SessionExpiredException()
            }
            throw e
        }
        val stored = fresh.toStored(fallbackEmail = s.email)
        store.save(stored)
        cached = stored
        _state.value = AuthState.SignedIn(stored.userId, stored.email, sessionValid = true)
        return stored
    }

    private fun GoTrueSession.toStored(fallbackEmail: String = ""): StoredSession = StoredSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt ?: (nowSeconds() + expiresIn),
        userId = user.id,
        email = user.email ?: fallbackEmail,
    )

    companion object {
        const val EXPIRY_MARGIN_SECONDS = 60L
    }
}

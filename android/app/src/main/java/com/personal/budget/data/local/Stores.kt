package com.personal.budget.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/*
 * Two DataStore files:
 *   auth.preferences_pb  tokens only. EXCLUDED from Android backup (see the backup rules in res/xml):
 *                        a restored device must sign in again.
 *   prefs.preferences_pb appearance + sync bookkeeping. Included in backup.
 * Keys are never renamed; new keys get defaults (docs/ANDROID.md "DataStore compatibility").
 */
private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")
private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "prefs")

data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch seconds. */
    val expiresAt: Long,
    val userId: String,
    val email: String,
)

class AuthStore(context: Context) {
    private val store = context.applicationContext.authDataStore

    private object Keys {
        val access = stringPreferencesKey("access_token")
        val refresh = stringPreferencesKey("refresh_token")
        val expiresAt = longPreferencesKey("expires_at")
        val userId = stringPreferencesKey("user_id")
        val email = stringPreferencesKey("email")
    }

    suspend fun load(): StoredSession? = store.data.first().let { p ->
        val access = p[Keys.access] ?: return null
        val refresh = p[Keys.refresh] ?: return null
        StoredSession(access, refresh, p[Keys.expiresAt] ?: 0, p[Keys.userId] ?: return null, p[Keys.email].orEmpty())
    }

    suspend fun save(s: StoredSession) {
        store.edit {
            it[Keys.access] = s.accessToken
            it[Keys.refresh] = s.refreshToken
            it[Keys.expiresAt] = s.expiresAt
            it[Keys.userId] = s.userId
            it[Keys.email] = s.email
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }
}

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"), LIGHT("light", "Light"), DARK("dark", "Dark");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

data class AppPrefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Off by default so Android matches the web app's colours. */
    val dynamicColor: Boolean = false,
    /** Epoch millis of the last successful sync, 0 = never. */
    val lastSyncAt: Long = 0,
    /** The user whose data is in the local database (to detect a different account signing in). */
    val ownerUserId: String? = null,
    /** Email of the owner, so an expired session can be renewed without retyping it. */
    val ownerEmail: String? = null,
)

class PrefsStore(context: Context) {
    private val store = context.applicationContext.prefsDataStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val ownerUserId = stringPreferencesKey("owner_user_id")
        val ownerEmail = stringPreferencesKey("owner_email")
    }

    val prefs: Flow<AppPrefs> = store.data.map { p ->
        AppPrefs(
            theme = ThemeMode.from(p[Keys.theme]),
            dynamicColor = p[Keys.dynamicColor] ?: false,
            lastSyncAt = p[Keys.lastSyncAt] ?: 0,
            ownerUserId = p[Keys.ownerUserId],
            ownerEmail = p[Keys.ownerEmail],
        )
    }

    suspend fun current(): AppPrefs = prefs.first()

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.key }
    suspend fun setDynamicColor(on: Boolean) = store.edit { it[Keys.dynamicColor] = on }
    suspend fun setLastSyncAt(millis: Long) = store.edit { it[Keys.lastSyncAt] = millis }
    suspend fun setOwner(userId: String?, email: String?) = store.edit {
        if (userId == null) it.remove(Keys.ownerUserId) else it[Keys.ownerUserId] = userId
        if (email == null) it.remove(Keys.ownerEmail) else it[Keys.ownerEmail] = email
        if (userId == null) it.remove(Keys.lastSyncAt)
    }
}

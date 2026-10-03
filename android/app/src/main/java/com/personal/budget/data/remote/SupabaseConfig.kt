package com.personal.budget.data.remote

import com.personal.budget.BuildConfig

/**
 * Public client config baked in at build time from config/supabase.json (or the
 * SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY env vars). The publishable key is designed to
 * ship in clients; row-level security protects the data.
 */
data class SupabaseConfig(val url: String, val publishableKey: String) {
    val isConfigured: Boolean get() = url.isNotBlank() && publishableKey.isNotBlank()

    companion object {
        fun fromBuildConfig() = SupabaseConfig(BuildConfig.SUPABASE_URL.trimEnd('/'), BuildConfig.SUPABASE_KEY)
    }
}

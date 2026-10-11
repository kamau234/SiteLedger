package com.siteledger.app.data.remote

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Owns the Supabase client. When no project URL/key is configured the app runs in
 * local-only mode and never touches this class.
 */
class SupabaseProvider(
    private val url: String,
    private val publishableKey: String,
) {
    val isConfigured: Boolean
        get() = url.isNotBlank() && publishableKey.isNotBlank()

    val client: io.github.jan.supabase.SupabaseClient by lazy {
        check(isConfigured) { "Supabase is not configured" }
        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = publishableKey,
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
}

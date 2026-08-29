package com.personalstrava.app.sync

import com.personalstrava.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * One Supabase client for the whole app, built from BuildConfig fields that
 * are themselves populated from android/local.properties (never committed —
 * see .env.example and docs/android.md). Only the anon key is embedded here;
 * it is safe to ship because every table is RLS-scoped to auth.uid().
 */
object SupabaseClientProvider {
    fun create(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            // Same Google OAuth provider as the web client — same Supabase
            // user_id on both, per spec section 9. Deep link handled by
            // MainActivity's intent-filter for personalstrava://auth-callback.
            scheme = "personalstrava"
            host = "auth-callback"
        }
        install(Postgrest)
        install(Storage)
    }
}

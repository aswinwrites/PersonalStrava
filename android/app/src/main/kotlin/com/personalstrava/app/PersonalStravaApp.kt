package com.personalstrava.app

import android.app.Application
import com.personalstrava.app.data.local.AppDatabase
import com.personalstrava.app.sync.SupabaseClientProvider

class PersonalStravaApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val supabase by lazy { SupabaseClientProvider.create() }

    override fun onCreate() {
        super.onCreate()
        // WorkManager's periodic sync + Health Connect step-ingestion jobs are
        // scheduled from MainActivity on first successful sign-in (see
        // docs/sync.md) rather than unconditionally here, since they need an
        // authenticated Supabase session to do anything useful.
    }
}

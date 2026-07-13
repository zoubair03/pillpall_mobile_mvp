package com.example.data.auth

import android.content.Context
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

// supabase-kt's own "default" SessionManager (SettingsSessionManager, backed
// by the multiplatform-settings library) turned out not to actually persist
// anything on Android in this project — verified empirically: after a real
// sign-in, no session-related SharedPreferences file existed on disk at all,
// and the app landed back on the sign-in screen after a cold restart. Rather
// than chase that library's Android context-acquisition internals, this is a
// minimal, directly-verified-working SessionManager using plain
// SharedPreferences.
class PillPalSessionManager(context: Context) : SessionManager {
    private val prefs = context.getSharedPreferences("pillpal_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        prefs.edit().putString(KEY, json.encodeToString(session)).apply()
    }

    override suspend fun loadSession(): UserSession {
        val raw = prefs.getString(KEY, null) ?: error("No session stored")
        return json.decodeFromString(raw)
    }

    override suspend fun deleteSession() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "session"
    }
}

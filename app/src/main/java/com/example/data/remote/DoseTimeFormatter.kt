package com.example.data.remote

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Bridges Postgres "time" ("08:00:00") and the app's human display format
// ("08:00 AM"). java.time works natively here (no desugaring needed) since
// minSdk is 26 — required by the Supabase SDK anyway.
object DoseTimeFormatter {
    private val postgresFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val displayFormat = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    fun toDisplay(postgresTime: String): String = try {
        LocalTime.parse(postgresTime, postgresFormat).format(displayFormat)
    } catch (e: Exception) {
        postgresTime
    }

    fun toPostgres(hour: Int, minute: Int): String =
        LocalTime.of(hour, minute).format(postgresFormat)
}

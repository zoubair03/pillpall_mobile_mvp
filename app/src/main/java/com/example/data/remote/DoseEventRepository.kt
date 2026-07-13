package com.example.data.remote

import com.example.data.remote.dto.DoseEventDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PrimaryKey
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoseEventRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    // Most recent events first — dose_events is append-only history, this
    // is the adherence log a screen would page through.
    suspend fun getRecentEvents(deviceId: String, limit: Long = 100): List<DoseEventDto> =
        supabaseClient.from("dose_events")
            .select {
                filter { eq("device_id", deviceId) }
                order(column = "occurred_at", order = Order.DESCENDING)
                limit(limit)
            }
            .decodeList<DoseEventDto>()

    // dose_events is append-only — every change the app ever sees here is
    // an Insert (device echoing a dispense/ack, or check-missed-doses
    // recording a miss).
    @OptIn(SupabaseExperimental::class)
    fun observeEvents(deviceId: String): Flow<List<DoseEventDto>> =
        supabaseClient.from("dose_events")
            .selectAsFlow(
                primaryKey = PrimaryKey<DoseEventDto>("id") { it.id },
                filter = FilterOperation("device_id", FilterOperator.EQ, deviceId),
            )
}

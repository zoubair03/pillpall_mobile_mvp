package com.example.data.remote

import com.example.data.remote.dto.AlertDto
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
class AlertsRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    suspend fun getRecentAlerts(deviceId: String, limit: Long = 100): List<AlertDto> =
        supabaseClient.from("alerts")
            .select {
                filter { eq("device_id", deviceId) }
                order(column = "created_at", order = Order.DESCENDING)
                limit(limit)
            }
            .decodeList<AlertDto>()

    suspend fun acknowledge(alertId: String) {
        // RLS: "acknowledge alerts for linked devices" is an UPDATE-only
        // policy — this is the only field the app is permitted to change
        // on an alert row (no delete policy exists).
        supabaseClient.from("alerts").update({
            set("acknowledged", true)
        }) {
            filter { eq("id", alertId) }
        }
    }

    @OptIn(SupabaseExperimental::class)
    fun observeAlerts(deviceId: String): Flow<List<AlertDto>> =
        supabaseClient.from("alerts")
            .selectAsFlow(
                primaryKey = PrimaryKey<AlertDto>("id") { it.id },
                filter = FilterOperation("device_id", FilterOperator.EQ, deviceId),
            )
}

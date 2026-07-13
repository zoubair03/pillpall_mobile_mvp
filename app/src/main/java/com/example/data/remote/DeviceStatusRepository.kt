package com.example.data.remote

import com.example.data.remote.dto.DeviceStatusDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PrimaryKey
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceStatusRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    suspend fun getStatus(deviceId: String): DeviceStatusDto? =
        supabaseClient.from("device_status")
            .select { filter { eq("device_id", deviceId) } }
            .decodeList<DeviceStatusDto>()
            .firstOrNull()

    // device_status is a one-row-per-device upserted snapshot, so the
    // filtered result set is always 0 or 1 rows — selectAsFlow (the SDK's
    // own reactive-select helper, handles channel subscribe/cleanup
    // internally) keeps it live without hand-rolling a Realtime channel.
    @OptIn(SupabaseExperimental::class)
    fun observeStatus(deviceId: String): Flow<DeviceStatusDto?> =
        supabaseClient.from("device_status")
            .selectAsFlow(
                primaryKey = PrimaryKey<DeviceStatusDto>("device_id") { it.deviceId },
                filter = FilterOperation("device_id", FilterOperator.EQ, deviceId),
            )
            .map { it.firstOrNull() }
}

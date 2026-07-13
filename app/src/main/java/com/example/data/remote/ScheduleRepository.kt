package com.example.data.remote

import com.example.data.remote.dto.DoseType
import com.example.data.remote.dto.ScheduleDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PrimaryKey
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    suspend fun getSchedules(deviceId: String): List<ScheduleDto> =
        supabaseClient.from("schedules")
            .select { filter { eq("device_id", deviceId) } }
            .decodeList<ScheduleDto>()

    // timeOfDay must already be "HH:mm:ss" — see DoseTimeFormatter.
    suspend fun updateTime(deviceId: String, dose: DoseType, timeOfDay: String) {
        supabaseClient.from("schedules").update({
            set("time_of_day", timeOfDay)
        }) {
            filter {
                eq("device_id", deviceId)
                eq("dose", dose.name.lowercase())
            }
        }
    }

    @OptIn(SupabaseExperimental::class)
    fun observeSchedules(deviceId: String): Flow<List<ScheduleDto>> =
        supabaseClient.from("schedules")
            .selectAsFlow(
                primaryKey = PrimaryKey<ScheduleDto>("id") { it.id },
                filter = FilterOperation("device_id", FilterOperator.EQ, deviceId),
            )
}

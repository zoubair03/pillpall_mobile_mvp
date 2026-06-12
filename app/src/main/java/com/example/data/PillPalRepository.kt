package com.example.data

import kotlinx.coroutines.flow.Flow

class PillPalRepository(private val dao: PillPalDao) {
    val allSchedules: Flow<List<ScheduleItem>> = dao.getAllSchedulesFlow()
    val allLogs: Flow<List<HistoryLog>> = dao.getAllLogsFlow()

    suspend fun getAllSchedulesList() = dao.getAllSchedules()

    suspend fun updateScheduleTime(id: String, newTime: String) {
        dao.updateScheduleTime(id, newTime)
    }

    suspend fun updateScheduleActive(id: String, isActive: Boolean) {
        dao.updateScheduleActive(id, isActive)
    }

    suspend fun insertLog(log: HistoryLog) {
        dao.insertLog(log)
    }

    suspend fun clearHistory() {
        dao.clearAllLogs()
    }
}

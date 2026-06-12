package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Entity(tableName = "schedules")
data class ScheduleItem(
    @PrimaryKey val id: String, // morning, midday, night
    val label: String,
    val time: String,
    val isActive: Boolean = true
)

@Entity(tableName = "history_logs")
data class HistoryLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // e.g. "dose_dispensed", "dose_missed", "low_battery", "manual_drop", "calibrated", "restart"
    val title: String,
    val description: String,
    val time: String,
    val dateLabel: String, // "Today", "Yesterday", "Wednesday, Oct 25", etc.
    val category: String, // "Doses" or "Hardware Status"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface PillPalDao {
    @Query("SELECT * FROM schedules")
    fun getAllSchedulesFlow(): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM schedules")
    suspend fun getAllSchedules(): List<ScheduleItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ScheduleItem>)

    @Query("UPDATE schedules SET time = :newTime WHERE id = :id")
    suspend fun updateScheduleTime(id: String, newTime: String)

    @Query("UPDATE schedules SET isActive = :isActive WHERE id = :id")
    suspend fun updateScheduleActive(id: String, isActive: Boolean)

    @Query("SELECT * FROM history_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<HistoryLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HistoryLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<HistoryLog>)

    @Query("DELETE FROM history_logs")
    suspend fun clearAllLogs()
}

@Database(entities = [ScheduleItem::class, HistoryLog::class], version = 1, exportSchema = false)
abstract class PillPalDatabase : RoomDatabase() {
    abstract fun pillPalDao(): PillPalDao

    companion object {
        @Volatile
        private var INSTANCE: PillPalDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): PillPalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PillPalDatabase::class.java,
                    "pillpal_database"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Pre-populate database with default items on a background thread
                        scope.launch(Dispatchers.IO) {
                            val dao = getDatabase(context, scope).pillPalDao()
                            // Insert default schedules
                            dao.insertSchedules(
                                listOf(
                                    ScheduleItem("morning", "Roue du Matin", "08:00 AM"),
                                    ScheduleItem("midday", "Roue du Midi", "01:30 PM"),
                                    ScheduleItem("night", "Roue du Soir", "08:00 PM")
                                )
                            )
                            // Insert default rich history log messages matching the reference design layout!
                            dao.insertLogs(
                                listOf(
                                    HistoryLog(
                                        type = "low_battery",
                                        title = "Alerte Batterie Faible",
                                        description = "Batterie de l'appareil à 15%.",
                                        time = "14:30",
                                        dateLabel = "Aujourd'hui",
                                        category = "Statut Matériel",
                                        timestamp = System.currentTimeMillis() - 2 * 60 * 60 * 1000 // 2 hours ago
                                    ),
                                    HistoryLog(
                                        type = "dose_missed",
                                        title = "Dose du Midi Manquée",
                                        description = "Le médicament est resté dans le plateau.",
                                        time = "12:15",
                                        dateLabel = "Aujourd'hui",
                                        category = "Doses",
                                        timestamp = System.currentTimeMillis() - 4 * 60 * 60 * 1000 // 4 hours ago
                                    ),
                                    HistoryLog(
                                        type = "dose_dispensed",
                                        title = "Dose du Matin Distribuée",
                                        description = "Le carrousel A a tourné avec succès.",
                                        time = "08:05",
                                        dateLabel = "Aujourd'hui",
                                        category = "Doses",
                                        timestamp = System.currentTimeMillis() - 8 * 60 * 60 * 1000 // 8 hours ago
                                    ),
                                    HistoryLog(
                                        type = "dose_dispensed",
                                        title = "Dose du Soir Distribuée",
                                        description = "Le carrousel B a tourné avec succès.",
                                        time = "20:00",
                                        dateLabel = "Hier",
                                        category = "Doses",
                                        timestamp = System.currentTimeMillis() - 20 * 60 * 60 * 1000 // Yesterday
                                    )
                                )
                            )
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.hse.management

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "records")
data class HseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val module: String,
    val title: String,
    val type: String,
    val category: String = "",
    val description: String = "",
    val responsible: String = "",
    val priority: String = "Medium",
    val targetDate: String = "",
    val status: String = "Open",
    val correctiveAction: String = "",
    val rootCause: String = "",
    val investigationMethod: String = "",
    val standard: String = "",
    val createdBy: String = "System User",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_log")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val action: String,
    val module: String,
    val recordId: Long,
    val at: Long = System.currentTimeMillis()
)

@Dao
interface HseDao {
    @Query("SELECT * FROM records ORDER BY id DESC") fun records(): Flow<List<HseRecord>>
    @Query("SELECT COUNT(*) FROM records WHERE module = 'Observation'") fun observationCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM records WHERE module = 'Incident'") fun incidentCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM records WHERE module = 'Audit'") fun auditCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM records WHERE module = 'CAPA'") fun capaCount(): Flow<Int>
    @Insert suspend fun insert(record: HseRecord): Long
    @Delete suspend fun delete(record: HseRecord)
    @Insert suspend fun log(log: AuditLog)
}

@Database(entities = [HseRecord::class, AuditLog::class], version = 1, exportSchema = false)
abstract class HseDatabase : RoomDatabase() {
    abstract fun dao(): HseDao
    companion object {
        @Volatile private var instance: HseDatabase? = null
        fun get(context: Context): HseDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HseDatabase::class.java, "hse_management.db").build().also { instance = it }
        }
    }
}

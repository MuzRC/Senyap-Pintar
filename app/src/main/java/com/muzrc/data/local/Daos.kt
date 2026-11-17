package com.muzrc.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppProfileDao {
    @Query("SELECT * FROM app_profiles ORDER BY monitored DESC, appName ASC")
    fun observeAll(): Flow<List<AppProfileEntity>>

    @Query("SELECT * FROM app_profiles")
    suspend fun getAllOnce(): List<AppProfileEntity>

    @Query("SELECT * FROM app_profiles WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): AppProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AppProfileEntity>)

    @Query("DELETE FROM app_profiles WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("DELETE FROM app_profiles")
    suspend fun deleteAll()
}

@Dao
interface QuietScheduleDao {
    @Query("SELECT * FROM quiet_schedules ORDER BY startHour ASC, startMinute ASC")
    fun observeAll(): Flow<List<QuietScheduleEntity>>

    @Query("SELECT * FROM quiet_schedules")
    suspend fun getAllOnce(): List<QuietScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: QuietScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<QuietScheduleEntity>)

    @Query("DELETE FROM quiet_schedules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM quiet_schedules")
    suspend fun deleteAll()
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestampMillis DESC LIMIT 500")
    fun observeRecent(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE dateKey = :dateKey ORDER BY timestampMillis DESC")
    fun observeByDate(dateKey: String): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs WHERE dateKey = :dateKey ORDER BY timestampMillis DESC")
    suspend fun getByDateOnce(dateKey: String): List<ActivityLogEntity>

    @Query("SELECT DISTINCT dateKey FROM activity_logs ORDER BY dateKey DESC LIMIT 30")
    fun observeAvailableDates(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ActivityLogEntity)

    @Query("DELETE FROM activity_logs WHERE dateKey = :dateKey")
    suspend fun deleteByDate(dateKey: String)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAll()
}

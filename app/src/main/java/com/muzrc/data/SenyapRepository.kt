package com.muzrc.data

import com.muzrc.data.local.ActivityLogDao
import com.muzrc.data.local.ActivityLogEntity
import com.muzrc.data.local.AppProfileDao
import com.muzrc.data.local.AppProfileEntity
import com.muzrc.data.local.LocalBackupManager
import com.muzrc.data.local.PreferencesDataStore
import com.muzrc.data.local.QuietScheduleDao
import com.muzrc.data.local.QuietScheduleEntity
import com.muzrc.model.ActivityLogEntry
import com.muzrc.model.AppSoundProfile
import com.muzrc.model.InstalledAppInfo
import com.muzrc.model.LogActionType
import com.muzrc.model.QuietSchedule
import com.muzrc.model.ThemeMode
import com.muzrc.model.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SenyapRepository(
    private val profileDao: AppProfileDao,
    private val scheduleDao: QuietScheduleDao,
    private val activityLogDao: ActivityLogDao,
    private val preferencesStore: PreferencesDataStore,
    private val installedAppsProvider: InstalledAppsProvider
) {
    val preferencesFlow: Flow<UserPreferences> = preferencesStore.preferencesFlow

    val profilesFlow: Flow<List<AppSoundProfile>> = profileDao.observeAll()
        .map { list -> list.map { it.toDomain() } }

    val schedulesFlow: Flow<List<QuietSchedule>> = scheduleDao.observeAll()
        .map { list -> list.map { it.toDomain() } }

    val availableLogDatesFlow: Flow<List<String>> = activityLogDao.observeAvailableDates()

    fun observeLogsByDate(dateKey: String): Flow<List<ActivityLogEntry>> =
        activityLogDao.observeByDate(dateKey).map { list -> list.map { it.toDomain() } }

    fun observeRecentLogs(): Flow<List<ActivityLogEntry>> =
        activityLogDao.observeRecent().map { list -> list.map { it.toDomain() } }

    suspend fun getInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        installedAppsProvider.loadLaunchableApps()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        preferencesStore.setThemeMode(mode)
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        preferencesStore.setMonitoringEnabled(enabled)
    }

    suspend fun setDefaultRestoreOnExit(restore: Boolean) {
        preferencesStore.setDefaultRestoreOnExit(restore)
    }

    suspend fun setPollIntervalMs(intervalMs: Long) {
        preferencesStore.setPollIntervalMs(intervalMs)
    }

    suspend fun saveProfile(profile: AppSoundProfile) {
        val updated = profile.copy(updatedAtMillis = System.currentTimeMillis())
        profileDao.upsert(AppProfileEntity.fromDomain(updated))
    }

    suspend fun deleteProfile(packageName: String) {
        profileDao.deleteByPackage(packageName)
    }

    suspend fun saveSchedule(schedule: QuietSchedule) {
        val updated = schedule.copy(updatedAtMillis = System.currentTimeMillis())
        scheduleDao.upsert(QuietScheduleEntity.fromDomain(updated))
    }

    suspend fun deleteSchedule(scheduleId: String) {
        scheduleDao.deleteById(scheduleId)
    }

    suspend fun recordLog(
        packageName: String,
        appName: String,
        actionType: LogActionType,
        previousVolumePercent: Int,
        appliedVolumePercent: Int,
        reasonLabel: String
    ) {
        val now = System.currentTimeMillis()
        val entry = ActivityLogEntry(
            timestampMillis = now,
            dateKey = formatDateKey(now),
            packageName = packageName,
            appName = appName,
            actionType = actionType,
            previousVolumePercent = previousVolumePercent.coerceIn(0, 100),
            appliedVolumePercent = appliedVolumePercent.coerceIn(0, 100),
            reasonLabel = reasonLabel
        )
        activityLogDao.insert(ActivityLogEntity.fromDomain(entry))
    }

    suspend fun clearLogsForDate(dateKey: String) {
        activityLogDao.deleteByDate(dateKey)
    }

    suspend fun exportBackupJson(): Result<String> {
        return try {
            val prefs = preferencesStore.getPreferencesOnce()
            val profiles = profileDao.getAllOnce().map { it.toDomain() }
            val schedules = scheduleDao.getAllOnce().map { it.toDomain() }
            val json = LocalBackupManager.serializeBackup(prefs, profiles, schedules)
            preferencesStore.setLastBackupAt(System.currentTimeMillis())
            Result.success(json)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackupJson(jsonString: String): Result<Int> {
        return try {
            val snapshot = LocalBackupManager.parseBackup(jsonString)
            var restoredItems = 0
            if (snapshot.preferences != null) {
                preferencesStore.updateAllPreferences(snapshot.preferences)
                restoredItems++
            }
            if (snapshot.profiles.isNotEmpty()) {
                profileDao.upsertAll(snapshot.profiles.map { AppProfileEntity.fromDomain(it) })
                restoredItems += snapshot.profiles.size
            }
            if (snapshot.schedules.isNotEmpty()) {
                scheduleDao.upsertAll(snapshot.schedules.map { QuietScheduleEntity.fromDomain(it) })
                restoredItems += snapshot.schedules.size
            }
            preferencesStore.setLastBackupAt(System.currentTimeMillis())
            Result.success(restoredItems)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun todayDateKey(): String = formatDateKey(System.currentTimeMillis())

        fun formatDateKey(timestampMillis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date(timestampMillis))
        }
    }
}

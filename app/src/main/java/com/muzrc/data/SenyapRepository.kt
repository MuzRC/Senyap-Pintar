package com.muzrc.data

import com.muzrc.data.local.ActivityLogDao
import com.muzrc.data.local.ActivityLogEntity
import com.muzrc.data.local.AppProfileDao
import com.muzrc.data.local.AppProfileEntity
import com.muzrc.data.local.PreferencesDataStore
import com.muzrc.data.local.QuietScheduleDao
import com.muzrc.data.local.QuietScheduleEntity
import com.muzrc.data.remote.CloudBackupRepository
import com.muzrc.model.ActivityLogEntry
import com.muzrc.model.AppSoundProfile
import com.muzrc.model.InstalledAppInfo
import com.muzrc.model.LogActionType
import com.muzrc.model.QuietSchedule
import com.muzrc.model.ThemeMode
import com.muzrc.model.UserPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val cloudRepository: CloudBackupRepository,
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

    suspend fun setThemeMode(mode: ThemeMode): Result<Unit> {
        preferencesStore.setThemeMode(mode)
        return syncPreferencesIfSignedIn()
    }

    suspend fun setMonitoringEnabled(enabled: Boolean): Result<Unit> {
        preferencesStore.setMonitoringEnabled(enabled)
        return syncPreferencesIfSignedIn()
    }

    suspend fun setDefaultRestoreOnExit(restore: Boolean): Result<Unit> {
        preferencesStore.setDefaultRestoreOnExit(restore)
        return syncPreferencesIfSignedIn()
    }

    suspend fun setPollIntervalMs(intervalMs: Long): Result<Unit> {
        preferencesStore.setPollIntervalMs(intervalMs)
        return syncPreferencesIfSignedIn()
    }

    suspend fun saveProfile(profile: AppSoundProfile): Result<Unit> {
        val updated = profile.copy(updatedAtMillis = System.currentTimeMillis())
        profileDao.upsert(AppProfileEntity.fromDomain(updated))
        val uid = Firebase.auth.currentUser?.uid
        return if (!uid.isNullOrBlank()) {
            cloudRepository.saveProfile(uid, updated)
        } else {
            Result.success(Unit)
        }
    }

    suspend fun deleteProfile(packageName: String): Result<Unit> {
        profileDao.deleteByPackage(packageName)
        val uid = Firebase.auth.currentUser?.uid
        return if (!uid.isNullOrBlank()) {
            cloudRepository.deleteProfile(uid, packageName)
        } else {
            Result.success(Unit)
        }
    }

    suspend fun saveSchedule(schedule: QuietSchedule): Result<Unit> {
        val updated = schedule.copy(updatedAtMillis = System.currentTimeMillis())
        scheduleDao.upsert(QuietScheduleEntity.fromDomain(updated))
        val uid = Firebase.auth.currentUser?.uid
        return if (!uid.isNullOrBlank()) {
            cloudRepository.saveSchedule(uid, updated)
        } else {
            Result.success(Unit)
        }
    }

    suspend fun deleteSchedule(scheduleId: String): Result<Unit> {
        scheduleDao.deleteById(scheduleId)
        val uid = Firebase.auth.currentUser?.uid
        return if (!uid.isNullOrBlank()) {
            cloudRepository.deleteSchedule(uid, scheduleId)
        } else {
            Result.success(Unit)
        }
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

    suspend fun backupNowToCloud(userId: String): Result<Long> {
        return try {
            val prefs = preferencesStore.getPreferencesOnce()
            val profiles = profileDao.getAllOnce().map { it.toDomain() }
            val schedules = scheduleDao.getAllOnce().map { it.toDomain() }
            val result = cloudRepository.backupAll(userId, prefs, profiles, schedules)
            result.onSuccess { timestamp ->
                preferencesStore.setLastCloudBackupAt(timestamp)
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromCloud(userId: String): Result<Int> {
        return try {
            val snapshot = cloudRepository.restoreAllFromCloud(userId).getOrThrow()
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
            preferencesStore.setLastCloudBackupAt(System.currentTimeMillis())
            Result.success(restoredItems)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncInitialCloudIfLocalEmpty(userId: String) {
        try {
            val localProfiles = profileDao.getAllOnce()
            val localSchedules = scheduleDao.getAllOnce()
            if (localProfiles.isEmpty() && localSchedules.isEmpty()) {
                restoreFromCloud(userId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-fatal initial sync fallback
        }
    }

    private suspend fun syncPreferencesIfSignedIn(): Result<Unit> {
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank()) return Result.success(Unit)
        val prefs = preferencesStore.preferencesFlow.first()
        val res = cloudRepository.savePreferences(uid, prefs)
        if (res.isSuccess) {
            preferencesStore.setLastCloudBackupAt(System.currentTimeMillis())
        }
        return res
    }

    companion object {
        fun todayDateKey(): String = formatDateKey(System.currentTimeMillis())

        fun formatDateKey(timestampMillis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date(timestampMillis))
        }
    }
}

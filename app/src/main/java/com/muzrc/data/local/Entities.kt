package com.muzrc.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.muzrc.model.ActivityLogEntry
import com.muzrc.model.AppSoundProfile
import com.muzrc.model.LogActionType
import com.muzrc.model.ProfileMode
import com.muzrc.model.QuietSchedule

@Entity(tableName = "app_profiles")
data class AppProfileEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val monitored: Boolean,
    val profileMode: String,
    val targetVolumePercent: Int,
    val restoreOnExit: Boolean,
    val scheduleOnly: Boolean,
    val updatedAtMillis: Long
) {
    fun toDomain(): AppSoundProfile = AppSoundProfile(
        packageName = packageName,
        appName = appName,
        monitored = monitored,
        profileMode = ProfileMode.fromRaw(profileMode),
        targetVolumePercent = targetVolumePercent.coerceIn(0, 100),
        restoreOnExit = restoreOnExit,
        scheduleOnly = scheduleOnly,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(profile: AppSoundProfile): AppProfileEntity = AppProfileEntity(
            packageName = profile.packageName,
            appName = profile.appName,
            monitored = profile.monitored,
            profileMode = profile.profileMode.name,
            targetVolumePercent = profile.targetVolumePercent.coerceIn(0, 100),
            restoreOnExit = profile.restoreOnExit,
            scheduleOnly = profile.scheduleOnly,
            updatedAtMillis = profile.updatedAtMillis
        )
    }
}

@Entity(tableName = "quiet_schedules")
data class QuietScheduleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeekCsv: String,
    val targetVolumePercent: Int,
    val applyToAllApps: Boolean,
    val enabled: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    fun toDomain(): QuietSchedule = QuietSchedule(
        id = id,
        title = title,
        startHour = startHour.coerceIn(0, 23),
        startMinute = startMinute.coerceIn(0, 59),
        endHour = endHour.coerceIn(0, 23),
        endMinute = endMinute.coerceIn(0, 59),
        daysOfWeek = daysOfWeekCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) },
        targetVolumePercent = targetVolumePercent.coerceIn(0, 100),
        applyToAllApps = applyToAllApps,
        enabled = enabled,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    companion object {
        fun fromDomain(schedule: QuietSchedule): QuietScheduleEntity = QuietScheduleEntity(
            id = schedule.id,
            title = schedule.title,
            startHour = schedule.startHour.coerceIn(0, 23),
            startMinute = schedule.startMinute.coerceIn(0, 59),
            endHour = schedule.endHour.coerceIn(0, 23),
            endMinute = schedule.endMinute.coerceIn(0, 59),
            daysOfWeekCsv = schedule.daysOfWeek.distinct().sorted().joinToString(","),
            targetVolumePercent = schedule.targetVolumePercent.coerceIn(0, 100),
            applyToAllApps = schedule.applyToAllApps,
            enabled = schedule.enabled,
            createdAtMillis = schedule.createdAtMillis,
            updatedAtMillis = schedule.updatedAtMillis
        )
    }
}

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMillis: Long,
    val dateKey: String,
    val packageName: String,
    val appName: String,
    val actionType: String,
    val previousVolumePercent: Int,
    val appliedVolumePercent: Int,
    val reasonLabel: String
) {
    fun toDomain(): ActivityLogEntry = ActivityLogEntry(
        id = id,
        timestampMillis = timestampMillis,
        dateKey = dateKey,
        packageName = packageName,
        appName = appName,
        actionType = LogActionType.fromRaw(actionType),
        previousVolumePercent = previousVolumePercent,
        appliedVolumePercent = appliedVolumePercent,
        reasonLabel = reasonLabel
    )

    companion object {
        fun fromDomain(entry: ActivityLogEntry): ActivityLogEntity = ActivityLogEntity(
            id = entry.id,
            timestampMillis = entry.timestampMillis,
            dateKey = entry.dateKey,
            packageName = entry.packageName,
            appName = entry.appName,
            actionType = entry.actionType.name,
            previousVolumePercent = entry.previousVolumePercent,
            appliedVolumePercent = entry.appliedVolumePercent,
            reasonLabel = entry.reasonLabel
        )
    }
}

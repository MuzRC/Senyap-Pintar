package com.muzrc.data

import com.muzrc.model.AppSoundProfile
import com.muzrc.model.LogActionType
import com.muzrc.model.ProfileMode
import com.muzrc.model.QuietSchedule
import java.util.Calendar

data class VolumeDecision(
    val shouldControl: Boolean,
    val targetVolumePercent: Int,
    val restoreOnExit: Boolean,
    val actionType: LogActionType,
    val reasonLabel: String,
    val activeScheduleTitle: String? = null
)

object ScheduleEvaluator {

    /**
     * Converts [Calendar.DAY_OF_WEEK] (Sun=1..Sat=7) into ISO day-of-week (Mon=1..Sun=7).
     */
    fun isoDayOfWeek(calendar: Calendar = Calendar.getInstance()): Int {
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    fun previousIsoDay(isoDay: Int): Int = if (isoDay == 1) 7 else isoDay - 1

    fun isScheduleActiveAt(
        schedule: QuietSchedule,
        isoDayOfWeek: Int,
        hour: Int,
        minute: Int
    ): Boolean {
        if (!schedule.enabled) return false
        val currentMins = hour * 60 + minute
        val startMins = schedule.startHour * 60 + schedule.startMinute
        val endMins = schedule.endHour * 60 + schedule.endMinute

        return when {
            startMins == endMins -> isoDayOfWeek in schedule.daysOfWeek
            startMins < endMins -> {
                isoDayOfWeek in schedule.daysOfWeek && currentMins in startMins until endMins
            }
            else -> {
                // Overnight window (e.g. 22:30 - 06:00)
                val inLateEvening = isoDayOfWeek in schedule.daysOfWeek && currentMins >= startMins
                val inEarlyMorning = previousIsoDay(isoDayOfWeek) in schedule.daysOfWeek && currentMins < endMins
                inLateEvening || inEarlyMorning
            }
        }
    }

    fun evaluate(
        foregroundPackage: String?,
        profiles: List<AppSoundProfile>,
        schedules: List<QuietSchedule>,
        defaultRestoreOnExit: Boolean,
        calendar: Calendar = Calendar.getInstance()
    ): VolumeDecision {
        val isoDay = isoDayOfWeek(calendar)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val activeSchedules = schedules.filter { isScheduleActiveAt(it, isoDay, hour, minute) }
        val profile = foregroundPackage?.let { pkg ->
            profiles.firstOrNull { it.packageName == pkg }
        }

        // 1. Check global quiet schedules first (or schedules scoped to monitored apps)
        val globalSchedule = activeSchedules
            .filter { it.applyToAllApps }
            .minByOrNull { it.targetVolumePercent }

        val scopedSchedule = if (profile != null && profile.monitored) {
            activeSchedules.minByOrNull { it.targetVolumePercent }
        } else {
            null
        }

        // 2. Evaluate app profile when monitored and not NORMAL
        val appRequiresControl = profile != null &&
            profile.monitored &&
            profile.profileMode != ProfileMode.NORMAL &&
            (!profile.scheduleOnly || activeSchedules.isNotEmpty())

        if (appRequiresControl && profile != null) {
            val effectiveTarget = if (scopedSchedule != null) {
                minOf(profile.targetVolumePercent, scopedSchedule.targetVolumePercent)
            } else {
                profile.targetVolumePercent
            }
            val action = if (effectiveTarget == 0) LogActionType.DISENYAPKAN else LogActionType.DILARASKAN
            val reason = if (scopedSchedule != null) {
                "Profil ${profile.profileMode.labelMs} + Jadual ${scopedSchedule.title}"
            } else {
                "Profil ${profile.profileMode.labelMs}"
            }
            return VolumeDecision(
                shouldControl = true,
                targetVolumePercent = effectiveTarget.coerceIn(0, 100),
                restoreOnExit = profile.restoreOnExit,
                actionType = action,
                reasonLabel = reason,
                activeScheduleTitle = scopedSchedule?.title
            )
        }

        // 3. Global schedule active even if current app has no specific profile
        if (globalSchedule != null) {
            val action = LogActionType.JADUAL_AKTIF
            return VolumeDecision(
                shouldControl = true,
                targetVolumePercent = globalSchedule.targetVolumePercent.coerceIn(0, 100),
                restoreOnExit = defaultRestoreOnExit,
                actionType = action,
                reasonLabel = "Jadual ${globalSchedule.title}",
                activeScheduleTitle = globalSchedule.title
            )
        }

        return VolumeDecision(
            shouldControl = false,
            targetVolumePercent = 100,
            restoreOnExit = defaultRestoreOnExit,
            actionType = LogActionType.DIPULIHKAN,
            reasonLabel = "Tiada peraturan aktif",
            activeScheduleTitle = activeSchedules.firstOrNull()?.title
        )
    }
}

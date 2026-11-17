package com.muzrc.data.local

import com.muzrc.model.AppSoundProfile
import com.muzrc.model.ProfileMode
import com.muzrc.model.QuietSchedule
import com.muzrc.model.ThemeMode
import com.muzrc.model.UserPreferences
import org.json.JSONArray
import org.json.JSONObject

data class LocalBackupSnapshot(
    val preferences: UserPreferences?,
    val profiles: List<AppSoundProfile>,
    val schedules: List<QuietSchedule>
)

object LocalBackupManager {
    private const val BACKUP_FORMAT_VERSION = 1

    fun serializeBackup(
        preferences: UserPreferences,
        profiles: List<AppSoundProfile>,
        schedules: List<QuietSchedule>
    ): String {
        val root = JSONObject().apply {
            put("app", "SenyapPintar")
            put("version", BACKUP_FORMAT_VERSION)
            put("exportedAtMillis", System.currentTimeMillis())

            put("preferences", JSONObject().apply {
                put("themeMode", preferences.themeMode.name)
                put("monitoringEnabled", preferences.monitoringEnabled)
                put("defaultRestoreOnExit", preferences.defaultRestoreOnExit)
                put("pollIntervalMs", preferences.pollIntervalMs.coerceIn(500L, 10000L))
            })

            put("profiles", JSONArray().apply {
                profiles.forEach { profile ->
                    put(JSONObject().apply {
                        put("packageName", profile.packageName)
                        put("appName", profile.appName)
                        put("monitored", profile.monitored)
                        put("profileMode", profile.profileMode.name)
                        put("targetVolumePercent", profile.targetVolumePercent.coerceIn(0, 100))
                        put("restoreOnExit", profile.restoreOnExit)
                        put("scheduleOnly", profile.scheduleOnly)
                        put("updatedAtMillis", profile.updatedAtMillis)
                    })
                }
            })

            put("schedules", JSONArray().apply {
                schedules.forEach { schedule ->
                    put(JSONObject().apply {
                        put("id", schedule.id)
                        put("title", schedule.title)
                        put("startHour", schedule.startHour.coerceIn(0, 23))
                        put("startMinute", schedule.startMinute.coerceIn(0, 59))
                        put("endHour", schedule.endHour.coerceIn(0, 23))
                        put("endMinute", schedule.endMinute.coerceIn(0, 59))
                        put("daysOfWeek", JSONArray().apply {
                            schedule.daysOfWeek.forEach { put(it) }
                        })
                        put("targetVolumePercent", schedule.targetVolumePercent.coerceIn(0, 100))
                        put("applyToAllApps", schedule.applyToAllApps)
                        put("enabled", schedule.enabled)
                        put("createdAtMillis", schedule.createdAtMillis)
                        put("updatedAtMillis", schedule.updatedAtMillis)
                    })
                }
            })
        }
        return root.toString(2)
    }

    fun parseBackup(jsonString: String): LocalBackupSnapshot {
        val root = JSONObject(jsonString)
        val now = System.currentTimeMillis()

        val prefsObj = root.optJSONObject("preferences")
        val prefs = prefsObj?.let {
            UserPreferences(
                themeMode = ThemeMode.fromRaw(it.optString("themeMode", ThemeMode.SISTEM.name)),
                monitoringEnabled = it.optBoolean("monitoringEnabled", false),
                defaultRestoreOnExit = it.optBoolean("defaultRestoreOnExit", true),
                pollIntervalMs = it.optLong("pollIntervalMs", 1200L).coerceIn(500L, 10000L),
                lastBackupAtMillis = now
            )
        }

        val profilesList = mutableListOf<AppSoundProfile>()
        val profilesArr = root.optJSONArray("profiles")
        if (profilesArr != null) {
            for (i in 0 until profilesArr.length()) {
                val obj = profilesArr.optJSONObject(i) ?: continue
                val pkg = obj.optString("packageName").trim()
                if (pkg.isEmpty()) continue
                profilesList.add(
                    AppSoundProfile(
                        packageName = pkg.take(160),
                        appName = obj.optString("appName").trim().ifEmpty { pkg }.take(100),
                        monitored = obj.optBoolean("monitored", true),
                        profileMode = ProfileMode.fromRaw(obj.optString("profileMode", ProfileMode.SENYAP.name)),
                        targetVolumePercent = obj.optInt("targetVolumePercent", 0).coerceIn(0, 100),
                        restoreOnExit = obj.optBoolean("restoreOnExit", true),
                        scheduleOnly = obj.optBoolean("scheduleOnly", false),
                        updatedAtMillis = obj.optLong("updatedAtMillis", now)
                    )
                )
            }
        }

        val schedulesList = mutableListOf<QuietSchedule>()
        val schedulesArr = root.optJSONArray("schedules")
        if (schedulesArr != null) {
            for (i in 0 until schedulesArr.length()) {
                val obj = schedulesArr.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                if (id.isEmpty()) continue
                val daysArr = obj.optJSONArray("daysOfWeek")
                val days = buildList {
                    if (daysArr != null) {
                        for (j in 0 until daysArr.length()) {
                            val d = daysArr.optInt(j, -1)
                            if (d in 1..7) add(d)
                        }
                    }
                }.distinct().sorted().ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) }

                schedulesList.add(
                    QuietSchedule(
                        id = id.take(80),
                        title = obj.optString("title").trim().ifEmpty { "Jadual Senyap" }.take(80),
                        startHour = obj.optInt("startHour", 22).coerceIn(0, 23),
                        startMinute = obj.optInt("startMinute", 0).coerceIn(0, 59),
                        endHour = obj.optInt("endHour", 6).coerceIn(0, 23),
                        endMinute = obj.optInt("endMinute", 0).coerceIn(0, 59),
                        daysOfWeek = days,
                        targetVolumePercent = obj.optInt("targetVolumePercent", 0).coerceIn(0, 100),
                        applyToAllApps = obj.optBoolean("applyToAllApps", true),
                        enabled = obj.optBoolean("enabled", true),
                        createdAtMillis = obj.optLong("createdAtMillis", now),
                        updatedAtMillis = obj.optLong("updatedAtMillis", now)
                    )
                )
            }
        }

        return LocalBackupSnapshot(
            preferences = prefs,
            profiles = profilesList,
            schedules = schedulesList
        )
    }
}

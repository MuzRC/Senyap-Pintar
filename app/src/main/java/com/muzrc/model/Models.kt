package com.muzrc.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

enum class ThemeMode(val labelMs: String) {
    SISTEM("Ikut Sistem"),
    GELAP("Mod Gelap"),
    TERANG("Mod Terang");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SISTEM
    }
}

enum class ProfileMode(val labelMs: String, val defaultPercent: Int) {
    SENYAP("Senyap Penuh (0%)", 0),
    RENDAH("Rendah (15%)", 15),
    KHUSUS("Khusus", 30),
    NORMAL("Normal", 100);

    companion object {
        fun fromRaw(raw: String?): ProfileMode =
            entries.firstOrNull { it.name == raw } ?: SENYAP
    }
}

enum class LogActionType(val labelMs: String) {
    DISENYAPKAN("Disenyapkan"),
    DILARASKAN("Dilaraskan"),
    DIPULIHKAN("Dipulihkan"),
    JADUAL_AKTIF("Jadual Aktif");

    companion object {
        fun fromRaw(raw: String?): LogActionType =
            entries.firstOrNull { it.name == raw } ?: DISENYAPKAN
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class AppSoundProfile(
    val packageName: String,
    val appName: String,
    val monitored: Boolean = true,
    val profileMode: ProfileMode = ProfileMode.SENYAP,
    val targetVolumePercent: Int = 0,
    val restoreOnExit: Boolean = true,
    val scheduleOnly: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class QuietSchedule(
    val id: String,
    val title: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeek: List<Int>, // 1 = Isnin .. 7 = Ahad
    val targetVolumePercent: Int,
    val applyToAllApps: Boolean,
    val enabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SISTEM,
    val monitoringEnabled: Boolean = false,
    val defaultRestoreOnExit: Boolean = true,
    val pollIntervalMs: Long = 1200L,
    val lastCloudBackupAtMillis: Long? = null
)

data class ActivityLogEntry(
    val id: Long = 0L,
    val timestampMillis: Long,
    val dateKey: String,
    val packageName: String,
    val appName: String,
    val actionType: LogActionType,
    val previousVolumePercent: Int,
    val appliedVolumePercent: Int,
    val reasonLabel: String
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String
)

@Keep
@IgnoreExtraProperties
data class CloudUserPreferences(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themeMode: String = ThemeMode.SISTEM.name,
    val monitoringEnabled: Boolean = false,
    val defaultRestoreOnExit: Boolean = true,
    val pollIntervalMs: Long = 1200L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudAppSoundProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val packageName: String = "",
    val appName: String = "",
    val monitored: Boolean = true,
    val profileMode: String = ProfileMode.SENYAP.name,
    val targetVolumePercent: Long = 0L,
    val restoreOnExit: Boolean = true,
    val scheduleOnly: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudQuietSchedule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val startHour: Long = 22L,
    val startMinute: Long = 0L,
    val endHour: Long = 6L,
    val endMinute: Long = 0L,
    val daysOfWeek: List<Long> = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L),
    val targetVolumePercent: Long = 0L,
    val applyToAllApps: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

fun sanitizeFirestoreDocId(raw: String): String {
    val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        .trim('_')
        .take(128)
    return cleaned.ifEmpty { "doc_${raw.hashCode().toUInt()}" }
}

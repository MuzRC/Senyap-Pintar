package com.muzrc.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.muzrc.model.InstalledAppInfo
import kotlin.math.roundToInt

class AudioVolumeController(private val context: Context) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun getMaxMediaVolume(): Int =
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    fun getCurrentMediaVolumeIndex(): Int =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun getCurrentMediaVolumePercent(): Int {
        val max = getMaxMediaVolume()
        val current = getCurrentMediaVolumeIndex().coerceIn(0, max)
        return ((current.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    fun setMediaVolumePercent(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        val max = getMaxMediaVolume()
        val targetIndex = ((clamped / 100f) * max.toFloat()).roundToInt().coerceIn(0, max)
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
        } catch (_: SecurityException) {
            // Device policy / DND restricted stream change
        } catch (_: Exception) {
            // Ignore transient AudioManager errors
        }
    }
}

object UsageAccessHelper {
    private val ignoredSystemPackages = setOf(
        "com.android.systemui",
        "android"
    )

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun detectForegroundPackage(context: Context, lookbackMillis: Long = 120_000L): String? {
        if (!hasUsageStatsPermission(context)) return null
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = (endTime - lookbackMillis).coerceAtLeast(0L)
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            val isForeground = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            val pkg = event.packageName
            if (isForeground &&
                event.timeStamp >= latestTime &&
                !pkg.isNullOrBlank() &&
                pkg !in ignoredSystemPackages
            ) {
                latestTime = event.timeStamp
                latestPackage = pkg
            }
        }

        if (latestPackage != null) return latestPackage

        // Fallback to recent UsageStats if no transition happened inside lookback window
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            (endTime - 30 * 60_000L).coerceAtLeast(0L),
            endTime
        )
        return stats
            ?.asSequence()
            ?.filter { !it.packageName.isNullOrBlank() && it.packageName !in ignoredSystemPackages }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }
}

class InstalledAppsProvider(private val context: Context) {
    private val pm = context.applicationContext.packageManager

    fun loadLaunchableApps(): List<InstalledAppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        return resolved
            .asSequence()
            .map { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                InstalledAppInfo(packageName = pkg, appName = label)
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    fun resolveAppName(packageName: String): String {
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString().trim().ifEmpty { packageName }
        } catch (_: Exception) {
            packageName
        }
    }

    fun loadAppIcon(packageName: String): Drawable? {
        return try {
            pm.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
    }
}

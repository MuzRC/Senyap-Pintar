package com.muzrc.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.muzrc.MainActivity
import com.muzrc.R
import com.muzrc.SenyapPintarApp
import com.muzrc.data.AudioVolumeController
import com.muzrc.data.InstalledAppsProvider
import com.muzrc.data.ScheduleEvaluator
import com.muzrc.data.UsageAccessHelper
import com.muzrc.data.local.AppDatabase
import com.muzrc.data.local.PreferencesDataStore
import com.muzrc.model.LogActionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MonitorRuntimeStatus(
    val serviceRunning: Boolean = false,
    val foregroundPackage: String? = null,
    val foregroundAppName: String? = null,
    val controllingActive: Boolean = false,
    val activeReason: String? = null,
    val activeScheduleTitle: String? = null,
    val currentVolumePercent: Int = 50
)

class MediaMonitorForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var lastDetectedForegroundPackage: String? = null

    private lateinit var audioController: AudioVolumeController
    private lateinit var appsProvider: InstalledAppsProvider
    private lateinit var preferencesStore: PreferencesDataStore
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        val app = application as? SenyapPintarApp
        audioController = app?.container?.audioController ?: AudioVolumeController(this)
        appsProvider = app?.container?.installedAppsProvider ?: InstalledAppsProvider(this)
        preferencesStore = app?.container?.preferencesStore ?: PreferencesDataStore(this)
        database = app?.container?.database ?: AppDatabase.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, ACTION_PAUSE -> {
                serviceScope.launch {
                    restoreIfNeededOnShutdown()
                    if (intent.action == ACTION_PAUSE) {
                        preferencesStore.setMonitoringEnabled(false)
                    }
                    lastDetectedForegroundPackage = null
                    _runtimeStatus.value = _runtimeStatus.value.copy(
                        serviceRunning = false,
                        controllingActive = false,
                        activeReason = null
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }

        val initialNotification = buildNotification(getString(R.string.notif_monitor_idle))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        startMonitorLoop()
        return START_STICKY
    }

    private fun startMonitorLoop() {
        if (monitorJob?.isActive == true) return
        _runtimeStatus.value = _runtimeStatus.value.copy(
            serviceRunning = true,
            currentVolumePercent = audioController.getCurrentMediaVolumePercent()
        )

        monitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    evaluateTick()
                } catch (_: Exception) {
                    // Keep monitor resilient across transient OS/package queries
                }
                val pollMs = try {
                    preferencesStore.getPreferencesOnce().pollIntervalMs
                } catch (_: Exception) {
                    1200L
                }
                delay(pollMs)
            }
        }
    }

    private suspend fun evaluateTick() {
        val prefs = preferencesStore.getPreferencesOnce()
        if (!prefs.monitoringEnabled) {
            restoreIfNeededOnShutdown()
            lastDetectedForegroundPackage = null
            _runtimeStatus.value = _runtimeStatus.value.copy(
                serviceRunning = false,
                controllingActive = false,
                currentVolumePercent = audioController.getCurrentMediaVolumePercent()
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val currentVolume = audioController.getCurrentMediaVolumePercent()
        val lookbackMs = if (lastDetectedForegroundPackage == null) 30 * 60_000L else 60_000L
        val detectedPkg = UsageAccessHelper.detectForegroundPackage(this, lookbackMs)

        val effectivePkg = when {
            detectedPkg == null -> lastDetectedForegroundPackage
            detectedPkg == packageName -> lastDetectedForegroundPackage
            else -> {
                lastDetectedForegroundPackage = detectedPkg
                detectedPkg
            }
        }

        val profiles = database.appProfileDao().getAllOnce().map { it.toDomain() }
        val schedules = database.quietScheduleDao().getAllOnce().map { it.toDomain() }

        val decision = ScheduleEvaluator.evaluate(
            foregroundPackage = effectivePkg,
            profiles = profiles,
            schedules = schedules,
            defaultRestoreOnExit = prefs.defaultRestoreOnExit
        )

        val activeSession = preferencesStore.getActiveSession()
        val appName = effectivePkg?.let { appsProvider.resolveAppName(it) } ?: "Peranti"

        if (decision.shouldControl) {
            val targetPkg = effectivePkg ?: "global_schedule"
            val targetAppName = if (effectivePkg != null) appName else "Jadual Global"
            val previousVolBeforeChange = currentVolume

            if (activeSession.savedRestoreVolumePercent == null) {
                preferencesStore.saveActiveSession(
                    restoreVolumePercent = currentVolume,
                    managedPackageName = targetPkg,
                    managedAppName = targetAppName
                )
            } else if (activeSession.managedPackageName != targetPkg) {
                preferencesStore.saveActiveSession(
                    restoreVolumePercent = activeSession.savedRestoreVolumePercent,
                    managedPackageName = targetPkg,
                    managedAppName = targetAppName
                )
            }

            if (currentVolume != decision.targetVolumePercent ||
                activeSession.managedPackageName != targetPkg
            ) {
                audioController.setMediaVolumePercent(decision.targetVolumePercent)
                (application as? SenyapPintarApp)?.container?.repository?.recordLog(
                    packageName = targetPkg,
                    appName = targetAppName,
                    actionType = decision.actionType,
                    previousVolumePercent = previousVolBeforeChange,
                    appliedVolumePercent = decision.targetVolumePercent,
                    reasonLabel = decision.reasonLabel
                )
                updateNotification(
                    "$targetAppName: Kelantangan media ${decision.targetVolumePercent}% (${decision.reasonLabel})"
                )
            }

            _runtimeStatus.value = MonitorRuntimeStatus(
                serviceRunning = true,
                foregroundPackage = effectivePkg,
                foregroundAppName = appName,
                controllingActive = true,
                activeReason = decision.reasonLabel,
                activeScheduleTitle = decision.activeScheduleTitle,
                currentVolumePercent = audioController.getCurrentMediaVolumePercent()
            )
        } else {
            if (activeSession.savedRestoreVolumePercent != null) {
                val managedPkg = activeSession.managedPackageName
                val managedProfile = managedPkg?.let { pkg ->
                    profiles.firstOrNull { it.packageName == pkg }
                }
                val shouldRestore = managedProfile?.restoreOnExit ?: prefs.defaultRestoreOnExit
                if (shouldRestore) {
                    val restoreVol = activeSession.savedRestoreVolumePercent
                    audioController.setMediaVolumePercent(restoreVol)
                    (application as? SenyapPintarApp)?.container?.repository?.recordLog(
                        packageName = managedPkg ?: "sistem",
                        appName = activeSession.managedAppName ?: "Aplikasi",
                        actionType = LogActionType.DIPULIHKAN,
                        previousVolumePercent = currentVolume,
                        appliedVolumePercent = restoreVol,
                        reasonLabel = "Keluar aplikasi -> Kelantangan asal dipulihkan"
                    )
                }
                preferencesStore.clearActiveSession()
                updateNotification(getString(R.string.notif_monitor_idle))
            }

            _runtimeStatus.value = MonitorRuntimeStatus(
                serviceRunning = true,
                foregroundPackage = effectivePkg,
                foregroundAppName = appName,
                controllingActive = false,
                activeReason = null,
                activeScheduleTitle = decision.activeScheduleTitle,
                currentVolumePercent = audioController.getCurrentMediaVolumePercent()
            )
        }
    }

    private suspend fun restoreIfNeededOnShutdown() {
        val activeSession = preferencesStore.getActiveSession()
        val restoreVol = activeSession.savedRestoreVolumePercent
        if (restoreVol != null) {
            val currentVol = audioController.getCurrentMediaVolumePercent()
            val prefs = preferencesStore.getPreferencesOnce()
            if (prefs.defaultRestoreOnExit) {
                audioController.setMediaVolumePercent(restoreVol)
                (application as? SenyapPintarApp)?.container?.repository?.recordLog(
                    packageName = activeSession.managedPackageName ?: "sistem",
                    appName = activeSession.managedAppName ?: "Aplikasi",
                    actionType = LogActionType.DIPULIHKAN,
                    previousVolumePercent = currentVol,
                    appliedVolumePercent = restoreVol,
                    reasonLabel = "Pemantauan dihentikan -> Kelantangan dipulihkan"
                )
            }
            preferencesStore.clearActiveSession()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_monitor_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_monitor_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(contentText: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaMonitorForegroundService::class.java).apply { action = ACTION_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_monitor_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_pause_monitor), pauseIntent)
            .build()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        _runtimeStatus.value = _runtimeStatus.value.copy(
            serviceRunning = false,
            controllingActive = false
        )
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "senyap_pintar_monitor_channel"
        private const val NOTIFICATION_ID = 1042
        private const val ACTION_START = "com.muzrc.action.START_MONITOR"
        private const val ACTION_STOP = "com.muzrc.action.STOP_MONITOR"
        private const val ACTION_PAUSE = "com.muzrc.action.PAUSE_MONITOR"

        private val _runtimeStatus = MutableStateFlow(MonitorRuntimeStatus())
        val runtimeStatus: StateFlow<MonitorRuntimeStatus> = _runtimeStatus.asStateFlow()

        fun startMonitoring(context: Context) {
            val intent = Intent(context, MediaMonitorForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, MediaMonitorForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

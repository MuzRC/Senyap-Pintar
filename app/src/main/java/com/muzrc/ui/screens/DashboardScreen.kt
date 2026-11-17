package com.muzrc.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.muzrc.data.UsageAccessHelper
import com.muzrc.model.ActivityLogEntry
import com.muzrc.model.AppSoundProfile
import com.muzrc.model.LogActionType
import com.muzrc.model.ProfileMode
import com.muzrc.model.QuietSchedule
import com.muzrc.model.UiState
import com.muzrc.model.UserPreferences
import com.muzrc.service.MediaMonitorForegroundService
import com.muzrc.service.MonitorRuntimeStatus
import com.muzrc.ui.components.AppPackageIcon
import com.muzrc.ui.components.EmptyStateCard
import com.muzrc.ui.components.SectionHeader
import com.muzrc.ui.components.StatusPill
import com.muzrc.ui.components.VolumeBarIndicator
import com.muzrc.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    preferences: UserPreferences,
    profilesState: UiState<List<AppSoundProfile>>,
    schedulesState: UiState<List<QuietSchedule>>,
    logsState: UiState<List<ActivityLogEntry>>,
    runtimeStatus: MonitorRuntimeStatus,
    manualVolumePercent: Int,
    onToggleMonitoring: (Boolean) -> Unit,
    onVolumeSliderChange: (Int) -> Unit,
    onRefreshVolume: () -> Unit,
    onToggleProfileMonitored: (AppSoundProfile) -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToSchedules: () -> Unit,
    onShowMessage: (String) -> Unit,
    isScheduleActiveNow: (QuietSchedule) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasUsagePermission by remember {
        mutableStateOf(UsageAccessHelper.hasUsageStatsPermission(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(UsageAccessHelper.hasNotificationPermission(context))
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (!granted) {
            onShowMessage("Kebenaran notifikasi diperlukan untuk memaparkan status pemantauan.")
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasUsagePermission = UsageAccessHelper.hasUsageStatsPermission(context)
        hasNotificationPermission = UsageAccessHelper.hasNotificationPermission(context)
        onRefreshVolume()
    }

    val profiles = (profilesState as? UiState.Success)?.data.orEmpty()
    val monitoredProfiles = profiles.filter { it.monitored }
    val schedules = (schedulesState as? UiState.Success)?.data.orEmpty()
    val activeSchedulesCount = schedules.count { it.enabled && isScheduleActiveNow(it) }
    val todayLogs = (logsState as? UiState.Success)?.data.orEmpty()
    val todayMuteCount = todayLogs.count { it.actionType != LogActionType.DIPULIHKAN }

    val liveVolume = if (runtimeStatus.serviceRunning) {
        runtimeStatus.currentVolumePercent
    } else {
        manualVolumePercent
    }
    var draggingVolume by remember { mutableStateOf<Float?>(null) }
    val displayedVolume = draggingVolume?.roundToInt() ?: liveVolume

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Automation Control Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = if (preferences.monitoringEnabled && hasUsagePermission) {
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                        MaterialTheme.colorScheme.surfaceContainer
                                    )
                                } else {
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        MaterialTheme.colorScheme.surfaceContainer
                                    )
                                }
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (preferences.monitoringEnabled && hasUsagePermission) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (runtimeStatus.controllingActive) {
                                                Icons.Rounded.VolumeOff
                                            } else {
                                                Icons.Rounded.GraphicEq
                                            },
                                            contentDescription = null,
                                            tint = if (preferences.monitoringEnabled && hasUsagePermission) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = if (preferences.monitoringEnabled && hasUsagePermission) {
                                            "Pemantauan Pintar Aktif"
                                        } else {
                                            "Pemantauan Dijeda"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when {
                                            !hasUsagePermission -> "Menunggu kebenaran Akses Penggunaan"
                                            runtimeStatus.controllingActive ->
                                                "${runtimeStatus.foregroundAppName ?: "Aplikasi"} • ${runtimeStatus.activeReason ?: "Dikawal"}"
                                            preferences.monitoringEnabled ->
                                                "Sedang memantau ${monitoredProfiles.size} aplikasi & ${schedules.count { it.enabled }} jadual"
                                            else -> "Hidupkan suis untuk mula kawalan automatik"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = preferences.monitoringEnabled,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        if (!hasUsagePermission) {
                                            onShowMessage("Sila benarkan Akses Penggunaan untuk mengesan aplikasi dibuka.")
                                            UsageAccessHelper.openUsageAccessSettings(context)
                                            return@Switch
                                        }
                                        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        onToggleMonitoring(true)
                                        MediaMonitorForegroundService.startMonitoring(context)
                                    } else {
                                        onToggleMonitoring(false)
                                        MediaMonitorForegroundService.stopMonitoring(context)
                                    }
                                },
                                modifier = Modifier.testTag("master_monitor_switch")
                            )
                        }

                        if (activeSchedulesCount > 0) {
                            val activeNames = schedules
                                .filter { it.enabled && isScheduleActiveNow(it) }
                                .joinToString(", ") { it.title }
                            StatusPill(
                                label = "Jadual Aktif: $activeNames",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        VolumeBarIndicator(volumePercent = displayedVolume)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Slider(
                                value = draggingVolume ?: displayedVolume.toFloat(),
                                onValueChange = {
                                    draggingVolume = it
                                    onVolumeSliderChange(it.roundToInt())
                                },
                                onValueChangeFinished = {
                                    draggingVolume = null
                                },
                                valueRange = 0f..100f,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("volume_slider")
                            )
                            FilledTonalButton(
                                onClick = {
                                    draggingVolume = null
                                    val next = if (displayedVolume == 0) 60 else 0
                                    onVolumeSliderChange(next)
                                },
                                modifier = Modifier.testTag("quick_mute_button")
                            ) {
                                Icon(
                                    imageVector = if (displayedVolume == 0) {
                                        Icons.Rounded.VolumeUp
                                    } else {
                                        Icons.Rounded.VolumeMute
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (displayedVolume == 0) "Pulih" else "Senyap")
                            }
                        }
                    }
                }
            }
        }

        // 2. Permission Readiness Banner (shown if permissions missing)
        item {
            AnimatedVisibility(visible = !hasUsagePermission || !hasNotificationPermission) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Persediaan Kebenaran Sistem",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "SenyapPintar memerlukan akses khas untuk mengetahui aplikasi yang dibuka di skrin hadapan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!hasUsagePermission) {
                                Button(
                                    onClick = { UsageAccessHelper.openUsageAccessSettings(context) },
                                    modifier = Modifier.testTag("grant_usage_access_button")
                                ) {
                                    Text("Beri Akses Penggunaan")
                                }
                            }
                            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                OutlinedButton(
                                    onClick = {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    modifier = Modifier.testTag("grant_notif_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Benarkan Notifikasi")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Quick Metrics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardMetricCard(
                    label = "Dipantau",
                    value = monitoredProfiles.size.toString(),
                    icon = Icons.Rounded.Apps,
                    onClick = onNavigateToProfiles,
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricCard(
                    label = "Jadual",
                    value = schedules.count { it.enabled }.toString(),
                    icon = Icons.Rounded.Schedule,
                    onClick = onNavigateToSchedules,
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricCard(
                    label = "Senyap Hari Ini",
                    value = todayMuteCount.toString(),
                    icon = Icons.Rounded.CheckCircle,
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4. Monitored Applications Status Section
        item {
            SectionHeader(
                title = "Status Aplikasi Dipantau",
                subtitle = "Pantau profil suara setiap aplikasi yang dipilih",
                action = {
                    FilledTonalButton(
                        onClick = onNavigateToProfiles,
                        modifier = Modifier.testTag("dashboard_manage_apps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Urus")
                    }
                }
            )
        }

        when (profilesState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.VolumeOff,
                        title = "Ralat Memuatkan Profil",
                        message = profilesState.message
                    )
                }
            }

            is UiState.Success -> {
                if (profiles.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Apps,
                            title = "Belum Ada Aplikasi Dipantau",
                            message = "Pilih aplikasi seperti TikTok, Instagram, atau permainan untuk menyenyapkan audio media secara automatik semasa dibuka.",
                            actionLabel = "Pilih Aplikasi Sekarang",
                            onAction = onNavigateToProfiles
                        )
                    }
                } else {
                    items(profiles, key = { it.packageName }) { profile ->
                        val isControllingThisApp = runtimeStatus.controllingActive &&
                            runtimeStatus.foregroundPackage == profile.packageName
                        MonitoredAppStatusCard(
                            profile = profile,
                            isControllingNow = isControllingThisApp,
                            monitoringServiceActive = preferences.monitoringEnabled && hasUsagePermission,
                            onToggle = { onToggleProfileMonitored(profile) },
                            onClick = onNavigateToProfiles
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MonitoredAppStatusCard(
    profile: AppSoundProfile,
    isControllingNow: Boolean,
    monitoringServiceActive: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val statusText = when {
        isControllingNow -> "Sedang Disenyapkan"
        !profile.monitored -> "Dimatikan"
        !monitoringServiceActive -> "Sedia (Dijeda)"
        profile.scheduleOnly -> "Aktif Ikut Jadual"
        else -> "Aktif Dipantau"
    }
    val (pillBg, pillFg) = when {
        isControllingNow -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        profile.monitored && monitoringServiceActive ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("dashboard_app_card_${profile.packageName}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppPackageIcon(
                packageName = profile.packageName,
                appName = profile.appName,
                size = 46.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        label = statusText,
                        containerColor = pillBg,
                        contentColor = pillFg
                    )
                    val modeText = if (profile.profileMode == ProfileMode.NORMAL) {
                        "Normal"
                    } else {
                        "${profile.targetVolumePercent}%"
                    }
                    StatusPill(
                        label = modeText,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = profile.monitored,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("dashboard_app_switch_${profile.packageName}")
            )
        }
    }
}

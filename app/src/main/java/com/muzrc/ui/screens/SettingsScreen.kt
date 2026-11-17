package com.muzrc.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.muzrc.auth.signOutUser
import com.muzrc.data.UsageAccessHelper
import com.muzrc.model.ThemeMode
import com.muzrc.model.UserPreferences
import com.muzrc.service.MediaMonitorForegroundService
import com.muzrc.ui.components.SectionHeader
import com.muzrc.ui.components.StatusPill
import com.muzrc.ui.theme.JetBrainsMonoFontFamily
import com.muzrc.ui.viewmodel.CloudSyncState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    cloudSyncState: CloudSyncState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDefaultRestoreOnExit: (Boolean) -> Unit,
    onSetPollIntervalMs: (Long) -> Unit,
    onBackupNow: () -> Unit,
    onRestoreFromCloud: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser = Firebase.auth.currentUser
    val dateTimeFormatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("ms", "MY")) }

    var hasUsagePermission by remember {
        mutableStateOf(UsageAccessHelper.hasUsageStatsPermission(context))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasUsagePermission = UsageAccessHelper.hasUsageStatsPermission(context)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(
                title = "Tetapan & Sandaran Awan",
                subtitle = "Urus tema malam, automasi pemulihan, dan penyegerakan akaun"
            )
        }

        // 1. Cloud Backup & Account Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.displayName ?: "Akaun Google Disambung",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = currentUser?.email ?: "-",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val lastBackupLabel = preferences.lastCloudBackupAtMillis?.let {
                        "Sandaran terakhir: ${dateTimeFormatter.format(Date(it))}"
                    } ?: "Belum pernah disandarkan secara manual"

                    Text(
                        text = cloudSyncState.statusMessage ?: lastBackupLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onBackupNow,
                            enabled = !cloudSyncState.isSyncing,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_now_button")
                        ) {
                            if (cloudSyncState.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sandar")
                        }

                        FilledTonalButton(
                            onClick = onRestoreFromCloud,
                            enabled = !cloudSyncState.isSyncing,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("restore_cloud_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pulihkan")
                        }
                    }

                    HorizontalDivider()

                    OutlinedButton(
                        onClick = {
                            MediaMonitorForegroundService.stopMonitoring(context)
                            signOutUser(credentialManager, scope)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Keluar Akaun Google")
                    }
                }
            }
        }

        // 2. Dark Theme Selector Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Tema & Keselesaan Malam", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Pilih mod gelap untuk mengurangkan silau pada waktu malam",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("theme_mode_selector"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.labelMs) },
                                modifier = Modifier.testTag("theme_chip_${mode.name}")
                            )
                        }
                    }
                }
            }
        }

        // 3. Automation Behavior Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Kelakuan Kawalan Media", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Pulihkan Kelantangan Asal Semasa Keluar",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Tetapan lalai apabila keluar daripada aplikasi dipantau atau jadual tamat",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.defaultRestoreOnExit,
                            onCheckedChange = onSetDefaultRestoreOnExit,
                            modifier = Modifier.testTag("settings_restore_switch")
                        )
                    }

                    HorizontalDivider()

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Kekerapan Semakan Aplikasi", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${preferences.pollIntervalMs} ms",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = preferences.pollIntervalMs.toFloat(),
                            onValueChange = { onSetPollIntervalMs((it / 100f).roundToInt() * 100L) },
                            valueRange = 800f..3000f,
                            modifier = Modifier.testTag("poll_interval_slider")
                        )
                        Text(
                            "Nilai lebih rendah bertindak lebih pantas; nilai lebih tinggi menjimatkan bateri.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. System Permissions Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Akses Penggunaan", style = MaterialTheme.typography.titleSmall)
                        }
                        StatusPill(
                            label = if (hasUsagePermission) "Dibenarkan" else "Diperlukan",
                            containerColor = if (hasUsagePermission) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = if (hasUsagePermission) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = { UsageAccessHelper.openUsageAccessSettings(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_usage_settings_button")
                    ) {
                        Text("Buka Tetapan Akses Penggunaan Android")
                    }
                }
            }
        }
    }
}

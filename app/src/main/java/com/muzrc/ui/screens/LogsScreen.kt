package com.muzrc.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muzrc.data.LogExporter
import com.muzrc.data.SenyapRepository
import com.muzrc.model.ActivityLogEntry
import com.muzrc.model.LogActionType
import com.muzrc.model.UiState
import com.muzrc.ui.components.AppPackageIcon
import com.muzrc.ui.components.EmptyStateCard
import com.muzrc.ui.components.SectionHeader
import com.muzrc.ui.components.StatusPill
import com.muzrc.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
    availableDates: List<String>,
    selectedDateKey: String,
    logsState: UiState<List<ActivityLogEntry>>,
    onSelectDate: (String) -> Unit,
    onClearDateLogs: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale("ms", "MY")) }
    val todayKey = remember { SenyapRepository.todayDateKey() }

    val entries = (logsState as? UiState.Success)?.data.orEmpty()
    val muteCount = entries.count { it.actionType != LogActionType.DIPULIHKAN }
    val restoreCount = entries.count { it.actionType == LogActionType.DIPULIHKAN }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Log Aktiviti Harian",
                subtitle = "Rekod automatik penyenyapan dan pemulihan kelantangan media",
                action = {
                    if (entries.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("clear_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Bersihkan log tarikh ini",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }

        // Date Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableDates.forEach { dateKey ->
                    val label = if (dateKey == todayKey) "Hari Ini ($dateKey)" else dateKey
                    FilterChip(
                        selected = dateKey == selectedDateKey,
                        onClick = { onSelectDate(dateKey) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("log_date_chip_$dateKey")
                    )
                }
            }
        }

        // Summary + Export Actions Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Pelarasan / Senyap",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$muteCount kali",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Pemulihan Keluar",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$restoreCount kali",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                LogExporter.shareCsv(context, selectedDateKey, entries)
                                    .onFailure {
                                        onShowMessage("Gagal mengeksport CSV: ${it.localizedMessage}")
                                    }
                            },
                            enabled = entries.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_csv_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Eksport CSV")
                        }

                        FilledTonalButton(
                            onClick = {
                                LogExporter.shareTextSummary(context, selectedDateKey, entries)
                                    .onFailure {
                                        onShowMessage("Gagal berkongsi ringkasan: ${it.localizedMessage}")
                                    }
                            },
                            enabled = entries.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_summary_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kongsi Teks")
                        }
                    }
                }
            }
        }

        when (logsState) {
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
                        icon = Icons.Rounded.History,
                        title = "Ralat Memuatkan Log",
                        message = logsState.message
                    )
                }
            }

            is UiState.Success -> {
                if (entries.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.History,
                            title = "Tiada Aktiviti Pada Tarikh Ini",
                            message = "Apabila Pemantauan Pintar aktif, setiap kali aplikasi disenyapkan atau kelantangan dipulihkan akan direkodkan di sini."
                        )
                    }
                } else {
                    items(entries, key = { it.id }) { entry ->
                        ActivityLogCard(
                            entry = entry,
                            timeLabel = timeFormatter.format(Date(entry.timestampMillis))
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Padam Log Aktiviti?") },
            text = { Text("Semua rekod log untuk tarikh $selectedDateKey akan dipadamkan secara kekal.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearDateLogs()
                        showClearDialog = false
                    }
                ) {
                    Text("Padam", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun ActivityLogCard(
    entry: ActivityLogEntry,
    timeLabel: String
) {
    val (pillBg, pillFg) = when (entry.actionType) {
        LogActionType.DISENYAPKAN ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        LogActionType.DILARASKAN ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        LogActionType.JADUAL_AKTIF ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        LogActionType.DIPULIHKAN ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppPackageIcon(
                packageName = entry.packageName,
                appName = entry.appName,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.appName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        label = entry.actionType.labelMs,
                        containerColor = pillBg,
                        contentColor = pillFg
                    )
                    Text(
                        text = "${entry.previousVolumePercent}% → ${entry.appliedVolumePercent}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.reasonLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

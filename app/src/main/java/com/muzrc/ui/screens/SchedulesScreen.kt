package com.muzrc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muzrc.model.QuietSchedule
import com.muzrc.model.UiState
import com.muzrc.ui.components.EmptyStateCard
import com.muzrc.ui.components.SectionHeader
import com.muzrc.ui.components.StatusPill
import com.muzrc.ui.components.formatDaysSummary
import com.muzrc.ui.components.formatTime24h
import com.muzrc.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

@Composable
fun SchedulesScreen(
    schedulesState: UiState<List<QuietSchedule>>,
    isScheduleActiveNow: (QuietSchedule) -> Boolean,
    onSaveSchedule: (
        existingId: String?,
        title: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        daysOfWeek: List<Int>,
        targetVolumePercent: Int,
        applyToAllApps: Boolean,
        enabled: Boolean
    ) -> Unit,
    onToggleSchedule: (QuietSchedule) -> Unit,
    onDeleteSchedule: (QuietSchedule) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditor by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<QuietSchedule?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingSchedule = null
                    showEditor = true
                },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Tambah Jadual") },
                modifier = Modifier.testTag("add_schedule_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionHeader(
                    title = "Jadual Mod Senyap",
                    subtitle = "Automatikkan kawalan bunyi media mengikut waktu & hari"
                )
            }

            when (schedulesState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Schedule,
                            title = "Ralat Memuatkan Jadual",
                            message = schedulesState.message
                        )
                    }
                }

                is UiState.Success -> {
                    val list = schedulesState.data
                    if (list.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Rounded.Bedtime,
                                title = "Tiada Jadual Mod Senyap",
                                message = "Cipta jadual seperti Waktu Tidur (22:30 – 06:00) atau Waktu Kerja supaya media kekal senyap secara automatik.",
                                actionLabel = "Tambah Jadual Baharu",
                                onAction = {
                                    editingSchedule = null
                                    showEditor = true
                                }
                            )
                        }
                    } else {
                        items(list, key = { it.id }) { schedule ->
                            ScheduleCard(
                                schedule = schedule,
                                isActiveNow = schedule.enabled && isScheduleActiveNow(schedule),
                                onToggle = { onToggleSchedule(schedule) },
                                onEdit = {
                                    editingSchedule = schedule
                                    showEditor = true
                                },
                                onDelete = { onDeleteSchedule(schedule) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ScheduleEditorSheet(
            existing = editingSchedule,
            onDismiss = { showEditor = false },
            onSave = { title, sh, sm, eh, em, days, vol, allApps ->
                onSaveSchedule(
                    editingSchedule?.id,
                    title,
                    sh,
                    sm,
                    eh,
                    em,
                    days,
                    vol,
                    allApps,
                    editingSchedule?.enabled ?: true
                )
                showEditor = false
            }
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: QuietSchedule,
    isActiveNow: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onEdit)
            .testTag("schedule_card_${schedule.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = schedule.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isActiveNow) {
                            StatusPill(
                                label = "AKTIF SEKARANG",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatTime24h(schedule.startHour, schedule.startMinute)} — ${formatTime24h(schedule.endHour, schedule.endMinute)}",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (schedule.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("schedule_switch_${schedule.id}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatDaysSummary(schedule.daysOfWeek),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(
                            label = "Kelantangan ${schedule.targetVolumePercent}%",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        StatusPill(
                            label = if (schedule.applyToAllApps) "Semua Aplikasi" else "Aplikasi Dipantau",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_schedule_${schedule.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Padam jadual ${schedule.title}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditorSheet(
    existing: QuietSchedule?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        daysOfWeek: List<Int>,
        targetVolumePercent: Int,
        applyToAllApps: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(existing?.title ?: "Waktu Senyap") }
    var startHour by remember { mutableIntStateOf(existing?.startHour ?: 22) }
    var startMinute by remember { mutableIntStateOf(existing?.startMinute ?: 30) }
    var endHour by remember { mutableIntStateOf(existing?.endHour ?: 6) }
    var endMinute by remember { mutableIntStateOf(existing?.endMinute ?: 0) }
    var selectedDays by remember {
        mutableStateOf(existing?.daysOfWeek?.toSet() ?: setOf(1, 2, 3, 4, 5, 6, 7))
    }
    var targetVolume by remember { mutableIntStateOf(existing?.targetVolumePercent ?: 0) }
    var applyToAllApps by remember { mutableStateOf(existing?.applyToAllApps ?: true) }
    var pickingStartTime by remember { mutableStateOf<Boolean?>(null) }

    val dayLabels = listOf(
        1 to "Isn",
        2 to "Sel",
        3 to "Rab",
        4 to "Kha",
        5 to "Jum",
        6 to "Sab",
        7 to "Aha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (existing == null) "Tambah Jadual Baharu" else "Sunting Jadual",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 80) title = it },
                label = { Text("Nama Jadual") },
                placeholder = { Text("Contoh: Waktu Tidur atau Mesyuarat") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("schedule_title_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { pickingStartTime = true }
                        .testTag("pick_start_time_button"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Masa Mula", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatTime24h(startHour, startMinute),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { pickingStartTime = false }
                        .testTag("pick_end_time_button"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Masa Tamat", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatTime24h(endHour, endMinute),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hari Aktif", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dayLabels.forEach { (dayNum, label) ->
                        val isSelected = dayNum in selectedDays
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedDays = if (isSelected && selectedDays.size > 1) {
                                    selectedDays - dayNum
                                } else {
                                    selectedDays + dayNum
                                }
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("day_chip_$dayNum")
                        )
                    }
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Kelantangan Media Semasa Jadual", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "$targetVolume%",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = targetVolume.toFloat(),
                    onValueChange = { targetVolume = it.roundToInt() },
                    valueRange = 0f..100f,
                    modifier = Modifier.testTag("schedule_volume_slider")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Terapkan kepada Semua Aplikasi", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (applyToAllApps) {
                            "Semua aplikasi disenyapkan semasa waktu jadual ini"
                        } else {
                            "Hanya aplikasi dalam senarai dipantau akan disenyapkan"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = applyToAllApps,
                    onCheckedChange = { applyToAllApps = it },
                    modifier = Modifier.testTag("schedule_scope_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Batal")
                }
                Button(
                    onClick = {
                        onSave(
                            title,
                            startHour,
                            startMinute,
                            endHour,
                            endMinute,
                            selectedDays.toList().sorted(),
                            targetVolume,
                            applyToAllApps
                        )
                    },
                    enabled = title.isNotBlank() && selectedDays.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_schedule_button")
                ) {
                    Text("Simpan Jadual")
                }
            }
        }
    }

    val isPickingStart = pickingStartTime
    if (isPickingStart != null) {
        val pickerState = rememberTimePickerState(
            initialHour = if (isPickingStart) startHour else endHour,
            initialMinute = if (isPickingStart) startMinute else endMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pickingStartTime = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isPickingStart) {
                            startHour = pickerState.hour
                            startMinute = pickerState.minute
                        } else {
                            endHour = pickerState.hour
                            endMinute = pickerState.minute
                        }
                        pickingStartTime = null
                    }
                ) {
                    Text("Tetapkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingStartTime = null }) {
                    Text("Batal")
                }
            },
            title = {
                Text(if (isPickingStart) "Pilih Masa Mula" else "Pilih Masa Tamat")
            },
            text = {
                TimePicker(state = pickerState)
            }
        )
    }
}

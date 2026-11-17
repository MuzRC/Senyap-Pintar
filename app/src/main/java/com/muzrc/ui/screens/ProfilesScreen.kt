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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muzrc.model.AppSoundProfile
import com.muzrc.model.InstalledAppInfo
import com.muzrc.model.ProfileMode
import com.muzrc.model.UiState
import com.muzrc.ui.components.AppPackageIcon
import com.muzrc.ui.components.EmptyStateCard
import com.muzrc.ui.components.SectionHeader
import com.muzrc.ui.components.StatusPill
import com.muzrc.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

private enum class ProfileFilter(val labelMs: String) {
    SEMUA("Semua"),
    DIPANTAU("Dipantau"),
    BELUM("Belum Ditambah")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    profilesState: UiState<List<AppSoundProfile>>,
    installedApps: List<InstalledAppInfo>,
    defaultRestoreOnExit: Boolean,
    onRefreshApps: () -> Unit,
    onSaveProfile: (AppSoundProfile) -> Unit,
    onDeleteProfile: (packageName: String, appName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ProfileFilter.SEMUA) }
    var editingApp by remember { mutableStateOf<Pair<InstalledAppInfo, AppSoundProfile?>?>(null) }

    val savedProfiles = (profilesState as? UiState.Success)?.data.orEmpty()
    val savedByPkg = remember(savedProfiles) { savedProfiles.associateBy { it.packageName } }

    // Merge installed apps with any backed-up profiles whose apps might not currently be installed
    val combinedApps = remember(installedApps, savedProfiles) {
        val installedPkgs = installedApps.map { it.packageName }.toSet()
        val extraSaved = savedProfiles
            .filter { it.packageName !in installedPkgs }
            .map { InstalledAppInfo(it.packageName, it.appName) }
        (installedApps + extraSaved).sortedWith(
            compareByDescending<InstalledAppInfo> { savedByPkg[it.packageName]?.monitored == true }
                .thenByDescending { savedByPkg.containsKey(it.packageName) }
                .thenBy { it.appName.lowercase() }
        )
    }

    val filteredApps = remember(combinedApps, savedByPkg, searchQuery, selectedFilter) {
        combinedApps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            val saved = savedByPkg[app.packageName]
            val matchesFilter = when (selectedFilter) {
                ProfileFilter.SEMUA -> true
                ProfileFilter.DIPANTAU -> saved?.monitored == true
                ProfileFilter.BELUM -> saved == null
            }
            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        SectionHeader(
            title = "Profil Suara Aplikasi",
            subtitle = "Ketik mana-mana aplikasi untuk menetapkan paras suara media",
            action = {
                IconButton(
                    onClick = onRefreshApps,
                    modifier = Modifier.testTag("refresh_apps_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Muat semula senarai aplikasi"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari nama aplikasi...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Kosongkan carian")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ProfileFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.labelMs) },
                    modifier = Modifier.testTag("profile_filter_${filter.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                EmptyStateCard(
                    icon = Icons.Rounded.Apps,
                    title = "Tiada Aplikasi Ditemui",
                    message = "Cuba ubah kata carian atau penapis untuk melihat aplikasi yang tersedia pada peranti."
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { appInfo ->
                    val existing = savedByPkg[appInfo.packageName]
                    AppProfileListCard(
                        appInfo = appInfo,
                        existingProfile = existing,
                        onClick = { editingApp = appInfo to existing }
                    )
                }
            }
        }
    }

    val currentPair = editingApp
    if (currentPair != null) {
        ProfileEditorBottomSheet(
            appInfo = currentPair.first,
            existingProfile = currentPair.second,
            defaultRestoreOnExit = defaultRestoreOnExit,
            onDismiss = { editingApp = null },
            onSave = { updated ->
                onSaveProfile(updated)
                editingApp = null
            },
            onDelete = {
                onDeleteProfile(currentPair.first.packageName, currentPair.first.appName)
                editingApp = null
            }
        )
    }
}

@Composable
private fun AppProfileListCard(
    appInfo: InstalledAppInfo,
    existingProfile: AppSoundProfile?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("profile_app_card_${appInfo.packageName}"),
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
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                size = 44.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appInfo.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (existingProfile != null && existingProfile.monitored) {
                val (bg, fg) = if (existingProfile.targetVolumePercent == 0) {
                    MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                }
                StatusPill(
                    label = "${existingProfile.profileMode.name} ${existingProfile.targetVolumePercent}%",
                    containerColor = bg,
                    contentColor = fg
                )
            } else if (existingProfile != null) {
                StatusPill(
                    label = "Dijeda",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Sunting profil ${appInfo.appName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditorBottomSheet(
    appInfo: InstalledAppInfo,
    existingProfile: AppSoundProfile?,
    defaultRestoreOnExit: Boolean,
    onDismiss: () -> Unit,
    onSave: (AppSoundProfile) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var monitored by remember { mutableStateOf(existingProfile?.monitored ?: true) }
    var selectedMode by remember {
        mutableStateOf(existingProfile?.profileMode ?: ProfileMode.SENYAP)
    }
    var targetVolume by remember {
        mutableIntStateOf(existingProfile?.targetVolumePercent ?: 0)
    }
    var restoreOnExit by remember {
        mutableStateOf(existingProfile?.restoreOnExit ?: defaultRestoreOnExit)
    }
    var scheduleOnly by remember { mutableStateOf(existingProfile?.scheduleOnly ?: false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppPackageIcon(
                    packageName = appInfo.packageName,
                    appName = appInfo.appName,
                    size = 48.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo.appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = appInfo.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (existingProfile != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Padam profil",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pantau Aplikasi Ini", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Aktifkan kawalan kelantangan automatik apabila dibuka",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = monitored,
                    onCheckedChange = { monitored = it },
                    modifier = Modifier.testTag("sheet_monitored_switch")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mod Profil Suara", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = {
                                selectedMode = mode
                                targetVolume = when (mode) {
                                    ProfileMode.SENYAP -> 0
                                    ProfileMode.RENDAH -> 15
                                    ProfileMode.KHUSUS -> if (targetVolume == 0 || targetVolume == 100) 30 else targetVolume
                                    ProfileMode.NORMAL -> 100
                                }
                            },
                            label = { Text(mode.labelMs) },
                            modifier = Modifier.testTag("sheet_mode_${mode.name}")
                        )
                    }
                }
            }

            if (selectedMode != ProfileMode.NORMAL) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sasaran Kelantangan Media", style = MaterialTheme.typography.titleSmall)
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
                        onValueChange = {
                            val next = it.roundToInt()
                            targetVolume = next
                            selectedMode = when (next) {
                                0 -> ProfileMode.SENYAP
                                15 -> ProfileMode.RENDAH
                                else -> ProfileMode.KHUSUS
                            }
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.testTag("sheet_volume_slider")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pulihkan Selepas Keluar", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Kembalikan kelantangan asal selepas menutup aplikasi ini",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = restoreOnExit,
                    onCheckedChange = { restoreOnExit = it },
                    modifier = Modifier.testTag("sheet_restore_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ikut Jadual Mod Senyap Sahaja", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Senyapkan hanya apabila ada jadual masa yang sedang aktif",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = scheduleOnly,
                    onCheckedChange = { scheduleOnly = it },
                    modifier = Modifier.testTag("sheet_schedule_only_switch")
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
                            AppSoundProfile(
                                packageName = appInfo.packageName,
                                appName = appInfo.appName,
                                monitored = monitored,
                                profileMode = selectedMode,
                                targetVolumePercent = targetVolume,
                                restoreOnExit = restoreOnExit,
                                scheduleOnly = scheduleOnly
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_profile_button")
                ) {
                    Text("Simpan Profil")
                }
            }
        }
    }
}

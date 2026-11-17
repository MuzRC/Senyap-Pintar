package com.muzrc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.muzrc.SenyapPintarApp
import com.muzrc.data.UsageAccessHelper
import com.muzrc.model.ThemeMode
import com.muzrc.service.MediaMonitorForegroundService
import com.muzrc.ui.screens.DashboardScreen
import com.muzrc.ui.screens.LogsScreen
import com.muzrc.ui.screens.ProfilesScreen
import com.muzrc.ui.screens.SchedulesScreen
import com.muzrc.ui.screens.SettingsScreen
import com.muzrc.ui.viewmodel.MainViewModel

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_PROFILES = "profiles"
const val ROUTE_SCHEDULES = "schedules"
const val ROUTE_LOGS = "logs"
const val ROUTE_SETTINGS = "settings"

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavDestinationItem(ROUTE_DASHBOARD, "Papan", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    NavDestinationItem(ROUTE_PROFILES, "Profil", Icons.Rounded.Apps, Icons.Outlined.Apps),
    NavDestinationItem(ROUTE_SCHEDULES, "Jadual", Icons.Rounded.Schedule, Icons.Outlined.Schedule),
    NavDestinationItem(ROUTE_LOGS, "Log", Icons.Rounded.History, Icons.Outlined.History),
    NavDestinationItem(ROUTE_SETTINGS, "Tetapan", Icons.Rounded.Settings, Icons.Outlined.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val viewModel: MainViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as SenyapPintarApp
                MainViewModel(
                    repository = app.container.repository,
                    audioController = app.container.audioController
                )
            }
        }
    )

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val profilesState by viewModel.profilesState.collectAsStateWithLifecycle()
    val schedulesState by viewModel.schedulesState.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val availableDates by viewModel.availableLogDates.collectAsStateWithLifecycle()
    val selectedDateKey by viewModel.selectedDateKey.collectAsStateWithLifecycle()
    val logsState by viewModel.logsState.collectAsStateWithLifecycle()
    val runtimeStatus by viewModel.runtimeStatus.collectAsStateWithLifecycle()
    val manualVolumePercent by viewModel.manualVolumePercent.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    LaunchedEffect(preferences.monitoringEnabled, runtimeStatus.serviceRunning) {
        if (preferences.monitoringEnabled &&
            UsageAccessHelper.hasUsageStatsPermission(context) &&
            !runtimeStatus.serviceRunning
        ) {
            MediaMonitorForegroundService.startMonitoring(context)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (preferences.monitoringEnabled &&
            UsageAccessHelper.hasUsageStatsPermission(context) &&
            !runtimeStatus.serviceRunning
        ) {
            MediaMonitorForegroundService.startMonitoring(context)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DASHBOARD

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (preferences.monitoringEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SenyapPintar",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val nextMode = when (preferences.themeMode) {
                                    ThemeMode.SISTEM -> ThemeMode.GELAP
                                    ThemeMode.GELAP -> ThemeMode.TERANG
                                    ThemeMode.TERANG -> ThemeMode.SISTEM
                                }
                                viewModel.setThemeMode(nextMode)
                                viewModel.showMessage("Tema ditukar ke: ${nextMode.labelMs}")
                            },
                            modifier = Modifier.testTag("topbar_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (preferences.themeMode == ThemeMode.TERANG) {
                                    Icons.Rounded.LightMode
                                } else {
                                    Icons.Rounded.DarkMode
                                },
                                contentDescription = "Tukar tema gelap atau terang"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("rail_${item.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD,
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize()
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                preferences = preferences,
                                profilesState = profilesState,
                                schedulesState = schedulesState,
                                logsState = logsState,
                                runtimeStatus = runtimeStatus,
                                manualVolumePercent = manualVolumePercent,
                                onToggleMonitoring = viewModel::setMonitoringEnabled,
                                onVolumeSliderChange = viewModel::setMediaVolumePercent,
                                onRefreshVolume = viewModel::refreshMediaVolume,
                                onToggleProfileMonitored = viewModel::toggleProfileMonitored,
                                onNavigateToProfiles = { navigateTo(ROUTE_PROFILES) },
                                onNavigateToSchedules = { navigateTo(ROUTE_SCHEDULES) },
                                onShowMessage = viewModel::showMessage,
                                isScheduleActiveNow = viewModel::isScheduleCurrentlyActive
                            )
                        }

                        composable(ROUTE_PROFILES) {
                            ProfilesScreen(
                                profilesState = profilesState,
                                installedApps = installedApps,
                                defaultRestoreOnExit = preferences.defaultRestoreOnExit,
                                onRefreshApps = viewModel::refreshInstalledApps,
                                onSaveProfile = viewModel::saveProfile,
                                onDeleteProfile = viewModel::deleteProfile
                            )
                        }

                        composable(ROUTE_SCHEDULES) {
                            SchedulesScreen(
                                schedulesState = schedulesState,
                                isScheduleActiveNow = viewModel::isScheduleCurrentlyActive,
                                onSaveSchedule = viewModel::saveSchedule,
                                onToggleSchedule = viewModel::toggleScheduleEnabled,
                                onDeleteSchedule = viewModel::deleteSchedule
                            )
                        }

                        composable(ROUTE_LOGS) {
                            LogsScreen(
                                availableDates = availableDates,
                                selectedDateKey = selectedDateKey,
                                logsState = logsState,
                                onSelectDate = viewModel::selectLogDate,
                                onClearDateLogs = viewModel::clearLogsForSelectedDate,
                                onShowMessage = viewModel::showMessage
                            )
                        }

                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                preferences = preferences,
                                backupState = backupState,
                                updateState = updateState,
                                onSetThemeMode = viewModel::setThemeMode,
                                onSetDefaultRestoreOnExit = viewModel::setDefaultRestoreOnExit,
                                onSetPollIntervalMs = viewModel::setPollIntervalMs,
                                onExportBackupUri = { uri -> viewModel.exportBackupToUri(context, uri) },
                                onImportBackupUri = { uri -> viewModel.importBackupFromUri(context, uri) },
                                onCheckForUpdates = viewModel::checkForUpdates,
                                onDismissUpdateDialog = viewModel::dismissUpdateDialog,
                                onShowMessage = viewModel::showMessage
                            )
                        }
                    }
                }
            }
        }
    }
}

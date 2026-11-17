package com.muzrc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muzrc.data.AudioVolumeController
import com.muzrc.data.ScheduleEvaluator
import com.muzrc.data.SenyapRepository
import com.muzrc.model.ActivityLogEntry
import com.muzrc.model.AppSoundProfile
import com.muzrc.model.InstalledAppInfo
import com.muzrc.model.QuietSchedule
import com.muzrc.model.ThemeMode
import com.muzrc.model.UiState
import com.muzrc.model.UserPreferences
import com.muzrc.service.MediaMonitorForegroundService
import com.muzrc.service.MonitorRuntimeStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

data class CloudSyncState(
    val isSyncing: Boolean = false,
    val statusMessage: String? = null
)

class MainViewModel(
    private val repository: SenyapRepository,
    private val audioController: AudioVolumeController,
    private val currentUserId: String
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UserPreferences()
        )

    val profilesState: StateFlow<UiState<List<AppSoundProfile>>> = repository.profilesFlow
        .map<List<AppSoundProfile>, UiState<List<AppSoundProfile>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(UiState.Error(error.localizedMessage ?: "Gagal memuatkan profil aplikasi."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val schedulesState: StateFlow<UiState<List<QuietSchedule>>> = repository.schedulesFlow
        .map<List<QuietSchedule>, UiState<List<QuietSchedule>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(UiState.Error(error.localizedMessage ?: "Gagal memuatkan jadual mod senyap."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _selectedDateKey = MutableStateFlow(SenyapRepository.todayDateKey())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    val availableLogDates: StateFlow<List<String>> = repository.availableLogDatesFlow
        .map { dates ->
            val today = SenyapRepository.todayDateKey()
            (listOf(today) + dates).distinct()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = listOf(SenyapRepository.todayDateKey())
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val logsState: StateFlow<UiState<List<ActivityLogEntry>>> = _selectedDateKey
        .flatMapLatest { dateKey ->
            repository.observeLogsByDate(dateKey)
                .map<List<ActivityLogEntry>, UiState<List<ActivityLogEntry>>> { UiState.Success(it) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(UiState.Error(error.localizedMessage ?: "Gagal memuatkan log aktiviti."))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val runtimeStatus: StateFlow<MonitorRuntimeStatus> = MediaMonitorForegroundService.runtimeStatus

    private val _manualVolumePercent = MutableStateFlow(audioController.getCurrentMediaVolumePercent())
    val manualVolumePercent: StateFlow<Int> = _manualVolumePercent.asStateFlow()

    private val _cloudSyncState = MutableStateFlow(CloudSyncState())
    val cloudSyncState: StateFlow<CloudSyncState> = _cloudSyncState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        refreshInstalledApps()
        viewModelScope.launch {
            repository.syncInitialCloudIfLocalEmpty(currentUserId)
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = repository.getInstalledApps()
        }
    }

    fun refreshMediaVolume() {
        _manualVolumePercent.value = audioController.getCurrentMediaVolumePercent()
    }

    fun setMediaVolumePercent(percent: Int) {
        audioController.setMediaVolumePercent(percent)
        _manualVolumePercent.value = audioController.getCurrentMediaVolumePercent()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode).onFailure {
                _snackbarMessage.value = "Tema dikemas kini secara tempatan (sandaran awan tertunda)."
            }
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMonitoringEnabled(enabled).onFailure {
                _snackbarMessage.value = "Status pemantauan disimpan secara tempatan."
            }
        }
    }

    fun setDefaultRestoreOnExit(restore: Boolean) {
        viewModelScope.launch {
            repository.setDefaultRestoreOnExit(restore)
        }
    }

    fun setPollIntervalMs(intervalMs: Long) {
        viewModelScope.launch {
            repository.setPollIntervalMs(intervalMs)
        }
    }

    fun saveProfile(profile: AppSoundProfile) {
        viewModelScope.launch {
            val result = repository.saveProfile(profile)
            _snackbarMessage.value = if (result.isSuccess) {
                "Profil ${profile.appName} disimpan."
            } else {
                "Profil ${profile.appName} disimpan di peranti (sandaran awan gagal)."
            }
        }
    }

    fun toggleProfileMonitored(profile: AppSoundProfile) {
        saveProfile(profile.copy(monitored = !profile.monitored))
    }

    fun deleteProfile(packageName: String, appName: String) {
        viewModelScope.launch {
            val result = repository.deleteProfile(packageName)
            _snackbarMessage.value = if (result.isSuccess) {
                "Profil $appName dipadamkan."
            } else {
                "Profil $appName dipadamkan secara tempatan."
            }
        }
    }

    fun saveSchedule(
        existingId: String?,
        title: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        daysOfWeek: List<Int>,
        targetVolumePercent: Int,
        applyToAllApps: Boolean,
        enabled: Boolean = true
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _snackbarMessage.value = "Sila masukkan nama jadual."
            return
        }
        if (daysOfWeek.isEmpty()) {
            _snackbarMessage.value = "Sila pilih sekurang-kurangnya satu hari."
            return
        }
        val now = System.currentTimeMillis()
        val id = existingId ?: "jadual_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val schedule = QuietSchedule(
            id = id,
            title = cleanTitle.take(80),
            startHour = startHour.coerceIn(0, 23),
            startMinute = startMinute.coerceIn(0, 59),
            endHour = endHour.coerceIn(0, 23),
            endMinute = endMinute.coerceIn(0, 59),
            daysOfWeek = daysOfWeek.distinct().sorted(),
            targetVolumePercent = targetVolumePercent.coerceIn(0, 100),
            applyToAllApps = applyToAllApps,
            enabled = enabled,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        viewModelScope.launch {
            val result = repository.saveSchedule(schedule)
            _snackbarMessage.value = if (result.isSuccess) {
                "Jadual \"${schedule.title}\" disimpan."
            } else {
                "Jadual disimpan secara tempatan (sandaran awan tertunda)."
            }
        }
    }

    fun toggleScheduleEnabled(schedule: QuietSchedule) {
        viewModelScope.launch {
            repository.saveSchedule(schedule.copy(enabled = !schedule.enabled))
        }
    }

    fun deleteSchedule(schedule: QuietSchedule) {
        viewModelScope.launch {
            repository.deleteSchedule(schedule.id)
            _snackbarMessage.value = "Jadual \"${schedule.title}\" dipadamkan."
        }
    }

    fun selectLogDate(dateKey: String) {
        _selectedDateKey.value = dateKey
    }

    fun clearLogsForSelectedDate() {
        val date = _selectedDateKey.value
        viewModelScope.launch {
            repository.clearLogsForDate(date)
            _snackbarMessage.value = "Log aktiviti untuk $date telah dibersihkan."
        }
    }

    fun isScheduleCurrentlyActive(schedule: QuietSchedule): Boolean {
        val cal = Calendar.getInstance()
        return ScheduleEvaluator.isScheduleActiveAt(
            schedule = schedule,
            isoDayOfWeek = ScheduleEvaluator.isoDayOfWeek(cal),
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE)
        )
    }

    fun backupNowToCloud() {
        viewModelScope.launch {
            _cloudSyncState.value = CloudSyncState(isSyncing = true, statusMessage = "Menyandar ke awan...")
            val result = repository.backupNowToCloud(currentUserId)
            result.fold(
                onSuccess = {
                    _cloudSyncState.value = CloudSyncState(
                        isSyncing = false,
                        statusMessage = "Sandaran awan berjaya disiapkan."
                    )
                    _snackbarMessage.value = "Semua tetapan dan profil berjaya disandarkan ke awan."
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Gagal menyandar ke awan."
                    _cloudSyncState.value = CloudSyncState(isSyncing = false, statusMessage = msg)
                    _snackbarMessage.value = "Sandaran awan gagal: $msg"
                }
            )
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            _cloudSyncState.value = CloudSyncState(isSyncing = true, statusMessage = "Memulihkan daripada awan...")
            val result = repository.restoreFromCloud(currentUserId)
            result.fold(
                onSuccess = { count ->
                    _cloudSyncState.value = CloudSyncState(
                        isSyncing = false,
                        statusMessage = "$count item dipulihkan daripada awan."
                    )
                    _snackbarMessage.value = if (count > 0) {
                        "$count tetapan dan profil berjaya dipulihkan."
                    } else {
                        "Tiada data sandaran awan ditemui untuk akaun ini."
                    }
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Gagal memulihkan data awan."
                    _cloudSyncState.value = CloudSyncState(isSyncing = false, statusMessage = msg)
                    _snackbarMessage.value = "Pemulihan awan gagal: $msg"
                }
            )
        }
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}

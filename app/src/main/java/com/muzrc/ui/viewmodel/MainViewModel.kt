package com.muzrc.ui.viewmodel

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

data class BackupState(
    val isProcessing: Boolean = false,
    val statusMessage: String? = null
)

class MainViewModel(
    private val repository: SenyapRepository,
    private val audioController: AudioVolumeController
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

    private val _backupState = MutableStateFlow(BackupState())
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        refreshInstalledApps()
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
            repository.setThemeMode(mode)
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMonitoringEnabled(enabled)
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
            repository.saveProfile(profile)
            _snackbarMessage.value = "Profil ${profile.appName} disimpan."
        }
    }

    fun toggleProfileMonitored(profile: AppSoundProfile) {
        saveProfile(profile.copy(monitored = !profile.monitored))
    }

    fun deleteProfile(packageName: String, appName: String) {
        viewModelScope.launch {
            repository.deleteProfile(packageName)
            _snackbarMessage.value = "Profil $appName dipadamkan."
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
            repository.saveSchedule(schedule)
            _snackbarMessage.value = "Jadual \"${schedule.title}\" disimpan."
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

    fun exportBackupToUri(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _backupState.value = BackupState(isProcessing = true, statusMessage = "Mengeksport sandaran...")
            try {
                val json = repository.exportBackupJson().getOrThrow()
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Tidak dapat membuka fail destinasi.")
                }
                _backupState.value = BackupState(
                    isProcessing = false,
                    statusMessage = "Fail sandaran berjaya disimpan."
                )
                _snackbarMessage.value = "Tetapan, profil, dan jadual berjaya dieksport."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Gagal mengeksport sandaran."
                _backupState.value = BackupState(isProcessing = false, statusMessage = msg)
                _snackbarMessage.value = "Eksport gagal: $msg"
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _backupState.value = BackupState(isProcessing = true, statusMessage = "Memulihkan fail sandaran...")
            try {
                val json = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    } ?: throw IllegalStateException("Tidak dapat membaca fail sandaran.")
                }
                val count = repository.importBackupJson(json).getOrThrow()
                _backupState.value = BackupState(
                    isProcessing = false,
                    statusMessage = "$count item dipulihkan daripada fail."
                )
                _snackbarMessage.value = if (count > 0) {
                    "$count tetapan dan profil berjaya dipulihkan."
                } else {
                    "Tiada data sandaran sah ditemui dalam fail ini."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Gagal membaca fail sandaran."
                _backupState.value = BackupState(isProcessing = false, statusMessage = msg)
                _snackbarMessage.value = "Pemulihan gagal: $msg"
            }
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

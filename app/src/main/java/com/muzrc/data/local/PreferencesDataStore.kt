package com.muzrc.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.muzrc.model.ThemeMode
import com.muzrc.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "senyap_pintar_prefs")

data class ActiveVolumeSession(
    val savedRestoreVolumePercent: Int? = null,
    val managedPackageName: String? = null,
    val managedAppName: String? = null
)

class PreferencesDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val DEFAULT_RESTORE_ON_EXIT = booleanPreferencesKey("default_restore_on_exit")
        val POLL_INTERVAL_MS = longPreferencesKey("poll_interval_ms")
        val LAST_CLOUD_BACKUP_AT = longPreferencesKey("last_cloud_backup_at")
        val SAVED_RESTORE_VOLUME = intPreferencesKey("saved_restore_volume")
        val MANAGED_PACKAGE_NAME = stringPreferencesKey("managed_package_name")
        val MANAGED_APP_NAME = stringPreferencesKey("managed_app_name")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = ThemeMode.fromRaw(prefs[Keys.THEME_MODE]),
            monitoringEnabled = prefs[Keys.MONITORING_ENABLED] ?: false,
            defaultRestoreOnExit = prefs[Keys.DEFAULT_RESTORE_ON_EXIT] ?: true,
            pollIntervalMs = (prefs[Keys.POLL_INTERVAL_MS] ?: 1200L).coerceIn(500L, 10000L),
            lastCloudBackupAtMillis = prefs[Keys.LAST_CLOUD_BACKUP_AT]
        )
    }

    suspend fun getPreferencesOnce(): UserPreferences = preferencesFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITORING_ENABLED] = enabled }
    }

    suspend fun setDefaultRestoreOnExit(restore: Boolean) {
        context.dataStore.edit { it[Keys.DEFAULT_RESTORE_ON_EXIT] = restore }
    }

    suspend fun setPollIntervalMs(intervalMs: Long) {
        context.dataStore.edit { it[Keys.POLL_INTERVAL_MS] = intervalMs.coerceIn(500L, 10000L) }
    }

    suspend fun updateAllPreferences(prefs: UserPreferences) {
        context.dataStore.edit { store ->
            store[Keys.THEME_MODE] = prefs.themeMode.name
            store[Keys.MONITORING_ENABLED] = prefs.monitoringEnabled
            store[Keys.DEFAULT_RESTORE_ON_EXIT] = prefs.defaultRestoreOnExit
            store[Keys.POLL_INTERVAL_MS] = prefs.pollIntervalMs.coerceIn(500L, 10000L)
            if (prefs.lastCloudBackupAtMillis != null) {
                store[Keys.LAST_CLOUD_BACKUP_AT] = prefs.lastCloudBackupAtMillis
            }
        }
    }

    suspend fun setLastCloudBackupAt(timestampMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_CLOUD_BACKUP_AT] = timestampMillis }
    }

    suspend fun getActiveSession(): ActiveVolumeSession {
        val prefs = context.dataStore.data.first()
        val vol = prefs[Keys.SAVED_RESTORE_VOLUME]
        return ActiveVolumeSession(
            savedRestoreVolumePercent = if (vol != null && vol >= 0) vol else null,
            managedPackageName = prefs[Keys.MANAGED_PACKAGE_NAME]?.takeIf { it.isNotBlank() },
            managedAppName = prefs[Keys.MANAGED_APP_NAME]?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun saveActiveSession(
        restoreVolumePercent: Int,
        managedPackageName: String,
        managedAppName: String
    ) {
        context.dataStore.edit { store ->
            store[Keys.SAVED_RESTORE_VOLUME] = restoreVolumePercent.coerceIn(0, 100)
            store[Keys.MANAGED_PACKAGE_NAME] = managedPackageName
            store[Keys.MANAGED_APP_NAME] = managedAppName
        }
    }

    suspend fun clearActiveSession() {
        context.dataStore.edit { store ->
            store.remove(Keys.SAVED_RESTORE_VOLUME)
            store.remove(Keys.MANAGED_PACKAGE_NAME)
            store.remove(Keys.MANAGED_APP_NAME)
        }
    }
}

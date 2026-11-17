package com.muzrc

import android.app.Application
import android.content.Context
import com.muzrc.data.AudioVolumeController
import com.muzrc.data.InstalledAppsProvider
import com.muzrc.data.SenyapRepository
import com.muzrc.data.local.AppDatabase
import com.muzrc.data.local.PreferencesDataStore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val preferencesStore: PreferencesDataStore by lazy { PreferencesDataStore(appContext) }
    val audioController: AudioVolumeController by lazy { AudioVolumeController(appContext) }
    val installedAppsProvider: InstalledAppsProvider by lazy { InstalledAppsProvider(appContext) }
    val repository: SenyapRepository by lazy {
        SenyapRepository(
            profileDao = database.appProfileDao(),
            scheduleDao = database.quietScheduleDao(),
            activityLogDao = database.activityLogDao(),
            preferencesStore = preferencesStore,
            installedAppsProvider = installedAppsProvider
        )
    }
}

class SenyapPintarApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

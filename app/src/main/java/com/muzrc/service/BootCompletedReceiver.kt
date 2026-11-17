package com.muzrc.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.muzrc.SenyapPintarApp
import com.muzrc.data.UsageAccessHelper
import com.muzrc.data.local.PreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = (appContext as? SenyapPintarApp)?.container?.preferencesStore
                    ?: PreferencesDataStore(appContext)
                val prefs = store.getPreferencesOnce()
                if (prefs.monitoringEnabled && UsageAccessHelper.hasUsageStatsPermission(appContext)) {
                    MediaMonitorForegroundService.startMonitoring(appContext)
                }
            } catch (_: Exception) {
                // Ignore ForegroundServiceStartNotAllowedException on restricted boot states
            } finally {
                pendingResult.finish()
            }
        }
    }
}

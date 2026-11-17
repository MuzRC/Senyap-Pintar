package com.muzrc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muzrc.model.UserPreferences
import com.muzrc.ui.AppNavigation
import com.muzrc.ui.theme.SenyapPintarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferencesFlow = (application as SenyapPintarApp).container.preferencesStore.preferencesFlow

        setContent {
            val prefs by preferencesFlow.collectAsStateWithLifecycle(initialValue = UserPreferences())
            SenyapPintarTheme(themeMode = prefs.themeMode) {
                AppNavigation()
            }
        }
    }
}

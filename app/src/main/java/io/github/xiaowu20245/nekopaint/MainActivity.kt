package io.github.xiaowu20245.nekopaint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaowu20245.nekopaint.core.datastore.ThemeMode
import io.github.xiaowu20245.nekopaint.core.ui.NekoPaintApp
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoPaintTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as NekoPaintApplication
        setContent {
            val themeMode by app.container.settingsStore.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamicColor by app.container.settingsStore.dynamicColor
                .collectAsStateWithLifecycle(initialValue = false)
            val reduceMotion by app.container.settingsStore.reduceMotion
                .collectAsStateWithLifecycle(initialValue = false)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            NekoPaintTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
            ) {
                NekoPaintApp(reduceMotion = reduceMotion)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (application as NekoPaintApplication).container.taskManager.flushPending()
    }
}

package io.github.xiaowu20245.nekopaint.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

class SettingsStore(context: Context) {

    private val dataStore = context.appSettingsDataStore

    val showTempPreviews: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ShowTempPreviews] ?: false
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        val raw = preferences[ThemeModeKey]
        runCatching { ThemeMode.valueOf(raw.orEmpty()) }.getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setShowTempPreviews(value: Boolean) {
        dataStore.edit { it[ShowTempPreviews] = value }
    }

    suspend fun setThemeMode(value: ThemeMode) {
        dataStore.edit { it[ThemeModeKey] = value.name }
    }

    private companion object {
        val ShowTempPreviews = booleanPreferencesKey("show_temp_previews")
        val ThemeModeKey = stringPreferencesKey("theme_mode")
    }
}

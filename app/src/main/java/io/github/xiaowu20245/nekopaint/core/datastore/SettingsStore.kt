package io.github.xiaowu20245.nekopaint.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
)

class SettingsStore(context: Context) {

    private val dataStore = context.appSettingsDataStore

    val showTempPreviews: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ShowTempPreviews] ?: false
    }

    suspend fun setShowTempPreviews(value: Boolean) {
        dataStore.edit { it[ShowTempPreviews] = value }
    }

    private companion object {
        val ShowTempPreviews = booleanPreferencesKey("show_temp_previews")
    }
}

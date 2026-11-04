package io.github.xiaowu20245.nekopaint.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.serverConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "server_config",
)

class ServerConfigStore(context: Context) {

    private val dataStore = context.serverConfigDataStore

    val config: Flow<ServerConfig?> = dataStore.data.map { preferences ->
        val host = preferences[Keys.Host] ?: return@map null
        ServerConfig(
            name = preferences[Keys.Name] ?: "我的 ComfyUI",
            scheme = preferences[Keys.Scheme] ?: "http",
            host = host,
            port = preferences[Keys.Port] ?: ServerConfig.DEFAULT_PORT,
        )
    }

    suspend fun save(config: ServerConfig) {
        dataStore.edit { preferences ->
            preferences[Keys.Name] = config.name
            preferences[Keys.Scheme] = config.scheme
            preferences[Keys.Host] = config.host
            preferences[Keys.Port] = config.port
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private object Keys {
        val Name = stringPreferencesKey("name")
        val Scheme = stringPreferencesKey("scheme")
        val Host = stringPreferencesKey("host")
        val Port = intPreferencesKey("port")
    }
}

package io.github.xiaowu20245.nekopaint.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.serverConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "server_config",
)

class ServerConfigStore(
    context: Context,
    private val json: Json,
) {

    private val dataStore = context.serverConfigDataStore

    val servers: Flow<List<ServerProfile>> = dataStore.data.map { preferences ->
        val raw = preferences[Keys.ServersJson]
        if (raw != null) {
            runCatching {
                json.decodeFromString<List<ServerProfile>>(raw)
            }.getOrDefault(emptyList())
        } else {
            legacyProfile(preferences)?.let { listOf(it) }.orEmpty()
        }
    }

    val activeServerId: Flow<String?> = dataStore.data.map { preferences ->
        if (preferences[Keys.ServersJson] != null) {
            preferences[Keys.ActiveId]
        } else {
            legacyProfile(preferences)?.id
        }
    }

    val activeServer: Flow<ServerProfile?> = combine(servers, activeServerId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    val config: Flow<ServerConfig?> = activeServer.map { it?.toConfig() }

    init {
        // 旧版本只有一个服务器配置；后台迁移为列表，不阻塞首屏。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            migrateLegacyIfNeeded()
        }
    }

    suspend fun migrateLegacyIfNeeded() {
        dataStore.edit { preferences ->
            if (preferences[Keys.ServersJson] != null) return@edit
            val legacy = legacyProfile(preferences) ?: return@edit
            preferences[Keys.ServersJson] = json.encodeToString(listOf(legacy))
            if (preferences[Keys.ActiveId] == null) {
                preferences[Keys.ActiveId] = legacy.id
            }
        }
    }

    suspend fun saveServer(profile: ServerProfile, activate: Boolean = false) {
        dataStore.edit { preferences ->
            val current = decodeServers(preferences).toMutableList()
            val index = current.indexOfFirst { it.id == profile.id }
            if (index >= 0) current[index] = profile else current.add(profile)
            preferences[Keys.ServersJson] = json.encodeToString(current)
            if (activate || preferences[Keys.ActiveId] == null) {
                preferences[Keys.ActiveId] = profile.id
            }
        }
    }

    suspend fun setActive(id: String) {
        dataStore.edit { preferences ->
            val current = decodeServers(preferences)
            if (current.any { it.id == id }) {
                preferences[Keys.ActiveId] = id
            }
        }
    }

    suspend fun deleteServer(id: String) {
        dataStore.edit { preferences ->
            val current = decodeServers(preferences).filterNot { it.id == id }
            preferences[Keys.ServersJson] = json.encodeToString(current)
            if (preferences[Keys.ActiveId] == id || preferences[Keys.ActiveId] == null) {
                val next = current.firstOrNull()?.id
                if (next == null) {
                    preferences.remove(Keys.ActiveId)
                } else {
                    preferences[Keys.ActiveId] = next
                }
            }
        }
    }

    /**
     * 兼容旧调用：更新当前服务器，或创建第一个服务器。
     */
    suspend fun save(config: ServerConfig) {
        val existingId = activeServer.first()?.id ?: DEFAULT_SERVER_ID
        saveServer(
            profile = ServerProfile.fromConfig(config = config, id = existingId),
            activate = true,
        )
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private fun decodeServers(preferences: Preferences): List<ServerProfile> =
        preferences[Keys.ServersJson]?.let { raw ->
            runCatching {
                json.decodeFromString<List<ServerProfile>>(raw)
            }.getOrNull()
        }.orEmpty()

    private fun legacyProfile(preferences: Preferences): ServerProfile? {
        val host = preferences[Keys.Host] ?: return null
        return ServerProfile(
            id = DEFAULT_SERVER_ID,
            name = preferences[Keys.Name] ?: ServerConfig.DEFAULT_NAME,
            scheme = preferences[Keys.Scheme] ?: "http",
            host = host,
            port = preferences[Keys.Port] ?: ServerConfig.DEFAULT_PORT,
        )
    }

    private object Keys {
        val ServersJson = stringPreferencesKey("servers_json")
        val ActiveId = stringPreferencesKey("active_server_id")
        // 旧版本字段，只用于迁移。
        val Name = stringPreferencesKey("name")
        val Scheme = stringPreferencesKey("scheme")
        val Host = stringPreferencesKey("host")
        val Port = intPreferencesKey("port")
    }

    companion object {
        const val DEFAULT_SERVER_ID = "default"
    }
}

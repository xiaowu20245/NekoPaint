package io.github.xiaowu20245.nekopaint.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.xiaowu20245.nekopaint.core.model.ArchivedServer
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
        decodeServers(preferences)
    }

    val archivedServers: Flow<List<ArchivedServer>> = dataStore.data.map { preferences ->
        decodeArchivedServers(preferences)
    }

    val cacheIdentities: Flow<Map<String, String>> = dataStore.data.map { preferences ->
        decodeCacheIdentities(preferences)
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

    suspend fun saveServer(profile: ServerProfile, activate: Boolean = false): Result<Unit> {
        var result: Result<Unit> = Result.failure(
            IllegalStateException("服务器配置保存失败"),
        )
        dataStore.edit { preferences ->
            val current = decodeServers(preferences)
            val existing = current.firstOrNull { it.id == profile.id }
            val cacheIdentities = decodeCacheIdentities(preferences).toMutableMap()
            if (existing == null && cacheIdentities.containsKey(profile.id)) {
                result = Result.failure(
                    IllegalStateException("服务器缓存身份冲突，请换一个服务器或清理旧缓存"),
                )
                return@edit
            }
            if (existing != null &&
                cacheIdentities[profile.id] != existing.endpointKey
            ) {
                result = Result.failure(
                    IllegalStateException("服务器缓存身份不一致，请删除后重新添加服务器"),
                )
                return@edit
            }
            if (existing != null && existing.endpointKey != profile.endpointKey) {
                result = Result.failure(
                    IllegalStateException("服务器地址变化必须使用 replaceServerEndpoint"),
                )
                return@edit
            }
            if (existing == null &&
                decodeArchivedServers(preferences).any { it.id == profile.id }
            ) {
                result = Result.failure(
                    IllegalStateException("已归档的服务器不能通过 saveServer 复活"),
                )
                return@edit
            }
            val updated = current.toMutableList()
            val index = updated.indexOfFirst { it.id == profile.id }
            if (index >= 0) updated[index] = profile else updated.add(profile)
            preferences[Keys.ServersJson] = json.encodeToString(updated)
            val currentActive = preferences[Keys.ActiveId]
            if (activate ||
                currentActive == null ||
                updated.none { it.id == currentActive }
            ) {
                preferences[Keys.ActiveId] = profile.id
            }
            cacheIdentities[profile.id] = profile.endpointKey
            preferences[Keys.CacheIdentitiesJson] =
                json.encodeToString(cacheIdentities)
            result = Result.success(Unit)
        }
        return result
    }

    suspend fun setActive(id: String) {
        dataStore.edit { preferences ->
            val current = decodeServers(preferences)
            if (current.any { it.id == id }) {
                preferences[Keys.ActiveId] = id
            }
        }
    }

    suspend fun replaceServerEndpoint(
        oldProfile: ServerProfile,
        newProfile: ServerProfile,
        activate: Boolean,
    ): Result<Unit> {
        var result: Result<Unit> = Result.failure(
            IllegalStateException("找不到要替换的服务器"),
        )
        dataStore.edit { preferences ->
            val replacement = ServerConfigReplacementPlanner.replace(
                servers = decodeServers(preferences),
                archivedServers = decodeArchivedServers(preferences),
                oldProfile = oldProfile,
                newProfile = newProfile,
                activeId = preferences[Keys.ActiveId],
                activate = activate,
            ).getOrNull() ?: return@edit
            writeReplacement(preferences, replacement)
            val cacheIdentities = decodeCacheIdentities(preferences).toMutableMap()
            cacheIdentities.remove(oldProfile.id)
            cacheIdentities[newProfile.id] = newProfile.endpointKey
            preferences[Keys.CacheIdentitiesJson] =
                json.encodeToString(cacheIdentities)
            result = Result.success(Unit)
        }
        return result
    }

    suspend fun deleteServer(id: String) {
        dataStore.edit { preferences ->
            val replacement = ServerConfigReplacementPlanner.delete(
                servers = decodeServers(preferences),
                archivedServers = decodeArchivedServers(preferences),
                id = id,
                activeId = preferences[Keys.ActiveId],
            ).getOrNull() ?: return@edit
            writeReplacement(preferences, replacement)
            val cacheIdentities = decodeCacheIdentities(preferences).toMutableMap()
            cacheIdentities.remove(id)
            preferences[Keys.CacheIdentitiesJson] =
                json.encodeToString(cacheIdentities)
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private fun decodeServers(preferences: Preferences): List<ServerProfile> {
        val raw = preferences[Keys.ServersJson]
        if (raw != null) {
            return runCatching {
                json.decodeFromString<List<ServerProfile>>(raw)
            }.getOrDefault(emptyList())
        }
        return legacyProfile(preferences)?.let { listOf(it) }.orEmpty()
    }

    private fun decodeArchivedServers(preferences: Preferences): List<ArchivedServer> =
        preferences[Keys.ArchivedServersJson]?.let { raw ->
            runCatching {
                json.decodeFromString<List<ArchivedServer>>(raw)
            }.getOrDefault(emptyList())
        }.orEmpty()

    private fun decodeCacheIdentities(preferences: Preferences): Map<String, String> =
        preferences[Keys.CacheIdentitiesJson]?.let { raw ->
            runCatching {
                json.decodeFromString<Map<String, String>>(raw)
            }.getOrDefault(emptyMap())
        }.orEmpty()

    private fun writeReplacement(
        preferences: MutablePreferences,
        replacement: ServerConfigReplacement,
    ) {
        preferences[Keys.ServersJson] = json.encodeToString(replacement.servers)
        preferences[Keys.ArchivedServersJson] = json.encodeToString(replacement.archivedServers)
        replacement.activeId?.let { preferences[Keys.ActiveId] = it }
            ?: preferences.remove(Keys.ActiveId)
    }

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
        val ArchivedServersJson = stringPreferencesKey("archived_servers_json")
        val CacheIdentitiesJson = stringPreferencesKey("cache_identities_json")
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

package io.github.xiaowu20245.nekopaint.core.datastore

import kotlinx.coroutines.flow.Flow

interface NekoSettingsPreferences {
    val agentModeByDefault: Flow<Boolean>
    suspend fun setAgentModeByDefault(value: Boolean)
}

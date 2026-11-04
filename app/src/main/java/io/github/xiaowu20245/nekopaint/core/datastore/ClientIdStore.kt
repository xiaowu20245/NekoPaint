package io.github.xiaowu20245.nekopaint.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.clientIdDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "client_identity",
)

class ClientIdStore(context: Context) {

    private val dataStore = context.clientIdDataStore

    suspend fun getOrCreate(): String {
        val existing = dataStore.data.first()[Key]
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        dataStore.edit { it[Key] = created }
        return created
    }

    private companion object {
        val Key = stringPreferencesKey("client_id")
    }
}

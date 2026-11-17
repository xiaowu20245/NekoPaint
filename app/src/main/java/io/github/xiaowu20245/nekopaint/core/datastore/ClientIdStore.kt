package io.github.xiaowu20245.nekopaint.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.clientIdDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "client_identity",
)

class ClientIdStore(context: Context) {

    private val dataStore = context.clientIdDataStore
    private val allocator = ClientIdAllocator(
        read = { dataStore.data.first()[Key] },
        write = { created -> dataStore.edit { it[Key] = created } },
    )

    suspend fun getOrCreate(): String = allocator.getOrCreate()

    private companion object {
        val Key = stringPreferencesKey("client_id")
    }
}

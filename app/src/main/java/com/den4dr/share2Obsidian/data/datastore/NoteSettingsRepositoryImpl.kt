package com.den4dr.share2Obsidian.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val VAULT_KEY = stringPreferencesKey("vault")
internal val FOLDER_KEY = stringPreferencesKey("folder")

/**
 * DataStore Preferences を使った [NoteSettingsRepository] の実装。
 */
class NoteSettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : NoteSettingsRepository {

    override fun getSettings(): Flow<NoteSettings> = dataStore.data.map { prefs ->
        NoteSettings(
            vault = prefs[VAULT_KEY] ?: "",
            folder = prefs[FOLDER_KEY] ?: "",
        )
    }

    override suspend fun saveVault(vault: String) {
        dataStore.edit { it[VAULT_KEY] = vault }
    }

    override suspend fun saveFolder(folder: String) {
        dataStore.edit { it[FOLDER_KEY] = folder }
    }
}

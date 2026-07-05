package com.den4dr.share2Obsidian.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * NoteSettingsRepositoryImpl の DataStore 読み書きを検証する計器テスト（TASK-0046 / REQ-021）。
 * 実 DataStore の IO を伴うため計器テストとして実行する。
 */
@RunWith(AndroidJUnit4::class)
class NoteSettingsRepositoryImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createRepository(scope: CoroutineScope): NoteSettingsRepositoryImpl {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.filesDir, "note_settings_test_${UUID.randomUUID()}.preferences_pb")
        }
        return NoteSettingsRepositoryImpl(dataStore)
    }

    // 保存した vault/folder を読み出せる（REQ-021）
    @Test
    fun saveAndReadVaultAndFolder() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = createRepository(scope)

        repo.saveVault("MyVault")
        repo.saveFolder("Notes")
        val settings = repo.getSettings().first()

        assertEquals("MyVault", settings.vault)
        assertEquals("Notes", settings.folder)
        scope.cancel()
    }

    // 未設定時は空文字列が返る（デフォルト値）
    @Test
    fun unsetSettings_returnEmptyStrings() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = createRepository(scope)

        val settings = repo.getSettings().first()

        assertEquals("", settings.vault)
        assertEquals("", settings.folder)
        scope.cancel()
    }
}

package com.den4dr.share2Obsidian.data.llm

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * [LlmSettingsRepositoryImpl] が実 EncryptedSharedPreferences を用いて apiKey を
 * 暗号化保存することを検証する計器テスト（TASK-0058 / TC-08 / NFR-101・REQ-401）。
 *
 * 実行環境注記: この開発環境には adb/emulator が存在しないため connectedAndroidTest による実機実行はできない。
 * Red フェーズでは本ファイルのコンパイルが通ること（暫定確認）をもって記録とし、
 * 実機/エミュレータでの実行確認は別途デバイス環境で行うこと。
 *
 * 【テスト対象】: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImpl.kt`（未実装）
 * このファイルは Red フェーズの時点では `LlmSettingsRepositoryImpl` クラスが存在しないためコンパイルに失敗する。
 */
@RunWith(AndroidJUnit4::class)
class LlmSettingsRepositoryImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefsFileName = "llm_secure_prefs_test_${UUID.randomUUID()}"

    private fun createRealEncryptedPrefs() =
        EncryptedSharedPreferences.create(
            context,
            prefsFileName,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    // TC-08: apiKey が SharedPreferences ファイルに平文で含まれない
    @Test
    fun saveApiKey_notStoredAsPlaintextInPreferencesFile() = runBlocking {
        // 【テスト目的】: 実 EncryptedSharedPreferences に保存した apiKey がファイル実体に平文で書かれていないことを確認する
        // 【テスト内容】: 実際の MasterKey + AES256_SIV/AES256_GCM で構成した EncryptedSharedPreferences を使い saveApiKey を実行後、
        //                対応する shared_prefs XML ファイルの内容を検査する
        // 【期待される動作】: ファイル内容に平文の apiKey 文字列が含まれない（暗号化されている）
        // 🔵 信頼性レベル: TASK-0058「統合テスト1」・NFR-101 より（testcases.md TC-08）

        // 【テストデータ準備】: 保存後のファイル内容と比較するための代表的なダミー API キーを用意する
        // 【初期条件設定】: 一意なファイル名の実 EncryptedSharedPreferences・DataStore を用意する
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.filesDir, "llm_settings_androidtest_${UUID.randomUUID()}.preferences_pb")
        }
        val encryptedPrefs = createRealEncryptedPrefs()
        val repo = LlmSettingsRepositoryImpl(dataStore, encryptedPrefs)
        val secretApiKey = "sk-test-secret"

        // 【実際の処理実行】: saveApiKey を実行し、実ファイルの内容を読み取る
        // 【処理内容】: EncryptedSharedPreferences.create() で構成した暗号化ストレージへの書き込み
        repo.saveApiKey(secretApiKey)
        val prefsFile = File(context.filesDir.parentFile, "shared_prefs/$prefsFileName.xml")
        val fileContent = if (prefsFile.exists()) prefsFile.readText() else ""

        // 【結果検証】: ファイル内容に平文の apiKey 文字列が含まれないこと
        assertFalse(fileContent.contains(secretApiKey)) // 【確認内容】: 保存した apiKey がファイル実体に平文で残らないこと（暗号化保存の実効性確認）

        scope.cancel()
    }

    @After
    fun tearDown() {
        // 【テスト後処理】: 実ファイルとして作成された暗号化 SharedPreferences を削除し、次のテストに影響しないようにする
        // 【状態復元】: shared_prefs 配下のテスト用ファイルをクリーンアップする
        context.deleteSharedPreferences(prefsFileName)
    }
}

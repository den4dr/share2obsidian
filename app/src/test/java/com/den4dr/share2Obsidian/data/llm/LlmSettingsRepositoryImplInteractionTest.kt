package com.den4dr.share2Obsidian.data.llm

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * [LlmSettingsRepositoryImpl] の保存先ルーティング・リスナー解除といった
 * インタラクション（呼び出し有無）を MockK の verify で検証するユニットテスト（TASK-0058）。
 *
 * 状態ベースの検証（保存値の読み出し等）は [LlmSettingsRepositoryImplTest] 側で扱う。
 *
 * 【テスト対象】: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImpl.kt`
 */
// 【Greenフェーズ追記】: 既存 Robolectric テストと同様に sdk=34 を明示（targetSdk=36 既定値だと
// DefaultSdkPicker が IllegalArgumentException を投げるため） 🔵
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LlmSettingsRepositoryImplInteractionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createDataStore(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(context.filesDir, "llm_settings_interaction_test_${UUID.randomUUID()}.preferences_pb")
        }

    // TC-05: saveApiKey が DataStore ではなく EncryptedSharedPreferences に保存する
    @Test
    fun saveApiKey_writesToEncryptedPrefsEditor_notDataStore() = runBlocking {
        // 【テスト目的】: saveApiKey() 呼び出しが EncryptedSharedPreferences 経路にのみ書き込み、DataStore には書き込まないことを確認
        // 【テスト内容】: encryptedPrefs をモック化し、putString の呼び出し引数を verify する
        // 【期待される動作】: encryptedPrefs.edit().putString(API_KEY_KEY相当のキー, apiKey) が呼ばれる
        // 🔵 信頼性レベル: TASK-0058 実装詳細§3・REQ-401 より（testcases.md TC-05）

        // 【テストデータ準備】: 保存経路そのものを検証するためのダミー API キーを用意する
        // 【初期条件設定】: encryptedPrefs・editor を MockK でモックし、putString/apply をスタブする
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = createDataStore(scope)
        val encryptedPrefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        val keySlot = slot<String>()
        val valueSlot = slot<String>()
        every { encryptedPrefs.edit() } returns editor
        every { editor.putString(capture(keySlot), capture(valueSlot)) } returns editor
        every { editor.apply() } returns Unit
        every { encryptedPrefs.getString(any(), any()) } returns ""
        every { encryptedPrefs.registerOnSharedPreferenceChangeListener(any()) } returns Unit
        every { encryptedPrefs.unregisterOnSharedPreferenceChangeListener(any()) } returns Unit
        val repo = LlmSettingsRepositoryImpl(dataStore, encryptedPrefs)

        // 【実際の処理実行】: saveApiKey を呼び出す
        // 【処理内容】: EncryptedSharedPreferences 相当のストレージへの書き込みを実行する
        repo.saveApiKey("sk-verify-target")

        // 【結果検証】: encryptedPrefs.edit().putString(...) が正しい値で呼ばれたことを確認
        verify(exactly = 1) { editor.putString(any(), "sk-verify-target") } // 【確認内容】: apiKey が EncryptedSharedPreferences 経路に書き込まれること
        verify(exactly = 1) { editor.apply() } // 【確認内容】: 変更が apply() によって確定されること

        // 【追加検証】: DataStore の Preferences に apiKey の値が漏れていないこと
        val rawPreferences = dataStore.data.first()
        val containsApiKeyValue = rawPreferences.asMap().values.any { it == "sk-verify-target" }
        org.junit.Assert.assertFalse(containsApiKeyValue) // 【確認内容】: apiKey が DataStore 経路に漏れないこと（REQ-401）

        scope.cancel()
    }

    // TC-09: callbackFlow が collect 終了時にリスナー登録解除する
    @Test
    fun getSettingsCollectCancelled_unregistersSharedPreferenceListener() = runBlocking {
        // 【テスト目的】: encryptedApiKeyFlow() の collect 終了後、登録したリスナーが解除されることを確認する
        // 【テスト内容】: getSettings() を collect し、キャンセル後に unregisterOnSharedPreferenceChangeListener が呼ばれるか verify する
        // 【期待される動作】: register と対になる unregister が同一リスナーで呼ばれる
        // 🟡 信頼性レベル: note.md「6. 注意事項§2」・callbackFlow 一般パターンからの妥当な推測（testcases.md TC-09）

        // 【テストデータ準備】: register/unregister の呼び出しをキャプチャするため encryptedPrefs をモックする
        // 【初期条件設定】: ViewModel のスコープ破棄時に Flow collect がキャンセルされる状況を再現する
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = createDataStore(scope)
        val encryptedPrefs = mockk<SharedPreferences>()
        val registerSlot = slot<SharedPreferences.OnSharedPreferenceChangeListener>()
        val unregisterSlot = slot<SharedPreferences.OnSharedPreferenceChangeListener>()
        // 【同期用シグナル】: combine()/callbackFlow は内部で子コルーチンを起動して register を呼ぶため、
        // register 呼び出しが実際に発生するまで待ってからキャンセルする（起動直後の cancel だと
        // register 前にジョブが終了してしまう競合状態を避けるための待ち合わせ） 🔵
        val registered = CompletableDeferred<Unit>()
        every { encryptedPrefs.getString(any(), any()) } returns ""
        every { encryptedPrefs.registerOnSharedPreferenceChangeListener(capture(registerSlot)) } answers {
            registered.complete(Unit)
        }
        every { encryptedPrefs.unregisterOnSharedPreferenceChangeListener(capture(unregisterSlot)) } returns Unit
        val repo = LlmSettingsRepositoryImpl(dataStore, encryptedPrefs)

        // 【実際の処理実行】: getSettings() を collect する coroutine を起動し、リスナー登録完了を待ってからキャンセルする
        // 【処理内容】: collect 開始でリスナー登録、キャンセルでリスナー解除が発生することを期待する
        val collectJob = scope.launch { repo.getSettings().collect {} }
        withTimeout(5_000) { registered.await() }
        collectJob.cancelAndJoin()

        // 【結果検証】: 登録・解除が同一リスナーで1回ずつ呼ばれていること
        verify(exactly = 1) { encryptedPrefs.registerOnSharedPreferenceChangeListener(any()) } // 【確認内容】: collect 開始時にリスナーが登録されること
        verify(exactly = 1) { encryptedPrefs.unregisterOnSharedPreferenceChangeListener(any()) } // 【確認内容】: collect 終了時にリスナーが解除されること
        org.junit.Assert.assertTrue(registerSlot.isCaptured && unregisterSlot.isCaptured) // 【確認内容】: register/unregister が実際に呼び出されキャプチャできていること
        org.junit.Assert.assertSame(registerSlot.captured, unregisterSlot.captured) // 【確認内容】: 登録したものと同一のリスナーインスタンスが解除されること（リーク防止）

        scope.cancel()
    }
}

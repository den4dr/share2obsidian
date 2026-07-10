package com.den4dr.share2Obsidian.data.llm

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * [LlmSettingsRepositoryImpl] の DataStore（endpointUrl/model）と
 * EncryptedSharedPreferences 相当（apiKey）の合成・読み書きを検証するユニットテスト（TASK-0058）。
 *
 * DataStore は Robolectric 上の実ファイル I/O を使用し、EncryptedSharedPreferences は
 * 実暗号化検証（TC-08）を計器テストに分離するため、本ファイルでは [FakeSharedPreferences] で代替する。
 *
 * 【テスト対象】: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImpl.kt`
 */
// 【Greenフェーズ追記】: 既存 Robolectric テスト（ObsidianUriBuilderTest 等）と同様に sdk=34 を明示しないと
// targetSdk(36) を既定値として拾おうとして DefaultSdkPicker が IllegalArgumentException を投げるため追加 🔵
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LlmSettingsRepositoryImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createRepository(
        scope: CoroutineScope,
        encryptedPrefs: SharedPreferences = FakeSharedPreferences(),
    ): LlmSettingsRepositoryImpl {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.filesDir, "llm_settings_test_${UUID.randomUUID()}.preferences_pb")
        }
        return LlmSettingsRepositoryImpl(dataStore, encryptedPrefs)
    }

    // TC-01: saveEndpointUrl/saveModel 保存後に getSettings() が更新値を返す
    @Test
    fun saveEndpointUrlAndModel_reflectedInGetSettings() = runBlocking {
        // 【テスト目的】: DataStore 側設定(endpointUrl/model)の保存が getSettings() に反映されることを確認
        // 【テスト内容】: saveEndpointUrl/saveModel 実行後に getSettings().first() の値を検証
        // 【期待される動作】: 書き込んだ値がそのまま LlmSettings として読み出せる
        // 🔵 信頼性レベル: TASK-0058 テストケース1・testcases.md TC-01

        // 【テストデータ準備】: 一意ファイル名の実 DataStore を持つ Repository を生成（既存テスト衝突回避）
        // 【初期条件設定】: DataStore は未初期化（キー未保存）
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = createRepository(scope)

        // 【実際の処理実行】: DataStore 保存系メソッドを呼び出す
        // 【処理内容】: endpointUrl と model を別キーに書き込む
        repo.saveEndpointUrl("https://api.example.com/v1/chat")
        repo.saveModel("gpt-4o-mini")

        // 【結果検証】: getSettings() の最初の発行値を取得し各フィールドを確認
        val settings = repo.getSettings().first()

        assertEquals("https://api.example.com/v1/chat", settings.endpointUrl) // 【確認内容】: endpointUrl が保存値と一致すること
        assertEquals("gpt-4o-mini", settings.model) // 【確認内容】: model が保存値と一致すること

        // 【テスト後処理】: coroutine スコープをキャンセルし DataStore リソースを解放
        scope.cancel()
    }

    // TC-02: saveApiKey 保存後に getSettings() が更新値を返す
    @Test
    fun saveApiKey_reflectedInGetSettings() = runBlocking {
        // 【テスト目的】: EncryptedSharedPreferences 側設定(apiKey)の保存が getSettings() に反映されることを確認
        // 【テスト内容】: saveApiKey 実行後に getSettings().first() の apiKey を検証
        // 【期待される動作】: 書き込んだ apiKey がそのまま LlmSettings として読み出せる
        // 🔵 信頼性レベル: TASK-0058 テストケース2・testcases.md TC-02

        // 【テストデータ準備】: フェイク EncryptedSharedPreferences を使い apiKey 保存経路を検証する
        // 【初期条件設定】: apiKey 未保存の状態から開始する
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = createRepository(scope)

        // 【実際の処理実行】: apiKey 保存系メソッドを呼び出す
        // 【処理内容】: EncryptedSharedPreferences 相当のストレージへ書き込む
        repo.saveApiKey("sk-test-12345")

        // 【結果検証】: getSettings() の最初の発行値の apiKey を確認
        val settings = repo.getSettings().first()

        assertEquals("sk-test-12345", settings.apiKey) // 【確認内容】: apiKey が保存値と一致すること

        scope.cancel()
    }

    // TC-04: 3項目すべて保存後に統合された LlmSettings を返す
    @Test
    fun saveAllThreeFields_returnsCombinedLlmSettings() = runBlocking {
        // 【テスト目的】: DataStore側(endpointUrl/model)とEncryptedSharedPreferences側(apiKey)双方保存後の統合読み出しを確認
        // 【テスト内容】: 3メソッドすべてを呼び出した後 getSettings().first() の全項目を検証
        // 【期待される動作】: combine() により3項目すべてを含む単一の LlmSettings が発行される
        // 🟡 信頼性レベル: requirements.md データフロー 4.2 からの妥当な推測（testcases.md TC-04）

        // 【テストデータ準備】: 実利用時の完全な設定入力を代表するデータセットを用意する
        // 【初期条件設定】: 何も保存していない状態から開始する
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = createRepository(scope)

        // 【実際の処理実行】: endpointUrl・model・apiKey をすべて保存する
        // 【処理内容】: 2つの異なるストレージへの書き込みを行う
        repo.saveEndpointUrl("https://api.example.com/v1/chat")
        repo.saveModel("gpt-4o-mini")
        repo.saveApiKey("sk-test-abc")

        // 【結果検証】: getSettings() が3項目すべてを含む LlmSettings を返すことを確認
        val settings = repo.getSettings().first()

        assertEquals(
            LlmSettings(endpointUrl = "https://api.example.com/v1/chat", apiKey = "sk-test-abc", model = "gpt-4o-mini"),
            settings,
        ) // 【確認内容】: 3項目が取り違えなく正しいフィールドに割り当てられていること

        scope.cancel()
    }

    // TC-06: 保存後に再保存（上書き）すると最新値が返る
    @Test
    fun saveModelTwice_returnsLatestValue() = runBlocking {
        // 【テスト目的】: 同一項目を2回保存した際、最新（2回目）の値が返ることを確認
        // 【テスト内容】: saveModel を異なる値で2回呼び出し、getSettings().first() の model を検証
        // 【期待される動作】: DataStore が last-write-wins で最新値を保持する
        // 🟡 信頼性レベル: DataStore の一般挙動からの妥当な推測（testcases.md TC-06）

        // 【テストデータ準備】: ユーザーが設定画面で値を変更する実運用シナリオを再現する
        // 【初期条件設定】: 何も保存していない状態から開始する
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = createRepository(scope)

        // 【実際の処理実行】: model を2回、異なる値で保存する
        // 【処理内容】: 1回目 "gpt-4o-mini"、2回目 "gpt-4o" で上書きする
        repo.saveModel("gpt-4o-mini")
        repo.saveModel("gpt-4o")

        // 【結果検証】: 最新（2回目）の値のみが読み出せること
        val settings = repo.getSettings().first()

        assertEquals("gpt-4o", settings.model) // 【確認内容】: 古い値 "gpt-4o-mini" が残存せず最新値のみ反映されていること

        scope.cancel()
    }

    // TC-07: apiKey が平文 DataStore に一切保存されない（REQ-401）
    @Test
    fun saveApiKey_neverWrittenToDataStore() = runBlocking {
        // 【テスト目的】: apiKey が誤って DataStore（平文 Preferences）に保存される情報漏洩を防止できていることを確認
        // 【テスト内容】: saveApiKey 実行後、DataStore 側の全 Preferences エントリを走査し apiKey 文字列の混入がないか検証
        // 【期待される動作】: DataStore の Preferences に apiKey の値が一切含まれない
        // 🔵 信頼性レベル: TASK-0058 完了条件・REQ-401 より（testcases.md TC-07）

        // 【テストデータ準備】: 平文保存の有無を判定するための明確な apiKey 文字列を用意する
        // 【初期条件設定】: DataStore は未初期化の状態から開始する
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.filesDir, "llm_settings_test_${UUID.randomUUID()}.preferences_pb")
        }
        val repo = LlmSettingsRepositoryImpl(dataStore, FakeSharedPreferences())

        // 【実際の処理実行】: saveApiKey を呼び出し、DataStore の内容を直接検査する
        // 【処理内容】: apiKey 保存後、同一 DataStore インスタンスの Preferences を取得する
        repo.saveApiKey("sk-should-not-be-plain")
        val rawPreferences = dataStore.data.first()

        // 【結果検証】: DataStore の値一覧に apiKey が含まれないこと
        val containsApiKeyValue = rawPreferences.asMap().values.any { it == "sk-should-not-be-plain" }
        assertFalse(containsApiKeyValue) // 【確認内容】: apiKey の値が DataStore のいずれのキーにも平文で存在しないこと

        scope.cancel()
    }

    // TC-10: 初期状態（未保存）では LlmSettings("","","") を返す
    @Test
    fun noSavedValues_returnsDefaultLlmSettings() = runBlocking {
        // 【テスト目的】: いずれのキーも未保存という初回起動直後相当の状態でデフォルト値が返ることを確認
        // 【テスト内容】: 何も保存していない Repository に対し getSettings().first() を呼び出す
        // 【期待される動作】: 各ソースの未設定値が空文字列に正規化される
        // 🔵 信頼性レベル: TASK-0058 テストケース3・requirements.md 4.3 より（testcases.md TC-10）

        // 【テストデータ準備】: 保存前の初期状態は必ず通過する境界であるため、何も書き込まずに検証する
        // 【初期条件設定】: DataStore・EncryptedSharedPreferences 相当ともに未設定
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = createRepository(scope)

        // 【実際の処理実行】: 保存操作を一切行わず getSettings() を呼び出す
        // 【処理内容】: 初期状態の読み出しのみ
        val settings = repo.getSettings().first()

        // 【結果検証】: 全フィールドが空文字列であること
        assertEquals(LlmSettings(endpointUrl = "", apiKey = "", model = ""), settings) // 【確認内容】: null や例外ではなく空文字列で安全に初期化されること

        scope.cancel()
    }

    // TC-11: 空文字列の保存が可能（値のクリア）
    @Test
    fun saveEndpointUrlEmptyString_clearsValue() = runBlocking {
        // 【テスト目的】: 保存済みの値を空文字列で上書きし「クリア」できることを確認
        // 【テスト内容】: 値を保存した後、空文字列で再保存し getSettings().first() を検証
        // 【期待される動作】: 空文字列が有効な保存値として扱われ、例外なく読み出せる
        // 🟡 信頼性レベル: DataStore の一般挙動からの妥当な推測（要件に明示なし、testcases.md TC-11）

        // 【テストデータ準備】: ユーザーが設定欄を空にして保存するケースを再現する
        // 【初期条件設定】: 事前に非空文字列を保存しておく
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = createRepository(scope)
        repo.saveEndpointUrl("https://x")

        // 【実際の処理実行】: 空文字列で再保存する
        // 【処理内容】: 同一キーに対する上書き保存
        repo.saveEndpointUrl("")

        // 【結果検証】: 空文字列がそのまま読み出せること
        val settings = repo.getSettings().first()

        assertEquals("", settings.endpointUrl) // 【確認内容】: 空文字列保存が例外なく成功し、読み出しでも空文字列になること

        scope.cancel()
    }

    // TC-12: DataStore と EncryptedSharedPreferences の変更が独立して反映される
    @Test
    fun saveApiKeyOnly_doesNotAffectEndpointUrlAndModel() = runBlocking {
        // 【テスト目的】: 片方のソースのみ更新した際、もう片方が保持される合成境界を確認
        // 【テスト内容】: endpointUrl/model 保存済みの状態で saveApiKey のみ実行し、全フィールドを検証
        // 【期待される動作】: combine() が片側更新でも他側の最新値を保持する
        // 🟡 信頼性レベル: TASK-0058 テストケース4・combine() 一般挙動からの妥当な推測（testcases.md TC-12）

        // 【テストデータ準備】: ユーザーが apiKey だけ後から入力・変更するケースを再現する
        // 【初期条件設定】: endpointUrl/model を事前に保存しておく
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = createRepository(scope)
        repo.saveEndpointUrl("https://api.example.com/v1/chat")
        repo.saveModel("gpt-4o-mini")

        // 【実際の処理実行】: apiKey のみを保存する
        // 【処理内容】: EncryptedSharedPreferences 相当のストレージのみ更新
        repo.saveApiKey("sk-only")

        // 【結果検証】: endpointUrl/model が変更前の値を維持し、apiKey のみ更新されていること
        val settings = repo.getSettings().first()

        assertEquals("https://api.example.com/v1/chat", settings.endpointUrl) // 【確認内容】: endpointUrl が更新前の値を維持していること
        assertEquals("gpt-4o-mini", settings.model) // 【確認内容】: model が更新前の値を維持していること
        assertEquals("sk-only", settings.apiKey) // 【確認内容】: apiKey のみ最新値に更新されていること

        scope.cancel()
    }

    // TC-13: 長い文字列・特殊文字を含む値の保存・読み出し
    @Test
    fun saveSpecialCharacterValues_roundTripsWithoutCorruption() = runBlocking {
        // 【テスト目的】: URL クエリ記号・非ASCII文字を含む値が無加工で往復できることを確認
        // 【テスト内容】: 記号・マルチバイト文字を含む endpointUrl/apiKey を保存し、そのまま読み出せるか検証
        // 【期待される動作】: 文字化け・切り詰めなく保存した値と完全一致する
        // 🟡 信頼性レベル: 一般的な文字列ストレージ挙動からの妥当な推測（要件に明示なし、testcases.md TC-13）

        // 【テストデータ準備】: プロキシ経由エンドポイントや特殊なキー形式を代表する値を用意する
        // 【初期条件設定】: 未保存の状態から開始する
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = createRepository(scope)
        val specialUrl = "https://api.example.com/v1/chat?x=1&y=あ"
        val specialApiKey = "sk-!@#\$%^&*()_+=あ漢"

        // 【実際の処理実行】: 特殊文字を含む値を保存する
        // 【処理内容】: DataStore と EncryptedSharedPreferences 相当それぞれへの書き込み
        repo.saveEndpointUrl(specialUrl)
        repo.saveApiKey(specialApiKey)

        // 【結果検証】: 保存した値がそのまま読み出せること
        val settings = repo.getSettings().first()

        assertEquals(specialUrl, settings.endpointUrl) // 【確認内容】: クエリ記号・非ASCII文字を含む URL が無加工で往復すること
        assertEquals(specialApiKey, settings.apiKey) // 【確認内容】: 記号・非ASCII文字を含む apiKey が無加工で往復すること
        assertTrue(settings.endpointUrl.isNotEmpty()) // 【確認内容】: 値が空文字化・欠落していないこと

        scope.cancel()
    }
}

/**
 * EncryptedSharedPreferences の代替として使用するテスト用フェイク実装。
 * put→apply/commit で値を保存し、登録済みリスナーへ変更キーを通知する。
 * TC-05・TC-09（保存先ルーティング・リスナー解除のインタラクション検証）は
 * MockK ベースの検証が必要なため、専用ファイル [LlmSettingsRepositoryImplInteractionTest] で扱う。
 *
 * 🟡 信頼性レベル: architecture.md の SharedPreferences.OnSharedPreferenceChangeListener 契約に基づく簡易実装
 */
internal class FakeSharedPreferences : SharedPreferences {
    private val storage = mutableMapOf<String, String>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getString(key: String?, defValue: String?): String? = storage[key] ?: defValue

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        listener?.let { listeners.add(it) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        listeners.remove(listener)
    }

    override fun getAll(): MutableMap<String, *> = storage.toMutableMap()
    override fun getInt(key: String?, defValue: Int): Int = throw UnsupportedOperationException("not used in tests")
    override fun getLong(key: String?, defValue: Long): Long = throw UnsupportedOperationException("not used in tests")
    override fun getFloat(key: String?, defValue: Float): Float = throw UnsupportedOperationException("not used in tests")
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = throw UnsupportedOperationException("not used in tests")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        throw UnsupportedOperationException("not used in tests")
    override fun contains(key: String?): Boolean = storage.containsKey(key)

    inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, String?>()
        private var shouldClear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            throw UnsupportedOperationException("not used in tests")
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor =
            throw UnsupportedOperationException("not used in tests")
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor =
            throw UnsupportedOperationException("not used in tests")
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor =
            throw UnsupportedOperationException("not used in tests")
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            throw UnsupportedOperationException("not used in tests")

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            shouldClear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (shouldClear) storage.clear()
            val changedKeys = pending.keys.toList()
            pending.forEach { (k, v) -> if (v == null) storage.remove(k) else storage[k] = v }
            pending.clear()
            changedKeys.forEach { key ->
                listeners.toList().forEach { it.onSharedPreferenceChanged(this@FakeSharedPreferences, key) }
            }
        }
    }
}

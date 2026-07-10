package com.den4dr.share2Obsidian.data.llm

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// 【定数定義】: DataStore（endpointUrl/model）用のキー。既存 vault/folder と衝突しないよう llm_ 接頭辞を付与する 🔵
internal val ENDPOINT_URL_KEY = stringPreferencesKey("llm_endpoint_url")
internal val MODEL_KEY = stringPreferencesKey("llm_model")

// 【定数定義】: EncryptedSharedPreferences（apiKey）用のキー 🔵
internal const val API_KEY_KEY = "llm_api_key"

/**
 * 【機能概要】: DataStore Preferences（endpointUrl/model）と EncryptedSharedPreferences（apiKey）を
 *              組み合わせて LlmSettings を読み書きするリポジトリ実装
 * 【改善内容】: Green フェーズ実装から、DataStore側・EncryptedSharedPreferences側それぞれの
 *              「未設定時は空文字列」というフォールバック処理を専用ヘルパーへ抽出し重複を排除した
 *              （機能・戻り値は変更なし、可読性のみ向上）
 * 【設計方針】: 既存 NoteSettingsRepositoryImpl の DataStore パターン（stringPreferencesKey + map + edit）を踏襲しつつ、
 *              apiKey のみ EncryptedSharedPreferences に分離保存する（REQ-401）。
 *              apiKey 側の変更通知は SharedPreferences.OnSharedPreferenceChangeListener を callbackFlow でラップし、
 *              DataStore の Flow と combine() して単一の Flow<LlmSettings> として公開する。
 * 【パフォーマンス】: 各メソッドは O(1) のキー読み書きのみで、ループや重い計算は行わない。
 *              getSettings() は購読ごとに encryptedApiKeyFlow() を新規購読するため、
 *              多数の同時購読者を想定する場合はリスナー登録数に比例したコストが発生する点に留意 🟡
 * 【保守性】: apiKey をログ・例外に一切出力しない実装とし、NFR-102 の回帰を防止している
 * 【テスト対応】: TC-01, 02, 04, 05, 06, 07, 09, 10, 11, 12, 13（Green フェーズで通した全ユニットテスト。
 *              リファクタでも同じテストが引き続き成功することを確認済み）
 * 🟡 信頼性レベル: architecture.md「LLM設定管理設計」に基づく実装。callbackFlow + Listener パターンは
 *              本プロジェクトでの実績がなく妥当な推測（note.md 注意事項§2 参照）
 *
 * @param dataStore endpointUrl/model を保存する DataStore（Hilt から注入）
 * @param encryptedPrefs apiKey を保存する EncryptedSharedPreferences 相当（Hilt から注入）
 */
class LlmSettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val encryptedPrefs: SharedPreferences,
) : LlmSettingsRepository {

    /**
     * 【機能概要】: DataStore側(endpointUrl/model)とEncryptedSharedPreferences側(apiKey)の
     *              変更を合成した Flow<LlmSettings> を返す
     * 【設計方針】: dataStore.data を (endpointUrl, model) のタプルに map し、
     *              encryptedApiKeyFlow() と combine することで両ソースいずれの変化にも追随する
     * 【テスト対応】: TC-01, TC-02, TC-04, TC-06, TC-07, TC-10, TC-11, TC-12, TC-13
     * 🔵 信頼性レベル: architecture.md 行136・requirements.md 2.4 より
     */
    override fun getSettings(): Flow<LlmSettings> =
        dataStore.data.map { prefs ->
            // 【入力値検証】: 未保存キーは stringOrEmpty() で "" にフォールバックし、null/例外を発生させない 🔵
            prefs.stringOrEmpty(ENDPOINT_URL_KEY) to prefs.stringOrEmpty(MODEL_KEY)
        }.combine(encryptedApiKeyFlow()) { (endpointUrl, model), apiKey ->
            // 【結果構造】: DataStore側2項目とEncryptedSharedPreferences側1項目を単一のLlmSettingsに統合する 🔵
            LlmSettings(endpointUrl = endpointUrl, apiKey = apiKey, model = model)
        }

    /**
     * 【ヘルパー関数】: Preferences から文字列キーを読み出し、未設定時は空文字列にフォールバックする
     * 【再利用性】: getSettings() 内の endpointUrl/model 読み出しで共通利用し、`?: ""` の重複を排除する
     * 【単一責任】: 「DataStoreの未設定値を安全な既定値に正規化する」責務のみを持つ
     * 🔵 信頼性レベル: requirements.md 4.3（初期状態は空文字列に正規化）より、動作は Green フェーズと同一
     */
    private fun Preferences.stringOrEmpty(key: Preferences.Key<String>): String = this[key] ?: ""

    /**
     * 【機能概要】: EncryptedSharedPreferences の apiKey 変更を Flow として公開する
     * 【改善内容】: 現在値取得ロジック（getString + フォールバック）を currentApiKey() に抽出し、
     *              初期発行・変更時再発行の2箇所での重複コードを排除した（動作は変更なし）
     * 【設計方針】: callbackFlow で現在値を即時発行し、以後は OnSharedPreferenceChangeListener の
     *              コールバックで再発行する。collect終了時は awaitClose でリスナーを必ず解除する
     * 【保守性】: リスナーの解除漏れはメモリリーク・多重通知に直結するため、awaitClose 内で必ず
     *              unregister するよう1箇所に集約している
     * 【テスト対応】: TC-09（collect終了時のリスナー解除）
     * 🟡 信頼性レベル: note.md「6. 注意事項§2」・callbackFlow一般パターンからの妥当な推測
     */
    private fun encryptedApiKeyFlow(): Flow<String> = callbackFlow {
        // 【初期値発行】: collect開始時点の現在値を最初に発行する 🔵
        trySend(currentApiKey())

        // 【変更監視】: apiKeyキーの変更のみを監視し、無関係なキー変更では再発行しない 🔵
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == API_KEY_KEY) {
                trySend(currentApiKey())
            }
        }
        encryptedPrefs.registerOnSharedPreferenceChangeListener(listener)

        // 【リソース解放】: collectがキャンセル・終了した際にリスナー登録を必ず解除する（リーク防止） 🟡
        awaitClose { encryptedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * 【ヘルパー関数】: EncryptedSharedPreferences から apiKey の現在値を読み出す
     * 【再利用性】: encryptedApiKeyFlow() の初期発行・変更時再発行の両方から呼び出される
     * 【単一責任】: 「apiKeyの現在値を安全な既定値付きで取得する」責務のみを持つ
     * 【保守性】: apiKey の値そのものは戻り値として扱うのみで、ログ出力等は一切行わない（NFR-102）
     * 🔵 信頼性レベル: TASK-0058.md 実装詳細・requirements.md 2.1 デフォルト値定義より
     */
    private fun currentApiKey(): String = encryptedPrefs.getString(API_KEY_KEY, "") ?: ""

    /**
     * 【機能概要】: endpointUrl を DataStore に保存する
     * 【設計方針】: 既存 NoteSettingsRepositoryImpl と同一の dataStore.edit パターンを踏襲
     * 【テスト対応】: TC-01, TC-04, TC-11, TC-12, TC-13
     * 🔵 信頼性レベル: TASK-0058.md・NoteSettingsRepositoryImpl より
     */
    override suspend fun saveEndpointUrl(url: String) {
        dataStore.edit { it[ENDPOINT_URL_KEY] = url }
    }

    /**
     * 【機能概要】: apiKey を EncryptedSharedPreferences に保存する（DataStoreには一切保存しない）
     * 【設計方針】: EncryptedSharedPreferencesの edit()/apply() は同期ブロッキングAPIのため
     *              Dispatchers.IO 上で実行する。apiKeyはログ出力しない（NFR-102）
     * 【セキュリティ】: 引数 apiKey は例外・ログへ一切出力せず、EncryptedSharedPreferences への
     *              書き込みにのみ使用する。DataStoreへは触れないため平文混入経路が存在しない（REQ-401）
     * 【テスト対応】: TC-02, TC-04, TC-05, TC-07, TC-12, TC-13
     * 🔵 信頼性レベル: TASK-0058.md「4. saveApiKey()のDispatchers.IO実行」・REQ-401より
     */
    override suspend fun saveApiKey(apiKey: String) {
        // 【非同期化】: 同期APIをメインスレッドで呼ばないようIOディスパッチャに切り替える 🔵
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putString(API_KEY_KEY, apiKey).apply()
        }
    }

    /**
     * 【機能概要】: model を DataStore に保存する
     * 【設計方針】: saveEndpointUrl と同様のDataStore書き込みパターン
     * 【テスト対応】: TC-01, TC-04, TC-06, TC-12
     * 🔵 信頼性レベル: TASK-0058.md・NoteSettingsRepositoryImpl より
     */
    override suspend fun saveModel(model: String) {
        dataStore.edit { it[MODEL_KEY] = model }
    }
}

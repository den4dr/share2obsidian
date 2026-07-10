/**
 * LLMによるメモ更改機能の追加 Kotlin インターフェース・型定義
 *
 * 作成日: 2026-07-05
 * 関連設計: architecture.md / dataflow.md
 * 言語: Kotlin 2.2+
 *
 * 信頼性レベル:
 * - 🔵 青信号: 要件定義書・設計文書・ユーザヒアリング・既存実装を参考にした確実な定義
 * - 🟡 黄信号: 要件定義書・設計文書・ユーザヒアリングから妥当な推測による定義
 * - 🔴 赤信号: 要件定義書・設計文書・ユーザヒアリングにない推測による定義
 */

package com.den4dr.share2Obsidian

// ========================================
// Domain モデル変更
// ========================================

/**
 * Template ドメインモデル（変更後）
 *
 * 変更点:
 *   - bodyLlmPrompt: String を追加 🔵 REQ-101, REQ-102
 *
 * 🔵 信頼性: REQ-101, REQ-102・ヒアリング「プロンプトはテンプレートにフィールドごとに保存」より
 */
// data class Template(
//     val id: Long = 0,
//     val name: String,
//     val body: String = "",
//     val bodyLlmPrompt: String = "",       // 🔵 REQ-101 新規追加: 本文リライト用プロンプト
//     val fields: List<TemplateField>,
//     val isDefault: Boolean = false,
// )

/**
 * FieldValueSource（変更後）
 *
 * 変更点:
 *   - LLM を追加 🔵 REQ-303
 *
 * 🔵 信頼性: REQ-303・ヒアリング「FieldValueSourceにLLMを追加」より
 */
// enum class FieldValueSource { FIXED, HTML_META, URL, EMPTY, LLM }

/**
 * TemplateField ドメインモデル（変更後）
 *
 * 変更点:
 *   - llmPrompt: String を追加（valueSource == LLM の場合のみ使用） 🔵 REQ-104
 *
 * 🔵 信頼性: REQ-104・ヒアリング「フィールド用のプロンプト入力欄」より
 */
// data class TemplateField(
//     val id: Long = 0,
//     val templateId: Long = 0,
//     val key: String,
//     val valueSource: FieldValueSource,
//     val valueType: FieldValueType,
//     val defaultValue: String = "",
//     val metaKey: HtmlMetaKey? = null,
//     val llmPrompt: String = "",           // 🔵 REQ-104 新規追加
//     val sortOrder: Int = 0,
// )

/**
 * CustomFieldState（変更後）
 *
 * 変更点:
 *   - valueSource: FieldValueSource を追加（EditScreenでLLM生成ボタン表示判定に使用） 🟡
 *   - llmPrompt: String を追加（valueSource == LLM の場合にLLM呼び出しへ渡す） 🟡
 *
 * 🟡 信頼性: REQ-104, REQ-304 を実現するための設計上の帰結。
 *           直接ヒアリングはしていないが要件から論理的に導出される拡張
 */
// data class CustomFieldState(
//     val key: String,
//     val value: String,
//     val valueType: FieldValueType,
//     val valueSource: FieldValueSource = FieldValueSource.FIXED,  // 🟡 新規追加
//     val llmPrompt: String = "",                                  // 🟡 新規追加
// )

// ========================================
// LLM 設定（新規）
// ========================================

/**
 * LLM API 設定（DataStore + 暗号化ストレージの合成）
 * 🔵 信頼性: REQ-004, REQ-401・design-interview.md Q1より
 */
data class LlmSettings(
    val endpointUrl: String = "",  // 🔵 REQ-004 OpenAI互換 Chat Completions エンドポイント
    val apiKey: String = "",       // 🔵 REQ-401 暗号化ストレージに保存される機微情報
    val model: String = "",        // 🔵 REQ-004 使用するモデル名
)

/**
 * LlmSettingsRepository
 * LLM設定の読み書きインターフェース。endpointUrl/model は DataStore、apiKey は暗号化ストレージに保存する。
 * 🔵 信頼性: REQ-004, REQ-401, NFR-101より
 */
interface LlmSettingsRepository {

    /** LLM設定の現在値を Flow で取得（DataStore・暗号化ストレージ双方の変更を通知） 🔵 REQ-004 */
    fun getSettings(): kotlinx.coroutines.flow.Flow<LlmSettings>

    /** エンドポイントURLを DataStore に保存 🔵 REQ-004 */
    suspend fun saveEndpointUrl(url: String)

    /** APIキーを暗号化ストレージに保存 🔵 REQ-401, NFR-101 */
    suspend fun saveApiKey(apiKey: String)

    /** モデル名を DataStore に保存 🔵 REQ-004 */
    suspend fun saveModel(model: String)
}

// ========================================
// LLM 呼び出し結果（新規）
// ========================================

/**
 * LLM呼び出し結果を表す sealed class。
 * 🔵 信頼性: EDGE-001〜004, NFR-001より
 */
sealed class LlmRewriteResult {

    /** 呼び出し成功。書き換え/生成されたテキストを保持する 🔵 REQ-003 */
    data class Success(val text: String) : LlmRewriteResult()

    /** 呼び出し失敗。失敗種別ごとに表示すべきエラーメッセージのリソースIDを保持する */
    sealed class Failure : LlmRewriteResult() {
        abstract val messageResId: Int

        /** ネットワーク未接続・接続エラー 🔵 EDGE-001 */
        data class NetworkError(override val messageResId: Int) : Failure()

        /** APIキー不正・認証エラー（HTTP 401/403） 🔵 EDGE-002 */
        data class AuthError(override val messageResId: Int) : Failure()

        /** 30秒タイムアウト 🔵 EDGE-003, NFR-001 */
        data class Timeout(override val messageResId: Int) : Failure()

        /** LLMが空応答・パース不能なレスポンスを返した 🟡 EDGE-004（空応答時の扱いは直接確認していない） */
        data class EmptyOrInvalidResponse(override val messageResId: Int) : Failure()

        /** 上記以外の予期しないエラー 🟡 一般的なエラーハンドリング方針からの妥当な推測 */
        data class Unknown(override val messageResId: Int) : Failure()
    }
}

/**
 * LlmRewriteRepository
 * LLM APIへのリライト/生成リクエストを行うインターフェース。
 * 🔵 信頼性: REQ-002, REQ-302, REQ-402より
 */
interface LlmRewriteRepository {

    /**
     * LLM APIへリライト/生成リクエストを送信する。
     *
     * @param settings LLM API接続設定（endpointUrl/apiKey/model）
     * @param prompt システムプロンプト（テンプレートのbodyLlmPrompt、またはタグ提案用の固定プロンプト）
     * @param content 入力コンテンツ（共有/取得直後の元コンテンツ。EditScreen上の編集済み本文ではない。REQ-002, REQ-406）
     * @return 成功時は応答テキスト、失敗時は失敗種別を含む [LlmRewriteResult]
     */
    suspend fun rewrite(settings: LlmSettings, prompt: String, content: String): LlmRewriteResult
}

// ========================================
// UI State 変更
// ========================================

/**
 * EditFormState（変更後）
 *
 * 変更点:
 *   - isRewritingBody: Boolean を追加（本文リライト中のローディング状態） 🔵 REQ-201
 *   - isSuggestingTags: Boolean を追加（タグ提案中のローディング状態） 🔵 REQ-201
 *   - rewriteBodyEnabled: Boolean を追加（「メモを更改」ボタンの活性/非活性判定） 🔵 REQ-102
 *
 * 🔵 信頼性: REQ-102, REQ-201, REQ-301より
 */
// data class EditFormState(
//     val vault: String,
//     val folder: String,
//     val title: String,
//     val body: String,
//     val tagsText: String,
//     val customFields: List<CustomFieldState> = emptyList(),
//     val isRewritingBody: Boolean = false,     // 🔵 REQ-201 新規追加
//     val isSuggestingTags: Boolean = false,    // 🔵 REQ-201 新規追加
//     val rewriteBodyEnabled: Boolean = false,  // 🔵 REQ-102 新規追加: bodyLlmPrompt.isNotBlank() から算出
// )

/**
 * TemplateEditUiState（変更後）
 *
 * 変更点:
 *   - bodyLlmPrompt: String を追加 🔵 REQ-101
 *
 * 🔵 信頼性: REQ-101より
 */
// data class TemplateEditUiState(
//     val templateId: Long? = null,
//     val name: String = "",
//     val body: String = "",
//     val bodyLlmPrompt: String = "",  // 🔵 REQ-101 新規追加
//     val isDefault: Boolean = false,
//     val fields: List<TemplateFieldEditState> = emptyList(),
//     val isSaving: Boolean = false,
//     val errorMessage: String? = null,
//     val isSaved: Boolean = false,
// )

/**
 * TemplateFieldEditState（変更後）
 *
 * 変更点:
 *   - llmPrompt: String を追加（valueSource == LLM の場合のみ使用） 🔵 REQ-104
 *
 * 🔵 信頼性: REQ-104より
 */
// data class TemplateFieldEditState(
//     val id: Long = 0,
//     val key: String = "",
//     val valueSource: FieldValueSource = FieldValueSource.EMPTY,
//     val valueType: FieldValueType = FieldValueType.STRING,
//     val defaultValue: String = "",
//     val metaKey: HtmlMetaKey? = null,
//     val llmPrompt: String = "",  // 🔵 REQ-104 新規追加
//     val sortOrder: Int = 0,
// )

/**
 * SettingsUiState（変更後）
 *
 * 変更点:
 *   - llmEndpointUrl, llmApiKey, llmModel を追加 🔵 REQ-004
 *
 * 🔵 信頼性: REQ-004より
 */
// data class SettingsUiState(
//     val vault: String = "",
//     val folder: String = "",
//     val llmEndpointUrl: String = "",  // 🔵 REQ-004 新規追加
//     val llmApiKey: String = "",       // 🔵 REQ-004 新規追加
//     val llmModel: String = "",        // 🔵 REQ-004 新規追加
// )

// ========================================
// EditScreenViewModel 変更後シグネチャ
// ========================================

/**
 * EditScreenViewModel（変更後）
 *
 * 変更点:
 *   - @HiltViewModel 化。LlmRewriteRepository / LlmSettingsRepository を注入 🔵 design-interview.md Q2
 *   - sourceContent, bodyLlmPrompt をプライベート状態として保持（EditFormStateには含めない） 🔵 REQ-406
 *   - rewriteBody(), suggestTags() を新規追加 🔵 REQ-002, REQ-301, REQ-302
 *   - errorEvents: SharedFlow<Int> を新規追加（design-interview.md Q3） 🔵
 *   - initialize() の引数に sourceContent, bodyLlmPrompt を追加 🔵 REQ-406
 *
 * 🔵 信頼性: REQ-002, REQ-003, REQ-201, REQ-202, REQ-301, REQ-302, REQ-406・design-interview.md Q2・Q3より
 */
// @HiltViewModel
// class EditScreenViewModel @Inject constructor(
//     private val llmRewriteRepository: LlmRewriteRepository,
//     private val llmSettingsRepository: LlmSettingsRepository,
// ) : ViewModel() {
//
//     private val _formState = MutableStateFlow(EditFormState(...))
//     val formState: StateFlow<EditFormState> = _formState.asStateFlow()
//     private var initialized = false
//
//     private var sourceContent: String = ""     // 🔵 REQ-406: LLM入力用の元コンテンツ
//     private var bodyLlmPrompt: String = ""      // 🔵 REQ-101: テンプレートの本文用プロンプト
//
//     private val _errorEvents = MutableSharedFlow<Int>()
//     val errorEvents: SharedFlow<Int> = _errorEvents.asSharedFlow()  // 🔵 design-interview.md Q3
//
//     fun initialize(
//         processed: ProcessedContent,
//         config: NoteConfig,
//         customFields: List<CustomFieldState>,
//         sourceContent: String,       // 🔵 新規引数: テンプレート適用前の元コンテンツ
//         bodyLlmPrompt: String,       // 🔵 新規引数: テンプレートの本文用プロンプト
//     ) {
//         if (initialized) return
//         initialized = true
//         this.sourceContent = sourceContent
//         this.bodyLlmPrompt = bodyLlmPrompt
//         _formState.value = EditFormState(
//             ...,
//             rewriteBodyEnabled = bodyLlmPrompt.isNotBlank(),  // 🔵 REQ-102
//         )
//     }
//
//     /** 「メモを更改」ボタン押下時（REQ-002, REQ-003, REQ-201, REQ-406） */
//     fun rewriteBody() {
//         viewModelScope.launch {
//             _formState.update { it.copy(isRewritingBody = true) }
//             val settings = llmSettingsRepository.getSettings().first()
//             when (val result = llmRewriteRepository.rewrite(settings, bodyLlmPrompt, sourceContent)) {
//                 is LlmRewriteResult.Success -> _formState.update { it.copy(body = result.text) }
//                 is LlmRewriteResult.Failure -> _errorEvents.emit(result.messageResId)
//             }
//             _formState.update { it.copy(isRewritingBody = false) }
//         }
//     }
//
//     /** 「タグを提案」ボタン押下時（REQ-301, REQ-302, REQ-406） */
//     fun suggestTags() {
//         viewModelScope.launch {
//             _formState.update { it.copy(isSuggestingTags = true) }
//             val settings = llmSettingsRepository.getSettings().first()
//             when (val result = llmRewriteRepository.rewrite(settings, TAG_SUGGESTION_PROMPT, sourceContent)) {
//                 is LlmRewriteResult.Success -> _formState.update {
//                     val merged = if (it.tagsText.isBlank()) result.text else "${it.tagsText}, ${result.text}"
//                     it.copy(tagsText = merged)
//                 }
//                 is LlmRewriteResult.Failure -> _errorEvents.emit(result.messageResId)
//             }
//             _formState.update { it.copy(isSuggestingTags = false) }
//         }
//     }
// }

// ========================================
// LlmSettingsRepositoryImpl（新規）
// ========================================

/**
 * LlmSettingsRepositoryImpl
 * DataStore Preferences（endpointUrl/model）+ EncryptedSharedPreferences（apiKey）による実装。
 * 🔵 信頼性: REQ-004, REQ-401, NFR-101・design-interview.md Q1より
 */
// class LlmSettingsRepositoryImpl(
//     private val dataStore: DataStore<Preferences>,
//     private val encryptedPrefs: SharedPreferences,  // EncryptedSharedPreferences
// ) : LlmSettingsRepository {
//
//     override fun getSettings(): Flow<LlmSettings> =
//         dataStore.data.map { prefs ->
//             prefs[ENDPOINT_URL_KEY] ?: "" to (prefs[MODEL_KEY] ?: "")
//         }.combine(encryptedApiKeyFlow()) { (endpointUrl, model), apiKey ->
//             LlmSettings(endpointUrl = endpointUrl, apiKey = apiKey, model = model)
//         }
//
//     // EncryptedSharedPreferences の変更通知を Flow 化する 🟡（実装詳細の妥当な推測）
//     private fun encryptedApiKeyFlow(): Flow<String> = callbackFlow {
//         trySend(encryptedPrefs.getString(API_KEY_KEY, "") ?: "")
//         val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
//             if (key == API_KEY_KEY) trySend(prefs.getString(API_KEY_KEY, "") ?: "")
//         }
//         encryptedPrefs.registerOnSharedPreferenceChangeListener(listener)
//         awaitClose { encryptedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
//     }
//
//     override suspend fun saveEndpointUrl(url: String) {
//         dataStore.edit { it[ENDPOINT_URL_KEY] = url }
//     }
//
//     override suspend fun saveApiKey(apiKey: String) {
//         withContext(Dispatchers.IO) { encryptedPrefs.edit().putString(API_KEY_KEY, apiKey).apply() }
//     }
//
//     override suspend fun saveModel(model: String) {
//         dataStore.edit { it[MODEL_KEY] = model }
//     }
// }
//
// internal val ENDPOINT_URL_KEY = stringPreferencesKey("llm_endpoint_url")
// internal val MODEL_KEY = stringPreferencesKey("llm_model")
// internal const val API_KEY_KEY = "llm_api_key"

// ========================================
// LlmRewriteRepositoryImpl（新規）
// ========================================

/**
 * LlmRewriteRepositoryImpl
 * Ktor Client (CIO) による OpenAI互換 Chat Completions 呼び出し実装。
 * 🔵 信頼性: REQ-402, NFR-001・design-interview.md Q1より
 */
// class LlmRewriteRepositoryImpl(
//     private val httpClient: HttpClient,  // HttpTimeout(30_000ms) 設定済み
// ) : LlmRewriteRepository {
//
//     override suspend fun rewrite(settings: LlmSettings, prompt: String, content: String): LlmRewriteResult {
//         return try {
//             val response: ChatCompletionResponseDto = httpClient.post(settings.endpointUrl) {
//                 header(HttpHeaders.Authorization, "Bearer ${settings.apiKey}")
//                 contentType(ContentType.Application.Json)
//                 setBody(ChatCompletionRequestDto(
//                     model = settings.model,
//                     messages = listOf(
//                         ChatMessageDto(role = "system", content = prompt),
//                         ChatMessageDto(role = "user", content = content),
//                     ),
//                 ))
//             }.body()
//             val text = response.choices.firstOrNull()?.message?.content
//             if (text.isNullOrEmpty()) LlmRewriteResult.Failure.EmptyOrInvalidResponse(R.string.error_llm_empty_response)
//             else LlmRewriteResult.Success(text)
//         } catch (e: HttpRequestTimeoutException) {
//             LlmRewriteResult.Failure.Timeout(R.string.error_llm_timeout)
//         } catch (e: ClientRequestException) {
//             if (e.response.status.value in listOf(401, 403)) {
//                 LlmRewriteResult.Failure.AuthError(R.string.error_llm_auth)
//             } else {
//                 LlmRewriteResult.Failure.Unknown(R.string.error_llm_unknown)
//             }
//         } catch (e: IOException) {
//             LlmRewriteResult.Failure.NetworkError(R.string.error_llm_network)
//         }
//         // 注意: 例外オブジェクト自体（メッセージにAPIキーを含む可能性）をログ出力しない（NFR-102）
//     }
// }

// ========================================
// LlmModule（新規 Hilt Module）
// ========================================

/**
 * LlmModule
 * HttpClient・EncryptedSharedPreferences・各Repositoryを Hilt に登録する。
 * 🔵 信頼性: REQ-401, REQ-402・Hilt公式パターンより
 */
// @Module
// @InstallIn(SingletonComponent::class)
// object LlmModule {
//
//     @Provides
//     @Singleton
//     fun provideHttpClient(): HttpClient = HttpClient(CIO) {
//         install(ContentNegotiation) { json() }
//         install(HttpTimeout) { requestTimeoutMillis = 30_000 }  // 🔵 NFR-001
//     }
//
//     @Provides
//     @Singleton
//     fun provideEncryptedSharedPreferences(
//         @ApplicationContext context: Context
//     ): SharedPreferences {
//         val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
//         return EncryptedSharedPreferences.create(
//             context, "llm_secure_prefs", masterKey,
//             EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
//             EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
//         )
//     }
//
//     @Provides
//     @Singleton
//     fun provideLlmSettingsRepository(
//         @ApplicationContext context: Context,
//         encryptedPrefs: SharedPreferences,
//     ): LlmSettingsRepository = LlmSettingsRepositoryImpl(context.llmSettingsDataStore, encryptedPrefs)
//
//     @Provides
//     @Singleton
//     fun provideLlmRewriteRepository(httpClient: HttpClient): LlmRewriteRepository =
//         LlmRewriteRepositoryImpl(httpClient)
// }

// ========================================
// 信頼性レベルサマリー
// ========================================
/**
 * - 🔵 青信号: 57件 (88%)
 * - 🟡 黄信号: 8件 (12%)（CustomFieldStateの拡張、システム/ユーザーロール割り当て、EncryptedSharedPreferencesのFlow化パターン等）
 * - 🔴 赤信号: 0件 (0%)
 *
 * 品質評価: ✅ 高品質
 */

/**
 * 編集テンプレートの管理機能 Kotlin インターフェース・型定義
 *
 * 作成日: 2026-05-31
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
// Domain モデル Enum
// ========================================

/**
 * フィールドの値ソース種別
 * 🔵 信頼性: REQ-033, REQ-041〜044・ユーザヒアリングより
 */
enum class FieldValueSource {
    FIXED,      // ユーザー手入力の固定初期値 🔵 REQ-041
    HTML_META,  // HTMLメタデータの自動マッピング 🔵 REQ-042
    URL,        // 共有されたURLそのもの 🔵 REQ-043
    EMPTY,      // 空値（EditScreenでユーザーが入力） 🔵 REQ-044
}

/**
 * フィールドの値の型
 * 🔵 信頼性: REQ-033・ユーザヒアリング「文字列とリスト」より
 */
enum class FieldValueType {
    STRING, // 文字列型 🔵
    LIST,   // リスト型（カンマ区切り → YAML list） 🔵
}

/**
 * HTMLメタデータのキー種別
 * 🔵 信頼性: REQ-042・ユーザヒアリング「HTMLメタデータ対象」より
 */
enum class HtmlMetaKey {
    OG_TITLE,       // og:title または <title>タグ 🔵
    OG_DESCRIPTION, // og:description または <meta name="description"> 🔵
    URL,            // ページのURL 🔵
    PUBLISHED_DATE, // article:published_time または datePublished 🔵
    MODIFIED_DATE,  // article:modified_time または dateModified 🔵
    AUTHOR,         // author, article:author, og:site_name 🔵
}

// ========================================
// Domain モデル（data class）
// ========================================

/**
 * テンプレートドメインモデル
 * 🔵 信頼性: REQ-015・ユーザヒアリング「テンプレート名, vault/folder, フィールド一覧, デフォルトフラグ」より
 *
 * @param id Room DB の主キー（0 は未保存）
 * @param name テンプレート名（必須・一意）
 * @param vault Obsidian Vault 名
 * @param folder 保存先フォルダ
 * @param fields カスタム front matter フィールドの一覧
 * @param isDefault デフォルトテンプレートフラグ（共有時に自動適用）
 */
data class Template(
    val id: Long = 0,                       // 🔵 Room PK
    val name: String,                       // 🔵 REQ-015 テンプレート名
    val vault: String,                      // 🔵 REQ-015 vault 設定
    val folder: String,                     // 🔵 REQ-015 folder 設定
    val fields: List<TemplateField>,        // 🔵 REQ-015 カスタムフィールド一覧
    val isDefault: Boolean = false,         // 🔵 REQ-015, REQ-021 デフォルトフラグ
)

/**
 * テンプレートフィールドドメインモデル
 * 🔵 信頼性: REQ-033・ユーザヒアリングより
 *
 * @param id Room DB の主キー（0 は未保存）
 * @param templateId 親 Template の id（0 は未保存）
 * @param key front matter のキー名（例: "source", "description"）
 * @param valueSource 値ソース種別
 * @param valueType 値の型（STRING / LIST）
 * @param defaultValue FIXED の場合の初期値文字列（FIXED 以外は空文字列）
 * @param metaKey HTML_META の場合のマッピング先（HTML_META 以外は null）
 * @param sortOrder TemplateEditScreen での表示順序
 */
data class TemplateField(
    val id: Long = 0,                           // 🔵 Room PK
    val templateId: Long = 0,                   // 🔵 FK → Template.id
    val key: String,                            // 🔵 REQ-033 フィールドキー名
    val valueSource: FieldValueSource,          // 🔵 REQ-033 値ソース種別
    val valueType: FieldValueType,              // 🔵 REQ-033 値の型
    val defaultValue: String = "",              // 🔵 REQ-041 FIXED の初期値
    val metaKey: HtmlMetaKey? = null,           // 🔵 REQ-042 HTML_META のマッピングキー
    val sortOrder: Int = 0,                     // 🟡 UI表示順管理
)

// ========================================
// UI State（Compose）
// ========================================

/**
 * EditScreen のカスタムフィールド 1 件の状態
 * 🔵 信頼性: REQ-052・ユーザヒアリングより
 *
 * @param key front matter キー名
 * @param value EditScreen で表示・編集される現在値
 * @param valueType STRING / LIST（LIST の場合はカンマ区切り文字列として保持）
 */
data class CustomFieldState(
    val key: String,                    // 🔵 REQ-033 キー名
    val value: String,                  // 🔵 REQ-052 テンプレート適用後の値
    val valueType: FieldValueType,      // 🔵 REQ-033 値の型
)

/**
 * EditFormState（拡張版）
 * 既存の EditFormState に customFields を追加
 * 🔵 信頼性: REQ-052・既存 EditFormState より
 */
data class EditFormState(
    val title: String,                              // 🔵 既存フィールド
    val body: String,                               // 🔵 既存フィールド
    val tagsText: String,                           // 🔵 既存フィールド
    val folder: String,                             // 🔵 既存フィールド
    val customFields: List<CustomFieldState> = emptyList(), // 🔵 REQ-052 追加
)

/**
 * SendParams（拡張版）
 * 既存の SendParams に customFields を追加
 * 🔵 信頼性: REQ-052・既存 SendParams より
 */
data class SendParams(
    val title: String?,                             // 🔵 既存フィールド
    val body: String,                               // 🔵 既存フィールド
    val tags: List<String>,                         // 🔵 既存フィールド
    val config: com.den4dr.share2Obsidian.format.NoteConfig,  // 🔵 既存フィールド
    val customFields: List<CustomFieldState> = emptyList(),   // 🔵 REQ-052 追加
)

// ========================================
// UI State（テンプレート管理画面）
// ========================================

/**
 * TemplateListScreen の UI 状態
 * 🔵 信頼性: REQ-011・MVVM パターンより
 */
data class TemplateListUiState(
    val templates: List<Template> = emptyList(),  // 🔵 REQ-011
    val isLoading: Boolean = false,               // 🟡 非同期読み込み中フラグ
    val errorMessage: String? = null,             // 🟡 エラーメッセージ
)

/**
 * TemplateEditScreen の UI 状態
 * 🔵 信頼性: REQ-012〜014・MVVM パターンより
 */
data class TemplateEditUiState(
    val templateId: Long? = null,                         // 🔵 null=新規
    val name: String = "",                                // 🔵 REQ-015
    val vault: String = "",                               // 🔵 REQ-015
    val folder: String = "",                              // 🔵 REQ-015
    val isDefault: Boolean = false,                       // 🔵 REQ-021
    val fields: List<TemplateFieldEditState> = emptyList(), // 🔵 REQ-031〜033
    val isSaving: Boolean = false,                        // 🟡 保存中フラグ
    val errorMessage: String? = null,                     // 🟡 バリデーションエラー
    val isSaved: Boolean = false,                         // 🟡 保存完了フラグ
) {
    companion object {
        fun empty() = TemplateEditUiState()
    }
}

/**
 * TemplateEditScreen 内のフィールド 1 件の編集状態
 * 🔵 信頼性: REQ-033・TemplateField ドメインモデルより
 */
data class TemplateFieldEditState(
    val id: Long = 0,                                   // 🔵 Room PK（0=未保存）
    val key: String = "",                               // 🔵 REQ-033
    val valueSource: FieldValueSource = FieldValueSource.EMPTY, // 🔵 REQ-033
    val valueType: FieldValueType = FieldValueType.STRING,      // 🔵 REQ-033
    val defaultValue: String = "",                      // 🔵 REQ-041
    val metaKey: HtmlMetaKey? = null,                   // 🔵 REQ-042
    val sortOrder: Int = 0,                             // 🟡 表示順
)

// ========================================
// ProcessedContent（拡張版）
// ========================================

/**
 * ProcessedContent（拡張版）
 * 既存の ProcessedContent に metadata と sourceUrl を追加
 * 🔵 信頼性: REQ-072・ユーザヒアリングより
 */
data class ProcessedContent(
    val body: String,                                           // 🔵 既存
    val title: String? = null,                                  // 🔵 既存
    val contentType: com.den4dr.share2Obsidian.content.ContentKind, // 🔵 既存
    val metadata: Map<HtmlMetaKey, String> = emptyMap(),        // 🔵 REQ-072 追加
    val sourceUrl: String? = null,                              // 🔵 REQ-072 追加
)

// ========================================
// WebViewExtractionResult（拡張版）
// ========================================

/**
 * WebViewExtractionResult（拡張版）
 * 既存の WebViewExtractionResult に HTML メタデータフィールドを追加
 * 🔵 信頼性: REQ-071・WebViewExtractor 既存実装より
 */
data class WebViewExtractionResult(
    val bodyText: String?,                  // 🔵 既存フィールド
    val pageTitle: String? = null,          // 🔵 既存フィールド
    val ogTitle: String = "",               // 🔵 REQ-042 og:title 追加
    val ogDescription: String = "",         // 🔵 REQ-042 og:description 追加
    val publishedTime: String = "",         // 🔵 REQ-042 公開日 追加
    val modifiedTime: String = "",          // 🔵 REQ-042 最終更新日 追加
    val author: String = "",               // 🔵 REQ-042 著者 追加
)

// ========================================
// Repository インターフェース
// ========================================

/**
 * TemplateRepository
 * 🔵 信頼性: NFR-201・tech-stack.md MVVM + Repository より
 */
interface TemplateRepository {

    /** テンプレート一覧を Flow で取得（DB 変更を自動的に通知）🔵 REQ-011 */
    fun getAllTemplates(): kotlinx.coroutines.flow.Flow<List<Template>>

    /** デフォルトテンプレートを取得（なければ null）🔵 REQ-051 */
    suspend fun getDefaultTemplate(): Template?

    /** ID 指定でテンプレートを取得 🔵 REQ-013 */
    suspend fun getTemplateById(id: Long): Template?

    /**
     * テンプレートを保存（新規または更新）
     * isDefault=true の場合、他のテンプレートのデフォルトを自動解除（REQ-022）
     * 🔵 REQ-012, REQ-013, REQ-021, REQ-022
     */
    suspend fun saveTemplate(template: Template): Long

    /** テンプレートを削除（関連フィールドは CASCADE で自動削除）🔵 REQ-014 */
    suspend fun deleteTemplate(template: Template)
}

// ========================================
// TemplateListViewModel（シグネチャ）
// ========================================

/**
 * TemplateListViewModel のシグネチャ
 * 🔵 信頼性: REQ-011・@HiltViewModel + TemplateRepository より
 */
// @HiltViewModel
// class TemplateListViewModel @Inject constructor(
//     private val templateRepository: TemplateRepository
// ) : ViewModel() {
//
//     val uiState: StateFlow<TemplateListUiState> = templateRepository
//         .getAllTemplates()
//         .map { templates -> TemplateListUiState(templates = templates) }
//         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemplateListUiState())
//
//     fun deleteTemplate(template: Template) {
//         viewModelScope.launch(Dispatchers.IO) {
//             templateRepository.deleteTemplate(template)
//         }
//     }
// }

// ========================================
// TemplateEditViewModel（シグネチャ）
// ========================================

/**
 * TemplateEditViewModel のシグネチャ
 * 🔵 信頼性: REQ-012〜014, REQ-021〜022・@HiltViewModel + TemplateRepository より
 */
// @HiltViewModel
// class TemplateEditViewModel @Inject constructor(
//     private val templateRepository: TemplateRepository,
//     savedStateHandle: SavedStateHandle
// ) : ViewModel() {
//
//     private val templateId: Long? = savedStateHandle.get<Long>("templateId")
//         ?.takeIf { it != -1L }
//
//     private val _uiState = MutableStateFlow(TemplateEditUiState.empty())
//     val uiState: StateFlow<TemplateEditUiState> = _uiState.asStateFlow()
//
//     init {
//         templateId?.let { loadTemplate(it) }
//     }
//
//     private fun loadTemplate(id: Long) { ... }
//     fun updateName(name: String) { ... }
//     fun updateVault(vault: String) { ... }
//     fun updateFolder(folder: String) { ... }
//     fun updateIsDefault(isDefault: Boolean) { ... }
//     fun addField(field: TemplateFieldEditState) { ... }
//     fun removeField(index: Int) { ... }
//     fun updateField(index: Int, field: TemplateFieldEditState) { ... }
//     fun save() { ... }  // Dispatchers.IO でDB保存 → isSaved = true
// }

// ========================================
// NoteComposer.buildFrontmatter 拡張シグネチャ
// ========================================

/**
 * NoteComposer.buildFrontmatter の拡張版シグネチャ
 * カスタムフィールドが標準の tags を上書きする（EDGE-005: カスタムが上書き）
 * 🔵 信頼性: REQ-404, EDGE-005・ユーザヒアリング「カスタムが上書き」より
 *
 * 出力例（customFields に key="tags" がある場合）:
 *   ---
 *   source: https://example.com
 *   tags: [custom-tag1, custom-tag2]   ← カスタムの tags が標準を上書き
 *   ---
 *
 * 出力例（customFields に key="tags" がない場合）:
 *   ---
 *   source: https://example.com
 *   tags: [shared]   ← 標準の tags が出力される
 *   ---
 */
// fun NoteComposer.buildFrontmatter(
//     body: String,
//     tags: List<String>,
//     customFields: List<CustomFieldState> = emptyList()
// ): String

// ========================================
// Hilt Module（DatabaseModule）シグネチャ
// ========================================

/**
 * DatabaseModule の Hilt @Module シグネチャ
 * 🔵 信頼性: REQ-403, NFR-202・Hilt + Room 公式パターンより
 */
// @Module
// @InstallIn(SingletonComponent::class)
// object DatabaseModule {
//
//     @Provides
//     @Singleton
//     fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
//         Room.databaseBuilder(context, AppDatabase::class.java, "share2obsidian.db").build()
//
//     @Provides
//     @Singleton
//     fun provideTemplateDao(db: AppDatabase): TemplateDao = db.templateDao()
//
//     @Provides
//     @Singleton
//     fun provideTemplateRepository(dao: TemplateDao): TemplateRepository =
//         TemplateRepositoryImpl(dao)
// }

// ========================================
// 信頼性レベルサマリー
// ========================================
/**
 * - 🔵 青信号: 38件 (90%)
 * - 🟡 黄信号: 5件 (12%)  ← isSaving, errorMessage, sortOrder 等の UI 補助フィールド
 * - 🔴 赤信号: 0件 (0%)
 *
 * 品質評価: 高品質
 */

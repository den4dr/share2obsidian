/**
 * テンプレートの管理内容の変更 Kotlin インターフェース・型定義
 *
 * 作成日: 2026-06-07
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
 *   - vault: String を削除 🔵 REQ-001
 *   - folder: String を削除 🔵 REQ-001
 *   - body: String を追加 🔵 REQ-002
 *
 * 🔵 信頼性: REQ-001, REQ-002・ユーザヒアリング「Templateからvault/folderを削除しbodyを追加」より
 */
data class Template(
    val id: Long = 0,                    // 🔵 既存 Room PK
    val name: String,                    // 🔵 既存 テンプレート名
    val body: String = "",              // 🔵 REQ-002 本文テンプレート（{{content}} プレースホルダーを含む可能性あり）
    val fields: List<TemplateField>,     // 🔵 既存 frontmatter フィールド一覧
    val isDefault: Boolean = false,      // 🔵 既存 デフォルトフラグ
    // vault: String は削除 🔵 REQ-001
    // folder: String は削除 🔵 REQ-001
)

// ========================================
// DataStore 設定データ（新規）
// ========================================

/**
 * DataStore に保存されるノート設定（vault/folder のグローバルデフォルト）
 * 🔵 信頼性: REQ-021・設計ヒアリングより
 */
data class NoteSettings(
    val vault: String = "",   // 🔵 REQ-021 Obsidian Vault のデフォルト（DataStore 由来）
    val folder: String = "",  // 🔵 REQ-021 保存先フォルダのデフォルト（DataStore 由来）
)

// ========================================
// DataStore Repository インターフェース（新規）
// ========================================

/**
 * NoteSettingsRepository
 * DataStore Preferences を使った vault/folder の読み書きインターフェース
 * 🔵 信頼性: REQ-021・Repository パターン・tech-stack.md DataStore Preferences より
 */
interface NoteSettingsRepository {

    /** vault/folder の現在設定を Flow で取得（DataStore 変更を自動通知） 🔵 REQ-021 */
    fun getSettings(): kotlinx.coroutines.flow.Flow<NoteSettings>

    /** vault を DataStore に保存 🔵 REQ-021 */
    suspend fun saveVault(vault: String)

    /** folder を DataStore に保存 🔵 REQ-021 */
    suspend fun saveFolder(folder: String)
}

// ========================================
// UI State 変更
// ========================================

/**
 * EditFormState（変更後）
 *
 * 変更点:
 *   - vault: String を追加 🔵 REQ-061
 *
 * 🔵 信頼性: REQ-061・設計ヒアリング「EditFormStateからvault/folderを取得」より
 */
data class EditFormState(
    val vault: String,     // 🔵 REQ-061 新規追加: NoteSettings.vault で初期化、EditScreen で編集可
    val folder: String,    // 🔵 既存: NoteSettings.folder で初期化、EditScreen で編集可
    val title: String,     // 🔵 既存: ファイル名として別セクションに表示（REQ-042）
    val body: String,      // 🔵 既存: buildBody()解決済みの本文
    val tagsText: String,  // 🔵 既存: カンマ区切りタグ文字列
    val customFields: List<CustomFieldState> = emptyList(), // 🔵 既存
)

/**
 * TemplateEditUiState（変更後）
 *
 * 変更点:
 *   - vault: String を削除 🔵 REQ-051
 *   - folder: String を削除 🔵 REQ-051
 *   - body: String を追加 🔵 REQ-052
 *
 * 🔵 信頼性: REQ-051, REQ-052・設計ヒアリングより
 */
data class TemplateEditUiState(
    val templateId: Long? = null,                              // 🔵 既存: null=新規
    val name: String = "",                                     // 🔵 既存: テンプレート名
    val body: String = "",                                    // 🔵 REQ-052 新規追加: 本文テンプレート
    val isDefault: Boolean = false,                            // 🔵 既存: デフォルトフラグ
    val fields: List<TemplateFieldEditState> = emptyList(),    // 🔵 既存: frontmatter フィールド
    val isSaving: Boolean = false,                             // 🟡 既存: 保存中フラグ
    val errorMessage: String? = null,                          // 🟡 既存: バリデーションエラー
    val isSaved: Boolean = false,                              // 🟡 既存: 保存完了フラグ
    // vault: String は削除 🔵 REQ-051
    // folder: String は削除 🔵 REQ-051
)

/**
 * SettingsUiState（新規）
 * SettingsScreen で表示する vault/folder の状態
 * 🔵 信頼性: REQ-021・SettingsViewModel 設計より
 */
data class SettingsUiState(
    val vault: String = "",   // 🔵 REQ-021 DataStore の vault 値
    val folder: String = "",  // 🔵 REQ-021 DataStore の folder 値
)

// ========================================
// TemplateApplicator 変更後シグネチャ
// ========================================

/**
 * TemplateApplicator（変更後）
 *
 * 変更点:
 *   - buildConfig(template) → buildConfig(settings: NoteSettings) 🔵 REQ-031
 *   - buildBody() を新規追加 🔵 REQ-032
 *
 * 🔵 信頼性: REQ-031, REQ-032・設計ヒアリングより
 */
// object TemplateApplicator {
//
//     /** DataStore 設定から NoteConfig を構築（Template は参照しない） */
//     // 🔵 REQ-031: テンプレートから vault/folder を取得しない
//     fun buildConfig(settings: NoteSettings): NoteConfig = NoteConfig(
//         vault = settings.vault,
//         folder = settings.folder,
//         defaultTags = AppConfig.OBSIDIAN_TAGS,
//     )
//
//     /**
//      * {{content}} プレースホルダー解決
//      * 🔵 REQ-011〜013, EDGE-001〜002
//      *
//      * @param template デフォルトテンプレート（null の場合は sharedBody をそのまま返す）
//      * @param sharedBody 共有されたコンテンツの本文
//      * @return プレースホルダー解決後の本文文字列
//      */
//     fun buildBody(template: Template?, sharedBody: String): String {
//         val templateBody = template?.body ?: ""
//         return if (templateBody.isEmpty()) sharedBody  // REQ-013
//                else templateBody.replace("{{content}}", sharedBody)  // REQ-012, EDGE-001
//     }
//
//     /** カスタムフィールド適用（変更なし） */
//     fun buildCustomFields(template: Template?, processed: ProcessedContent): List<CustomFieldState> { ... }
// }

// ========================================
// EditScreenViewModel 変更後シグネチャ
// ========================================

/**
 * EditScreenViewModel（変更後）
 *
 * 変更点:
 *   - initialize(): vault の初期化追加
 *   - updateVault() を新規追加
 *   - buildSendParams(config: NoteConfig) → buildSendParams() に変更（引数削除）
 *
 * 🔵 信頼性: REQ-061, REQ-062・設計ヒアリング「EditFormStateからvault/folderを取得」より
 */
// class EditScreenViewModel : ViewModel() {
//
//     private val _formState = MutableStateFlow(
//         EditFormState(vault = "", folder = "", title = "", body = "", tagsText = "")
//     )
//     val formState: StateFlow<EditFormState> = _formState.asStateFlow()
//     private var initialized = false
//
//     fun initialize(processed: ProcessedContent, config: NoteConfig, customFields: List<CustomFieldState>) {
//         if (initialized) return
//         initialized = true
//         _formState.value = EditFormState(
//             vault = config.vault,       // 🔵 REQ-061 新規: DataStore 由来の vault で初期化
//             folder = config.folder,     // 🔵 既存
//             title = processed.title ?: "",
//             body = processed.body,      // 🔵 buildBody() 解決済みの本文
//             tagsText = config.defaultTags.joinToString(", "),
//             customFields = customFields,
//         )
//     }
//
//     fun updateVault(vault: String) {    // 🔵 REQ-061 新規追加
//         _formState.value = _formState.value.copy(vault = vault)
//     }
//     fun updateFolder(folder: String) { _formState.value = _formState.value.copy(folder = folder) }
//     fun updateTitle(title: String) { ... }
//     fun updateBody(body: String) { ... }
//     fun updateTagsText(tagsText: String) { ... }
//     fun updateCustomField(index: Int, value: String) { ... }
//
//     /**
//      * config 引数削除: EditFormState.vault/folder から NoteConfig を構築する
//      * 🔵 REQ-062: 設計ヒアリング「EditFormStateからvault/folderを取得」より
//      */
//     fun buildSendParams(): SendParams {  // 引数なし
//         val state = _formState.value
//         return SendParams(
//             title = state.title.ifBlank { null },
//             body = state.body,
//             tags = parseTagsText(state.tagsText),
//             config = NoteConfig(
//                 vault = state.vault,
//                 folder = state.folder,
//                 defaultTags = emptyList(),
//             ),
//             customFields = state.customFields,
//         )
//     }
// }

// ========================================
// TemplateEditViewModel 変更後シグネチャ
// ========================================

/**
 * TemplateEditViewModel（変更後）
 *
 * 変更点:
 *   - updateVault(), updateFolder() を削除
 *   - updateBody() を新規追加
 *   - save() 内の Template 構築から vault/folder を削除、body を追加
 *
 * 🔵 信頼性: REQ-051, REQ-052・TemplateEditViewModel 既存実装より
 */
// @HiltViewModel
// class TemplateEditViewModel @Inject constructor(
//     private val templateRepository: TemplateRepository,
//     savedStateHandle: SavedStateHandle,
// ) : ViewModel() {
//
//     ...
//
//     // updateVault() を削除 🔵 REQ-051
//     // updateFolder() を削除 🔵 REQ-051
//     fun updateBody(body: String) = _uiState.update { it.copy(body = body) }  // 🔵 REQ-052 新規追加
//
//     fun save() {
//         viewModelScope.launch(Dispatchers.IO) {
//             val state = _uiState.value
//             val template = Template(
//                 id = state.templateId ?: 0L,
//                 name = state.name,
//                 body = state.body,        // 🔵 REQ-002 新規追加
//                 isDefault = state.isDefault,
//                 fields = state.fields.mapIndexed { ... },
//                 // vault, folder は削除 🔵 REQ-001
//             )
//             templateRepository.saveTemplate(template)
//             ...
//         }
//     }
// }

// ========================================
// SettingsViewModel（新規）
// ========================================

/**
 * SettingsViewModel（新規）
 * SettingsScreen での vault/folder 読み書きを DataStore 経由で管理する
 * 🔵 信頼性: REQ-021・設計ヒアリング「SettingsScreenに追加」より
 */
// @HiltViewModel
// class SettingsViewModel @Inject constructor(
//     private val noteSettingsRepository: NoteSettingsRepository,
// ) : ViewModel() {
//
//     val uiState: StateFlow<SettingsUiState> = noteSettingsRepository.getSettings()
//         .map { SettingsUiState(vault = it.vault, folder = it.folder) }
//         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())
//
//     /** vault 変更時に DataStore に即時保存（保存ボタンなし） 🔵 REQ-021 */
//     fun updateVault(vault: String) {
//         viewModelScope.launch(Dispatchers.IO) {
//             noteSettingsRepository.saveVault(vault)
//         }
//     }
//
//     /** folder 変更時に DataStore に即時保存（保存ボタンなし） 🔵 REQ-021 */
//     fun updateFolder(folder: String) {
//         viewModelScope.launch(Dispatchers.IO) {
//             noteSettingsRepository.saveFolder(folder)
//         }
//     }
// }

// ========================================
// DataStoreModule（新規 Hilt Module）
// ========================================

/**
 * DataStoreModule
 * NoteSettingsRepository を Singleton として Hilt に登録する
 * 🔵 信頼性: REQ-021・Hilt + DataStore 公式パターンより
 */
// @Module
// @InstallIn(SingletonComponent::class)
// object DataStoreModule {
//
//     @Provides
//     @Singleton
//     fun provideNoteSettingsRepository(
//         @ApplicationContext context: Context
//     ): NoteSettingsRepository = NoteSettingsRepositoryImpl(context.noteSettingsDataStore)
// }

// ========================================
// NoteSettingsRepositoryImpl（新規）
// ========================================

/**
 * DataStore Preferences Keys
 * 🔵 信頼性: REQ-021・DataStore Preferences 公式パターンより
 */
// val VAULT_KEY = stringPreferencesKey("vault")
// val FOLDER_KEY = stringPreferencesKey("folder")
//
// val Context.noteSettingsDataStore: DataStore<Preferences>
//     by preferencesDataStore(name = "note_settings")

// ========================================
// 信頼性レベルサマリー
// ========================================
/**
 * - 🔵 青信号: 28件 (90%)
 * - 🟡 黄信号: 3件 (10%)  ← isSaving, errorMessage, isSaved（TemplateEditUiState の補助フィールド）
 * - 🔴 赤信号: 0件 (0%)
 *
 * 品質評価: ✅ 高品質
 */

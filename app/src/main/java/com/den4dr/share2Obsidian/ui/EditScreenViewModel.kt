package com.den4dr.share2Obsidian.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.data.llm.LlmRewriteRepository
import com.den4dr.share2Obsidian.data.llm.LlmRewriteResult
import com.den4dr.share2Obsidian.data.llm.LlmSettingsRepository
import com.den4dr.share2Obsidian.domain.model.CustomFieldState
import com.den4dr.share2Obsidian.format.NoteConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 【機能概要】: 編集画面（EditScreen）のフォーム状態を管理する ViewModel
 * 【設計方針】: `StateFlow<EditFormState>` で UI 状態をイミュータブルに管理する。
 *              AndroidX ViewModel のライフサイクルにより、画面回転（Activity 再作成）時も
 *              ViewModel インスタンスが保持される。`initialized` フラグで重複初期化を防止する（EDGE-101）。
 * 【責務】:
 *   1. `initialize()` で ProcessedContent + NoteConfig からフォーム初期値を構築
 *   2. `updateXxx()` でユーザーのフォーム入力を StateFlow に反映
 *   3. `buildSendParams()` で送信時にフォーム状態を SendParams に変換（タグパース・タイトル null 変換）
 * 【依存関係】:
 *   - 依存先: EditFormState, SendParams, parseTagsText(), ProcessedContent, NoteConfig,
 *             LlmRewriteRepository, LlmSettingsRepository（TASK-0063 で Hilt コンストラクタ注入を追加）
 *   - 依存元: EditScreen Composable（TASK-0018, TASK-0065）
 * 【Hilt化】: TASK-0063 で `@HiltViewModel` 化し、`LlmRewriteRepository`・`LlmSettingsRepository` を
 *            コンストラクタ注入する。`MainActivity` の `by viewModels()` はそのまま動作する（Hilt互換）
 * 🔵 信頼性レベル: REQ-003, REQ-101, REQ-103, EDGE-101・interfaces.kt EditScreenViewModelSpec より
 * 🔵 信頼性レベル: Hilt化・rewriteBody() 部分は TASK-0063.md 実装詳細・design-interview.md Q2/Q3 より
 */
@HiltViewModel
class EditScreenViewModel @Inject constructor(
    private val llmRewriteRepository: LlmRewriteRepository,
    private val llmSettingsRepository: LlmSettingsRepository,
) : ViewModel() {

    // 【状態定義】: フォーム状態を保持する MutableStateFlow。外部には asStateFlow() でイミュータブルに公開する 🔵
    // 【初期値】: initialize() 呼び出し前のデフォルト値。すべて空文字列で初期化する 🟡
    private val _formState = MutableStateFlow(
        EditFormState(vault = "", title = "", body = "", tagsText = "", folder = "")
    )

    /**
     * 【プロパティ概要】: フォーム状態の公開 StateFlow（イミュータブル）
     * 🔵 信頼性レベル: REQ-003・EDGE-101 より
     */
    val formState: StateFlow<EditFormState> = _formState.asStateFlow()

    // 【イベント定義】: rewriteBody() 失敗時のエラー通知用 Hot Flow。EditScreen 側が一回限りのイベントとして購読する 🔵
    // 【設計方針】: MutableSharedFlow を private に隠蔽し、外部には asSharedFlow() でイミュータブルに公開する（design-interview.md Q3）
    // 【バッファ設計】: extraBufferCapacity=1 とし、emit() 時点で収集側の suspend receive と
    //              タイミングが完全一致しなくても emit() がサスペンドせず即座に配信できるようにする 🟡
    //              （バッファ0のままだと emit と collect のランデブーが完全に一致しないと値を取りこぼす恐れがあるため）
    private val _errorEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /**
     * 【プロパティ概要】: rewriteBody() 失敗時に、対応するエラーメッセージの string resource ID を発行する SharedFlow
     * 【テスト対応】: TC-0063-E01〜E05, B04
     * 🔵 信頼性レベル: design-interview.md Q3・interfaces.kt errorEvents 仕様より
     */
    val errorEvents: SharedFlow<Int> = _errorEvents.asSharedFlow()

    // 【重複初期化防止フラグ】: initialize() が2回以上呼ばれた場合に2回目以降を無視するためのフラグ 🔵
    // 【用途】: 画面回転時に Activity が再作成されても、ViewModel は生存し続けるため
    //           initialize() が再呼び出しされても既存の編集内容を上書きしない（EDGE-101）
    private var initialized = false

    /**
     * 【プロパティ概要】: LLM リライトの入力に使う、テンプレート適用前の元コンテンツ
     * 【設計方針】: `initialize()` の新規引数で受け取った値をそのまま `initialized` ガード配下で保持する
     *              （詳細は `initialize()` のドキュメント参照）。後続 TASK-0063 の `rewriteBody()` で
     *              LLM API への入力として使用される想定で、EditFormState には含めない（note.md 方針）
     * 【テスト対応】: TC-0062-N01, N03, B01, B02, B03
     * 🟡 信頼性レベル: testcases.md §0 方式A（観測点として ViewModel に保持プロパティを追加する方針）より
     */
    var sourceContent: String = ""
        private set

    /**
     * 【プロパティ概要】: デフォルトテンプレートに設定された本文用 LLM プロンプト
     * 【設計方針】: `sourceContent` と同様に `initialize()` の新規引数からそのまま保持する。
     *              `defaultTemplate` が null または `bodyLlmPrompt` 未設定の場合は空文字になる（REQ-102）
     * 【テスト対応】: TC-0062-N02, N03, E01, E02, B03
     * 🟡 信頼性レベル: testcases.md §0 方式A（観測点として ViewModel に保持プロパティを追加する方針）より
     */
    var bodyLlmPrompt: String = ""
        private set

    /**
     * 【機能概要】: ProcessedContent と NoteConfig からフォーム初期値をセットする
     * 【実装方針】: `initialized` フラグで重複呼び出しを防止し、初回のみ状態を更新する。
     *              2回目以降の呼び出し（画面回転時の Activity 再作成を想定）は何もせずに早期リターンする（EDGE-101）。
     *              `sourceContent`・`bodyLlmPrompt` も同じガードの対象とし、2回目以降は上書きしない
     * 【初期値マッピング】:
     *   - title   : `processed.title ?: ""`（null の場合は空文字）
     *   - body    : `processed.body`（そのまま使用）
     *   - tagsText: `config.defaultTags.joinToString(", ")`（List → カンマ+スペース区切り文字列）
     *   - folder  : `config.folder`（そのまま使用）
     * 🔵 信頼性レベル: REQ-001, REQ-003, REQ-405・acceptance-criteria.md TC-003-01〜04 より
     * 🟡 信頼性レベル: sourceContent/bodyLlmPrompt 引数・保持は requirements.md §2.2・testcases.md §0 より
     *
     * @param processed コンテンツ処理結果。`title` は nullable（共有元アプリがタイトルを提供しない場合は null）
     * @param config アプリ設定。`vault`・`folder`・`defaultTags` を含む（TASK-0015: NoteConfig）
     * @param sourceContent テンプレート適用前の元コンテンツ（`sourceContent` プロパティ参照）。省略時は空文字
     * @param bodyLlmPrompt テンプレートの本文用 LLM プロンプト（`bodyLlmPrompt` プロパティ参照）。省略時は空文字（REQ-102）
     */
    fun initialize(
        processed: ProcessedContent,
        config: NoteConfig,
        customFields: List<CustomFieldState> = emptyList(),
        sourceContent: String = "",
        bodyLlmPrompt: String = "",
    ) {
        // 【重複実行防止】: 画面回転時に initialize() が再度呼ばれても無視する（EDGE-101）🔵
        if (initialized) return

        // 【初期化フラグ更新】: 次回以降の呼び出しを無視するためにフラグを立てる 🔵
        initialized = true

        // 【新規プロパティ保持】: LLM リライト用の下地として、受け取った値をそのまま保持する 🟡
        this.sourceContent = sourceContent
        this.bodyLlmPrompt = bodyLlmPrompt

        // 【状態更新】: ProcessedContent と NoteConfig から EditFormState の初期値を構築して StateFlow に設定する 🔵
        _formState.value = EditFormState(
            // 【Vault 初期値】: NoteConfig.vault（DataStore 由来）で初期化する（REQ-061, REQ-022）🔵
            vault = config.vault,
            // 【タイトル初期値】: ProcessedContent.title が null の場合は空文字列で初期化する（TC-003-02）🔵
            title = processed.title ?: "",
            // 【本文初期値】: ProcessedContent.body をそのまま使用する（EDGE-002 空文字許容）🔵
            body = processed.body,
            // 【タグ初期値】: NoteConfig.defaultTags をカンマ+スペース区切り文字列に変換する（REQ-103）🔵
            tagsText = config.defaultTags.joinToString(", "),
            // 【フォルダ初期値】: NoteConfig.folder をそのまま使用する（REQ-405）🔵
            folder = config.folder,
            // 【カスタムフィールド初期値】: テンプレートから適用されたカスタムフィールド（REQ-052）🔵
            customFields = customFields,
            // 【ボタン活性判定初期値】: bodyLlmPrompt が非空の場合のみ「メモを更改」ボタンを活性化する（REQ-102）🔵
            rewriteBodyEnabled = bodyLlmPrompt.isNotBlank(),
        )
    }

    fun updateCustomField(index: Int, value: String) {
        val fields = _formState.value.customFields.toMutableList()
        if (index in fields.indices) {
            fields[index] = fields[index].copy(value = value)
            _formState.value = _formState.value.copy(customFields = fields)
        }
    }

    /**
     * 【機能概要】: フォーム状態のタイトルフィールドを更新する
     * 【実装方針】: `copy()` でタイトルのみを変更したイミュータブルな新しい状態を生成し、StateFlow に設定する。
     *              他のフィールド（body, tagsText, folder）には影響しない。
     * 【空文字許容】: 空文字列・スペースのみも有効な入力として許容（`buildSendParams()` で null 変換）
     * 🔵 信頼性レベル: REQ-003 より
     *
     * @param title 新しいタイトル文字列（空文字列・スペースのみも許容）
     */
    fun updateTitle(title: String) {
        // 【状態更新】: copy() でタイトルのみを変更した新しい状態を生成して StateFlow に設定する 🔵
        _formState.value = _formState.value.copy(title = title)
    }

    /**
     * 【機能概要】: フォーム状態の本文フィールドを更新する
     * 【実装方針】: `copy()` で本文のみを変更したイミュータブルな新しい状態を生成し、StateFlow に設定する。
     *              他のフィールド（title, tagsText, folder）には影響しない。
     * 【空文字許容】: 空ノートの作成を許容するため、空文字列も有効な入力として扱う（EDGE-002）
     * 🔵 信頼性レベル: REQ-003 より
     *
     * @param body 新しい本文文字列（空文字列も許容）
     */
    fun updateBody(body: String) {
        // 【状態更新】: copy() で本文のみを変更した新しい状態を生成して StateFlow に設定する 🔵
        _formState.value = _formState.value.copy(body = body)
    }

    /**
     * 【機能概要】: フォーム状態のタグテキストフィールドを更新する
     * 【実装方針】: `copy()` でタグテキストのみを変更したイミュータブルな新しい状態を生成し、StateFlow に設定する。
     *              他のフィールド（title, body, folder）には影響しない。
     * 【形式】: カンマ区切りのタグ文字列として保持する。`buildSendParams()` で `parseTagsText()` によりパースされる
     * 🔵 信頼性レベル: REQ-103 より
     *
     * @param tagsText 新しいタグ文字列（カンマ区切り形式。空文字列も許容）
     */
    fun updateTagsText(tagsText: String) {
        // 【状態更新】: copy() でタグテキストのみを変更した新しい状態を生成して StateFlow に設定する 🔵
        _formState.value = _formState.value.copy(tagsText = tagsText)
    }

    /**
     * 【機能概要】: フォーム状態のフォルダフィールドを更新する
     * 【実装方針】: `copy()` でフォルダのみを変更したイミュータブルな新しい状態を生成し、StateFlow に設定する。
     *              他のフィールド（title, body, tagsText）には影響しない。
     * 🔵 信頼性レベル: REQ-405 より
     *
     * @param folder 新しい保存先フォルダ文字列
     */
    fun updateFolder(folder: String) {
        // 【状態更新】: copy() でフォルダのみを変更した新しい状態を生成して StateFlow に設定する 🔵
        _formState.value = _formState.value.copy(folder = folder)
    }

    /**
     * 【機能概要】: フォーム状態の Vault フィールドを更新する（REQ-061）
     * 【実装方針】: `copy()` で vault のみを変更したイミュータブルな新しい状態を生成し、StateFlow に設定する。
     * 🔵 信頼性レベル: REQ-061・REQ-023 より
     *
     * @param vault 新しい Vault 名
     */
    fun updateVault(vault: String) {
        _formState.value = _formState.value.copy(vault = vault)
    }

    /**
     * 【機能概要】: LLM API を呼び出して本文（sourceContent）を書き換え、成功時は formState.body を即時上書きする
     * 【実装方針】: `viewModelScope.launch` で非同期実行する。入力はユーザーが編集した可能性のある
     *              `formState.body` ではなく、共有/取得直後の `sourceContent` を常に使用する（REQ-002, REQ-406）。
     *              `sourceContent` が空文字であってもガード（早期return）せず、そのまま LLM 呼び出しに渡す（EDGE-101）。
     *              ローディング切替・設定取得・成功/失敗分岐の共通処理は `runLlmRequest()` に集約する。
     * 【改善内容】: Refactorフェーズで `suggestTags()` と重複していたローディング管理・LLM呼び出しの枠組みを
     *              `runLlmRequest()` ヘルパーへ抽出した（振る舞いは変更なし、DRY原則適用）
     * 【テスト対応】: TC-0063-N01, N03, N04, E01〜E05, B02, B04
     * 🔵 信頼性レベル: TASK-0063.md 実装詳細5・REQ-002, REQ-003, REQ-201, REQ-406, EDGE-101 より
     */
    fun rewriteBody() {
        viewModelScope.launch {
            // 【共通処理呼び出し】: ローディング切替対象は isRewritingBody、成功時は formState.body を上書きする 🔵
            runLlmRequest(
                prompt = bodyLlmPrompt,
                setLoading = { loading -> _formState.update { it.copy(isRewritingBody = loading) } },
                onSuccess = { text -> _formState.update { it.copy(body = text) } },
            )
        }
    }

    /**
     * 【機能概要】: LLM API を呼び出して sourceContent からタグ候補を生成し、既存の tagsText に追加する（置換ではない）
     * 【実装方針】: `viewModelScope.launch` で非同期実行する。`rewriteBody()` と同一のローディング・エラー処理パターンを
     *              踏襲するが、入力プロンプトはテンプレート単位の `bodyLlmPrompt` ではなく、アプリ内固定の
     *              `TAG_SUGGESTION_PROMPT` を使用する（REQ-406 補足設計）。`sourceContent` が空文字であっても
     *              ガード（早期return）せず、そのまま LLM 呼び出しに渡す（EDGE-101）。
     * 【改善内容】: Refactorフェーズで `rewriteBody()` と重複していたローディング管理・LLM呼び出しの枠組みを
     *              `runLlmRequest()` ヘルパーへ抽出した（振る舞いは変更なし、DRY原則適用）
     * 【テスト対応】: TC-0068-N01, N02, N03, E01, B01, B02, B03
     * 🔵 信頼性レベル: TASK-0068.md 実装詳細2・4・5・6・REQ-201, REQ-301, REQ-302, REQ-406, EDGE-101 より
     */
    fun suggestTags() {
        viewModelScope.launch {
            // 【共通処理呼び出し】: ローディング切替対象は isSuggestingTags、成功時は既存 tagsText へ追加連結する 🔵
            runLlmRequest(
                prompt = TAG_SUGGESTION_PROMPT,
                setLoading = { loading -> _formState.update { it.copy(isSuggestingTags = loading) } },
                onSuccess = { text ->
                    // 【成功時マージ】: 既存 tagsText が空白なら生成結果のみ、そうでなければカンマ+スペース区切りで追加連結する（REQ-302）🔵
                    _formState.update { state ->
                        val current = state.tagsText
                        val merged = if (current.isBlank()) text else "$current, $text"
                        state.copy(tagsText = merged)
                    }
                },
            )
        }
    }

    /**
     * 【機能概要】: 指定インデックスのカスタムフィールド（`valueSource == FieldValueSource.LLM`）について、
     *              そのフィールドの `llmPrompt` と `sourceContent` を入力に LLM 呼び出しを行い、
     *              該当インデックスの `value` のみを応答テキストで更新する
     * 【実装方針】: `rewriteBody()`/`suggestTags()` と同様に入力は常に `sourceContent` を使用する
     *              （`formState.body` は使わない：REQ-002, REQ-406）。成功時は既存の `updateCustomField(index, value)`
     *              を再利用して対象インデックスのみを局所更新し、他インデックスの `CustomFieldState` には影響しない。
     *              ローディング状態は `isRewritingBody`/`isSuggestingTags`（Boolean）とは異なり、フィールド単位で
     *              対象を特定する必要があるため `EditFormState.generatingFieldIndex`（Int?）に対象 index を
     *              設定し、完了後に null へ戻す。
     * 【改善内容】: Refactorフェーズで `runLlmRequest()` を再利用する形に統一した。Boolean → Int? の変換は
     *              index を捕捉した `setLoading` クロージャ（true なら index、false なら null）で吸収する
     *              （振る舞いは変更なし、DRY原則適用）
     * 【テスト対応】: TC-0072-N01〜N04, E01〜E05, B01〜B04
     * 🔵 信頼性レベル: TASK-0072.md 実装詳細1・要件定義書 §2/§3 より（既存 rewriteBody/updateCustomField パターンの組合せ）
     *
     * @param index 生成対象カスタムフィールドの `formState.value.customFields` におけるインデックス（0始まり）
     */
    fun generateCustomFieldValue(index: Int) {
        viewModelScope.launch {
            // 【共通処理呼び出し】: 対象インデックスの llmPrompt を入力し、ローディングは generatingFieldIndex（Int?）へ
            // index/null を設定、成功時は既存 updateCustomField(index, value) で対象インデックスのみ更新する 🔵
            runLlmRequest(
                prompt = formState.value.customFields[index].llmPrompt,
                setLoading = { loading ->
                    _formState.update { it.copy(generatingFieldIndex = if (loading) index else null) }
                },
                onSuccess = { text -> updateCustomField(index, text) },
            )
        }
    }

    /**
     * 【ヘルパー関数】: `rewriteBody()` / `suggestTags()` / `generateCustomFieldValue()` に共通する
     *                  「ローディング開始 → 設定取得 → LLM呼び出し → 成功/失敗分岐 → ローディング終了」の
     *                  一連の流れを1箇所に集約する
     * 【再利用性】: `sourceContent` を入力として使う LLM 呼び出し処理であれば、プロンプトと成功時の
     *              状態反映方法（`onSuccess`）・ローディングフラグの切替方法（`setLoading`）だけを
     *              差し替えることで再利用できる（Int? 型の `generatingFieldIndex` も index を捕捉した
     *              クロージャで Boolean から変換して対応する）
     * 【単一責任】: 「LLM 呼び出しの往復とローディング状態管理」のみを担当し、各呼び出し元固有の
     *              状態更新ロジック（body 上書き／tagsText マージ／カスタムフィールド更新）は関与しない
     * 【設計方針】: 成功・失敗いずれの場合も必ずローディングを false に戻すため、`when` 分岐の後に
     *              `setLoading(false)` を1箇所だけ呼び出す構造にする（rewriteBody/suggestTags 双方の
     *              既存実装と同一の振る舞いを維持する）
     * 🔵 信頼性レベル: TASK-0063.md rewriteBody() 実装・TASK-0068.md suggestTags() 実装詳細より
     *              （両メソッドの既存ロジックをそのまま抽出したものであり、新規推測は含まない）
     *
     * @param prompt LLM に渡すプロンプト文字列（`bodyLlmPrompt`・`TAG_SUGGESTION_PROMPT`・フィールドの `llmPrompt`）
     * @param setLoading ローディング状態（true/false）を対応する `EditFormState` のフラグへ反映する関数
     * @param onSuccess 成功時の応答テキストを受け取り、呼び出し元固有の状態更新を行う関数
     */
    private suspend fun runLlmRequest(
        prompt: String,
        setLoading: (Boolean) -> Unit,
        onSuccess: (String) -> Unit,
    ) {
        // 【ローディング開始】: LLM 呼び出し開始を UI に伝える（REQ-201）🔵
        setLoading(true)

        // 【設定取得】: LLM API 接続設定（endpointUrl/apiKey/model）を取得する 🔵
        val settings = llmSettingsRepository.getSettings().first()

        // 【LLM呼び出し】: 入力は常に sourceContent を使用する（REQ-002, REQ-406）🔵
        // 【空文字ガード禁止】: sourceContent が空文字でも早期returnせずそのまま渡す（EDGE-101）🔵
        when (val result = llmRewriteRepository.rewrite(settings, prompt, sourceContent)) {
            is LlmRewriteResult.Success ->
                // 【成功時】: 呼び出し元固有の状態更新処理に応答テキストを渡す 🔵
                onSuccess(result.text)
            is LlmRewriteResult.Failure ->
                // 【失敗時】: 状態は変更せず、errorEvents に対応する messageResId を発行する（NFR-201）🔵
                _errorEvents.emit(result.messageResId)
        }

        // 【ローディング終了】: 成功・失敗いずれの場合もローディングを false に戻す（REQ-201）🔵
        setLoading(false)
    }

    /**
     * 【機能概要】: フォーム状態から送信パラメータ（SendParams）を構築して返す
     * 【実装方針】:
     *   - `title`: 空文字列・スペースのみの場合は `ifBlank { null }` で null に変換する（EDGE-001）
     *   - `body` : そのまま渡す（空文字列も許容: EDGE-002）
     *   - `tags` : カンマ区切りの `tagsText` を `parseTagsText()` で `List<String>` に変換する（REQ-103）
     *   - `config`: メソッド引数をそのまま SendParams に渡す（REQ-405）
     * 【保守性】: `config` をメソッド引数として受け取ることで、ViewModel が設定を保持せずステートレスな変換を実現
     * 🔵 信頼性レベル: REQ-101, REQ-103, EDGE-001, EDGE-002, EDGE-003・dataflow.md フロー3 より
     *
     * @param config 送信設定（`vault`・`folder`・`defaultTags` を含む NoteConfig）
     * @return タグパース・タイトル null 変換済みの送信パラメータ（`SendParams`）
     * @see parseTagsText タグ文字列のカンマ区切りパース処理（TASK-0016 実装）
     */
    fun buildSendParams(): SendParams {
        // 【現在の状態取得】: StateFlow から最新のフォーム状態を取り出す 🔵
        val state = _formState.value

        return SendParams(
            // 【タイトル変換】: 空文字列またはスペースのみのタイトルを null に変換する（EDGE-001）🔵
            title = state.title.ifBlank { null },
            // 【本文設定】: 本文はそのまま渡す（空文字列も許容、EDGE-002）🔵
            body = state.body,
            // 【タグパース】: カンマ区切りのタグ文字列を List<String> に変換する（REQ-103）🔵
            tags = parseTagsText(state.tagsText),
            // 【設定構築】: EditFormState の vault/folder から NoteConfig を構築する（REQ-062, REQ-024）🔵
            config = NoteConfig(
                vault = state.vault,
                folder = state.folder,
                defaultTags = emptyList(),
            ),
            // 【カスタムフィールド渡し】: フォーム状態のカスタムフィールドを SendParams に渡す（REQ-052）🔵
            customFields = state.customFields,
        )
    }

    companion object {
        // 【定数定義】: suggestTags() で使用するアプリ内固定プロンプト。テンプレート単位の bodyLlmPrompt とは異なり、
        //             テンプレートに依存しない統一文字列を使用する（REQ-406 補足設計）。
        // 【Context非依存】: 現行 EditScreenViewModel コンストラクタは Context を保持しないため、
        //             strings.xml の llm_tag_suggestion_prompt と同一内容の定数として直接保持する
        //             （note.md §6 技術的制約「アプリ内固定プロンプト」/ requirements.md §3 Context依存の懸念より）🟡
        private const val TAG_SUGGESTION_PROMPT: String =
            "以下の文章から関連するタグを3〜5個、カンマ区切りで提案してください"
    }
}

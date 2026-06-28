package com.den4dr.share2Obsidian.ui

import androidx.lifecycle.ViewModel
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.domain.model.CustomFieldState
import com.den4dr.share2Obsidian.format.NoteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 *   - 依存先: EditFormState, SendParams, parseTagsText(), ProcessedContent, NoteConfig
 *   - 依存元: EditScreen Composable（TASK-0018）
 * 🔵 信頼性レベル: REQ-003, REQ-101, REQ-103, EDGE-101・interfaces.kt EditScreenViewModelSpec より
 */
class EditScreenViewModel : ViewModel() {

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

    // 【重複初期化防止フラグ】: initialize() が2回以上呼ばれた場合に2回目以降を無視するためのフラグ 🔵
    // 【用途】: 画面回転時に Activity が再作成されても、ViewModel は生存し続けるため
    //           initialize() が再呼び出しされても既存の編集内容を上書きしない（EDGE-101）
    private var initialized = false

    /**
     * 【機能概要】: ProcessedContent と NoteConfig からフォーム初期値をセットする
     * 【実装方針】: `initialized` フラグで重複呼び出しを防止し、初回のみ `_formState` を更新する。
     *              2回目以降の呼び出し（画面回転時の Activity 再作成を想定）は何もせずに早期リターンする（EDGE-101）。
     * 【初期値マッピング】:
     *   - title   : `processed.title ?: ""`（null の場合は空文字）
     *   - body    : `processed.body`（そのまま使用）
     *   - tagsText: `config.defaultTags.joinToString(", ")`（List → カンマ+スペース区切り文字列）
     *   - folder  : `config.folder`（そのまま使用）
     * 🔵 信頼性レベル: REQ-001, REQ-003, REQ-405・acceptance-criteria.md TC-003-01〜04 より
     *
     * @param processed コンテンツ処理結果。`title` は nullable（共有元アプリがタイトルを提供しない場合は null）
     * @param config アプリ設定。`vault`・`folder`・`defaultTags` を含む（TASK-0015: NoteConfig）
     */
    fun initialize(
        processed: ProcessedContent,
        config: NoteConfig,
        customFields: List<CustomFieldState> = emptyList(),
    ) {
        // 【重複実行防止】: 画面回転時に initialize() が再度呼ばれても無視する（EDGE-101）🔵
        if (initialized) return

        // 【初期化フラグ更新】: 次回以降の呼び出しを無視するためにフラグを立てる 🔵
        initialized = true

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
}

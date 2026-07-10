package com.den4dr.share2Obsidian

import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.data.datastore.NoteSettings
import com.den4dr.share2Obsidian.domain.model.CustomFieldState
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.format.NoteConfig

object TemplateApplicator {

    /**
     * DataStore 由来の [NoteSettings] から [NoteConfig] を構築する。
     * テンプレートからは vault/folder を取得しない（REQ-031）。
     */
    fun buildConfig(settings: NoteSettings): NoteConfig = NoteConfig(
        vault = settings.vault,
        folder = settings.folder,
        defaultTags = AppConfig.OBSIDIAN_TAGS,
    )

    /**
     * テンプレートの本文（body）に含まれる `{{content}}` プレースホルダーを共有コンテンツで解決する。
     *
     * - body が空または null の場合: 共有コンテンツをそのまま返す（REQ-013, REQ-014）
     * - `{{content}}` を含む場合: すべて共有コンテンツで置換する（REQ-012, EDGE-001）
     * - `{{content}}` を含まない非空 body の場合: body のみを返す（EDGE-002）
     */
    fun buildBody(template: Template?, sharedBody: String): String {
        val templateBody = template?.body ?: ""
        return if (templateBody.isEmpty()) sharedBody
        else templateBody.replace("{{content}}", sharedBody)
    }

    /**
     * 【機能概要】: テンプレートのカスタムフィールド定義（TemplateField）を、EditScreenの編集状態
     * （CustomFieldState）へ変換する
     * 【実装方針】: FIXED/HTML_META/URL/EMPTY の既存 value 算出ロジックは変更せず、CustomFieldState
     * 生成時に valueSource/llmPrompt を新たに渡すことで、LLM生成ボタンの表示判定に必要な情報を橋渡しする
     * 【テスト対応】: TC-0070-N01〜N03, E01〜E02, B01〜B03（テンプレート適用時のLLM対応・回帰確認）
     * 🔵 信頼性レベル: 要件定義書 2-2 値算出テーブル・REQ-304 に基づく（推測なし）
     * @param template カスタムフィールド定義を持つテンプレート（null可）
     * @param processed 共有コンテンツの処理結果
     * @return 各 TemplateField を変換した編集状態のリスト（template が null の場合は空リスト）
     */
    fun buildCustomFields(
        template: Template?,
        processed: ProcessedContent,
    ): List<CustomFieldState> = template?.fields?.map { field ->
        // 【値算出】: valueSource ごとに value を算出する（既存ロジックを維持） 🔵
        val value = when (field.valueSource) {
            FieldValueSource.FIXED -> field.defaultValue
            FieldValueSource.HTML_META -> processed.metadata[field.metaKey] ?: ""
            FieldValueSource.URL -> processed.sourceUrl ?: ""
            FieldValueSource.EMPTY -> ""
            // 【LLM生成は本関数では行わない】: テンプレート適用時点ではLLM呼び出しを行わず、値は空文字のまま
            // EditScreen上のボタン押下時（TASK-0072/0073）に生成する（REQ-304の設計判断）
            // 🔵 信頼性レベル: 要件定義書 制約条件・design-interview.md Q2 に基づく（推測なし）
            FieldValueSource.LLM -> ""
        }
        // 【CustomFieldState生成】: valueSource/llmPrompt を渡すことで、EditScreen側でLLM生成ボタンの
        // 表示・活性判定が可能になる 🔵
        CustomFieldState(field.key, value, field.valueType, field.valueSource, field.llmPrompt)
    } ?: emptyList()
}

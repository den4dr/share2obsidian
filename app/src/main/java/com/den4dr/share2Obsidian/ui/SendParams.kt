package com.den4dr.share2Obsidian.ui

import com.den4dr.share2Obsidian.format.NoteConfig

/**
 * 【機能概要】: 送信ボタンタップ時に ViewModel が返す送信用パラメータのデータクラス
 * 【設計方針】: EditFormState から変換されたパラメータを NoteComposer へ渡すための中間データ構造。
 *              title のみ nullable（空文字列 → null 変換済み）とし、NoteComposer 側で
 *              タイトルあり/なしの Frontmatter 生成を分岐できるようにする（EDGE-001）。
 * 【保守性】: data class により equals/hashCode/copy/toString が自動生成される。
 *             テストコードでの期待値比較（assertEquals）に equals が利用される。
 * 🔵 信頼性レベル: REQ-101・interfaces.kt の SendParams 定義・dataflow.md の送信フローより
 *
 * @param title 編集後のタイトル（空文字の場合は null に変換済み、EDGE-001）
 * @param body 編集後の本文（空文字列を許容、EDGE-002）
 * @param tags parseTagsText() 適用済みのタグリスト（空リストを許容、EDGE-003）
 * @param config 送信設定（vault, folder, defaultTags を含む NoteConfig）
 */
data class SendParams(
    // 【フィールド定義】: 編集後のタイトル。タイトルなし送信の場合は null（EDGE-001）🔵
    val title: String?,
    // 【フィールド定義】: 編集後の本文。空ノート送信の場合は空文字列 ""（EDGE-002）🔵
    val body: String,
    // 【フィールド定義】: parseTagsText() 適用済みのタグリスト。タグなし送信は emptyList()（EDGE-003）🔵
    val tags: List<String>,
    // 【フィールド定義】: 送信設定（vault/folder/defaultTags）。NoteComposer への依存渡し用（REQ-405）🔵
    val config: NoteConfig,
    // 【フィールド定義】: テンプレートから適用されたカスタムフィールド一覧（REQ-052）🔵
    val customFields: List<com.den4dr.share2Obsidian.domain.model.CustomFieldState> = emptyList(),
)

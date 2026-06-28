package com.den4dr.share2Obsidian.ui

/**
 * 【ファイル概要】: 編集フォームの状態データクラスとタグパース関数を定義するファイル
 * 【役割】: EditScreenViewModel が StateFlow で管理する UI 状態（EditFormState）と、
 *           タグフィールドのカンマ区切り文字列をリストに変換するユーティリティ（parseTagsText）を提供する
 * 🔵 信頼性レベル: REQ-003・REQ-103・interfaces.kt の定義より
 */

/**
 * 【機能概要】: 編集フォームの状態を保持するデータクラス
 * 【設計方針】: Compose UI の StateFlow で管理される不変データとして定義する。
 *              4フィールドはすべて String 型（non-nullable）とし、
 *              title の null は呼び出し元で ?: "" に変換してから渡す（EDGE-001）。
 * 【保守性】: data class により equals/hashCode/copy/toString が自動生成される。
 *             StateFlow の値比較（Recomposition トリガー）に equals が利用される。
 * 🔵 信頼性レベル: REQ-003・interfaces.kt の EditFormState 定義・note.md の型定義より
 *
 * @param title タイトルフィールドの現在値（ProcessedContent.title ?: "" で初期化）
 * @param body 本文フィールドの現在値（ProcessedContent.body で初期化）
 * @param tagsText タグフィールドの現在値（カンマ区切り、config.defaultTags.joinToString(", ") で初期化）
 * @param folder フォルダフィールドの現在値（config.folder で初期化）
 */
data class EditFormState(
    // 【フィールド定義】: Vault フィールドの現在値。config.vault（DataStore 由来）で初期化する（REQ-061）🔵
    val vault: String = "",
    // 【フィールド定義】: タイトルフィールドの現在値。null は空文字列 "" として扱う（EDGE-001）🔵
    val title: String,
    // 【フィールド定義】: 本文フィールドの現在値。空文字列を許容する（EDGE-002）🔵
    val body: String,
    // 【フィールド定義】: タグフィールドの現在値。カンマ区切り文字列として保持（REQ-103）🔵
    val tagsText: String,
    // 【フィールド定義】: フォルダフィールドの現在値。config.folder 由来（REQ-405）🔵
    val folder: String,
    // 【フィールド定義】: テンプレートから適用されたカスタムフィールド一覧（REQ-052）🔵
    val customFields: List<com.den4dr.share2Obsidian.domain.model.CustomFieldState> = emptyList(),
)

/**
 * 【機能概要】: カンマ区切りのタグ文字列を List<String> に変換するユーティリティ関数
 * 【設計方針】: split → trim → filter の3ステップで純粋に変換する。
 *              外部依存なし・副作用なしの純粋関数として実装し、任意の呼び出し元から再利用可能にする。
 * 【パフォーマンス】: O(n)。ユーザーが入力するタグ文字列は通常数十文字程度のため、
 *                     中間リスト生成が2回あっても実用上問題なし（NFR-001: 100ms 制限を大幅に下回る）。
 * 【保守性】: EditFormState.kt と同ファイルに配置することで、
 *             タグ文字列（tagsText フィールド）と変換ロジックの近接性を保持する。
 * 🔵 信頼性レベル: REQ-103・interfaces.kt の parseTagsText 仕様・note.md のタグパース仕様より
 *
 * @param tagsText カンマ区切りのタグ文字列（空文字列も許容。例: "shared, web, clipping"）
 * @return トリム済みの非空タグリスト（例: ["shared", "web", "clipping"]）。入力が空の場合は emptyList()
 */
fun parseTagsText(tagsText: String): List<String> {
    // 【カンマ分割】: カンマ(",") を区切り文字として文字列を分割する 🔵
    // 【トリム処理】: 各要素の前後空白を trim() で除去する（REQ-103: スペース付き入力の正規化）🔵
    // 【空文字フィルタ】: trim() 後に空文字列になった要素を除去する（EDGE-003: カンマのみ/先頭末尾カンマへの対応）🔵
    return tagsText
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

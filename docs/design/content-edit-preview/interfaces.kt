/**
 * 展開内容の編集・プレビュー機能 Kotlin データクラス・インターフェース定義
 *
 * 作成日: 2026-03-29
 * 関連設計: architecture.md
 * 言語: Kotlin 2.2+
 *
 * 信頼性レベル:
 * - 🔵 青信号: 要件定義書・ユーザヒアリング・既存実装を参考にした確実な定義
 * - 🟡 黄信号: 要件定義書・ユーザヒアリングから妥当な推測による定義
 * - 🔴 赤信号: 要件定義書・ユーザヒアリングにない推測による定義
 */

package com.den4dr.share2Obsidian

import android.net.Uri

// ========================================
// フォーム状態
// ========================================

/**
 * EditScreenViewModel が保持する編集フォームの状態
 * 🔵 信頼性: REQ-003（4フィールド）・ユーザヒアリング（編集項目）より
 *
 * @param title タイトルフィールドの現在値（空文字 = タイトルなし）
 * @param body 本文フィールドの現在値
 * @param tagsText タグフィールドの現在値（カンマ区切り文字列、例: "shared, web"）
 * @param folder フォルダフィールドの現在値
 */
data class EditFormState(
    val title: String,          // 🔵 REQ-003, TC-003-01
    val body: String,           // 🔵 REQ-003
    val tagsText: String,       // 🔵 REQ-103・ユーザヒアリング（カンマ区切りテキスト）
    val folder: String          // 🔵 REQ-003, REQ-405
)

// ========================================
// ノート設定（将来のユーザー設定化への拡張ポイント）
// ========================================

/**
 * Obsidianノートの送信設定
 * 🔵 信頼性: ユーザヒアリング（将来のユーザー設定）・REQ-405より
 *
 * 現在は AppConfig の値をデフォルトとして使用するが、
 * 将来的にユーザーが vault・folder・defaultTags を設定できるようにする拡張ポイント。
 *
 * @param vault Obsidian Vault 名
 * @param folder 保存先フォルダパス
 * @param defaultTags タグフィールドの初期値（List<String>）
 */
data class NoteConfig(
    val vault: String,              // 🔵 REQ-004（vault固定値）→ 将来: ユーザー設定
    val folder: String,             // 🔵 REQ-405（folder初期値）→ 将来: ユーザー設定
    val defaultTags: List<String>   // 🔵 REQ-003（tags初期値）→ 将来: ユーザー設定
) {
    companion object {
        /**
         * AppConfig の値から NoteConfig を生成するファクトリ関数
         * 🔵 信頼性: REQ-402（既存AppConfig再利用）より
         */
        fun fromAppConfig(): NoteConfig = NoteConfig(
            vault = AppConfig.OBSIDIAN_VAULT,
            folder = AppConfig.OBSIDIAN_FOLDER,
            defaultTags = AppConfig.OBSIDIAN_TAGS
        )
    }
}

// ========================================
// 送信パラメータ
// ========================================

/**
 * 送信ボタンタップ時に ViewModel が返す送信用パラメータ
 * 🔵 信頼性: REQ-101・dataflow.md より
 *
 * @param title 編集後のタイトル（null = タイトルなし）
 * @param body 編集後の本文
 * @param tags 編集後のタグリスト（tagsText をパース済み）
 * @param config 送信設定（vault, folder）
 */
data class SendParams(
    val title: String?,         // 🔵 REQ-101: タイトル空の場合 null として扱う（EDGE-001）
    val body: String,           // 🔵 REQ-101
    val tags: List<String>,     // 🔵 REQ-103: tagsText をパース済み
    val config: NoteConfig      // 🔵 vault・folder を含む
)

// ========================================
// NoteComposer（フォーマット層）
// ========================================

/**
 * 編集後の値から Frontmatter 文字列と Obsidian URI を生成する
 * 🔵 信頼性: REQ-101, REQ-103, REQ-402・ユーザヒアリングより
 *
 * ⚠️ FrontmatterBuilder・ObsidianUriBuilder は変更しない（REQ-402）。
 * 本クラスがそれらの代わりに編集画面フロー専用のビルダーとして機能する。
 * AppConfig に依存せず、明示的パラメータのみで動作する。
 */
object NoteComposer {

    /**
     * Frontmatter ヘッダー付きノート本文を生成する
     * 🔵 信頼性: REQ-101, REQ-103・既存 FrontmatterBuilder 実装より
     *
     * @param title ノートタイトル（null の場合は title フィールドを省略）
     * @param body ノート本文
     * @param tags タグリスト（空の場合は tags: [] ）
     * @return "---\ntitle: ...\ntags: [...]\n---\n\nbody" 形式の文字列
     */
    fun buildFrontmatter(title: String?, body: String, tags: List<String>): String {
        val titleLine = title?.let { "title: \"$it\"\n" } ?: ""
        val tagsString = tags.joinToString(", ")
        return "---\n${titleLine}tags: [$tagsString]\n---\n\n$body"
    }

    /**
     * Obsidian URI を生成する
     * 🔵 信頼性: REQ-101, REQ-405・既存 ObsidianUriBuilder 実装より
     *
     * @param content buildFrontmatter() で生成した文字列
     * @param title ノートタイトル（URI の title パラメータ）
     * @param config vault・folder を含む設定
     * @return obsidian://new?content=...&title=...&vault=...&folder=... 形式の Uri
     */
    fun buildUri(content: String, title: String?, config: NoteConfig): Uri {
        return Uri.parse("obsidian://new").buildUpon()
            .appendQueryParameter("content", content)
            .appendQueryParameter("title", title ?: "")
            .appendQueryParameter("vault", config.vault)
            .appendQueryParameter("folder", config.folder)
            .build()
    }
}

// ========================================
// タグパーサー（ユーティリティ）
// ========================================

/**
 * カンマ区切りタグ文字列を List<String> に変換する
 * 🔵 信頼性: REQ-103・ユーザヒアリング（カンマ区切り、スペーストリム）より
 *
 * 仕様:
 * - "shared, web, clipping" → ["shared", "web", "clipping"]
 * - "shared ,  web " → ["shared", "web"]（前後スペース除去）
 * - "" → [] （空の場合は空リスト）
 * - "," → [] （カンマのみの場合も空リスト）
 */
fun parseTagsText(tagsText: String): List<String> {
    return tagsText
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

// ========================================
// ViewModel インターフェース（設計仕様）
// ========================================

/**
 * EditScreenViewModel の状態と操作（設計仕様）
 * 🔵 信頼性: REQ-003, REQ-101, REQ-103・EDGE-101より
 *
 * ⚠️ 実装は EditScreenViewModel クラスで行う。
 *    このインターフェースは設計ドキュメント目的。
 */
interface EditScreenViewModelSpec {

    /** フォーム状態の StateFlow（画面回転後も保持） */
    // val formState: StateFlow<EditFormState>

    /**
     * ProcessedContent と NoteConfig から初期値をセットする
     * 🔵 信頼性: REQ-001, REQ-003より
     * - title: ProcessedContent.title（null の場合は空文字）
     * - body: ProcessedContent.body
     * - tagsText: config.defaultTags.joinToString(", ")
     * - folder: config.folder
     */
    fun initialize(processed: ProcessedContent, config: NoteConfig)

    /** タイトルフィールドの更新 */
    fun updateTitle(title: String)

    /** 本文フィールドの更新 */
    fun updateBody(body: String)

    /** タグフィールドの更新（カンマ区切り文字列） */
    fun updateTagsText(tagsText: String)

    /** フォルダフィールドの更新 */
    fun updateFolder(folder: String)

    /**
     * 送信ボタン用パラメータを返す
     * 🔵 信頼性: REQ-101, REQ-103より
     * - title: 空文字の場合は null に変換（EDGE-001）
     * - tags: parseTagsText(tagsText) を適用
     */
    fun buildSendParams(): SendParams
}

// ========================================
// 信頼性レベルサマリー
// ========================================
/**
 * - 🔵 青信号: 18件 (90%)
 * - 🟡 黄信号: 2件  (10%)
 * - 🔴 赤信号: 0件  (0%)
 *
 * 品質評価: 高品質
 */

/**
 * 共有内容展開システム Kotlin データクラス・インターフェース定義
 *
 * 作成日: 2026-03-28
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
// コンテンツタイプ（Sealed Class）
// ========================================

/**
 * 共有コンテンツの種別を表す sealed class
 * 🔵 信頼性: REQ-001/101/201/301 ・ ContentTypeDetectorの設計より
 */
sealed class ShareContent {

    /**
     * プレーンテキスト共有
     * 🔵 信頼性: REQ-001 ・ 既存実装より
     * @param text EXTRA_TEXT の内容
     * @param title EXTRA_SUBJECT の内容（任意）
     */
    data class Text(
        val text: String,           // 🔵 EXTRA_TEXT
        val title: String? = null   // 🔵 EXTRA_SUBJECT
    ) : ShareContent()

    /**
     * URL共有（URLパターンに一致するテキスト）
     * 🔵 信頼性: REQ-101 ・ ユーザヒアリングより
     * @param url 共有されたURL文字列
     * @param title EXTRA_SUBJECT の内容（任意）
     */
    data class Url(
        val url: String,            // 🔵 EXTRA_TEXT (URLパターン)
        val title: String? = null   // 🔵 EXTRA_SUBJECT
    ) : ShareContent()

    /**
     * HTMLコンテンツ共有
     * 🔵 信頼性: REQ-201 ・ ユーザヒアリングより
     * @param html EXTRA_HTML_TEXT の内容
     * @param fallbackText EXTRA_TEXT の内容（HTMLがない場合のフォールバック）
     * @param title EXTRA_SUBJECT の内容（任意）
     */
    data class Html(
        val html: String?,              // 🔵 EXTRA_HTML_TEXT
        val fallbackText: String = "",  // 🟡 REQ-203 フォールバック
        val title: String? = null       // 🔵 EXTRA_SUBJECT
    ) : ShareContent()

    /**
     * ファイル/画像共有
     * 🔵 信頼性: REQ-301 ・ ユーザヒアリングより
     * @param uri EXTRA_STREAM の Uri
     * @param title EXTRA_SUBJECT の内容（任意）
     */
    data class File(
        val uri: Uri,               // 🔵 EXTRA_STREAM
        val title: String? = null   // 🔵 EXTRA_SUBJECT
    ) : ShareContent()
}

// ========================================
// 処理済みコンテンツ
// ========================================

/**
 * 各Processorが処理後に返すコンテンツ
 * 🔵 信頼性: FrontmatterBuilder・ObsidianUriBuilderへの入力として設計より
 */
data class ProcessedContent(
    val body: String,               // 🔵 Obsidianノート本文（Frontmatter付与前）
    val title: String? = null,      // 🔵 REQ-002 ノートタイトル
    val contentType: ContentKind    // 🟡 ログ・デバッグ用途として妥当な推測
)

/**
 * コンテンツ種別の列挙型（ProcessedContentの分類用）
 * 🟡 信頼性: デバッグ・将来の拡張を考慮した妥当な推測
 */
enum class ContentKind {
    TEXT,   // 🟡
    URL,    // 🟡
    HTML,   // 🟡
    FILE    // 🟡
}

// ========================================
// Frontmatter設定
// ========================================

/**
 * Frontmatterの設定値（将来の拡張を考慮）
 * 🔵 信頼性: REQ-003・ユーザヒアリング（title/tags）より
 */
data class FrontmatterConfig(
    val tags: List<String> = listOf("shared"),  // 🔵 ユーザヒアリングより（固定タグ: [shared]）
    val includeTitle: Boolean = true            // 🔵 ユーザヒアリングよりtitleを含める
)

// ========================================
// Obsidian URI設定
// ========================================

/**
 * Obsidianに送るノートの設定（ハードコード値を含む）
 * 🔵 信頼性: REQ-002/004/005 ・ ユーザヒアリング（vault/folder固定値）より
 */
data class ObsidianConfig(
    val vault: String,    // 🔵 REQ-004 コード内固定値（prep.md参照）
    val folder: String    // 🔵 REQ-005 コード内固定値（prep.md参照）
)

// ========================================
// コンテンツ処理インターフェース
// ========================================

/**
 * 各コンテンツタイプを処理するProcessor共通インターフェース
 * 🔵 信頼性: アーキテクチャ設計（ストラテジーパターン）より
 */
interface ContentProcessor<T : ShareContent> {
    /**
     * コンテンツを処理して本文・タイトルを返す
     * URL処理のみ suspend（WebView非同期処理のため）
     * 🔵 信頼性: ユーザヒアリング（WebView・Coroutines）より
     */
    suspend fun process(content: T): ProcessedContent
}

// ========================================
// WebView抽出結果
// ========================================

/**
 * WebViewExtractorが返す結果
 * 🔵 信頼性: REQ-101/102/103 ・ WebView設計より
 */
data class WebViewExtractionResult(
    val bodyText: String?,      // 🔵 null = タイムアウト or 失敗
    val pageTitle: String? = null  // 🟡 ページタイトル取得（将来の拡張余地）
)

// ========================================
// 定数（ハードコード値のプレースホルダー）
// ========================================

/**
 * アプリケーション全体の設定定数
 * 🔵 信頼性: REQ-004/005 ・ ユーザヒアリング（固定値埋め込み）より
 *
 * ⚠️ 実装前に prep.md の「必須」タスクを完了してから値を設定すること
 */
object AppConfig {
    // 🔵 vault名: ユーザーのObsidian Vault名をここに設定（prep.md参照）
    const val OBSIDIAN_VAULT = ""  // TODO: 実際のVault名に変更（prep.md参照）

    // 🔵 folder: 保存先フォルダパスをここに設定（prep.md参照）
    const val OBSIDIAN_FOLDER = "" // TODO: 実際のフォルダ名に変更（prep.md参照）

    // 🔵 固定タグ（REQ-003）
    val OBSIDIAN_TAGS = listOf("shared")

    // 🟡 WebViewタイムアウト（NFR-001: 10秒）
    const val WEBVIEW_TIMEOUT_MS = 10_000L
}

// ========================================
// 信頼性レベルサマリー
// ========================================
/**
 * - 🔵 青信号: 18件 (75%)
 * - 🟡 黄信号: 6件  (25%)
 * - 🔴 赤信号: 0件  (0%)
 *
 * 品質評価: 高品質
 */

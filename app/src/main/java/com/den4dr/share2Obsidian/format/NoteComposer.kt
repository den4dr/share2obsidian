package com.den4dr.share2Obsidian.format

import android.net.Uri

/**
 * 【機能概要】: 編集後の値（タイトル・本文・タグ・設定）から Frontmatter 文字列と Obsidian URI を生成するオブジェクト
 * 【実装方針】:
 *   - AppConfig を一切インポートしない（AppConfig 非依存設計・REQ-402）
 *   - 既存の FrontmatterBuilder / ObsidianUriBuilder は変更しない（REQ-402）
 *   - すべてのパラメータは関数引数として明示的に渡す
 * 【テスト対応】: TC-001〜TC-015 全テストケースを通すための実装
 * 🔵 信頼性レベル: interfaces.kt NoteComposer 定義・REQ-101, REQ-103, REQ-402, REQ-405 より
 */
object NoteComposer {

    /**
     * 【機能概要】: タイトル・本文・タグから Frontmatter 付きノート文字列を生成する
     * 【実装方針】: FrontmatterBuilder.build() と同等の出力形式を維持しながら、タグを引数で受け取る
     * 【テスト対応】:
     *   - TC-001: タイトルあり・複数タグ
     *   - TC-002: タイトル null 時の title フィールド省略
     *   - TC-003: 複数タグのカンマ+スペース区切り
     *   - TC-007: 空本文でもクラッシュしない
     *   - TC-008: タイトル null + 空本文
     *   - TC-009: ダブルクォートを含むタイトル（エスケープなし・既存動作互換）
     *   - TC-010: 空タグリスト → tags: []
     *   - TC-011: 単一タグ
     *   - TC-013: 複数行本文の改行保持
     *   - TC-015: FrontmatterBuilder.build() との出力互換性
     * 🔵 信頼性レベル: REQ-101, REQ-103・FrontmatterBuilder 実装より
     *
     * @param title ノートタイトル（null の場合は title フィールド省略）
     * @param body ノート本文（空文字列可）
     * @param tags タグリスト（空リスト可）
     * @returns String Frontmatter ヘッダー付きノート本文
     */
    fun buildFrontmatter(body: String, tags: List<String>): String {
        val tagsString = tags.joinToString(", ")
        return "---\ntags: [$tagsString]\n---\n\n$body"
    }

    /**
     * 【機能概要】: Frontmatter 付きコンテンツ・タイトル・設定から Obsidian URI を生成する
     * 【実装方針】: ObsidianUriBuilder.build() と同等の URI 構造を生成するが、vault/folder を AppConfig ではなく NoteConfig から取得する
     * 【テスト対応】:
     *   - TC-004: scheme・host・クエリパラメータの基本構造検証
     *   - TC-006: title=null 時に URI の title パラメータが空文字
     *   - TC-014: NoteConfig の vault/folder が URI に正しく反映される
     * 🔵 信頼性レベル: REQ-101, REQ-405・ObsidianUriBuilder 実装より
     *
     * @param content buildFrontmatter() の出力（Frontmatter 付きノート本文）
     * @param title ノートタイトル（null の場合は空文字として URI に設定）
     * @param config vault・folder を含む NoteConfig
     * @returns Uri Obsidian URI（scheme="obsidian", host="new"）
     */
    fun buildUri(content: String, title: String?, config: NoteConfig): Uri {
        // 【URI 構築開始】: obsidian://new をベースに URI を構築する 🔵
        // REQ-101: 送信ボタンタップ時に編集後の値から URI を構築し Obsidian を起動する
        return Uri.parse("obsidian://new").buildUpon()
            // 【content パラメータ】: Frontmatter 付きノート本文を URL エンコードして設定 🔵
            .appendQueryParameter("content", content)
            // 【title パラメータ】: title が null の場合は空文字列を設定（TC-006） 🔵
            .appendQueryParameter("title", title ?: "")
            // 【vault パラメータ】: NoteConfig の vault 値を設定（AppConfig 非依存） 🔵
            .appendQueryParameter("vault", config.vault)
            // 【folder パラメータ】: NoteConfig の folder 値を設定（スラッシュ含む場合も appendQueryParameter が適切にエンコード） 🔵
            .appendQueryParameter("folder", config.folder)
            .build()
    }
}

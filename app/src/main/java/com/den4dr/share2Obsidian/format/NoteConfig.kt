package com.den4dr.share2Obsidian.format

import com.den4dr.share2Obsidian.AppConfig

/**
 * 【機能概要】: Obsidian ノート送信の設定を保持するデータクラス
 * 【実装方針】: AppConfig に依存せず明示的パラメータで動作し、将来のユーザー設定化への拡張ポイントとなる
 * 【テスト対応】: TC-005（fromAppConfig の正常動作）、TC-012（data class 等価性）を通すための実装
 * 🔵 信頼性レベル: interfaces.kt NoteConfig 定義・REQ-405 より
 *
 * @param vault Obsidian Vault 名
 * @param folder 保存先フォルダパス
 * @param defaultTags タグフィールドの初期値
 */
data class NoteConfig(
    // 【フィールド定義】: Obsidian Vault 名。UI で変更された値をそのまま保持する 🔵
    val vault: String,
    // 【フィールド定義】: 保存先フォルダパス。サブフォルダ（スラッシュ含む）も許容する 🔵
    val folder: String,
    // 【フィールド定義】: タグフィールドの初期値。空リスト可 🔵
    val defaultTags: List<String>
) {
    companion object {
        /**
         * 【機能概要】: AppConfig の定数値から NoteConfig を生成するファクトリメソッド
         * 【実装方針】: AppConfig を参照できるのはこのメソッドのみ（NoteComposer は AppConfig 非依存）
         * 【テスト対応】: TC-005（fromAppConfig が AppConfig の値を正確に読み込む）を通すための実装
         * 🔵 信頼性レベル: REQ-405・AppConfig.kt の実装値に基づく
         * @returns NoteConfig AppConfig の値をマッピングした NoteConfig インスタンス
         */
        fun fromAppConfig(): NoteConfig {
            // 【AppConfig 参照】: ファクトリメソッド内でのみ AppConfig を参照する 🔵
            return NoteConfig(
                vault = AppConfig.OBSIDIAN_VAULT,       // 【vault 設定】: AppConfig.OBSIDIAN_VAULT = "testVault" を使用 🔵
                folder = AppConfig.OBSIDIAN_FOLDER,     // 【folder 設定】: AppConfig.OBSIDIAN_FOLDER = "70_clippings" を使用 🔵
                defaultTags = AppConfig.OBSIDIAN_TAGS   // 【tags 設定】: AppConfig.OBSIDIAN_TAGS = listOf("shared") を使用 🔵
            )
        }
    }
}

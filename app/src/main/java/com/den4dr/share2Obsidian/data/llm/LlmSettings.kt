package com.den4dr.share2Obsidian.data.llm

/**
 * 【機能概要】: LLM API接続設定（endpointUrl / apiKey / model）を保持するドメインモデル
 * 【実装方針】: interfaces.kt・requirements.md の型定義をそのまま反映した data class として実装
 * 【テスト対応】: LlmSettingsTest（TC-03: デフォルト値・構造的等価性）を通すための実装
 * 🔵 信頼性レベル: interfaces.kt 行93-97・requirements.md 2.1・TASK-0058.md より、ほぼ推測なし
 *
 * @property endpointUrl OpenAI互換 Chat Completions エンドポイント（DataStore保存）
 * @property apiKey LLM APIキー（EncryptedSharedPreferencesへの暗号化保存対象、機微情報）
 * @property model 使用モデル名（DataStore保存）
 */
data class LlmSettings(
    // 【変数定義】: 未設定状態を "" で表現する（null を使わず安全に扱う） 🔵
    val endpointUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)

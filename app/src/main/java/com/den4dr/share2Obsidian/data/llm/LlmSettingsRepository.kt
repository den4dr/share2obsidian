package com.den4dr.share2Obsidian.data.llm

import kotlinx.coroutines.flow.Flow

/**
 * 【機能概要】: LLM API接続設定の読み書きインターフェース
 * 【実装方針】: 既存 NoteSettingsRepository と同様のリポジトリパターンを踏襲しつつ、
 *              apiKey のみ暗号化ストレージ（EncryptedSharedPreferences）に保存先を分離する
 * 【テスト対応】: LlmSettingsRepositoryImplTest / LlmSettingsRepositoryImplInteractionTest 全般が
 *              本インターフェースの実装（LlmSettingsRepositoryImpl）を対象とする
 * 🔵 信頼性レベル: interfaces.kt 行104-117・requirements.md 2.2 より、ほぼ推測なし
 */
interface LlmSettingsRepository {

    /** 【機能概要】: DataStore（endpointUrl/model）と EncryptedSharedPreferences（apiKey）双方の変更を検知して発行する 🔵 */
    fun getSettings(): Flow<LlmSettings>

    /** 【機能概要】: endpointUrl を DataStore に保存する 🔵 */
    suspend fun saveEndpointUrl(url: String)

    /** 【機能概要】: apiKey を EncryptedSharedPreferences に保存する（REQ-401: 平文DataStore禁止） 🔵 */
    suspend fun saveApiKey(apiKey: String)

    /** 【機能概要】: model を DataStore に保存する 🔵 */
    suspend fun saveModel(model: String)
}

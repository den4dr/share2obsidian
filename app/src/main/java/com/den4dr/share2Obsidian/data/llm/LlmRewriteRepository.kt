package com.den4dr.share2Obsidian.data.llm

/**
 * 【機能概要】: ユーザーが設定した外部LLM API（OpenAI互換 Chat Completions形式）を呼び出し、
 *              プロンプトと元コンテンツから書き換え/生成テキストを取得するリポジトリのインターフェース
 * 【設計方針】: 上位層（EditScreenViewModel）がHTTP通信の詳細・エラーマッピングを意識せずに
 *              結果（成功/失敗）のみを扱えるよう抽象化する
 * 【テスト対応】: LlmRewriteRepositoryImplTest.kt / LlmRewriteRepositoryIntegrationTest.kt
 * 🔵 信頼性レベル: interfaces.kt L158-168・requirements.md 2.1 に基づく（推測なし）
 */
interface LlmRewriteRepository {

    /**
     * 【機能概要】: 指定した接続設定でLLM APIを呼び出し、書き換え/生成結果を取得する
     * 【実装方針】: suspend fun とし、Kotlin Coroutines上での非同期実行を前提とする
     * 🔵 信頼性レベル: interfaces.kt L158-168・requirements.md 2.1〜2.5 に基づく
     *
     * @param settings LLM API接続設定（endpointUrl / apiKey / model）
     * @param prompt system ロールに載せるプロンプト
     * @param content user ロールに載せる元コンテンツ（検証・加工せずそのまま送信する。EDGE-101）
     * @return 成功時は [LlmRewriteResult.Success]、失敗時は種別ごとの [LlmRewriteResult.Failure]
     */
    suspend fun rewrite(settings: LlmSettings, prompt: String, content: String): LlmRewriteResult
}

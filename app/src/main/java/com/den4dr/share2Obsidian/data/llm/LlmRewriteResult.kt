package com.den4dr.share2Obsidian.data.llm

/**
 * 【機能概要】: LLM呼び出し（メモ本文の書き換え等）の結果を表す sealed class
 * 【設計方針】: 成功時はテキストを保持する [Success]、失敗時は種別ごとに表示用 messageResId を
 *   保持する [Failure] サブクラスを定義する。sealed class にすることで when 式が exhaustive
 *   （分岐漏れをコンパイル時に検出可能）になる。
 * 【保守性】: 新たな失敗種別が必要になった場合は Failure のサブクラスを追加するだけでよく、
 *   既存の exhaustive な when 式は未対応の分岐をコンパイルエラーとして検出できる。
 * 【テスト対応】: LlmRewriteResultTest.kt の TC-N-04〜06, TC-B-04〜05 を通すための実装
 * 🔵 信頼性レベル: interfaces.kt「LLM 呼び出し結果（新規）」・requirements.md 2.1 に基づく（推測なし）
 */
sealed class LlmRewriteResult {

    /**
     * 【機能概要】: LLM呼び出しが成功したことを表し、書き換え/生成されたテキストを保持する
     * 【テスト対応】: TC-N-04（text保持）, TC-B-04（空文字保持）, TC-B-05（等価性）
     * 🔵 信頼性レベル: interfaces.kt 130行・REQ-003 に基づく
     *
     * @property text LLMによる書き換え/生成後のテキスト（REQ-003）。空文字も許容する（TC-B-04）
     */
    data class Success(val text: String) : LlmRewriteResult()

    /**
     * 【機能概要】: LLM呼び出しが失敗したことを表す sealed class
     * 【設計方針】: 全ての失敗種別が表示用 string resource ID を共通契約として持つよう
     *   abstract val messageResId を宣言し、各サブクラスで override する
     * 【テスト対応】: TC-N-05（各サブクラスのmessageResId保持）, TC-N-06（exhaustive when）
     * 🔵 信頼性レベル: interfaces.kt 133-149行・note.md 243-247行に基づく
     *
     * @property messageResId エラー表示用 string resource ID（`R.string.*`）。具体的な値の
     *   割当はTASK-0060（`LlmRewriteRepositoryImpl` の例外マッピング）で行う
     */
    sealed class Failure : LlmRewriteResult() {
        abstract val messageResId: Int

        /** 【失敗種別】: ネットワーク未接続・接続エラー（EDGE-001） 🔵 */
        data class NetworkError(override val messageResId: Int) : Failure()

        /** 【失敗種別】: APIキー不正・認証エラー（HTTP 401/403）（EDGE-002） 🔵 */
        data class AuthError(override val messageResId: Int) : Failure()

        /** 【失敗種別】: 30秒タイムアウト（EDGE-003, NFR-001） 🔵 */
        data class Timeout(override val messageResId: Int) : Failure()

        /** 【失敗種別】: 空応答・パース不能なレスポンス（EDGE-004） 🟡 */
        data class EmptyOrInvalidResponse(override val messageResId: Int) : Failure()

        /** 【失敗種別】: 上記以外の予期しないエラー 🟡 一般的なエラーハンドリング方針からの推測 */
        data class Unknown(override val messageResId: Int) : Failure()
    }
}

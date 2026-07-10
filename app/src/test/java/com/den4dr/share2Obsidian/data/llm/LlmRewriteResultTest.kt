package com.den4dr.share2Obsidian.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LlmRewriteResult] sealed class（Success / Failure の各サブクラス）を検証するユニットテスト（TASK-0059）。
 *
 * 【テスト対象】: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResult.kt`（未実装）
 * このファイルは Red フェーズの時点では LlmRewriteResult が存在しないためコンパイルに失敗する。
 */
class LlmRewriteResultTest {

    // TC-N-04: LlmRewriteResult.Success が保持するテキストを取得できる
    @Test
    fun `Success は生成テキストをtextプロパティで保持する`() {
        // 【テスト目的】: 成功結果型がテキストを正しく保持することを確認する
        // 【テスト内容】: LlmRewriteResult.Success(text) を生成し、text プロパティで元の文字列を取得できることを検証する
        // 【期待される動作】: コンストラクタで渡した文字列がそのまま保持される
        // 🔵 信頼性レベル: interfaces.kt 130行・requirements.md 2.1・REQ-003 に基づく（推測なし）

        // 【テストデータ準備】: REQ-003「応答テキストを書き換え結果として使用」を代表する成功結果を用意する
        // 【初期条件設定】: 特になし（コンストラクタ引数のみ）
        val result: LlmRewriteResult = LlmRewriteResult.Success(text = "書き換え結果")

        // 【実際の処理実行】: text プロパティ・サブタイプ判定を行う
        // 【処理内容】: data class のプロパティ読み出しと is 演算子による型チェック
        // 【結果検証】: text が正しく保持され、LlmRewriteResult のサブタイプであること
        assertTrue(result is LlmRewriteResult.Success) // 【確認内容】: LlmRewriteResult のサブタイプであること 🔵
        assertEquals("書き換え結果", (result as LlmRewriteResult.Success).text) // 【確認内容】: text が正しく保持されること 🔵
    }

    // TC-N-05: 各 Failure サブクラスが messageResId を保持する
    @Test
    fun `各Failureサブクラスがコンストラクタで渡したmessageResIdを保持する`() {
        // 【テスト目的】: 全失敗種別が定義され、messageResId を保持することを網羅的に確認する
        // 【テスト内容】: NetworkError/AuthError/Timeout/EmptyOrInvalidResponse/Unknown の5種にダミーIDを渡す
        // 【期待される動作】: 各インスタンスの messageResId がそれぞれ渡した値と一致する
        // 🔵 信頼性レベル: interfaces.kt 133-149行・requirements.md 2.1・EDGE-001〜004 に基づく
        //   （EmptyOrInvalidResponse/Unknown の位置づけ自体は🟡だが、型の存在確認としては🔵）

        // 【テストデータ準備】: 各失敗種別（EDGE-001〜004 + Unknown）を代表するダミーの string resource ID を用意する
        // 【初期条件設定】: 実際のR.string.*値の割当はTASK-0060の範囲だが、Intを保持できることを本タスクで確認する
        val networkError = LlmRewriteResult.Failure.NetworkError(messageResId = 1001)
        val authError = LlmRewriteResult.Failure.AuthError(messageResId = 1002)
        val timeout = LlmRewriteResult.Failure.Timeout(messageResId = 1003)
        val emptyOrInvalid = LlmRewriteResult.Failure.EmptyOrInvalidResponse(messageResId = 1004)
        val unknown = LlmRewriteResult.Failure.Unknown(messageResId = 1005)

        // 【実際の処理実行】: 各インスタンスの messageResId プロパティへアクセスする
        // 【処理内容】: abstract val messageResId を override した各サブクラスの値を読み出す
        // 【結果検証】: 5サブクラスすべてで格納した値が正しく取得できること
        assertEquals(1001, networkError.messageResId) // 【確認内容】: NetworkError が messageResId を保持すること（EDGE-001） 🔵
        assertEquals(1002, authError.messageResId) // 【確認内容】: AuthError が messageResId を保持すること（EDGE-002） 🔵
        assertEquals(1003, timeout.messageResId) // 【確認内容】: Timeout が messageResId を保持すること（EDGE-003） 🔵
        assertEquals(1004, emptyOrInvalid.messageResId) // 【確認内容】: EmptyOrInvalidResponse が messageResId を保持すること（EDGE-004） 🟡
        assertEquals(1005, unknown.messageResId) // 【確認内容】: Unknown が messageResId を保持すること 🟡

        // 【結果検証】: Failure 経由（アップキャスト後）でも messageResId にアクセス可能であること
        // 【期待値確認】: いずれも LlmRewriteResult.Failure（および LlmRewriteResult）のサブタイプであること
        val asFailure: LlmRewriteResult.Failure = timeout
        assertEquals(1003, asFailure.messageResId) // 【確認内容】: Failure型へのアップキャスト後もmessageResIdが取得できること 🔵
        assertTrue(networkError is LlmRewriteResult) // 【確認内容】: NetworkError が LlmRewriteResult のサブタイプであること 🔵
    }

    // TC-N-06: LlmRewriteResult を when で exhaustive に分岐できる
    @Test
    fun `LlmRewriteResultをSuccessと各Failureで網羅的にwhen分岐できる`() {
        // 【テスト目的】: sealed class 構造が exhaustive な分岐を可能にするアーキテクチャ制約の充足を確認する
        // 【テスト内容】: Success / Failure.Timeout の2ケースを else なしの when 式へ通す
        // 【期待される動作】: 与えた具体型に対応する分岐が選択され、期待した値が返る（elseなしでコンパイルが通る）
        // 🔵 信頼性レベル: note.md 243-247行・requirements.md 3章 アーキテクチャ制約に基づく（推測なし）

        // 【テストデータ準備】: 成功系と失敗系の代表インスタンスを用意する
        // 【初期条件設定】: sealed 階層の分岐が機能することを代表的に確認するための2値
        val success: LlmRewriteResult = LlmRewriteResult.Success("ok")
        val failure: LlmRewriteResult = LlmRewriteResult.Failure.Timeout(2003)

        // 【実際の処理実行】: else 節なしの when 式で分岐する
        // 【処理内容】: sealed class の exhaustive チェックにより全 Failure サブクラスを列挙する
        fun describe(result: LlmRewriteResult): String = when (result) {
            is LlmRewriteResult.Success -> result.text
            is LlmRewriteResult.Failure.NetworkError -> "NetworkError:${result.messageResId}"
            is LlmRewriteResult.Failure.AuthError -> "AuthError:${result.messageResId}"
            is LlmRewriteResult.Failure.Timeout -> "Timeout:${result.messageResId}"
            is LlmRewriteResult.Failure.EmptyOrInvalidResponse -> "EmptyOrInvalidResponse:${result.messageResId}"
            is LlmRewriteResult.Failure.Unknown -> "Unknown:${result.messageResId}"
        }

        // 【結果検証】: 各分岐で期待した値が取り出せること
        // 【期待値確認】: elseなしでコンパイルが通ること自体がexhaustive性の証明となる
        assertEquals("ok", describe(success)) // 【確認内容】: Success分岐でtextが取り出せること 🔵
        assertEquals("Timeout:2003", describe(failure)) // 【確認内容】: Failure.Timeout分岐でmessageResIdが取り出せること 🔵
    }

    // TC-B-04: LlmRewriteResult.Success が空文字テキストを保持できる
    @Test
    fun `Successは空文字テキストでも生成でき保持できる`() {
        // 【テスト目的】: 成功結果型の文字列境界（空文字）の確認を行う
        // 【テスト内容】: LlmRewriteResult.Success("") を生成するケース
        // 【期待される動作】: 空文字でもSuccessとして生成・保持でき、text == "" を返す
        // 🟡 信頼性レベル: interfaces.kt 130行の型定義からの妥当な推測（空文字の明示要件はない）

        // 【テストデータ準備】: 型定義段階での境界確認用に空文字を用意する
        // 【初期条件設定】: 実際の空応答分岐（EmptyOrInvalidResponseへの振り分け）はTASK-0060の範囲
        val result = LlmRewriteResult.Success(text = "")

        // 【実際の処理実行】: text プロパティ・サブタイプ判定を行う
        // 【処理内容】: data class のプロパティ読み出しと is 演算子による型チェック
        // 【結果検証】: 非空文字（TC-N-04）と空文字（本ケース）で保持挙動が一貫すること
        assertEquals("", result.text) // 【確認内容】: 空文字がそのまま保持されること 🟡
        assertTrue(result is LlmRewriteResult.Success) // 【確認内容】: 空文字でも型が破綻しないこと 🟡
    }

    // TC-B-05: 同一内容の Success / Failure は data class として等価になる
    @Test
    fun `同一値のSuccessFailure同士はequalsで等しく種別や値が異なれば等しくない`() {
        // 【テスト目的】: data class 生成による構造的等価性の確認を行う
        // 【テスト内容】: 種別違い（同一messageResId）と値違いの両側からequals境界を突く
        // 【期待される動作】: 値が同じなら別インスタンスでも==がtrue、種別または値が異なればfalse
        // 🟡 信頼性レベル: Kotlin data class 標準仕様からの妥当な推測（要件に等価性の明記はないがnote.mdでdata class採用が確定）

        // 【テストデータ準備】: 同一値・異種別・異なる値の組み合わせを用意する
        // 【初期条件設定】: 後続タスク・テストで結果比較を行う際の基盤性質を検証する
        val successA1 = LlmRewriteResult.Success("a")
        val successA2 = LlmRewriteResult.Success("a")
        val successB = LlmRewriteResult.Success("b")
        val networkError1 = LlmRewriteResult.Failure.NetworkError(1)
        val networkError2 = LlmRewriteResult.Failure.NetworkError(1)
        val authError1 = LlmRewriteResult.Failure.AuthError(1)

        // 【実際の処理実行】: equals（==）による比較を行う
        // 【処理内容】: data class の自動生成されたequals()を呼び出す
        // 【結果検証】: 種別と値の双方を考慮した同値判定が行われること
        assertEquals(successA1, successA2) // 【確認内容】: 同一textのSuccess同士は等価であること 🟡
        assertEquals(networkError1, networkError2) // 【確認内容】: 同一messageResIdの同種Failure同士は等価であること 🟡
        assertFalse(networkError1 == authError1) // 【確認内容】: 同一messageResIdでも種別が異なれば等価でないこと 🟡
        assertFalse(successA1 == successB) // 【確認内容】: 値が異なれば等価でないこと 🟡
    }
}

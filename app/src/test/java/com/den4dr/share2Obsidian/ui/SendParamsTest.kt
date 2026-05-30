package com.den4dr.share2Obsidian.ui

import com.den4dr.share2Obsidian.format.NoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SendParams データクラス 単体テスト
 *
 * テスト対象: com.den4dr.share2Obsidian.ui.SendParams
 * テストケース: TC-016-007〜008, TC-016-012〜013, TC-016-019
 *
 * 実行: mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.SendParamsTest"
 *
 * 注意: SendParams は純粋 Kotlin data class のため Robolectric 不要。通常 JUnit テストで実行可能。
 */
class SendParamsTest {

    // ================================================================
    // テスト共通ヘルパー
    // ================================================================

    /**
     * テスト用の基本 NoteConfig を生成するヘルパー
     * 【テストデータ準備用】: 各テストで共通する NoteConfig の生成を集約
     */
    private fun defaultConfig() = NoteConfig(
        vault = "testVault",
        folder = "70_clippings",
        defaultTags = listOf("shared")
    )

    // ================================================================
    // 1. 正常系テストケース
    // ================================================================

    /**
     * TC-016-007: SendParams の全フィールド指定でのインスタンス生成
     * 🔵 青信号: REQ-101・interfaces.kt の SendParams 定義に基づく
     */
    @Test
    fun `TC-016-007 SendParams の全フィールド指定でインスタンスが正しく生成される`() {
        // 【テスト目的】: SendParams データクラスが title, body, tags, config の4フィールドを正しく保持すること
        // 【テスト内容】: 送信ボタンタップ時に ViewModel が生成する典型的なパラメータを確認
        // 【期待される動作】: コンストラクタに渡した値がそのまま各プロパティから取得できる
        // 🔵 信頼性レベル: REQ-101・interfaces.kt の SendParams 定義に直接基づく

        // 【テストデータ準備】: 送信時の典型的なパラメータ。ユーザーが編集後に送信ボタンをタップした場合
        // 【初期条件設定】: タイトル・本文・タグ・設定をすべて指定した状態
        val title = "テスト"
        val body = "本文"
        val tags = listOf("shared", "web")
        val config = defaultConfig()

        // 【実際の処理実行】: SendParams コンストラクタを呼び出す
        // 【処理内容】: 4フィールドを持つデータクラスのインスタンスを生成する
        val sendParams = SendParams(
            title = title,
            body = body,
            tags = tags,
            config = config
        )

        // 【結果検証】: 各フィールドの値を確認
        // 【期待値確認】: data class は不変フィールドを保持するため、コンストラクタの値がそのまま返される
        assertEquals(title, sendParams.title) // 【確認内容】: title フィールドが入力値と一致すること 🔵
        assertEquals(body, sendParams.body) // 【確認内容】: body フィールドが入力値と一致すること 🔵
        assertEquals(tags, sendParams.tags) // 【確認内容】: tags フィールドが入力値と一致すること 🔵
        assertEquals(config, sendParams.config) // 【確認内容】: config フィールドが入力値と一致すること 🔵
        assertEquals(2, sendParams.tags.size) // 【確認内容】: tags のサイズが 2 であること 🔵
    }

    /**
     * TC-016-008: SendParams.title が null のケース
     * 🔵 青信号: EDGE-001・interfaces.kt の `title: String?` 定義に基づく
     */
    @Test
    fun `TC-016-008 SendParams の title が null でインスタンスが正しく生成される`() {
        // 【テスト目的】: title=null で SendParams を生成できること（EDGE-001 のタイトルなし送信）
        // 【テスト内容】: ユーザーがタイトルを空にして送信した場合（空文字 → null 変換後）を確認
        // 【期待される動作】: title が null のまま保持される
        // 🔵 信頼性レベル: EDGE-001・interfaces.kt の `title: String?` 定義に直接基づく

        // 【テストデータ準備】: タイトルなし送信のパターン。EditFormState.title が "" → null 変換後
        // 【初期条件設定】: EDGE-001 - タイトルフィールドが空の状態で送信
        val config = defaultConfig()

        // 【実際の処理実行】: title=null で SendParams を生成
        // 【処理内容】: nullable String? フィールドに null を渡してデータクラスを生成
        val sendParams = SendParams(
            title = null,
            body = "本文",
            tags = listOf("shared"),
            config = config
        )

        // 【結果検証】: title が null であり、他のフィールドは正常値であることを確認
        // 【期待値確認】: EDGE-001 仕様「タイトル空の場合 null で送信」に準拠
        assertNull(sendParams.title) // 【確認内容】: title が null であること 🔵
        assertEquals("本文", sendParams.body) // 【確認内容】: body は正常値であること 🔵
        assertEquals(listOf("shared"), sendParams.tags) // 【確認内容】: tags は正常値であること 🔵
        assertEquals(config, sendParams.config) // 【確認内容】: config は正常値であること 🔵
    }

    // ================================================================
    // 2. 異常系テストケース
    // ================================================================

    /**
     * TC-016-012: SendParams.body が空文字列でも正常にインスタンスが生成される
     * 🟡 黄信号: EDGE-002 から妥当な推測
     */
    @Test
    fun `TC-016-012 SendParams の body が空文字列でも正常にインスタンスが生成される`() {
        // 【テスト目的】: body="" で SendParams を生成できること（EDGE-002 の空ノート送信）
        // 【テスト内容】: ユーザーが本文をすべて削除して送信した場合の処理を確認
        // 【期待される動作】: body が空文字列 "" のまま保持され、例外が発生しない
        // 🟡 信頼性レベル: EDGE-002 から妥当な推測（要件定義で「本文空許容」と記載あり）

        // 【テストデータ準備】: 空本文での送信パターン。ユーザーが共有テキストを編集画面で全削除
        // 【初期条件設定】: EDGE-002 - 本文フィールドが空の状態で送信
        val config = defaultConfig()

        // 【実際の処理実行】: body="" で SendParams を生成
        // 【処理内容】: 空文字列の body を持つデータクラスを生成する
        val sendParams = SendParams(
            title = "タイトル",
            body = "",
            tags = listOf("shared"),
            config = config
        )

        // 【結果検証】: body が空文字列 "" であることを確認
        // 【期待値確認】: EDGE-002「本文空の場合、空ノートとして送信」に準拠
        assertEquals("", sendParams.body) // 【確認内容】: body が空文字列 "" であること 🟡
        assertEquals("タイトル", sendParams.title) // 【確認内容】: title は正常値であること 🟡
        assertEquals(listOf("shared"), sendParams.tags) // 【確認内容】: tags は正常値であること 🟡
    }

    /**
     * TC-016-013: SendParams.tags が空リストでも正常にインスタンスが生成される
     * 🟡 黄信号: EDGE-003 から妥当な推測
     */
    @Test
    fun `TC-016-013 SendParams の tags が空リストでも正常にインスタンスが生成される`() {
        // 【テスト目的】: tags=emptyList() で SendParams を生成できること（EDGE-003 のタグなし送信）
        // 【テスト内容】: ユーザーがタグをすべて削除して送信した場合の処理を確認
        // 【期待される動作】: tags が空リストのまま保持され、例外が発生しない
        // 🟡 信頼性レベル: EDGE-003 から妥当な推測（要件定義で「タグ空許容」と記載あり）

        // 【テストデータ準備】: タグなし送信のパターン。parseTagsText("") → [] の後
        // 【初期条件設定】: EDGE-003 - タグフィールドが空の状態で送信
        val config = defaultConfig()

        // 【実際の処理実行】: tags=emptyList() で SendParams を生成
        // 【処理内容】: 空リストの tags を持つデータクラスを生成する
        val sendParams = SendParams(
            title = "タイトル",
            body = "本文",
            tags = emptyList(),
            config = config
        )

        // 【結果検証】: tags が空リストであることを確認
        // 【期待値確認】: EDGE-003「タグ空の場合、空リストとして NoteComposer に渡す」に準拠
        assertTrue(sendParams.tags.isEmpty()) // 【確認内容】: tags が空リストであること 🟡
        assertEquals(0, sendParams.tags.size) // 【確認内容】: tags のサイズが 0 であること 🟡
        assertEquals(emptyList<String>(), sendParams.tags) // 【確認内容】: 空リストと等価であること 🟡
    }

    // ================================================================
    // 3. 境界値テストケース
    // ================================================================

    /**
     * TC-016-019: 同一パラメータの SendParams インスタンスが等価と判定される
     * 🟡 黄信号: Kotlin data class の標準動作からの妥当な推測
     */
    @Test
    fun `TC-016-019 同一パラメータの SendParams インスタンスが equals で等価と判定される`() {
        // 【テスト目的】: data class としての構造的等価性（equals/hashCode）が正しく動作すること
        // 【テスト内容】: NoteConfig を含む入れ子 data class でも等価性が成立することを確認
        // 【期待される動作】: 同一パラメータなら equals が true、hashCode が一致する
        // 🟡 信頼性レベル: Kotlin data class の標準動作。テストコードでの期待値比較に重要

        // 【テストデータ準備】: 同一パラメータで2インスタンスを作成
        // 【初期条件設定】: NoteConfig を含む完全に同一のパラメータセット
        val config1 = NoteConfig(vault = "testVault", folder = "70_clippings", defaultTags = listOf("shared"))
        val config2 = NoteConfig(vault = "testVault", folder = "70_clippings", defaultTags = listOf("shared"))

        val sendParams1 = SendParams(
            title = "タイトル",
            body = "本文",
            tags = listOf("shared", "web"),
            config = config1
        )
        val sendParams2 = SendParams(
            title = "タイトル",
            body = "本文",
            tags = listOf("shared", "web"),
            config = config2
        )

        // 【実際の処理実行】: equals() と hashCode() を評価
        // 【処理内容】: Kotlin data class が自動生成する equals/hashCode を利用（入れ子 data class 含む）
        val isEqual = sendParams1 == sendParams2
        val hashCodesMatch = sendParams1.hashCode() == sendParams2.hashCode()

        // 【結果検証】: data class の等価性（入れ子 NoteConfig を含む）を確認
        // 【期待値確認】: Kotlin data class の構造的等価性が入れ子でも保証される
        assertTrue(isEqual) // 【確認内容】: 同一パラメータの SendParams インスタンスが等価であること 🟡
        assertTrue(hashCodesMatch) // 【確認内容】: hashCode が一致すること（NoteConfig を含む計算） 🟡
        assertEquals(sendParams1, sendParams2) // 【確認内容】: assertEquals でも等価であること 🟡
    }
}

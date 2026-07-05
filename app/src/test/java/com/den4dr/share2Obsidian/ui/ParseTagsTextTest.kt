package com.den4dr.share2Obsidian.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * parseTagsText() 関数 単体テスト
 *
 * テスト対象: com.den4dr.share2Obsidian.ui.parseTagsText
 * テストケース: TC-016-004〜006, TC-016-009〜011, TC-016-014〜016
 *
 * 実行: mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.ParseTagsTextTest"
 *
 * 注意: parseTagsText は純粋 Kotlin 関数のため Robolectric 不要。通常 JUnit テストで実行可能。
 */
class ParseTagsTextTest {

    // ================================================================
    // 1. 正常系テストケース
    // ================================================================

    /**
     * TC-016-004: カンマ区切りの複数タグが正しくパースされる
     * 🔵 青信号: REQ-103・TC-101-02 に基づく
     */
    @Test
    fun `TC-016-004 カンマ区切りの複数タグが正しくパースされる`() {
        // 【テスト目的】: parseTagsText() が "shared, web, clipping" を ["shared", "web", "clipping"] に正しく変換すること
        // 【テスト内容】: 典型的なユーザー入力（スペース付きカンマ区切り3タグ）のパース処理を確認
        // 【期待される動作】: カンマで分割後、各要素がトリムされて3要素のリストに格納される
        // 🔵 信頼性レベル: REQ-103 の入出力仕様・TC-101-02 に直接基づく

        // 【テストデータ準備】: REQ-103 の例示値そのまま。ユーザーが共有テキストのタグフィールドに入力する典型値
        // 【初期条件設定】: スペース付きカンマ区切りの3タグ文字列
        val input = "shared, web, clipping"

        // 【実際の処理実行】: parseTagsText() を呼び出す
        // 【処理内容】: カンマで split → trim → 空文字フィルタリングを行う
        val result = parseTagsText(input)

        // 【結果検証】: リストの内容と順序を確認
        // 【期待値確認】: REQ-103 の入出力仕様「"shared, web, clipping" → ["shared", "web", "clipping"]」に準拠
        assertEquals(3, result.size) // 【確認内容】: リストのサイズが 3 であること 🔵
        assertEquals("shared", result[0]) // 【確認内容】: 1番目の要素が "shared" であること 🔵
        assertEquals("web", result[1]) // 【確認内容】: 2番目の要素が "web" であること 🔵
        assertEquals("clipping", result[2]) // 【確認内容】: 3番目の要素が "clipping" であること 🔵
        assertEquals(listOf("shared", "web", "clipping"), result) // 【確認内容】: リスト全体が期待値と完全一致すること 🔵
    }

    /**
     * TC-016-005: 前後スペースを含むタグが正しくトリムされる
     * 🔵 青信号: REQ-103・TC-101-03 に基づく
     */
    @Test
    fun `TC-016-005 前後スペースを含むタグが正しくトリムされる`() {
        // 【テスト目的】: parseTagsText() が各タグの前後空白を trim() で除去すること
        // 【テスト内容】: ユーザーが不揃いにスペースを入力した場合のトリム処理を確認
        // 【期待される動作】: 各タグの前後空白が除去され、純粋なタグ文字列のみがリストに入る
        // 🔵 信頼性レベル: REQ-103・TC-101-03 に直接基づく

        // 【テストデータ準備】: TC-101-03 の例示値。スペースが不揃いなカンマ区切りタグ
        // 【初期条件設定】: 前後に不揃いのスペースを含む3タグ文字列
        val input = "shared ,  web , clipping "

        // 【実際の処理実行】: 不揃いスペース付き入力で parseTagsText() を呼び出す
        // 【処理内容】: split(",") 後に各要素を trim() して前後空白を除去
        val result = parseTagsText(input)

        // 【結果検証】: トリム後の各タグを確認
        // 【期待値確認】: REQ-103「trim() による前後空白除去」仕様に準拠
        assertEquals(3, result.size) // 【確認内容】: リストのサイズが 3 であること 🔵
        assertEquals("shared", result[0]) // 【確認内容】: 1番目の要素がトリム済み "shared" であること 🔵
        assertEquals("web", result[1]) // 【確認内容】: 2番目の要素がトリム済み "web" であること 🔵
        assertEquals("clipping", result[2]) // 【確認内容】: 3番目の要素がトリム済み "clipping" であること 🔵
        assertTrue(result.none { it.startsWith(" ") || it.endsWith(" ") }) // 【確認内容】: 全タグが前後スペースを持たないこと 🔵
    }

    /**
     * TC-016-006: 単一タグがリスト化される
     * 🔵 青信号: REQ-103 通常ケースに基づく
     */
    @Test
    fun `TC-016-006 単一タグがサイズ1のリストに変換される`() {
        // 【テスト目的】: parseTagsText() がカンマのない単一タグ入力を1要素リストに変換すること
        // 【テスト内容】: デフォルトタグが1つのみ設定されている場合の処理を確認
        // 【期待される動作】: カンマなしの入力がサイズ 1 のリストに変換される
        // 🔵 信頼性レベル: REQ-103 の通常ケースに直接基づく

        // 【テストデータ準備】: カンマを含まない単一タグ文字列
        // 【初期条件設定】: "shared" の1タグのみの状態
        val input = "shared"

        // 【実際の処理実行】: カンマなし入力で parseTagsText() を呼び出す
        // 【処理内容】: split(",") が1要素のリストを返し、filter で空文字以外が残る
        val result = parseTagsText(input)

        // 【結果検証】: 単一要素リストへの変換を確認
        // 【期待値確認】: カンマなし入力でも正しくリスト化される
        assertEquals(1, result.size) // 【確認内容】: リストのサイズが 1 であること 🔵
        assertEquals("shared", result[0]) // 【確認内容】: 唯一の要素が "shared" であること 🔵
        assertEquals(listOf("shared"), result) // 【確認内容】: リスト全体が ["shared"] と一致すること 🔵
    }

    // ================================================================
    // 2. 異常系テストケース
    // ================================================================

    /**
     * TC-016-009: 空文字列入力で空リストが返される
     * 🟡 黄信号: EDGE-003 から妥当な推測
     */
    @Test
    fun `TC-016-009 空文字列入力で空リストが返される`() {
        // 【テスト目的】: parseTagsText("") が空リストを返し、例外が発生しないこと
        // 【テスト内容】: ユーザーがタグフィールドを完全に空にした場合の処理を確認
        // 【期待される動作】: 空文字列入力が安全に空リストとして処理される（クラッシュなし）
        // 🟡 信頼性レベル: EDGE-003 から妥当な推測（要件定義で「空文字列も許容」と記載あり）

        // 【テストデータ準備】: 空文字列。タグフィールドを全削除した状態
        // 【初期条件設定】: EDGE-003 - タグなしで送信する状態
        val input = ""

        // 【実際の処理実行】: 空文字列で parseTagsText() を呼び出す
        // 【処理内容】: "".split(",") → [""] → trim → filter { isNotEmpty() } → []
        val result = parseTagsText(input)

        // 【結果検証】: 空リストが返されることを確認
        // 【期待値確認】: EDGE-003「タグ空の場合、空リストとして処理」に準拠
        assertTrue(result.isEmpty()) // 【確認内容】: 結果が空リストであること 🟡
        assertEquals(0, result.size) // 【確認内容】: リストサイズが 0 であること 🟡
        assertEquals(emptyList<String>(), result) // 【確認内容】: 空リストと等価であること 🟡
    }

    /**
     * TC-016-010: カンマのみの入力で空リストが返される
     * 🟡 黄信号: TC-103-02 から妥当な推測
     */
    @Test
    fun `TC-016-010 カンマのみの入力で空リストが返される`() {
        // 【テスト目的】: parseTagsText(",") が空リストを返すこと
        // 【テスト内容】: カンマで分割後に空文字列のみが残るケースの処理を確認
        // 【期待される動作】: split 後の全要素が空文字列となり、filter で全て除去される
        // 🟡 信頼性レベル: TC-103-02 から妥当な推測

        // 【テストデータ準備】: カンマのみの文字列。split(",") が ["", ""] を返すパターン
        // 【初期条件設定】: 有効なタグ文字を持たないカンマのみの入力
        val input = ","

        // 【実際の処理実行】: カンマのみ入力で parseTagsText() を呼び出す
        // 【処理内容】: ",".split(",") → ["", ""] → trim → filter { isNotEmpty() } → []
        val result = parseTagsText(input)

        // 【結果検証】: 空リストが返されることを確認
        // 【期待値確認】: split + trim + filter の組み合わせで空要素が完全に除去される
        assertTrue(result.isEmpty()) // 【確認内容】: 結果が空リストであること 🟡
        assertEquals(emptyList<String>(), result) // 【確認内容】: 空リストと等価であること 🟡
    }

    /**
     * TC-016-011: スペースとカンマだけの入力で空リストが返される
     * 🟡 黄信号: EDGE-003 から妥当な推測
     */
    @Test
    fun `TC-016-011 スペースとカンマだけの入力で空リストが返される`() {
        // 【テスト目的】: parseTagsText("  ,  ,  ") が空リストを返すこと
        // 【テスト内容】: trim 後に空文字列になる要素のフィルタリングを確認
        // 【期待される動作】: trim + filter の組み合わせでスペースのみのタグが全て除去される
        // 🟡 信頼性レベル: EDGE-003 から妥当な推測

        // 【テストデータ準備】: スペースとカンマのみ。split後各要素がtrim()で空文字になる
        // 【初期条件設定】: 有効なタグ文字を含まないスペースのみの区切り入力
        val input = "  ,  ,  "

        // 【実際の処理実行】: スペース+カンマのみ入力で parseTagsText() を呼び出す
        // 【処理内容】: split(",") → ["  ", "  ", "  "] → trim → ["", "", ""] → filter → []
        val result = parseTagsText(input)

        // 【結果検証】: 空リストが返されることを確認
        // 【期待値確認】: trim 後に空文字列となる要素が filter で全て除去される
        assertTrue(result.isEmpty()) // 【確認内容】: 結果が空リストであること 🟡
        assertEquals(emptyList<String>(), result) // 【確認内容】: スペースのみのタグが有効タグとして残らないこと 🟡
    }

    // ================================================================
    // 3. 境界値テストケース
    // ================================================================

    /**
     * TC-016-014: 末尾カンマで空要素がフィルタリングされる
     * 🟡 黄信号: REQ-103 のパース仕様から妥当な推測
     */
    @Test
    fun `TC-016-014 末尾カンマがある入力で有効タグのみが返される`() {
        // 【テスト目的】: "shared, web," の末尾カンマ後の空文字列が除去されること
        // 【テスト内容】: split(",") で末尾が空文字列になるパターンのフィルタリングを確認
        // 【期待される動作】: 末尾の空要素が filter { isNotEmpty() } で除去され、有効タグのみが残る
        // 🟡 信頼性レベル: REQ-103 のパース仕様から妥当な推測

        // 【テストデータ準備】: 末尾カンマあり。ユーザーがタグ入力中に次のタグを入力せずに送信
        // 【初期条件設定】: split(",") が ["shared", " web", ""] を返すパターン
        val input = "shared, web,"

        // 【実際の処理実行】: 末尾カンマ付き入力で parseTagsText() を呼び出す
        // 【処理内容】: split(",") → ["shared", " web", ""] → trim → ["shared", "web", ""] → filter → ["shared", "web"]
        val result = parseTagsText(input)

        // 【結果検証】: 有効な2タグのみが含まれることを確認
        // 【期待値確認】: 末尾の空文字列要素が除去され、有効タグのみが返される
        assertEquals(2, result.size) // 【確認内容】: リストのサイズが 2 であること（末尾の空要素は除去） 🟡
        assertEquals("shared", result[0]) // 【確認内容】: 1番目の要素が "shared" であること 🟡
        assertEquals("web", result[1]) // 【確認内容】: 2番目の要素が "web" であること 🟡
        assertEquals(listOf("shared", "web"), result) // 【確認内容】: 全体が ["shared", "web"] と一致すること 🟡
    }

    /**
     * TC-016-015: 先頭カンマで空要素がフィルタリングされる
     * 🟡 黄信号: REQ-103 のパース仕様から妥当な推測
     */
    @Test
    fun `TC-016-015 先頭カンマがある入力で有効タグのみが返される`() {
        // 【テスト目的】: ",shared, web" の先頭カンマ前の空文字列が除去されること
        // 【テスト内容】: split(",") で先頭が空文字列になるパターンのフィルタリングを確認
        // 【期待される動作】: 先頭の空要素が filter { isNotEmpty() } で除去され、有効タグのみが残る
        // 🟡 信頼性レベル: REQ-103 のパース仕様から妥当な推測

        // 【テストデータ準備】: 先頭カンマあり。ユーザーが誤ってカンマから入力を開始した場合
        // 【初期条件設定】: split(",") が ["", "shared", " web"] を返すパターン
        val input = ",shared, web"

        // 【実際の処理実行】: 先頭カンマ付き入力で parseTagsText() を呼び出す
        // 【処理内容】: split(",") → ["", "shared", " web"] → trim → ["", "shared", "web"] → filter → ["shared", "web"]
        val result = parseTagsText(input)

        // 【結果検証】: 有効な2タグのみが含まれることを確認
        // 【期待値確認】: 先頭の空文字列要素が除去され、有効タグのみが返される
        assertEquals(2, result.size) // 【確認内容】: リストのサイズが 2 であること（先頭の空要素は除去） 🟡
        assertEquals("shared", result[0]) // 【確認内容】: 1番目の要素が "shared" であること 🟡
        assertEquals("web", result[1]) // 【確認内容】: 2番目の要素が "web" であること 🟡
        assertEquals(listOf("shared", "web"), result) // 【確認内容】: 全体が ["shared", "web"] と一致すること 🟡
    }

    /**
     * TC-016-016: 連続カンマで空要素がフィルタリングされる
     * 🟡 黄信号: REQ-103 のパース仕様から妥当な推測
     */
    @Test
    fun `TC-016-016 連続するカンマがある入力で有効タグのみが返される`() {
        // 【テスト目的】: "shared,,web" の連続カンマ間の空文字列が除去されること
        // 【テスト内容】: split(",") で中間に空文字列が生まれるパターンのフィルタリングを確認
        // 【期待される動作】: 中間の空要素が filter { isNotEmpty() } で除去され、有効タグのみが残る
        // 🟡 信頼性レベル: REQ-103 のパース仕様から妥当な推測

        // 【テストデータ準備】: 連続カンマあり。ユーザーがカンマを誤って2回入力した場合
        // 【初期条件設定】: split(",") が ["shared", "", "web"] を返すパターン
        val input = "shared,,web"

        // 【実際の処理実行】: 連続カンマ入力で parseTagsText() を呼び出す
        // 【処理内容】: split(",") → ["shared", "", "web"] → trim → ["shared", "", "web"] → filter → ["shared", "web"]
        val result = parseTagsText(input)

        // 【結果検証】: 有効な2タグのみが含まれることを確認
        // 【期待値確認】: 中間の空文字列要素が除去され、有効タグのみが返される
        assertEquals(2, result.size) // 【確認内容】: リストのサイズが 2 であること（連続カンマ間の空要素は除去） 🟡
        assertEquals("shared", result[0]) // 【確認内容】: 1番目の要素が "shared" であること 🟡
        assertEquals("web", result[1]) // 【確認内容】: 2番目の要素が "web" であること 🟡
        assertEquals(listOf("shared", "web"), result) // 【確認内容】: 全体が ["shared", "web"] と一致すること 🟡
    }
}

package com.den4dr.share2Obsidian.ui

import com.den4dr.share2Obsidian.content.ContentKind
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.format.NoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EditFormState データクラス 単体テスト
 *
 * テスト対象: com.den4dr.share2Obsidian.ui.EditFormState
 * テストケース: TC-016-001〜003, TC-016-017〜018
 *
 * 実行: mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditFormStateTest"
 *
 * 注意: EditFormState は純粋 Kotlin data class のため Robolectric 不要。通常 JUnit テストで実行可能。
 */
class EditFormStateTest {

    // ================================================================
    // 1. 正常系テストケース
    // ================================================================

    /**
     * TC-016-001: EditFormState の全フィールド指定でのインスタンス生成
     * 🔵 青信号: REQ-003・interfaces.kt の EditFormState 定義に基づく
     */
    @Test
    fun `TC-016-001 EditFormState の全フィールド指定でインスタンスが正しく生成される`() {
        // 【テスト目的】: EditFormState データクラスが title, body, tagsText, folder の4フィールドを正しく保持すること
        // 【テスト内容】: コンストラクタに渡した値が各プロパティからそのまま取得できることを確認
        // 【期待される動作】: 4フィールドすべてが入力値と完全一致する
        // 🔵 信頼性レベル: REQ-003（編集フィールド定義）・interfaces.kt の EditFormState 定義に直接基づく

        // 【テストデータ準備】: ユーザーが共有テキストを受け取った際の典型的な初期値
        // 【初期条件設定】: ProcessedContent + NoteConfig 由来の典型的なフィールド値
        val title = "テスト記事"
        val body = "本文テスト"
        val tagsText = "shared, web"
        val folder = "70_clippings"

        // 【実際の処理実行】: EditFormState コンストラクタを呼び出す
        // 【処理内容】: 4フィールドを持つデータクラスのインスタンスを生成する
        val formState = EditFormState(
            title = title,
            body = body,
            tagsText = tagsText,
            folder = folder
        )

        // 【結果検証】: 各フィールドの値を確認
        // 【期待値確認】: data class は不変フィールドを保持するため、コンストラクタの値がそのまま返される
        assertEquals(title, formState.title) // 【確認内容】: title フィールドが入力値と一致すること 🔵
        assertEquals(body, formState.body) // 【確認内容】: body フィールドが入力値と一致すること 🔵
        assertEquals(tagsText, formState.tagsText) // 【確認内容】: tagsText フィールドが入力値と一致すること 🔵
        assertEquals(folder, formState.folder) // 【確認内容】: folder フィールドが入力値と一致すること 🔵
    }

    /**
     * TC-016-002: ProcessedContent.title が存在する場合の EditFormState 初期値
     * 🔵 青信号: REQ-003・TC-003-01〜04・dataflow.md フロー1 に基づく
     */
    @Test
    fun `TC-016-002 ProcessedContent と NoteConfig から正しい初期値で EditFormState が生成される`() {
        // 【テスト目的】: ViewModel が initialize() で行う初期化パターンが正しいことを事前検証する
        // 【テスト内容】: ProcessedContent.title と config.defaultTags.joinToString() から生成された EditFormState の値を確認
        // 【期待される動作】: title, body, tagsText, folder の4フィールドすべてが正しい初期値を持つ
        // 🔵 信頼性レベル: REQ-003・TC-003-01〜04・dataflow.md フロー1 に直接基づく

        // 【テストデータ準備】: テキスト共有の基本パターン（dataflow.md フロー1）
        // 【初期条件設定】: ProcessedContent(title あり) + NoteConfig(defaultTags=["shared"])
        val processed = ProcessedContent(
            body = "本文",
            title = "タイトル",
            contentType = ContentKind.TEXT
        )
        val config = NoteConfig(
            vault = "testVault",
            folder = "70_clippings",
            defaultTags = listOf("shared")
        )

        // 【実際の処理実行】: ViewModel の initialize() パターンを模倣して EditFormState を生成
        // 【処理内容】: processed.title ?: "" と config.defaultTags.joinToString(", ") を使って初期化
        val formState = EditFormState(
            title = processed.title ?: "",
            body = processed.body,
            tagsText = config.defaultTags.joinToString(", "),
            folder = config.folder
        )

        // 【結果検証】: 各フィールドが期待する初期値を持つことを確認
        // 【期待値確認】: REQ-003 の初期値仕様に準拠
        assertEquals("タイトル", formState.title) // 【確認内容】: title が ProcessedContent.title 値であること 🔵
        assertEquals("本文", formState.body) // 【確認内容】: body が ProcessedContent.body 値であること 🔵
        assertEquals("shared", formState.tagsText) // 【確認内容】: tagsText が defaultTags.joinToString() の値であること 🔵
        assertEquals("70_clippings", formState.folder) // 【確認内容】: folder が config.folder 値であること 🔵
    }

    /**
     * TC-016-003: ProcessedContent.title が null の場合に title が空文字列になる
     * 🔵 青信号: EDGE-001・TC-003-01・interfaces.kt `processed.title ?: ""` に基づく
     */
    @Test
    fun `TC-016-003 ProcessedContent の title が null の場合に EditFormState の title が空文字列になる`() {
        // 【テスト目的】: ProcessedContent.title が null の場合に title フィールドが空文字列 "" で初期化されること
        // 【テスト内容】: null 安全演算子 ?: "" による null→空文字列変換パターンを確認
        // 【期待される動作】: title フィールドが null ではなく空文字列 "" であること
        // 🔵 信頼性レベル: EDGE-001・TC-003-01・interfaces.kt `processed.title ?: ""` に直接基づく

        // 【テストデータ準備】: 共有元アプリが EXTRA_SUBJECT を提供しない場合（EDGE-001）
        // 【初期条件設定】: title=null の ProcessedContent。EXTRA_SUBJECT なしの共有
        val processed = ProcessedContent(
            body = "本文",
            title = null,
            contentType = ContentKind.TEXT
        )
        val config = NoteConfig(
            vault = "testVault",
            folder = "70_clippings",
            defaultTags = listOf("shared")
        )

        // 【実際の処理実行】: null タイトルで初期化パターンを実行
        // 【処理内容】: processed.title ?: "" が "" を返し、EditFormState.title が "" になる
        val formState = EditFormState(
            title = processed.title ?: "",
            body = processed.body,
            tagsText = config.defaultTags.joinToString(", "),
            folder = config.folder
        )

        // 【結果検証】: title が null ではなく空文字列 "" であることを確認
        // 【期待値確認】: EDGE-001 仕様「タイトル空の場合、空文字列として保持」に準拠
        assertEquals("", formState.title) // 【確認内容】: title が null ではなく空文字列 "" であること 🔵
        assertEquals("本文", formState.body) // 【確認内容】: body は影響なく正常値であること 🔵
        assertEquals("shared", formState.tagsText) // 【確認内容】: tagsText は影響なく正常値であること 🔵
    }

    // ================================================================
    // 3. 境界値テストケース
    // ================================================================

    /**
     * TC-016-017: 同一パラメータの EditFormState インスタンスが等価と判定される
     * 🟡 黄信号: Kotlin data class の標準動作からの妥当な推測
     */
    @Test
    fun `TC-016-017 同一パラメータの EditFormState インスタンスが equals で等価と判定される`() {
        // 【テスト目的】: data class の構造的等価性（equals/hashCode）が正しく動作すること
        // 【テスト内容】: 同一パラメータで2つのインスタンスを生成して equals/hashCode を確認
        // 【期待される動作】: 同一パラメータなら equals が true、hashCode が一致する
        // 🟡 信頼性レベル: Kotlin data class の標準動作。StateFlow の値比較で使用されるため重要

        // 【テストデータ準備】: 同一パラメータで2インスタンスを作成
        // 【初期条件設定】: data class の構造的等価性を確認するための同一値
        val formState1 = EditFormState(
            title = "タイトル",
            body = "本文",
            tagsText = "shared",
            folder = "70_clippings"
        )
        val formState2 = EditFormState(
            title = "タイトル",
            body = "本文",
            tagsText = "shared",
            folder = "70_clippings"
        )

        // 【実際の処理実行】: equals() と hashCode() を評価
        // 【処理内容】: Kotlin data class が自動生成する equals/hashCode を利用
        val isEqual = formState1 == formState2
        val hashCodesMatch = formState1.hashCode() == formState2.hashCode()

        // 【結果検証】: data class の等価性を確認
        // 【期待値確認】: Kotlin data class の構造的等価性が保証される（Compose Recomposition トリガーに重要）
        assertTrue(isEqual) // 【確認内容】: 同一パラメータの EditFormState インスタンスが等価であること 🟡
        assertTrue(hashCodesMatch) // 【確認内容】: hashCode が一致すること 🟡
        assertFalse(formState1 === formState2) // 【確認内容】: 参照は異なる（異なるインスタンス）であること 🟡
    }

    /**
     * TC-016-018: copy() メソッドで特定フィールドのみが変更される
     * 🟡 黄信号: Kotlin data class の標準動作からの妥当な推測
     */
    @Test
    fun `TC-016-018 copy メソッドで特定フィールドのみが変更され他は元の値が保持される`() {
        // 【テスト目的】: EditFormState.copy() が指定フィールドのみを更新し、他は元の値を保持すること
        // 【テスト内容】: ユーザーがタイトルのみを編集した際のViewModel状態更新パターンを確認
        // 【期待される動作】: copy(title="新タイトル") 後に title のみが変更され、他フィールドは不変
        // 🟡 信頼性レベル: Kotlin data class の標準動作。ViewModel の状態更新パターンで使用

        // 【テストデータ準備】: 元の EditFormState から copy() でタイトルのみ変更
        // 【初期条件設定】: ユーザーがタイトルフィールドを編集した場合のパターン
        val original = EditFormState(
            title = "元タイトル",
            body = "元本文",
            tagsText = "shared",
            folder = "70_clippings"
        )

        // 【実際の処理実行】: copy() でタイトルのみ変更
        // 【処理内容】: Kotlin data class の copy() メソッドで title フィールドのみを変更
        val updated = original.copy(title = "新タイトル")

        // 【結果検証】: title のみが更新され、他のフィールドは元の値を保持することを確認
        // 【期待値確認】: copy() による部分更新が正確に行われることを保証
        assertEquals("新タイトル", updated.title) // 【確認内容】: title が "新タイトル" に更新されていること 🟡
        assertEquals("元本文", updated.body) // 【確認内容】: body は元の値 "元本文" のままであること 🟡
        assertEquals("shared", updated.tagsText) // 【確認内容】: tagsText は元の値 "shared" のままであること 🟡
        assertEquals("70_clippings", updated.folder) // 【確認内容】: folder は元の値 "70_clippings" のままであること 🟡
        assertEquals("元タイトル", original.title) // 【確認内容】: 元のインスタンスは不変であること（イミュータブル保証） 🟡
    }
}

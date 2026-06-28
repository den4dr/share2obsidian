package com.den4dr.share2Obsidian.ui

import com.den4dr.share2Obsidian.content.ContentKind
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.domain.model.CustomFieldState
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.format.NoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * EditScreenViewModel 単体テスト（TASK-0017）
 *
 * テスト対象:
 *   - EditScreenViewModel.initialize()     : フォーム初期値のセット・重複初期化防止
 *   - EditScreenViewModel.updateTitle()    : タイトルフィールド更新
 *   - EditScreenViewModel.updateBody()     : 本文フィールド更新
 *   - EditScreenViewModel.updateTagsText() : タグテキストフィールド更新
 *   - EditScreenViewModel.updateFolder()   : フォルダフィールド更新
 *   - EditScreenViewModel.buildSendParams(): 送信パラメータ生成（タグパース・タイトルnull変換）
 *
 * 実行: mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditScreenViewModelTest {

    // ----------------------------------------------------------------
    // テスト共通設定
    // ----------------------------------------------------------------

    /** 【テスト前準備】: 各テストで共通に使用する標準的な入力データを定義 */
    private lateinit var standardProcessed: ProcessedContent
    private lateinit var standardConfig: NoteConfig

    @Before
    fun setUp() {
        // 【テスト前準備】: 各テスト実行前に標準的なテストデータを初期化する
        // 【環境初期化】: テストが独立して動作するよう、毎回フレッシュなデータを作成する
        standardProcessed = ProcessedContent(
            body = "共有テキスト",
            title = "ページタイトル",
            contentType = ContentKind.TEXT
        )
        standardConfig = NoteConfig(
            vault = "testVault",
            folder = "70_clippings",
            defaultTags = listOf("shared")
        )
    }

    // ================================================================
    // 1. 正常系テストケース
    // ================================================================

    /**
     * TC-001: initialize() で初期値が正しくセットされる
     * 🔵 青信号: REQ-003, TC-003-01〜04, interfaces.kt の EditScreenViewModelSpec に基づく
     */
    @Test
    fun `TC-001 initialize で ProcessedContent と NoteConfig から初期値が正しくセットされる`() {
        // 【テスト目的】: initialize() メソッドが EditFormState の全4フィールドを正しく初期化すること
        // 【テスト内容】: 典型的なテキスト共有シナリオで ProcessedContent と NoteConfig から初期値を生成
        // 【期待される動作】: formState.value の各フィールドが入力データに基づいて設定される
        // 🔵 信頼性レベル: REQ-003 の初期値マッピング仕様（title, body, tagsText, folder）に基づく

        // Arrange
        val viewModel = EditScreenViewModel()

        // Act
        // 【実際の処理実行】: ViewModel の初期化メソッドを呼び出す
        // 【処理内容】: ProcessedContent と NoteConfig から EditFormState を構築して StateFlow に設定
        viewModel.initialize(standardProcessed, standardConfig)

        // Assert
        // 【結果検証】: 4フィールドすべてが正しくマッピングされていることを確認
        // 【期待値確認】: REQ-003 の初期値マッピングルールに基づく
        assertEquals(
            "タイトルが ProcessedContent.title の値で初期化されていること",
            "ページタイトル",
            viewModel.formState.value.title
        ) // 【確認内容】: title = processed.title ?: "" のマッピング 🔵
        assertEquals(
            "本文が ProcessedContent.body の値で初期化されていること",
            "共有テキスト",
            viewModel.formState.value.body
        ) // 【確認内容】: body = processed.body のマッピング 🔵
        assertEquals(
            "タグテキストが config.defaultTags.joinToString(\", \") で初期化されていること",
            "shared",
            viewModel.formState.value.tagsText
        ) // 【確認内容】: tagsText = config.defaultTags.joinToString(", ") のマッピング 🔵
        assertEquals(
            "フォルダが config.folder の値で初期化されていること",
            "70_clippings",
            viewModel.formState.value.folder
        ) // 【確認内容】: folder = config.folder のマッピング 🔵
        assertEquals(
            "Vault が config.vault の値で初期化されていること（REQ-061）",
            "testVault",
            viewModel.formState.value.vault
        ) // 【確認内容】: vault = config.vault のマッピング 🔵
    }

    /**
     * TC-002: initialize() は2回目以降の呼び出しを無視する（画面回転対応）
     * 🔵 青信号: EDGE-101, TC-EDGE-101-01, note.md の重複初期化防止ロジック仕様に基づく
     */
    @Test
    fun `TC-002 initialize は2回目以降の呼び出しを無視して状態を保持する`() {
        // 【テスト目的】: initialized フラグにより重複初期化が防止され、画面回転後もユーザー入力が保持されること
        // 【テスト内容】: 初回 initialize → updateTitle → 2回目 initialize の順で実行し、状態が保持されることを確認
        // 【期待される動作】: 2回目の initialize() 呼び出しが無視され、変更後のタイトルが維持される
        // 🔵 信頼性レベル: EDGE-101「画面回転後も状態が保持される」要件に基づく

        // Arrange
        val viewModel = EditScreenViewModel()

        // Act
        // 【実際の処理実行】: 初回初期化 → 状態変更 → 2回目初期化の順で操作
        // 【処理内容】: 画面回転時のシナリオを再現（Activity 再作成で initialize が再呼び出し）
        viewModel.initialize(standardProcessed, standardConfig)
        viewModel.updateTitle("変更後タイトル") // ユーザーが編集した状態をシミュレート
        viewModel.initialize(standardProcessed, standardConfig) // 画面回転後の再初期化を再現

        // Assert
        // 【結果検証】: 2回目の initialize() 後もユーザーが編集したタイトルが保持されていること
        // 【期待値確認】: initialized フラグが true の場合に早期リターンすることによる保護
        assertEquals(
            "2回目の initialize 後も変更後のタイトルが保持されること",
            "変更後タイトル",
            viewModel.formState.value.title
        ) // 【確認内容】: initialized フラグによる重複初期化防止が機能していること 🔵
    }

    /**
     * TC-003: title が null の ProcessedContent で初期化した場合に空文字でセットされる
     * 🔵 青信号: TC-003-02, interfaces.kt の processed.title ?: "" 仕様に基づく
     */
    @Test
    fun `TC-003 title が null の ProcessedContent で初期化すると title フィールドが空文字になる`() {
        // 【テスト目的】: ProcessedContent.title が null の場合に空文字 "" で初期化されること
        // 【テスト内容】: title = null の ProcessedContent で initialize() を呼び出す
        // 【期待される動作】: processed.title ?: "" の変換ロジックにより null が空文字に変換される
        // 🔵 信頼性レベル: TC-003-02「タイトルが null の場合、タイトルフィールドは空」に準拠

        // Arrange
        // 【テストデータ準備】: 共有元アプリがタイトルを提供しない場合（EXTRA_SUBJECT なし）を想定
        val processedWithoutTitle = ProcessedContent(
            body = "本文のみ",
            title = null,
            contentType = ContentKind.TEXT
        )
        val viewModel = EditScreenViewModel()

        // Act
        // 【実際の処理実行】: title = null の ProcessedContent で初期化
        // 【処理内容】: Kotlin の ?: 演算子で null → 空文字への安全な変換を検証
        viewModel.initialize(processedWithoutTitle, standardConfig)

        // Assert
        // 【結果検証】: NullPointerException なしで空文字が設定されていること
        // 【期待値確認】: title が null の場合の安全なフォールバック処理
        assertEquals(
            "title が null の場合は空文字が設定されること",
            "",
            viewModel.formState.value.title
        ) // 【確認内容】: processed.title ?: "" の null 安全変換 🔵
        assertEquals(
            "title が null でも body は正常に設定されること",
            "本文のみ",
            viewModel.formState.value.body
        ) // 【確認内容】: title の null は他フィールドに影響しないこと 🔵
    }

    /**
     * TC-004: updateTitle() でタイトルが変更される
     * 🔵 青信号: REQ-003, note.md の状態更新パターンに基づく
     */
    @Test
    fun `TC-004 updateTitle でフォーム状態のタイトルが正しく更新される`() {
        // 【テスト目的】: updateTitle() が title フィールドのみを更新し、他フィールドに影響しないこと
        // 【テスト内容】: 初期化後に updateTitle() を呼び出し、title のみが変更されることを確認
        // 【期待される動作】: copy(title = title) による部分的なイミュータブル更新が正しく機能する
        // 🔵 信頼性レベル: REQ-003・note.md の状態更新パターン `_formState.value.copy(title = title)` に基づく

        // Arrange
        val viewModel = EditScreenViewModel()
        viewModel.initialize(standardProcessed, standardConfig)

        // Act
        // 【実際の処理実行】: ユーザーがタイトルフィールドを編集した場合をシミュレート
        // 【処理内容】: _formState.value = _formState.value.copy(title = "新しいタイトル") が実行される
        viewModel.updateTitle("新しいタイトル")

        // Assert
        // 【結果検証】: title のみが変更され、他フィールドが初期値を保持していること
        assertEquals(
            "updateTitle 後に title が新しい値に更新されていること",
            "新しいタイトル",
            viewModel.formState.value.title
        ) // 【確認内容】: title が指定した値に更新されていること 🔵
        assertEquals(
            "updateTitle 後に body が変更されていないこと",
            "共有テキスト",
            viewModel.formState.value.body
        ) // 【確認内容】: title の更新が body に影響しないこと 🔵
        assertEquals(
            "updateTitle 後に tagsText が変更されていないこと",
            "shared",
            viewModel.formState.value.tagsText
        ) // 【確認内容】: title の更新が tagsText に影響しないこと 🔵
        assertEquals(
            "updateTitle 後に folder が変更されていないこと",
            "70_clippings",
            viewModel.formState.value.folder
        ) // 【確認内容】: title の更新が folder に影響しないこと 🔵
    }

    /**
     * TC-004b: updateBody() で本文が変更される
     * 🔵 青信号: REQ-003, note.md の update メソッド仕様に基づく
     */
    @Test
    fun `TC-004b updateBody でフォーム状態の本文が正しく更新される`() {
        // 【テスト目的】: updateBody() が body フィールドのみを更新し、他フィールドに影響しないこと
        // 【テスト内容】: 初期化後に updateBody() を呼び出し、body のみが変更されることを確認
        // 【期待される動作】: copy(body = body) による部分的なイミュータブル更新が正しく機能する
        // 🔵 信頼性レベル: REQ-003・note.md の update メソッド仕様に基づく

        // Arrange
        val viewModel = EditScreenViewModel()
        viewModel.initialize(standardProcessed, standardConfig)

        // Act
        // 【実際の処理実行】: ユーザーが本文フィールドを編集した場合をシミュレート
        viewModel.updateBody("新しい本文")

        // Assert
        assertEquals(
            "updateBody 後に body が新しい値に更新されていること",
            "新しい本文",
            viewModel.formState.value.body
        ) // 【確認内容】: body が指定した値に更新されていること 🔵
        assertEquals(
            "updateBody 後に title が変更されていないこと",
            "ページタイトル",
            viewModel.formState.value.title
        ) // 【確認内容】: body の更新が title に影響しないこと 🔵
        assertEquals(
            "updateBody 後に tagsText が変更されていないこと",
            "shared",
            viewModel.formState.value.tagsText
        ) // 【確認内容】: body の更新が tagsText に影響しないこと 🔵
    }

    /**
     * TC-004c: updateTagsText() でタグテキストが変更される
     * 🔵 青信号: REQ-103, note.md の update メソッド仕様に基づく
     */
    @Test
    fun `TC-004c updateTagsText でフォーム状態のタグテキストが正しく更新される`() {
        // 【テスト目的】: updateTagsText() が tagsText フィールドのみを更新し、他フィールドに影響しないこと
        // 【テスト内容】: 初期化後に updateTagsText() を呼び出し、tagsText のみが変更されることを確認
        // 【期待される動作】: copy(tagsText = tagsText) による部分的なイミュータブル更新が正しく機能する
        // 🔵 信頼性レベル: REQ-103・note.md の update メソッド仕様に基づく

        // Arrange
        val viewModel = EditScreenViewModel()
        viewModel.initialize(standardProcessed, standardConfig)

        // Act
        // 【実際の処理実行】: ユーザーがタグフィールドにカンマ区切りで複数タグを入力した場合をシミュレート
        viewModel.updateTagsText("shared, web, clipping")

        // Assert
        assertEquals(
            "updateTagsText 後に tagsText が新しい値に更新されていること",
            "shared, web, clipping",
            viewModel.formState.value.tagsText
        ) // 【確認内容】: tagsText が指定した値に更新されていること 🔵
        assertEquals(
            "updateTagsText 後に title が変更されていないこと",
            "ページタイトル",
            viewModel.formState.value.title
        ) // 【確認内容】: tagsText の更新が title に影響しないこと 🔵
        assertEquals(
            "updateTagsText 後に body が変更されていないこと",
            "共有テキスト",
            viewModel.formState.value.body
        ) // 【確認内容】: tagsText の更新が body に影響しないこと 🔵
    }

    /**
     * TC-004d: updateFolder() でフォルダが変更される
     * 🔵 青信号: REQ-405, note.md の update メソッド仕様に基づく
     */
    @Test
    fun `TC-004d updateFolder でフォーム状態のフォルダが正しく更新される`() {
        // 【テスト目的】: updateFolder() が folder フィールドのみを更新し、他フィールドに影響しないこと
        // 【テスト内容】: 初期化後に updateFolder() を呼び出し、folder のみが変更されることを確認
        // 【期待される動作】: copy(folder = folder) による部分的なイミュータブル更新が正しく機能する
        // 🔵 信頼性レベル: REQ-405・note.md の update メソッド仕様に基づく

        // Arrange
        val viewModel = EditScreenViewModel()
        viewModel.initialize(standardProcessed, standardConfig)

        // Act
        // 【実際の処理実行】: ユーザーが保存先フォルダを変更した場合をシミュレート
        viewModel.updateFolder("inbox/notes")

        // Assert
        assertEquals(
            "updateFolder 後に folder が新しい値に更新されていること",
            "inbox/notes",
            viewModel.formState.value.folder
        ) // 【確認内容】: folder が指定した値に更新されていること 🔵
        assertEquals(
            "updateFolder 後に title が変更されていないこと",
            "ページタイトル",
            viewModel.formState.value.title
        ) // 【確認内容】: folder の更新が title に影響しないこと 🔵
        assertEquals(
            "updateFolder 後に tagsText が変更されていないこと",
            "shared",
            viewModel.formState.value.tagsText
        ) // 【確認内容】: folder の更新が tagsText に影響しないこと 🔵
    }

    /**
     * TC-005: buildSendParams() でタグがパースされる
     * 🔵 青信号: REQ-103, TC-101-02/03, note.md の buildSendParams 実装仕様に基づく
     */
    @Test
    fun `TC-005 buildSendParams でカンマ区切りタグテキストが List にパースされる`() {
        // 【テスト目的】: buildSendParams() 内で parseTagsText() が呼び出され、tagsText が List<String> に変換されること
        // 【テスト内容】: "shared, web" という tagsText で buildSendParams() を呼び出す
        // 【期待される動作】: "shared, web" が ["shared", "web"] に変換された SendParams が返される
        // 🔵 信頼性レベル: REQ-103・TC-101-02/03 のタグパース仕様に基づく

        // Arrange
        val viewModel = EditScreenViewModel()
        viewModel.initialize(standardProcessed, standardConfig)
        viewModel.updateTagsText("shared, web")

        // Act
        // 【実際の処理実行】: 送信ボタンタップ時のパラメータ生成を呼び出す
        // 【処理内容】: parseTagsText(tagsText) でカンマ区切りリストに変換する
        val sendParams = viewModel.buildSendParams()

        // Assert
        // 【結果検証】: タグが正しくパースされていること
        assertEquals(
            "buildSendParams でタグリストが parseTagsText によりパースされること",
            listOf("shared", "web"),
            sendParams.tags
        ) // 【確認内容】: "shared, web" → ["shared", "web"] の変換 🔵
        assertEquals(
            "buildSendParams でタイトルが正しく設定されること",
            "ページタイトル",
            sendParams.title
        ) // 【確認内容】: 非空タイトルは null に変換されないこと 🔵
        assertEquals(
            "buildSendParams で本文が正しく設定されること",
            "共有テキスト",
            sendParams.body
        ) // 【確認内容】: body がそのまま渡されること 🔵
    }

    /**
     * TC-005b: buildSendParams() で config が正しく渡される
     * 🔵 青信号: REQ-405, note.md の buildSendParams 実装仕様に基づく
     */
    @Test
    fun `TC-005b buildSendParams で引数の NoteConfig がそのまま SendParams に設定される`() {
        // 【テスト目的】: buildSendParams(config) の引数 config が SendParams.config にそのまま渡されること
        // 【テスト内容】: カスタム設定を持つ NoteConfig で buildSendParams() を呼び出す
        // 【期待される動作】: メソッド引数の config が変更されずに SendParams に設定される
        // 🔵 信頼性レベル: REQ-405・note.md の buildSendParams 実装仕様に基づく

        // Arrange
        // 【テストデータ準備】: カスタム設定値を持つ NoteConfig を作成して config の受け渡しを確認
        val customConfig = NoteConfig(
            vault = "myVault",
            folder = "inbox",
            defaultTags = listOf("test")
        )
        val viewModel = EditScreenViewModel()
        viewModel.initialize(standardProcessed, customConfig)

        // Act
        val sendParams = viewModel.buildSendParams()

        // Assert
        // buildSendParams() は EditFormState の vault/folder から config を構築する（REQ-062）
        assertEquals(
            "buildSendParams で config.vault が EditFormState 由来であること",
            "myVault",
            sendParams.config.vault
        ) // 【確認内容】: vault が EditFormState.vault（customConfig.vault 由来）であること 🔵
        assertEquals(
            "buildSendParams で config.folder が EditFormState 由来であること",
            "inbox",
            sendParams.config.folder
        ) // 【確認内容】: folder が EditFormState.folder（customConfig.folder 由来）であること 🔵
    }

    // ================================================================
    // 2. 異常系テストケース（エッジケース）
    // ================================================================

    /**
     * TC-006: buildSendParams() で空タイトルが null になる
     * 🔵 青信号: EDGE-001, note.md の state.title.ifBlank { null } 実装仕様に基づく
     */
    @Test
    fun `TC-006 buildSendParams で空文字タイトルが null に変換される`() {
        // 【テスト目的】: buildSendParams() 内で ifBlank { null } により空タイトルが null に変換されること
        // 【テスト内容】: title を空文字に更新してから buildSendParams() を呼び出す
        // 【期待される動作】: SendParams.title が null になる（タイトルなしノート作成を意味する）
        // 🔵 信頼性レベル: EDGE-001・note.md の `state.title.ifBlank { null }` 実装仕様に基づく

        // Arrange
        val viewModel = EditScreenViewModel()
        viewModel.initialize(standardProcessed, standardConfig)
        // 【初期条件設定】: ユーザーがタイトルフィールドを全削除した状態をシミュレート
        viewModel.updateTitle("")

        // Act
        // 【実際の処理実行】: 空タイトルで送信パラメータを生成
        // 【処理内容】: state.title.ifBlank { null } により "" → null に変換される
        val sendParams = viewModel.buildSendParams()

        // Assert
        // 【結果検証】: 空文字タイトルが null に変換されていること
        assertNull(
            "空文字タイトルは buildSendParams で null に変換されること（EDGE-001）",
            sendParams.title
        ) // 【確認内容】: ifBlank { null } が空文字に対して正しく動作すること 🔵
    }

    /**
     * TC-007: buildSendParams() でスペースのみタイトルが null になる
     * 🟡 黄信号: EDGE-001 から妥当な推測。ifBlank の動作は Kotlin 標準ライブラリ仕様に基づく
     */
    @Test
    fun `TC-007 buildSendParams でスペースのみのタイトルが null に変換される`() {
        // 【テスト目的】: buildSendParams() 内で ifBlank { null } によりスペースのみのタイトルが null に変換されること
        // 【テスト内容】: title をスペースのみに更新してから buildSendParams() を呼び出す
        // 【期待される動作】: Kotlin の isBlank() がスペースのみの文字列を true と判定し、null が返される
        // 🟡 信頼性レベル: EDGE-001 から妥当な推測（ifBlank の動作仕様）

        // Arrange
        val viewModel = EditScreenViewModel()
        viewModel.initialize(standardProcessed, standardConfig)
        // 【初期条件設定】: ユーザーが誤ってスペースのみ入力した状態をシミュレート
        viewModel.updateTitle("   ")

        // Act
        // 【実際の処理実行】: スペースのみのタイトルで送信パラメータを生成
        // 【処理内容】: state.title.ifBlank { null } により "   " → null に変換される
        val sendParams = viewModel.buildSendParams()

        // Assert
        // 【結果検証】: スペースのみのタイトルが null に変換されていること
        assertNull(
            "スペースのみのタイトルは buildSendParams で null に変換されること（EDGE-001）",
            sendParams.title
        ) // 【確認内容】: ifBlank がスペースのみ文字列を blank と判定すること 🟡
    }

    /**
     * TC-009: buildSendParams() で空本文がそのまま渡される
     * 🔵 青信号: EDGE-002「本文空で送信」、note.md の buildSendParams 実装仕様に基づく
     */
    @Test
    fun `TC-009 buildSendParams で空文字の本文がそのまま SendParams に設定される`() {
        // 【テスト目的】: 空本文が正常に処理され、エラーにならないこと
        // 【テスト内容】: body を空文字に更新してから buildSendParams() を呼び出す
        // 【期待される動作】: SendParams.body が空文字のまま設定される（空ノートの作成を許容）
        // 🔵 信頼性レベル: EDGE-002「本文空で送信」・requirements.md の変換ロジック表に基づく

        // Arrange
        val viewModel = EditScreenViewModel()
        viewModel.initialize(standardProcessed, standardConfig)
        // 【初期条件設定】: ユーザーが本文を全削除した状態をシミュレート
        viewModel.updateBody("")

        // Act
        val sendParams = viewModel.buildSendParams()

        // Assert
        // 【結果検証】: 空文字本文がそのまま SendParams に設定されていること
        assertEquals(
            "空文字の本文は buildSendParams でそのまま設定されること（EDGE-002）",
            "",
            sendParams.body
        ) // 【確認内容】: body が空文字を許容すること 🔵
    }

    /**
     * TC-010: buildSendParams() で空タグテキストが空リストになる
     * 🔵 青信号: EDGE-003「タグ空で送信」、TASK-0016 で parseTagsText("") = emptyList() 検証済みに基づく
     */
    @Test
    fun `TC-010 buildSendParams で空文字のタグテキストが空リストに変換される`() {
        // 【テスト目的】: 空タグテキストが parseTagsText("") → emptyList() に変換されること
        // 【テスト内容】: tagsText を空文字に更新してから buildSendParams() を呼び出す
        // 【期待される動作】: SendParams.tags が emptyList() になる（タグなしノートの作成を許容）
        // 🔵 信頼性レベル: EDGE-003・TASK-0016 の parseTagsText 実装仕様に基づく

        // Arrange
        val viewModel = EditScreenViewModel()
        viewModel.initialize(standardProcessed, standardConfig)
        // 【初期条件設定】: ユーザーがタグフィールドを全削除した状態をシミュレート
        viewModel.updateTagsText("")

        // Act
        val sendParams = viewModel.buildSendParams()

        // Assert
        // 【結果検証】: 空タグテキストが emptyList() に変換されていること
        assertEquals(
            "空文字のタグテキストは buildSendParams で emptyList() に変換されること（EDGE-003）",
            emptyList<String>(),
            sendParams.tags
        ) // 【確認内容】: parseTagsText("") = [] であること 🔵
    }

    // ================================================================
    // 3. 境界値テストケース
    // ================================================================

    /**
     * TC-008: 複数タグ（デフォルトタグ）の初期値が joinToString で正しく変換される
     * 🔵 青信号: REQ-103, TC-003-03, Kotlin の joinToString 仕様に基づく
     */
    @Test
    fun `TC-008 複数のデフォルトタグが joinToString でカンマ+スペース区切り文字列になる`() {
        // 【テスト目的】: config.defaultTags に複数タグがある場合、joinToString(", ") で正しく変換されること
        // 【テスト内容】: defaultTags = listOf("shared", "web") の NoteConfig で initialize() を呼び出す
        // 【期待される動作】: tagsText = "shared, web"（カンマ+スペース区切り）が設定される
        // 🔵 信頼性レベル: REQ-103・TC-003-03・Kotlin joinToString 仕様に基づく

        // Arrange
        // 【テストデータ準備】: 複数タグを持つ NoteConfig で初期値変換を確認
        val multiTagConfig = NoteConfig(
            vault = "testVault",
            folder = "70_clippings",
            defaultTags = listOf("shared", "web")
        )
        val viewModel = EditScreenViewModel()

        // Act
        viewModel.initialize(standardProcessed, multiTagConfig)

        // Assert
        assertEquals(
            "複数のデフォルトタグが joinToString でカンマ+スペース区切りになること",
            "shared, web",
            viewModel.formState.value.tagsText
        ) // 【確認内容】: listOf("shared", "web").joinToString(", ") = "shared, web" 🔵
    }

    /**
     * TC-011: デフォルトタグが空リストの場合の初期化
     * 🟡 黄信号: Kotlin の joinToString 仕様から妥当な推測（EDGE-003 と整合性がある）
     */
    @Test
    fun `TC-011 デフォルトタグが空リストの場合に tagsText が空文字で初期化される`() {
        // 【テスト目的】: defaultTags = emptyList() の場合、joinToString が "" を返すこと
        // 【テスト内容】: defaultTags が空リストの NoteConfig で initialize() を呼び出す
        // 【期待される動作】: tagsText = "" で初期化される（emptyList().joinToString(", ") = ""）
        // 🟡 信頼性レベル: Kotlin の joinToString 仕様から妥当な推測

        // Arrange
        // 【テストデータ準備】: タグ未設定を想定した空リストの NoteConfig
        val emptyTagConfig = NoteConfig(
            vault = "testVault",
            folder = "70_clippings",
            defaultTags = emptyList()
        )
        val viewModel = EditScreenViewModel()

        // Act
        viewModel.initialize(standardProcessed, emptyTagConfig)

        // Assert
        assertEquals(
            "defaultTags が空リストの場合は tagsText が空文字で初期化されること",
            "",
            viewModel.formState.value.tagsText
        ) // 【確認内容】: emptyList().joinToString(", ") = "" 🟡
    }

    /**
     * TC-012: initialize() 前の formState デフォルト値
     * 🟡 黄信号: ViewModel の初期状態について要件定義に明示的な記載はないが、StateFlow の初期値は必須
     */
    @Test
    fun `TC-012 initialize 呼び出し前の formState がデフォルト値を持つ`() {
        // 【テスト目的】: ViewModel 生成直後（initialize 未呼出し）にも formState にアクセスできること
        // 【テスト内容】: initialize() を呼び出さずに formState.value にアクセスする
        // 【期待される動作】: NullPointerException なしに formState.value が取得でき、空の初期値を持つ
        // 🟡 信頼性レベル: StateFlow の初期値必須要件から妥当な推測

        // Arrange
        // 【初期条件設定】: initialize() を一切呼ばずに ViewModel を作成
        val viewModel = EditScreenViewModel()

        // Act & Assert
        // 【実際の処理実行】: formState.value に直接アクセスして初期値を確認
        // 【処理内容】: MutableStateFlow のコンストラクタに渡した初期値が返される
        assertNotNull(
            "initialize 前の formState.value が non-null であること",
            viewModel.formState.value
        ) // 【確認内容】: StateFlow の初期値が null でないこと 🟡
        assertEquals(
            "initialize 前の formState.value.title が空文字であること",
            "",
            viewModel.formState.value.title
        ) // 【確認内容】: デフォルト title が空文字であること 🟡
        assertEquals(
            "initialize 前の formState.value.body が空文字であること",
            "",
            viewModel.formState.value.body
        ) // 【確認内容】: デフォルト body が空文字であること 🟡
    }

    /**
     * TC-013: 連続した update メソッド呼び出し
     * 🟡 黄信号: data class の copy() と StateFlow の動作から妥当な推測
     */
    @Test
    fun `TC-013 複数の update メソッドを連続して呼び出した場合にすべての変更が反映される`() {
        // 【テスト目的】: 複数フィールドの連続更新が互いに干渉せず、最終状態に全変更が反映されること
        // 【テスト内容】: 4つの update メソッドを連続して呼び出し、最終状態を確認する
        // 【期待される動作】: 各 copy() が前回の更新結果を正しく引き継いで累積的に更新される
        // 🟡 信頼性レベル: data class の copy() と StateFlow の動作から妥当な推測

        // Arrange
        val viewModel = EditScreenViewModel()
        viewModel.initialize(standardProcessed, standardConfig)

        // Act
        // 【実際の処理実行】: ユーザーが複数フィールドを順番に編集した場合をシミュレート
        // 【処理内容】: 各 copy() が独立して動作し、最終状態に全フィールドの変更が反映される
        viewModel.updateTitle("新タイトル")
        viewModel.updateBody("新本文")
        viewModel.updateTagsText("tag1, tag2")
        viewModel.updateFolder("new_folder")

        // Assert
        // 【結果検証】: 全フィールドが連続更新後の最終値を保持していること
        assertEquals(
            "連続 update 後に title が最終値になっていること",
            "新タイトル",
            viewModel.formState.value.title
        ) // 【確認内容】: 最後の updateTitle が反映されていること 🟡
        assertEquals(
            "連続 update 後に body が最終値になっていること",
            "新本文",
            viewModel.formState.value.body
        ) // 【確認内容】: 最後の updateBody が反映されていること 🟡
        assertEquals(
            "連続 update 後に tagsText が最終値になっていること",
            "tag1, tag2",
            viewModel.formState.value.tagsText
        ) // 【確認内容】: 最後の updateTagsText が反映されていること 🟡
        assertEquals(
            "連続 update 後に folder が最終値になっていること",
            "new_folder",
            viewModel.formState.value.folder
        ) // 【確認内容】: 最後の updateFolder が反映されていること 🟡
    }

    // TC-CUSTOM-001: initialize() で customFields が FormState に設定される
    @Test
    fun `TC-CUSTOM-001 initialize で customFields が formState に設定される`() {
        val viewModel = EditScreenViewModel()
        val customFields = listOf(
            CustomFieldState("source", "https://example.com", FieldValueType.STRING)
        )
        viewModel.initialize(standardProcessed, standardConfig, customFields)
        assertEquals(customFields, viewModel.formState.value.customFields)
    }

    // TC-CUSTOM-002: buildSendParams() が customFields を含む SendParams を返す
    @Test
    fun `TC-CUSTOM-002 buildSendParams が customFields を含む SendParams を返す`() {
        val viewModel = EditScreenViewModel()
        val customFields = listOf(
            CustomFieldState("source", "https://example.com", FieldValueType.STRING)
        )
        viewModel.initialize(standardProcessed, standardConfig, customFields)
        val params = viewModel.buildSendParams()
        assertEquals(customFields, params.customFields)
    }
}

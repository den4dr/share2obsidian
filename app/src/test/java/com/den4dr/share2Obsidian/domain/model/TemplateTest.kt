package com.den4dr.share2Obsidian.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateTest {

    @Test
    fun `Template defaults - id is 0 and isDefault is false`() {
        val template = Template(
            name = "Web記事",
            fields = emptyList(),
        )
        assertEquals(0L, template.id)
        assertEquals(false, template.isDefault)
    }

    @Test
    fun `Template body defaults to empty string`() {
        val template = Template(name = "test", fields = emptyList(), isDefault = false)
        assertEquals("", template.body)
    }

    @Test
    fun `TemplateField metaKey is null when valueSource is not HTML_META`() {
        val field = TemplateField(
            key = "status",
            valueSource = FieldValueSource.FIXED,
            valueType = FieldValueType.STRING,
            defaultValue = "draft",
        )
        assertNull(field.metaKey)
    }

    @Test
    fun `FieldValueSource contains all expected values`() {
        // 【テスト目的】: FieldValueSource enum に LLM を含む全5値が定義されていることを確認する
        // 【テスト内容】: 既存4値（FIXED, HTML_META, URL, EMPTY）に加え LLM が追加されたことを .entries で検証する
        // 【期待される動作】: names に5つの enum 名が含まれ、うち1つが "LLM" であること
        // 🔵 信頼性レベル: 既存TemplateTest.kt実装・REQ-303（TASK-0056 TC2-B）に基づく（推測なし）
        val names = FieldValueSource.entries.map { it.name }
        assert("FIXED" in names) // 【確認内容】: 既存値 FIXED が保持されていること
        assert("HTML_META" in names) // 【確認内容】: 既存値 HTML_META が保持されていること
        assert("URL" in names) // 【確認内容】: 既存値 URL が保持されていること
        assert("EMPTY" in names) // 【確認内容】: 既存値 EMPTY が保持されていること
        assert("LLM" in names) // 【確認内容】: 新規値 LLM が追加されていること（REQ-303） 🔵
        assertEquals(5, names.size) // 【確認内容】: LLM追加により enum 総数が4から5に増えたこと 🔵
    }

    @Test
    fun `FieldValueType contains STRING and LIST`() {
        val names = FieldValueType.entries.map { it.name }
        assert("STRING" in names)
        assert("LIST" in names)
        assertEquals(2, names.size)
    }

    @Test
    fun `HtmlMetaKey contains all expected keys`() {
        val names = HtmlMetaKey.entries.map { it.name }
        assert("OG_TITLE" in names)
        assert("OG_DESCRIPTION" in names)
        assert("URL" in names)
        assert("PUBLISHED_DATE" in names)
        assert("MODIFIED_DATE" in names)
        assert("AUTHOR" in names)
        assertEquals(6, names.size)
    }

    @Test
    fun `CustomFieldState holds key value and type`() {
        val state = CustomFieldState(
            key = "source",
            value = "https://example.com",
            valueType = FieldValueType.STRING,
        )
        assertEquals("source", state.key)
        assertEquals("https://example.com", state.value)
        assertEquals(FieldValueType.STRING, state.valueType)
    }

    @Test
    fun `Template copy with updated isDefault`() {
        val original = Template(name = "Test", fields = emptyList())
        val updated = original.copy(isDefault = true)
        assertEquals(true, updated.isDefault)
        assertEquals(original.name, updated.name)
    }

    // ===== TASK-0056: Template/TemplateField/FieldValueSource への LLM 関連プロパティ追加 =====

    @Test
    fun `Template copy with bodyLlmPrompt updates only that property`() {
        // 【テスト目的】: Template.copy() で bodyLlmPrompt のみを部分更新できることを確認する
        // 【テスト内容】: bodyLlmPrompt 未設定（デフォルト""）の Template を copy(bodyLlmPrompt=...) し、対象のみ更新・他は不変であることを検証する
        // 【期待される動作】: 指定プロパティ bodyLlmPrompt のみが差し替わり、name/body/fields/isDefault/id は保持される
        // 🔵 信頼性レベル: TASK-0056.md テストケース1・requirements.md TC1・REQ-101 に基づく（推測なし）

        // 【テストデータ準備】: bodyLlmPrompt 未指定（デフォルト""）の Template を用意する
        // 【初期条件設定】: name="Web記事", fields=emptyList() の最小構成
        val original = Template(name = "Web記事", fields = emptyList())

        // 【実際の処理実行】: original.copy(bodyLlmPrompt = "要約してください") を呼び出す
        // 【処理内容】: data class 自動生成の copy() による bodyLlmPrompt の部分更新
        val updated = original.copy(bodyLlmPrompt = "要約してください")

        // 【結果検証】: bodyLlmPrompt が更新され、他プロパティが保持されることを検証する
        // 【期待値確認】: 更新値="要約してください"、他は元の値と一致
        assertEquals("要約してください", updated.bodyLlmPrompt) // 【確認内容】: bodyLlmPrompt が指定値へ更新されたこと 🔵
        assertEquals("Web記事", updated.name) // 【確認内容】: name が元の値のまま保持されたこと 🔵
        assertEquals("", updated.body) // 【確認内容】: body が元の値（デフォルト空文字）のまま保持されたこと 🔵
        assertEquals(emptyList<TemplateField>(), updated.fields) // 【確認内容】: fields が元の値のまま保持されたこと 🔵
        assertEquals(false, updated.isDefault) // 【確認内容】: isDefault が元の値のまま保持されたこと 🔵
        assertEquals(0L, updated.id) // 【確認内容】: id が元の値のまま保持されたこと 🔵
    }

    @Test
    fun `TemplateField copy with llmPrompt updates only that property`() {
        // 【テスト目的】: TemplateField.copy() で llmPrompt のみを部分更新できることを確認する
        // 【テスト内容】: valueSource==LLM かつ llmPrompt 未設定（デフォルト""）の TemplateField を copy(llmPrompt=...) し、対象のみ更新・他は不変であることを検証する
        // 【期待される動作】: 指定プロパティ llmPrompt のみが差し替わり、key/valueSource/valueType/metaKey/sortOrder は保持される
        // 🔵 信頼性レベル: TASK-0056.md テストケース3・requirements.md TC3・REQ-104 に基づく（推測なし）

        // 【テストデータ準備】: llmPrompt 未指定（デフォルト""）の TemplateField を用意する
        // 【初期条件設定】: key="title", valueSource=FieldValueSource.LLM, valueType=FieldValueType.STRING の構成
        val original = TemplateField(
            key = "title",
            valueSource = FieldValueSource.LLM,
            valueType = FieldValueType.STRING,
        )

        // 【実際の処理実行】: original.copy(llmPrompt = "タイトルを生成してください") を呼び出す
        // 【処理内容】: data class 自動生成の copy() による llmPrompt の部分更新
        val updated = original.copy(llmPrompt = "タイトルを生成してください")

        // 【結果検証】: llmPrompt が更新され、他プロパティが保持されることを検証する
        // 【期待値確認】: 更新値="タイトルを生成してください"、他は元の値と一致
        assertEquals("タイトルを生成してください", updated.llmPrompt) // 【確認内容】: llmPrompt が指定値へ更新されたこと 🔵
        assertEquals("title", updated.key) // 【確認内容】: key が元の値のまま保持されたこと 🔵
        assertEquals(FieldValueSource.LLM, updated.valueSource) // 【確認内容】: valueSource が元の値のまま保持されたこと 🔵
        assertEquals(FieldValueType.STRING, updated.valueType) // 【確認内容】: valueType が元の値のまま保持されたこと 🔵
        assertNull(updated.metaKey) // 【確認内容】: metaKey が元の値（null）のまま保持されたこと 🔵
        assertEquals(0, updated.sortOrder) // 【確認内容】: sortOrder が元の値のまま保持されたこと 🔵
    }

    @Test
    fun `Template and TemplateField accept bodyLlmPrompt and llmPrompt via constructor`() {
        // 【テスト目的】: bodyLlmPrompt / llmPrompt を名前付き引数で明示指定してインスタンス生成できることを確認する
        // 【テスト内容】: コンストラクタ経由で新規プロパティに値を渡し、生成後の値が正しく格納されることを検証する
        // 【期待される動作】: 渡した値がそのままプロパティに保持される
        // 🟡 信頼性レベル: data class 標準仕様からの妥当な推測。requirements.md TC5 に基づく

        // 【テストデータ準備】: bodyLlmPrompt / llmPrompt に値ありで Template / TemplateField を生成する
        // 【初期条件設定】: Repository マッピングや UI から値ありで生成される後続タスクの前提を先取り確認
        val template = Template(name = "記事", bodyLlmPrompt = "本文を整形", fields = emptyList())
        val field = TemplateField(
            key = "tag",
            valueSource = FieldValueSource.LLM,
            valueType = FieldValueType.LIST,
            llmPrompt = "タグ候補",
        )

        // 【実際の処理実行】: コンストラクタ生成のみ（追加の処理呼び出しはなし）
        // 【処理内容】: 名前付き引数によるインスタンス生成

        // 【結果検証】: 指定した値がそのままプロパティへ格納されていることを検証する
        // 【期待値確認】: 渡した文字列と一致すること
        assertEquals("本文を整形", template.bodyLlmPrompt) // 【確認内容】: Template.bodyLlmPrompt が指定値で生成されたこと 🟡
        assertEquals("タグ候補", field.llmPrompt) // 【確認内容】: TemplateField.llmPrompt が指定値で生成されたこと 🟡
    }

    @Test
    fun `FieldValueSource valueOf with invalid name throws IllegalArgumentException`() {
        // 【テスト目的】: LLM 追加後も未定義の enum 名を valueOf に渡すと例外が発生することを確認する
        // 【テスト内容】: 存在しない文字列 "INVALID" を FieldValueSource.valueOf に渡し、標準の例外契約が維持されることを検証する
        // 【期待される動作】: IllegalArgumentException がスローされる
        // 🔵 信頼性レベル: requirements.md 4.3 エラーケース・Kotlin enum 標準仕様・TC-ERR1 に基づく（推測なし）

        // 【テストデータ準備】: FieldValueSource に定義されていない文字列を用意する
        // 【初期条件設定】: DB に破損した/旧バージョンの未知文字列が保存されていた場合を想定
        var thrown: IllegalArgumentException? = null

        // 【実際の処理実行】: FieldValueSource.valueOf("INVALID") を呼び出す
        // 【処理内容】: enum の文字列→値変換処理
        try {
            FieldValueSource.valueOf("INVALID")
        } catch (e: IllegalArgumentException) {
            thrown = e
        }

        // 【結果検証】: IllegalArgumentException がスローされたことを検証する
        // 【期待値確認】: 例外がnullでないこと（＝スローされたこと）
        assertEquals(true, thrown != null) // 【確認内容】: 未定義名指定時に IllegalArgumentException がスローされること 🔵
    }

    @Test
    fun `Template and TemplateField default bodyLlmPrompt and llmPrompt to empty string`() {
        // 【テスト目的】: 新規プロパティを指定せずに生成した場合、デフォルト値が空文字になり既存呼び出しへの互換性が保たれることを確認する
        // 【テスト内容】: 既存コードと同様の生成パターンで Template / TemplateField を生成し、bodyLlmPrompt / llmPrompt が "" になることを検証する
        // 【期待される動作】: 新規プロパティ未指定でもコンパイル・生成が成立し、値はデフォルトの空文字になる
        // 🔵 信頼性レベル: TASK-0056.md テストケース4・requirements.md TC4・後方互換性制約に基づく（推測なし）

        // 【テストデータ準備】: 既存 TemplateTest.kt と同一の生成パターンを用いる
        // 【初期条件設定】: 新規プロパティ（bodyLlmPrompt/llmPrompt）を一切指定しない
        val template = Template(name = "Web記事", fields = emptyList())
        val field = TemplateField(
            key = "status",
            valueSource = FieldValueSource.FIXED,
            valueType = FieldValueType.STRING,
            defaultValue = "draft",
        )

        // 【実際の処理実行】: コンストラクタ生成のみ（追加の処理呼び出しはなし）
        // 【処理内容】: 新規プロパティを省略したインスタンス生成

        // 【結果検証】: bodyLlmPrompt / llmPrompt がデフォルトの空文字であることを検証する
        // 【期待値確認】: 既存の body / defaultValue と同様に空文字デフォルトで一貫していること
        assertEquals("", template.bodyLlmPrompt) // 【確認内容】: Template.bodyLlmPrompt が未指定時デフォルト""であること 🔵
        assertEquals("", field.llmPrompt) // 【確認内容】: TemplateField.llmPrompt が未指定時デフォルト""であること 🔵
    }

    @Test
    fun `Template and TemplateField accept explicit empty string for bodyLlmPrompt and llmPrompt`() {
        // 【テスト目的】: bodyLlmPrompt / llmPrompt に空文字を明示指定した場合もデフォルトと同値になることを確認する
        // 【テスト内容】: UI でプロンプト欄を空のまま保存したケースを想定し、明示的な""指定とデフォルト適用が同一結果になることを検証する
        // 【期待される動作】: 明示指定の""とデフォルトの""が区別なく同値として扱われる
        // 🟡 信頼性レベル: requirements.md 4.2 エッジケースからの妥当な推測（TC-EDGE1）

        // 【テストデータ準備】: bodyLlmPrompt / llmPrompt に空文字を明示的に渡す
        // 【初期条件設定】: 「未設定」を表す空文字がREQ-102のボタン非活性判定の入力となる想定
        val template = Template(name = "t", fields = emptyList(), bodyLlmPrompt = "")
        val field = TemplateField(
            key = "k",
            valueSource = FieldValueSource.EMPTY,
            valueType = FieldValueType.STRING,
            llmPrompt = "",
        )

        // 【実際の処理実行】: コンストラクタ生成のみ（追加の処理呼び出しはなし）
        // 【処理内容】: 名前付き引数で空文字を明示指定したインスタンス生成

        // 【結果検証】: 明示指定の空文字がデフォルト生成時と等価であることを検証する
        // 【期待値確認】: いずれも""であること
        assertEquals("", template.bodyLlmPrompt) // 【確認内容】: 明示的に指定した空文字が反映されていること 🟡
        assertEquals("", field.llmPrompt) // 【確認内容】: 明示的に指定した空文字が反映されていること 🟡
    }
}

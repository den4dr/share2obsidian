package com.den4dr.share2Obsidian.data.repository

import com.den4dr.share2Obsidian.data.db.TemplateDao
import com.den4dr.share2Obsidian.data.db.TemplateEntity
import com.den4dr.share2Obsidian.data.db.TemplateFieldEntity
import com.den4dr.share2Obsidian.data.db.TemplateWithFields
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.domain.model.TemplateField
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemplateRepositoryImplTest {

    private lateinit var dao: TemplateDao
    private lateinit var repository: TemplateRepositoryImpl

    @Before
    fun setUp() {
        dao = mockk()
        repository = TemplateRepositoryImpl(dao)
    }

    // TC-1: getAllTemplates が Flow を正しく変換（body マッピング含む）
    @Test
    fun getAllTemplates_convertsTodomainModels() = runBlocking {
        val entity = TemplateEntity(id = 1L, name = "テスト", body = "## 記事\n{{content}}", isDefault = false)
        val withFields = TemplateWithFields(template = entity, fields = emptyList())
        every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

        val result = repository.getAllTemplates().first()

        assertEquals(1, result.size)
        assertEquals("テスト", result[0].name)
        assertEquals("## 記事\n{{content}}", result[0].body)
        assertFalse(result[0].isDefault)
    }

    // TC-2: saveTemplate で isDefault=true の場合 clearDefaultExcept が呼ばれる
    @Test
    fun saveTemplate_withIsDefault_callsClearDefaultExcept() = runBlocking {
        val template = Template(
            id = 1L,
            name = "テスト",
            body = "b",
            isDefault = true,
            fields = emptyList(),
        )
        coEvery { dao.clearDefaultExcept(1L) } just Runs
        coEvery { dao.insertTemplate(any()) } returns 1L
        coEvery { dao.deleteFieldsByTemplateId(any()) } just Runs
        coEvery { dao.insertFields(any()) } just Runs

        repository.saveTemplate(template)

        coVerify { dao.clearDefaultExcept(1L) }
    }

    // TC-2b: saveTemplate で isDefault=false の場合 clearDefaultExcept が呼ばれない
    @Test
    fun saveTemplate_withoutIsDefault_doesNotCallClearDefaultExcept() = runBlocking {
        val template = Template(
            id = 0L,
            name = "テスト",
            body = "",
            isDefault = false,
            fields = emptyList(),
        )
        coEvery { dao.insertTemplate(any()) } returns 2L
        coEvery { dao.deleteFieldsByTemplateId(any()) } just Runs
        coEvery { dao.insertFields(any()) } just Runs

        repository.saveTemplate(template)

        coVerify(exactly = 0) { dao.clearDefaultExcept(any()) }
    }

    // TC-3: deleteTemplate で dao.deleteTemplate が呼ばれる（body マッピング含む）
    @Test
    fun deleteTemplate_callsDaoDeleteTemplate() = runBlocking {
        val template = Template(
            id = 5L,
            name = "テスト",
            body = "body",
            isDefault = false,
            fields = emptyList(),
        )
        coEvery { dao.deleteTemplate(any()) } just Runs

        repository.deleteTemplate(template)

        coVerify {
            dao.deleteTemplate(
                TemplateEntity(id = 5L, name = "テスト", body = "body", isDefault = false)
            )
        }
    }

    // TC-4: TemplateWithFields.toDomain() マッピング確認
    @Test
    fun getAllTemplates_mapsAllFieldsCorrectly() = runBlocking {
        val templateEntity = TemplateEntity(id = 1L, name = "テスト", body = "本文", isDefault = true)
        val fieldEntity = TemplateFieldEntity(
            id = 10L,
            templateId = 1L,
            key = "source",
            valueSource = "HTML_META",
            valueType = "STRING",
            defaultValue = "",
            metaKey = "OG_TITLE",
            sortOrder = 0,
        )
        val withFields = TemplateWithFields(template = templateEntity, fields = listOf(fieldEntity))
        every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

        val result = repository.getAllTemplates().first()

        assertEquals(1, result.size)
        val template = result[0]
        assertEquals(1L, template.id)
        assertEquals("テスト", template.name)
        assertEquals("本文", template.body)
        assertTrue(template.isDefault)
        assertEquals(1, template.fields.size)

        val field = template.fields[0]
        assertEquals(10L, field.id)
        assertEquals(1L, field.templateId)
        assertEquals("source", field.key)
        assertEquals(FieldValueSource.HTML_META, field.valueSource)
        assertEquals(FieldValueType.STRING, field.valueType)
        assertEquals("", field.defaultValue)
        assertEquals(HtmlMetaKey.OG_TITLE, field.metaKey)
        assertEquals(0, field.sortOrder)
    }

    // TC-4b: metaKey が空文字の場合 null にマッピング
    @Test
    fun getAllTemplates_emptyMetaKey_mapsToNull() = runBlocking {
        val templateEntity = TemplateEntity(id = 1L, name = "t", body = "", isDefault = false)
        val fieldEntity = TemplateFieldEntity(
            id = 1L,
            templateId = 1L,
            key = "k",
            valueSource = "FIXED",
            valueType = "STRING",
            defaultValue = "val",
            metaKey = "",
            sortOrder = 0,
        )
        val withFields = TemplateWithFields(template = templateEntity, fields = listOf(fieldEntity))
        every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

        val result = repository.getAllTemplates().first()

        assertNull(result[0].fields[0].metaKey)
    }

    // ==========================================================================================
    // TASK-0057 (database-migration-v3) Red フェーズ: 以下は未実装の bodyLlmPrompt/llmPrompt
    // カラムを参照するため、TemplateEntity/TemplateFieldEntity への該当プロパティ追加と
    // TemplateRepositoryImpl のマッピング修正が完了するまでコンパイルエラーとなる想定のテストである。
    // ==========================================================================================

    // TC-N01: bodyLlmPrompt が Entity→Domain 変換で保持される
    @Test
    fun getAllTemplates_mapsBodyLlmPrompt() = runBlocking {
        // 【テスト目的】: TemplateEntity.bodyLlmPrompt が toDomain() で Template.bodyLlmPrompt に正しくマッピングされることを確認する
        // 【テスト内容】: bodyLlmPrompt を持つ TemplateEntity を含む TemplateWithFields を dao から返し、getAllTemplates() の結果を検証する
        // 【期待される動作】: DB から読み込んだ bodyLlmPrompt 値が損失なくドメインモデルに反映される
        // 🔵 信頼性レベル: database-migration-v3-testcases.md TC-N01・database-schema.kt マッピング定義に基づく

        // 【テストデータ準備】: REQ-101 のテンプレート本文用LLMプロンプトを代表する実データを用意する
        // 【初期条件設定】: TemplateEntity に bodyLlmPrompt を設定し、dao をモックする
        val entity = TemplateEntity(
            id = 1L,
            name = "テスト",
            body = "本文",
            bodyLlmPrompt = "本文を要約",
            isDefault = false,
        )
        val withFields = TemplateWithFields(template = entity, fields = emptyList())
        every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

        // 【実際の処理実行】: getAllTemplates() の Flow を収集し toDomain() を発火させる
        // 【処理内容】: dao から取得した TemplateWithFields を Template に変換する
        val result = repository.getAllTemplates().first()

        // 【結果検証】: bodyLlmPrompt が損失なく保持されていることを確認する
        // 【期待値確認】: 既存カラム（name/body）も従来通り保持されていることを併せて確認する
        assertEquals("本文を要約", result[0].bodyLlmPrompt) // 【確認内容】: 新規カラム bodyLlmPrompt が正しくマッピングされること
        assertEquals("テスト", result[0].name) // 【確認内容】: 既存カラム name のマッピングが壊れていないこと
        assertEquals("本文", result[0].body) // 【確認内容】: 既存カラム body のマッピングが壊れていないこと
    }

    // TC-N02: llmPrompt と valueSource=LLM が TemplateField 往復変換で保持される
    @Test
    fun getAllTemplates_mapsLlmPromptAndLlmValueSource() = runBlocking {
        // 【テスト目的】: TemplateFieldEntity.llmPrompt が toDomain() で TemplateField.llmPrompt にマッピングされ、valueSource="LLM" が FieldValueSource.LLM に復元されることを確認する
        // 【テスト内容】: llmPrompt と valueSource="LLM" を持つ TemplateFieldEntity を含む TemplateWithFields を検証する
        // 【期待される動作】: フィールド単位のLLMプロンプトと LLM 値ソースが損失なく往復する
        // 🔵 信頼性レベル: database-migration-v3-testcases.md TC-N02 に基づく

        // 【テストデータ準備】: REQ-104/REQ-303 の代表実データ（valueSource=LLM, llmPrompt設定あり）を用意する
        // 【初期条件設定】: TemplateFieldEntity に llmPrompt を設定し、dao をモックする
        val templateEntity = TemplateEntity(id = 1L, name = "テスト", body = "本文", isDefault = false)
        val fieldEntity = TemplateFieldEntity(
            id = 10L,
            templateId = 1L,
            key = "title",
            valueSource = "LLM",
            valueType = "STRING",
            defaultValue = "",
            metaKey = "",
            llmPrompt = "タイトルを生成",
            sortOrder = 0,
        )
        val withFields = TemplateWithFields(template = templateEntity, fields = listOf(fieldEntity))
        every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

        // 【実際の処理実行】: getAllTemplates() の Flow を収集し toDomain() を発火させる
        // 【処理内容】: TemplateFieldEntity → TemplateField への変換を実行する
        val result = repository.getAllTemplates().first()

        // 【結果検証】: llmPrompt と valueSource が損失なく保持されていることを確認する
        val field = result[0].fields[0]
        assertEquals("タイトルを生成", field.llmPrompt) // 【確認内容】: 新規カラム llmPrompt が正しくマッピングされること
        assertEquals(FieldValueSource.LLM, field.valueSource) // 【確認内容】: valueSource="LLM" 文字列が FieldValueSource.LLM enum に正しく解決されること
    }

    // TC-N03: saveTemplate（toEntity）で bodyLlmPrompt/llmPrompt が Entity へ書き込まれる
    @Test
    fun saveTemplate_writesBodyLlmPromptAndFieldLlmPromptToEntity() = runBlocking {
        // 【テスト目的】: toEntity() が bodyLlmPrompt/llmPrompt を Entity に正しく設定し、dao へ渡すことを確認する
        // 【テスト内容】: bodyLlmPrompt を持つ Template と llmPrompt を持つ TemplateField を saveTemplate() で保存する
        // 【期待される動作】: 保存経路（Domain→Entity）でLLMプロンプトが欠落しない
        // 🔵 信頼性レベル: database-migration-v3-testcases.md TC-N03・要件定義書 2.3 マッピング表に基づく

        // 【テストデータ準備】: 保存方向のマッピングを検証するため bodyLlmPrompt/llmPrompt を持つデータを用意する
        // 【初期条件設定】: dao の insertTemplate/insertFields をモックし、渡された Entity をスロットで捕捉する
        val template = Template(
            id = 1L,
            name = "t",
            body = "b",
            bodyLlmPrompt = "要約して",
            isDefault = false,
            fields = listOf(
                TemplateField(
                    key = "title",
                    valueSource = FieldValueSource.LLM,
                    valueType = FieldValueType.STRING,
                    llmPrompt = "生成して",
                )
            ),
        )
        val templateEntitySlot = slot<TemplateEntity>()
        val fieldEntitiesSlot = slot<List<TemplateFieldEntity>>()
        coEvery { dao.insertTemplate(capture(templateEntitySlot)) } returns 1L
        coEvery { dao.deleteFieldsByTemplateId(any()) } just Runs
        coEvery { dao.insertFields(capture(fieldEntitiesSlot)) } just Runs

        // 【実際の処理実行】: saveTemplate() を実行し toEntity() マッピングを発火させる
        // 【処理内容】: Template/TemplateField を Entity に変換して dao に渡す
        repository.saveTemplate(template)

        // 【結果検証】: dao に渡された Entity が bodyLlmPrompt/llmPrompt を保持していることを確認する
        assertEquals("要約して", templateEntitySlot.captured.bodyLlmPrompt) // 【確認内容】: TemplateEntity へ bodyLlmPrompt が書き込まれること
        assertEquals("生成して", fieldEntitiesSlot.captured[0].llmPrompt) // 【確認内容】: TemplateFieldEntity へ llmPrompt が書き込まれること
    }

    // TC-E02: 未知の valueSource 文字列での toDomain 変換例外（LLM カラム追加後も既存挙動が維持されること）
    @Test(expected = IllegalArgumentException::class)
    fun getAllTemplates_unknownValueSource_throwsIllegalArgumentException(): Unit = runBlocking {
        // 【テスト目的】: 未知の valueSource 文字列に対して FieldValueSource.valueOf() が例外を投げる既存挙動が LLM カラム追加後も維持されることを確認する
        // 【テスト内容】: valueSource="UNKNOWN" を持つ TemplateFieldEntity を toDomain() 変換する
        // 【期待される動作】: IllegalArgumentException がスローされる
        // 🟡 信頼性レベル: database-migration-v3-testcases.md TC-E02（既存実装挙動からの妥当な推測）に基づく

        // 【テストデータ準備】: enum に存在しない valueSource 文字列を用意する
        // 【初期条件設定】: TemplateFieldEntity に llmPrompt を明示指定し、新規カラム追加が例外挙動に影響しないことも合わせて確認する
        val templateEntity = TemplateEntity(id = 1L, name = "t", body = "", isDefault = false)
        val fieldEntity = TemplateFieldEntity(
            id = 1L,
            templateId = 1L,
            key = "k",
            valueSource = "UNKNOWN",
            valueType = "STRING",
            defaultValue = "",
            metaKey = "",
            llmPrompt = "",
            sortOrder = 0,
        )
        val withFields = TemplateWithFields(template = templateEntity, fields = listOf(fieldEntity))
        every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

        // 【実際の処理実行】: getAllTemplates().first() の収集時に toDomain() が発火し valueOf が呼ばれる
        // 【処理内容】: FieldValueSource.valueOf("UNKNOWN") が呼び出される
        repository.getAllTemplates().first()

        // 【結果検証】: IllegalArgumentException が発生することを @Test(expected=...) で確認する（本文には到達しない）
    }

    // TC-B01: bodyLlmPrompt/llmPrompt 未設定（デフォルト ""）の往復保持
    @Test
    fun getAllTemplates_defaultBodyLlmPromptAndLlmPrompt_remainsEmpty() = runBlocking {
        // 【テスト目的】: bodyLlmPrompt/llmPrompt を指定しない場合、デフォルト値 "" が往復変換後も保持されることを確認する
        // 【テスト内容】: bodyLlmPrompt/llmPrompt を省略した TemplateEntity/TemplateFieldEntity を変換する
        // 【期待される動作】: 空文字が null化・欠落せず "" のまま復元される（後方互換性）
        // 🔵 信頼性レベル: database-migration-v3-testcases.md TC-B01・要件定義書 TC3 に基づく

        // 【テストデータ準備】: 既存呼び出し元同様、bodyLlmPrompt/llmPrompt を指定しないデータを用意する
        // 【初期条件設定】: Entity のデフォルト引数のみを利用する
        val templateEntity = TemplateEntity(id = 1L, name = "t", body = "b", isDefault = false)
        val fieldEntity = TemplateFieldEntity(
            id = 1L,
            templateId = 1L,
            key = "k",
            valueSource = "FIXED",
            valueType = "STRING",
            defaultValue = "v",
            metaKey = "",
            sortOrder = 0,
        )
        val withFields = TemplateWithFields(template = templateEntity, fields = listOf(fieldEntity))
        every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

        // 【実際の処理実行】: getAllTemplates() を実行し toDomain() のデフォルト値マッピングを確認する
        // 【処理内容】: bodyLlmPrompt/llmPrompt を指定しない Entity を変換する
        val result = repository.getAllTemplates().first()

        // 【結果検証】: bodyLlmPrompt/llmPrompt が "" のまま保持されることを確認する
        assertEquals("", result[0].bodyLlmPrompt) // 【確認内容】: Template.bodyLlmPrompt がデフォルト "" のまま保持されること
        assertEquals("", result[0].fields[0].llmPrompt) // 【確認内容】: TemplateField.llmPrompt がデフォルト "" のまま保持されること
    }

    // TC-B02(ユニット部分): フィールドが空（0件）のテンプレートのマッピング
    @Test
    fun getAllTemplates_emptyFieldsList_mapsBodyLlmPromptCorrectly() = runBlocking {
        // 【テスト目的】: フィールドが0件のテンプレートでも bodyLlmPrompt のマッピングが正常に動作することを確認する
        // 【テスト内容】: fields が空リストの TemplateWithFields で bodyLlmPrompt を検証する
        // 【期待される動作】: フィールド有無に関わらず bodyLlmPrompt マッピングが成功する
        // 🟡 信頼性レベル: database-migration-v3-testcases.md TC-B02（要件定義書エッジケースからの妥当な推測）に基づく

        // 【テストデータ準備】: カスタムフィールドを持たないシンプルなテンプレートを用意する
        // 【初期条件設定】: fields = emptyList() のまま bodyLlmPrompt を設定する
        val entity = TemplateEntity(id = 1L, name = "t", body = "b", bodyLlmPrompt = "要約", isDefault = false)
        val withFields = TemplateWithFields(template = entity, fields = emptyList())
        every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

        // 【実際の処理実行】: getAllTemplates() を実行する
        // 【処理内容】: fields が空リストの状態で bodyLlmPrompt マッピングを実行する
        val result = repository.getAllTemplates().first()

        // 【結果検証】: bodyLlmPrompt が保持され、fields が空のままであることを確認する
        assertEquals("要約", result[0].bodyLlmPrompt) // 【確認内容】: フィールド0件でも bodyLlmPrompt マッピングが成功すること
        assertTrue(result[0].fields.isEmpty()) // 【確認内容】: fields が空のまま維持されること
    }

    // TC-B03: 長文・改行・マルチバイトを含む LLM プロンプト文字列の保持
    @Test
    fun getAllTemplates_multilineAndUnicodeBodyLlmPrompt_preservedExactly() = runBlocking {
        // 【テスト目的】: 改行・マルチバイト・絵文字を含む長文プロンプト文字列が損失なく往復することを確認する
        // 【テスト内容】: 改行・全角・絵文字を含む bodyLlmPrompt を持つ TemplateEntity を変換する
        // 【期待される動作】: マッピングが単純代入であり、トリムやエスケープ等の副作用が発生しない
        // 🟡 信頼性レベル: database-migration-v3-testcases.md TC-B03（TEXT列+String単純代入からの妥当な推測）に基づく

        // 【テストデータ準備】: 改行・全角・絵文字混在の実用的なプロンプト文字列を用意する
        // 【初期条件設定】: bodyLlmPrompt に特殊文字列を設定する
        val prompt = "本文を3行で要約し、\n箇条書きにしてください。😀"
        val entity = TemplateEntity(id = 1L, name = "t", body = "b", bodyLlmPrompt = prompt, isDefault = false)
        val withFields = TemplateWithFields(template = entity, fields = emptyList())
        every { dao.getAllTemplatesWithFields() } returns flowOf(listOf(withFields))

        // 【実際の処理実行】: getAllTemplates() を実行する
        // 【処理内容】: 特殊文字を含む bodyLlmPrompt をマッピングする
        val result = repository.getAllTemplates().first()

        // 【結果検証】: 文字列がバイト等価で保持されることを確認する
        assertEquals(prompt, result[0].bodyLlmPrompt) // 【確認内容】: 改行・絵文字を含め文字列が完全一致すること
    }
}

package com.den4dr.share2Obsidian

import com.den4dr.share2Obsidian.content.ContentKind
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.data.datastore.NoteSettings
import com.den4dr.share2Obsidian.domain.model.CustomFieldState
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey
import com.den4dr.share2Obsidian.domain.model.Template
import com.den4dr.share2Obsidian.domain.model.TemplateField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateApplicatorTest {

    // TC-031-01: buildConfig は DataStore 設定（NoteSettings）から vault/folder を取得する
    @Test
    fun buildConfig_usesNoteSettings() {
        val config = TemplateApplicator.buildConfig(NoteSettings(vault = "MyVault", folder = "Notes"))
        assertEquals("MyVault", config.vault)
        assertEquals("Notes", config.folder)
        assertEquals(AppConfig.OBSIDIAN_TAGS, config.defaultTags)
    }

    // EDGE-003: 未設定（空文字列）の NoteSettings では vault/folder が空
    @Test
    fun buildConfig_emptySettings_returnsEmptyVaultAndFolder() {
        val config = TemplateApplicator.buildConfig(NoteSettings())
        assertEquals("", config.vault)
        assertEquals("", config.folder)
    }

    // TC-011-01: body に {{content}} が1つある場合、共有コンテンツで置換される
    @Test
    fun buildBody_singlePlaceholder_replacesWithSharedContent() {
        val template = Template(id = 1L, name = "t", body = "## 記事\n{{content}}\n\n## メモ\n", fields = emptyList())
        val result = TemplateApplicator.buildBody(template, "テスト本文")
        assertEquals("## 記事\nテスト本文\n\n## メモ\n", result)
    }

    // TC-011-02: body が空文字列の場合、共有コンテンツをそのまま使用する
    @Test
    fun buildBody_emptyBody_returnsSharedContent() {
        val template = Template(id = 1L, name = "t", body = "", fields = emptyList())
        assertEquals("テスト本文", TemplateApplicator.buildBody(template, "テスト本文"))
    }

    // TC-011-03: テンプレートが null の場合、共有コンテンツをそのまま使用する
    @Test
    fun buildBody_nullTemplate_returnsSharedContent() {
        assertEquals("テスト本文", TemplateApplicator.buildBody(null, "テスト本文"))
    }

    // TC-011-E01: body に {{content}} が複数ある場合、すべて置換される
    @Test
    fun buildBody_multiplePlaceholders_replacesAll() {
        val template = Template(id = 1L, name = "t", body = "{{content}}\n---\n{{content}}", fields = emptyList())
        assertEquals("テスト\n---\nテスト", TemplateApplicator.buildBody(template, "テスト"))
    }

    // TC-011-E02: body に {{content}} がなくかつ非空の場合、body のみが使用される
    @Test
    fun buildBody_noPlaceholderNonEmpty_returnsTemplateBodyOnly() {
        val template = Template(id = 1L, name = "t", body = "固定テキスト", fields = emptyList())
        assertEquals("固定テキスト", TemplateApplicator.buildBody(template, "テスト本文"))
    }

    // TC-3: HTML_META フィールドが ProcessedContent.metadata から値を取得
    @Test
    fun buildCustomFields_htmlMeta_getsFromMetadata() {
        val template = Template(
            id = 1L, name = "t", isDefault = true,
            fields = listOf(
                TemplateField(
                    key = "title",
                    valueSource = FieldValueSource.HTML_META,
                    valueType = FieldValueType.STRING,
                    metaKey = HtmlMetaKey.OG_TITLE,
                )
            )
        )
        val processed = ProcessedContent(
            body = "body",
            contentType = ContentKind.URL,
            metadata = mapOf(HtmlMetaKey.OG_TITLE to "記事タイトル"),
        )
        val customFields = TemplateApplicator.buildCustomFields(template, processed)
        assertEquals(1, customFields.size)
        assertEquals("記事タイトル", customFields[0].value)
        assertEquals("title", customFields[0].key)
    }

    // TC-4: URL フィールドが sourceUrl から値を取得
    @Test
    fun buildCustomFields_url_getsFromSourceUrl() {
        val template = Template(
            id = 1L, name = "t", isDefault = true,
            fields = listOf(
                TemplateField(
                    key = "source",
                    valueSource = FieldValueSource.URL,
                    valueType = FieldValueType.STRING,
                )
            )
        )
        val processed = ProcessedContent(
            body = "body",
            contentType = ContentKind.URL,
            sourceUrl = "https://example.com",
        )
        val customFields = TemplateApplicator.buildCustomFields(template, processed)
        assertEquals("https://example.com", customFields[0].value)
    }

    // TC-5: テンプレートが null の場合は空リスト
    @Test
    fun buildCustomFields_withNull_returnsEmpty() {
        val processed = ProcessedContent(body = "body", contentType = ContentKind.TEXT)
        val customFields = TemplateApplicator.buildCustomFields(null, processed)
        assertTrue(customFields.isEmpty())
    }

    // ==========================================================================
    // TASK-0070: CustomFieldState 拡張・TemplateApplicator.buildCustomFields() の LLM対応
    // 対象テストケース定義: docs/implements/llm-memo-rewrite/TASK-0070/custom-field-llm-support-testcases.md
    // ==========================================================================

    // TC-0070-N01: LLMフィールドの変換で value が空文字・valueSource=LLM・llmPrompt がテンプレート値で生成される
    @Test
    fun buildCustomFields_llm_setsEmptyValueAndKeepsPrompt() {
        // 【テスト目的】: LLMフィールドが value="" かつ valueSource=LLM・llmPrompt保持で変換されることを確認
        // 【テスト内容】: FieldValueSource.LLM の TemplateField を buildCustomFields に渡して結果を検証
        // 【期待される動作】: テンプレート適用時はLLM生成せず、生成方法とプロンプトのみ橋渡しする
        // 🔵 信頼性レベル: TASK-0070 テストケース1・REQ-304 に基づく（推測なし）

        // 【テストデータ準備】: LLM生成対象フィールド1件を持つテンプレートを用意
        // 【初期条件設定】: llmPrompt に代表的な生成指示文を設定
        val template = Template(
            id = 1L, name = "t",
            fields = listOf(
                TemplateField(
                    key = "summary",
                    valueSource = FieldValueSource.LLM,
                    valueType = FieldValueType.STRING,
                    llmPrompt = "要約を作成してください",
                )
            )
        )
        val processed = ProcessedContent(body = "body", contentType = ContentKind.TEXT)

        // 【実際の処理実行】: TemplateApplicator.buildCustomFields を呼び出す
        // 【処理内容】: TemplateField を CustomFieldState へ変換
        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        // 【結果検証】: 値が空文字であること、生成メタ情報が保持されることを確認
        // 【期待値確認】: 要件定義書 2-2 の値算出テーブルに基づく期待値
        assertEquals(1, customFields.size) // 【確認内容】: LLMフィールドが1件変換されること
        assertEquals("", customFields[0].value) // 【確認内容】: テンプレート適用時は値未生成（空文字）であること
        assertEquals(FieldValueSource.LLM, customFields[0].valueSource) // 【確認内容】: 生成方法がLLMとして保持されること
        assertEquals("要約を作成してください", customFields[0].llmPrompt) // 【確認内容】: プロンプトが引き継がれること
    }

    // TC-0070-N02: 既存4種（FIXED/HTML_META/URL/EMPTY）の value 算出が回帰せず、valueSource/llmPrompt が正しく設定される
    @Test
    fun buildCustomFields_existingSources_regressionWithValueSourceAndPrompt() {
        // 【テスト目的】: FIXED/HTML_META/URL/EMPTY の既存ロジックが変更されないこと、かつ valueSource/llmPrompt が新規に正しく設定されることを確認
        // 【テスト内容】: 4種類の valueSource を持つフィールドが混在するテンプレートを buildCustomFields に渡す
        // 【期待される動作】: 各フィールドの value は既存ロジックのまま、valueSource/llmPrompt は対応する TemplateField の値と一致する
        // 🔵 信頼性レベル: TASK-0070 テストケース2・要件定義書 TC2・既存 TemplateApplicatorTest.kt（TC-3/TC-4）に基づく（推測なし）

        // 【テストデータ準備】: FIXED/HTML_META/URL/EMPTY の4フィールドを混在させたテンプレートを用意
        // 【初期条件設定】: HTML_META 用のメタデータと URL 用の sourceUrl を ProcessedContent に設定
        val template = Template(
            id = 1L, name = "t",
            fields = listOf(
                TemplateField(key = "tag", valueSource = FieldValueSource.FIXED, valueType = FieldValueType.STRING, defaultValue = "memo"),
                TemplateField(key = "title", valueSource = FieldValueSource.HTML_META, valueType = FieldValueType.STRING, metaKey = HtmlMetaKey.OG_TITLE),
                TemplateField(key = "source", valueSource = FieldValueSource.URL, valueType = FieldValueType.STRING),
                TemplateField(key = "note", valueSource = FieldValueSource.EMPTY, valueType = FieldValueType.STRING),
            )
        )
        val processed = ProcessedContent(
            body = "body",
            contentType = ContentKind.URL,
            metadata = mapOf(HtmlMetaKey.OG_TITLE to "記事タイトル"),
            sourceUrl = "https://example.com",
        )

        // 【実際の処理実行】: TemplateApplicator.buildCustomFields を呼び出す
        // 【処理内容】: 4種類の TemplateField を CustomFieldState へ変換
        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        // 【結果検証】: 各フィールドの value が既存ロジックどおりであること、valueSource/llmPrompt が付与されていることを確認
        // 【期待値確認】: 要件定義書 2-2 の値算出テーブル（LLM以外の4行）に基づく
        assertEquals(4, customFields.size) // 【確認内容】: 4フィールドすべてが変換されること
        assertEquals("memo", customFields[0].value) // 【確認内容】: FIXED は defaultValue を使用すること
        assertEquals(FieldValueSource.FIXED, customFields[0].valueSource) // 【確認内容】: valueSource が FIXED であること
        assertEquals("", customFields[0].llmPrompt) // 【確認内容】: llmPrompt が未設定の既定値であること
        assertEquals("記事タイトル", customFields[1].value) // 【確認内容】: HTML_META は metadata から取得すること
        assertEquals(FieldValueSource.HTML_META, customFields[1].valueSource) // 【確認内容】: valueSource が HTML_META であること
        assertEquals("https://example.com", customFields[2].value) // 【確認内容】: URL は sourceUrl を使用すること
        assertEquals(FieldValueSource.URL, customFields[2].valueSource) // 【確認内容】: valueSource が URL であること
        assertEquals("", customFields[3].value) // 【確認内容】: EMPTY は常に空文字であること
        assertEquals(FieldValueSource.EMPTY, customFields[3].valueSource) // 【確認内容】: valueSource が EMPTY であること
    }

    // TC-0070-N03: CustomFieldState を3引数（key, value, valueType）で生成でき、valueSource/llmPrompt が既定値になる
    @Test
    fun customFieldState_threeArgConstructor_usesDefaults() {
        // 【テスト目的】: data class 拡張による既存コード（3引数コンストラクタ呼び出し）との互換性を確認
        // 【テスト内容】: CustomFieldState(key, value, valueType) の3引数呼び出しでインスタンス生成する
        // 【期待される動作】: valueSource が既定値 FIXED、llmPrompt が既定値 "" になる
        // 🔵 信頼性レベル: 要件定義書 TC6・2-1「後方互換性」に基づく（推測なし）

        // 【テストデータ準備】: 既存呼び出し箇所（NoteComposer等）を模した最小構成の3引数呼び出し
        // 【初期条件設定】: 追加フィールドは指定しない
        val state = CustomFieldState(key = "k", value = "v", valueType = FieldValueType.STRING)

        // 【実際の処理実行】: 生成された CustomFieldState のプロパティを直接検証
        // 【処理内容】: コンストラクタのデフォルト引数解決結果を確認
        // 【結果検証】: 既定値が意図どおりに適用されていることを確認
        assertEquals("v", state.value) // 【確認内容】: value が渡した値のまま保持されること
        assertEquals(FieldValueSource.FIXED, state.valueSource) // 【確認内容】: valueSource が既定値 FIXED になること
        assertEquals("", state.llmPrompt) // 【確認内容】: llmPrompt が既定値の空文字になること
    }

    // TC-0070-E01: HTML_META フィールドで metadata に該当キーが無い場合は空文字へフォールバック
    @Test
    fun buildCustomFields_htmlMeta_missingKey_fallsBackToEmpty() {
        // 【テスト目的】: メタデータ欠落時に例外を出さず空文字へ安全にフォールバックすることを確認
        // 【テスト内容】: metaKey に対応する値が metadata に存在しない状態で buildCustomFields を呼び出す
        // 【期待される動作】: value が空文字になり、valueSource は HTML_META のまま維持される
        // 🔵 信頼性レベル: 既存実装（processed.metadata[field.metaKey] ?: ""）・要件定義書 4-2 に基づく（推測なし）

        // 【テストデータ準備】: HTML_META フィールドを1件持つテンプレートを用意
        // 【初期条件設定】: ProcessedContent.metadata を空にして該当キー欠落を再現
        val template = Template(
            id = 1L, name = "t",
            fields = listOf(
                TemplateField(key = "title", valueSource = FieldValueSource.HTML_META, valueType = FieldValueType.STRING, metaKey = HtmlMetaKey.OG_TITLE),
            )
        )
        val processed = ProcessedContent(body = "body", contentType = ContentKind.URL, metadata = emptyMap())

        // 【実際の処理実行】: TemplateApplicator.buildCustomFields を呼び出す
        // 【処理内容】: metadata に存在しない metaKey を持つフィールドの変換を実行
        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        // 【結果検証】: 空文字フォールバックと valueSource の維持を確認
        // 【期待値確認】: 既存の null 安全フォールバック挙動と一致すること
        assertEquals("", customFields[0].value) // 【確認内容】: メタ情報欠落時は空文字にフォールバックすること
        assertEquals(FieldValueSource.HTML_META, customFields[0].valueSource) // 【確認内容】: valueSource は HTML_META のまま維持されること
    }

    // TC-0070-E02: URL フィールドで sourceUrl が null の場合は空文字へフォールバック
    @Test
    fun buildCustomFields_url_nullSourceUrl_fallsBackToEmpty() {
        // 【テスト目的】: sourceUrl が null の場合でも安全に空文字へフォールバックすることを確認
        // 【テスト内容】: ProcessedContent.sourceUrl が null の状態で URL フィールドを変換する
        // 【期待される動作】: value が空文字になり、valueSource は URL のまま維持される
        // 🔵 信頼性レベル: 既存実装（processed.sourceUrl ?: ""）・要件定義書 制約条件（null安全性）に基づく（推測なし）

        // 【テストデータ準備】: URL フィールドを1件持つテンプレートを用意
        // 【初期条件設定】: ProcessedContent.sourceUrl を null のままにして URL 欠落を再現
        val template = Template(
            id = 1L, name = "t",
            fields = listOf(
                TemplateField(key = "source", valueSource = FieldValueSource.URL, valueType = FieldValueType.STRING),
            )
        )
        val processed = ProcessedContent(body = "body", contentType = ContentKind.TEXT, sourceUrl = null)

        // 【実際の処理実行】: TemplateApplicator.buildCustomFields を呼び出す
        // 【処理内容】: sourceUrl が null の状態でのフィールド変換を実行
        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        // 【結果検証】: 空文字フォールバックと valueSource の維持を確認
        // 【期待値確認】: 既存の null 安全フォールバック挙動と一致すること
        assertEquals("", customFields[0].value) // 【確認内容】: sourceUrl が null の場合は空文字にフォールバックすること
        assertEquals(FieldValueSource.URL, customFields[0].valueSource) // 【確認内容】: valueSource は URL のまま維持されること
    }

    // TC-0070-B01: buildCustomFields に template=null を渡すと空リストが返る（valueSource/llmPrompt 追加後の回帰確認）
    @Test
    fun buildCustomFields_nullTemplate_returnsEmptyList() {
        // 【テスト目的】: CustomFieldState に valueSource/llmPrompt を追加した後も template=null の挙動が変わらないことを確認
        // 【テスト内容】: template に null を渡して buildCustomFields を呼び出す
        // 【期待される動作】: 例外を出さず空リストを返す
        // 🔵 信頼性レベル: TASK-0070 テストケース3・要件定義書 TC3・既存 TemplateApplicatorTest.kt（TC-5）に基づく（推測なし）

        // 【テストデータ準備】: template=null という下限境界の入力を用意
        // 【初期条件設定】: ProcessedContent は通常のテキスト共有を想定
        val processed = ProcessedContent(body = "body", contentType = ContentKind.TEXT)

        // 【実際の処理実行】: TemplateApplicator.buildCustomFields(null, processed) を呼び出す
        // 【処理内容】: null 安全な emptyList() へのフォールバックを実行
        val customFields = TemplateApplicator.buildCustomFields(null, processed)

        // 【結果検証】: 戻り値が空リストであることを確認
        // 【期待値確認】: null 安全フォールバックが維持されていること
        assertTrue(customFields.isEmpty()) // 【確認内容】: template が null の場合は空リストが返ること
    }

    // TC-0070-B02: template.fields が空リストのとき空の CustomFieldState リストが返る
    @Test
    fun buildCustomFields_emptyFields_returnsEmptyList() {
        // 【テスト目的】: フィールド0件のテンプレートでも例外を出さず空リストを返すことを確認
        // 【テスト内容】: fields=emptyList() のテンプレートを buildCustomFields に渡す
        // 【期待される動作】: 空リストが返る
        // 🟡 信頼性レベル: 要件定義書 TC4・4-2（fieldsが空）に基づく妥当な推測（既存テストには明示なし）

        // 【テストデータ準備】: カスタムフィールドを一切定義していないテンプレートを用意
        // 【初期条件設定】: null と「1件以上」の中間である0件境界を再現
        val template = Template(id = 1L, name = "t", body = "", fields = emptyList())
        val processed = ProcessedContent(body = "body", contentType = ContentKind.TEXT)

        // 【実際の処理実行】: TemplateApplicator.buildCustomFields を呼び出す
        // 【処理内容】: 空リストに対する map 処理を実行
        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        // 【結果検証】: 戻り値が空リストであることを確認
        // 【期待値確認】: emptyList().map { } は空リストになるという既存の挙動と一致すること
        assertTrue(customFields.isEmpty()) // 【確認内容】: fields が空の場合は空リストが返ること
    }

    // TC-0070-B03: LLMフィールドで llmPrompt="" のとき value=""・valueSource=LLM・llmPrompt="" が保持される
    @Test
    fun buildCustomFields_llmEmptyPrompt_keepsSourceAndEmptyPrompt() {
        // 【テスト目的】: プロンプト未設定（空文字）の LLM フィールドでもバリデーションせず分類・値を保持することを確認
        // 【テスト内容】: llmPrompt="" の LLM フィールドを buildCustomFields に渡す
        // 【期待される動作】: value=""、valueSource=LLM、llmPrompt="" がそのまま生成される
        // 🟡 信頼性レベル: 要件定義書 TC5・4-2（llmPrompt が空文字）に基づく妥当な推測

        // 【テストデータ準備】: llmPrompt を明示的に空文字にした LLM フィールドを持つテンプレートを用意
        // 【初期条件設定】: プロンプト未入力のままテンプレートを適用した状態を再現
        val template = Template(
            id = 1L, name = "t",
            fields = listOf(
                TemplateField(key = "summary", valueSource = FieldValueSource.LLM, valueType = FieldValueType.STRING, llmPrompt = ""),
            )
        )
        val processed = ProcessedContent(body = "body", contentType = ContentKind.TEXT)

        // 【実際の処理実行】: TemplateApplicator.buildCustomFields を呼び出す
        // 【処理内容】: プロンプト未設定の LLM フィールドの変換を実行
        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        // 【結果検証】: プロンプト空文字でも分類・値が意図どおり保持されることを確認
        // 【期待値確認】: バリデーションを行わず後続タスクへ橋渡しする設計方針と一致すること
        assertEquals("", customFields[0].value) // 【確認内容】: LLMフィールドの値は常に空文字であること
        assertEquals(FieldValueSource.LLM, customFields[0].valueSource) // 【確認内容】: valueSource が LLM のまま維持されること
        assertEquals("", customFields[0].llmPrompt) // 【確認内容】: llmPrompt が空文字のまま保持されること
    }
}

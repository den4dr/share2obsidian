# TDD Redフェーズ記録: CustomFieldState拡張・TemplateApplicator.buildCustomFields()のLLM対応

- **機能名**: custom-field-llm-support
- **タスクID**: TASK-0070
- **要件名**: llm-memo-rewrite
- **フェーズ**: Red（失敗するテスト作成）
- **作成日**: 2026-07-09

---

## 1. 対象テストケース

`docs/implements/llm-memo-rewrite/TASK-0070/custom-field-llm-support-testcases.md` に定義された全8件（利用可能な全テストケース。目標10件に対し、定義済みテストケースが8件のため全件を実装対象とした）。

| # | テストケースID | 分類 | テスト関数名 | 信頼性 |
|---|---|---|---|---|
| 1 | TC-0070-N01 | 正常系 | `buildCustomFields_llm_setsEmptyValueAndKeepsPrompt` | 🔵 |
| 2 | TC-0070-N02 | 正常系 | `buildCustomFields_existingSources_regressionWithValueSourceAndPrompt` | 🔵 |
| 3 | TC-0070-N03 | 正常系 | `customFieldState_threeArgConstructor_usesDefaults` | 🔵 |
| 4 | TC-0070-E01 | 異常系 | `buildCustomFields_htmlMeta_missingKey_fallsBackToEmpty` | 🔵 |
| 5 | TC-0070-E02 | 異常系 | `buildCustomFields_url_nullSourceUrl_fallsBackToEmpty` | 🔵 |
| 6 | TC-0070-B01 | 境界値 | `buildCustomFields_nullTemplate_returnsEmptyList` | 🔵 |
| 7 | TC-0070-B02 | 境界値 | `buildCustomFields_emptyFields_returnsEmptyList` | 🟡 |
| 8 | TC-0070-B03 | 境界値 | `buildCustomFields_llmEmptyPrompt_keepsSourceAndEmptyPrompt` | 🟡 |

信頼性レベル分布: 🔵 75%（6件） / 🟡 25%（2件） / 🔴 0%（テストケース定義書と一致）

---

## 2. テストファイル

`app/src/test/java/com/den4dr/share2Obsidian/TemplateApplicatorTest.kt`（既存ファイルに追記）

既存の5テスト（`buildConfig_usesNoteSettings` 等）はそのまま維持し、末尾に TASK-0070 用の8テストを追加した。

### 追加したテストコード全文

```kotlin
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

    // ...(既存5テストは省略。差分は app/src/test/java/com/den4dr/share2Obsidian/TemplateApplicatorTest.kt を参照)

    // TC-0070-N01: LLMフィールドの変換で value が空文字・valueSource=LLM・llmPrompt がテンプレート値で生成される
    @Test
    fun buildCustomFields_llm_setsEmptyValueAndKeepsPrompt() {
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

        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        assertEquals(1, customFields.size)
        assertEquals("", customFields[0].value)
        assertEquals(FieldValueSource.LLM, customFields[0].valueSource)
        assertEquals("要約を作成してください", customFields[0].llmPrompt)
    }

    // TC-0070-N02: 既存4種（FIXED/HTML_META/URL/EMPTY）の value 算出が回帰せず、valueSource/llmPrompt が正しく設定される
    @Test
    fun buildCustomFields_existingSources_regressionWithValueSourceAndPrompt() {
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

        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        assertEquals(4, customFields.size)
        assertEquals("memo", customFields[0].value)
        assertEquals(FieldValueSource.FIXED, customFields[0].valueSource)
        assertEquals("", customFields[0].llmPrompt)
        assertEquals("記事タイトル", customFields[1].value)
        assertEquals(FieldValueSource.HTML_META, customFields[1].valueSource)
        assertEquals("https://example.com", customFields[2].value)
        assertEquals(FieldValueSource.URL, customFields[2].valueSource)
        assertEquals("", customFields[3].value)
        assertEquals(FieldValueSource.EMPTY, customFields[3].valueSource)
    }

    // TC-0070-N03: CustomFieldState を3引数（key, value, valueType）で生成でき、valueSource/llmPrompt が既定値になる
    @Test
    fun customFieldState_threeArgConstructor_usesDefaults() {
        val state = CustomFieldState(key = "k", value = "v", valueType = FieldValueType.STRING)

        assertEquals("v", state.value)
        assertEquals(FieldValueSource.FIXED, state.valueSource)
        assertEquals("", state.llmPrompt)
    }

    // TC-0070-E01: HTML_META フィールドで metadata に該当キーが無い場合は空文字へフォールバック
    @Test
    fun buildCustomFields_htmlMeta_missingKey_fallsBackToEmpty() {
        val template = Template(
            id = 1L, name = "t",
            fields = listOf(
                TemplateField(key = "title", valueSource = FieldValueSource.HTML_META, valueType = FieldValueType.STRING, metaKey = HtmlMetaKey.OG_TITLE),
            )
        )
        val processed = ProcessedContent(body = "body", contentType = ContentKind.URL, metadata = emptyMap())

        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        assertEquals("", customFields[0].value)
        assertEquals(FieldValueSource.HTML_META, customFields[0].valueSource)
    }

    // TC-0070-E02: URL フィールドで sourceUrl が null の場合は空文字へフォールバック
    @Test
    fun buildCustomFields_url_nullSourceUrl_fallsBackToEmpty() {
        val template = Template(
            id = 1L, name = "t",
            fields = listOf(
                TemplateField(key = "source", valueSource = FieldValueSource.URL, valueType = FieldValueType.STRING),
            )
        )
        val processed = ProcessedContent(body = "body", contentType = ContentKind.TEXT, sourceUrl = null)

        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        assertEquals("", customFields[0].value)
        assertEquals(FieldValueSource.URL, customFields[0].valueSource)
    }

    // TC-0070-B01: buildCustomFields に template=null を渡すと空リストが返る（valueSource/llmPrompt 追加後の回帰確認）
    @Test
    fun buildCustomFields_nullTemplate_returnsEmptyList() {
        val processed = ProcessedContent(body = "body", contentType = ContentKind.TEXT)

        val customFields = TemplateApplicator.buildCustomFields(null, processed)

        assertTrue(customFields.isEmpty())
    }

    // TC-0070-B02: template.fields が空リストのとき空の CustomFieldState リストが返る
    @Test
    fun buildCustomFields_emptyFields_returnsEmptyList() {
        val template = Template(id = 1L, name = "t", body = "", fields = emptyList())
        val processed = ProcessedContent(body = "body", contentType = ContentKind.TEXT)

        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        assertTrue(customFields.isEmpty())
    }

    // TC-0070-B03: LLMフィールドで llmPrompt="" のとき value=""・valueSource=LLM・llmPrompt="" が保持される
    @Test
    fun buildCustomFields_llmEmptyPrompt_keepsSourceAndEmptyPrompt() {
        val template = Template(
            id = 1L, name = "t",
            fields = listOf(
                TemplateField(key = "summary", valueSource = FieldValueSource.LLM, valueType = FieldValueType.STRING, llmPrompt = ""),
            )
        )
        val processed = ProcessedContent(body = "body", contentType = ContentKind.TEXT)

        val customFields = TemplateApplicator.buildCustomFields(template, processed)

        assertEquals("", customFields[0].value)
        assertEquals(FieldValueSource.LLM, customFields[0].valueSource)
        assertEquals("", customFields[0].llmPrompt)
    }
}
```

（各テストには実装時に日本語コメント【テスト目的】【テスト内容】【期待される動作】【確認内容】等を付与済み。全文は実ファイル参照）

---

## 3. テスト実行結果（失敗の確認）

### 実行コマンド

```bash
mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.den4dr.share2Obsidian.TemplateApplicatorTest"
```

### 結果: コンパイルエラー（Redフェーズとして正当な失敗）

```
> Task :app:compileDebugUnitTestKotlin FAILED
e: .../TemplateApplicatorTest.kt:161:60 Unresolved reference 'valueSource'.
e: .../TemplateApplicatorTest.kt:162:53 Unresolved reference 'llmPrompt'.
e: .../TemplateApplicatorTest.kt:199:62 Unresolved reference 'valueSource'.
e: .../TemplateApplicatorTest.kt:200:42 Unresolved reference 'llmPrompt'.
...(以下同様、valueSource/llmPrompt 参照箇所すべてで Unresolved reference)

FAILURE: Build failed with an exception.
> Execution failed for task ':app:compileDebugUnitTestKotlin'.
   > Compilation error. See log for more details
```

**失敗理由**: `CustomFieldState`（`app/src/main/java/com/den4dr/share2Obsidian/domain/model/CustomFieldState.kt`）が現時点で `key`/`value`/`valueType` の3プロパティのみを持ち、`valueSource`・`llmPrompt` が未実装のため、テストコードが参照する `customFields[n].valueSource` / `customFields[n].llmPrompt` / `CustomFieldState(..., valueSource=..., llmPrompt=...)` がすべて `Unresolved reference` となりコンパイルが通らない。

Kotlinは静的型付け言語であり、未実装のプロパティ参照はコンパイルエラーとして現れる。これは「まだ実装されていない機能をテストする」というRedフェーズの原則に沿った正当な失敗である。テストの期待値・アサーション自体に誤りはない。

---

## 4. Greenフェーズで実装すべき内容

1. **`CustomFieldState.kt`**: `valueSource: FieldValueSource = FieldValueSource.FIXED` と `llmPrompt: String = ""` を追加する。

   ```kotlin
   data class CustomFieldState(
       val key: String,
       val value: String,
       val valueType: FieldValueType,
       val valueSource: FieldValueSource = FieldValueSource.FIXED,
       val llmPrompt: String = "",
   )
   ```

2. **`TemplateApplicator.kt` の `buildCustomFields()`**: `CustomFieldState` 生成時に `field.valueSource` と `field.llmPrompt` を渡すよう変更する。

   ```kotlin
   fun buildCustomFields(
       template: Template?,
       processed: ProcessedContent,
   ): List<CustomFieldState> = template?.fields?.map { field ->
       val value = when (field.valueSource) {
           FieldValueSource.FIXED -> field.defaultValue
           FieldValueSource.HTML_META -> processed.metadata[field.metaKey] ?: ""
           FieldValueSource.URL -> processed.sourceUrl ?: ""
           FieldValueSource.EMPTY -> ""
           FieldValueSource.LLM -> ""
       }
       CustomFieldState(field.key, value, field.valueType, field.valueSource, field.llmPrompt)
   } ?: emptyList()
   ```

3. 実装後、`mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.den4dr.share2Obsidian.TemplateApplicatorTest"` を再実行し、全13テスト（既存5＋新規8）が成功することを確認する。

4. 既存の他テスト（`NoteComposerTest`, `EditScreenViewModelTest` 等、`CustomFieldState` の3引数コンストラクタを使用する箇所）が引き続きコンパイル・成功することも確認する（TC-0070-N03が担保する後方互換性の実運用確認）。

---

## 5. 品質判定

- テスト実行: 実行可能な状態で作成し、コンパイルエラーという形で失敗することを確認済み（静的型付け言語における正当なRed失敗）
- 期待値: 各テストで `value`/`valueSource`/`llmPrompt`/リストサイズを具体的に明記
- アサーション: `assertEquals`/`assertTrue` を用途に応じて適切に使用
- 実装方針: 明確（CustomFieldStateへの2フィールド追加、buildCustomFields()での引き渡し追加のみ）
- 信頼性レベル分布: 🔵 75%（6件） / 🟡 25%（2件） / 🔴 0%

**総合判定**: ✅ 高品質。自動修正ループは不要。

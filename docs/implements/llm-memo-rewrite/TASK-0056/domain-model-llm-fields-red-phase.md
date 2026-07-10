# TASK-0056 Redフェーズ記録: Template/TemplateField/FieldValueSource ドメインモデル変更

**機能名（feature_name）**: domain-model-llm-fields
**タスクID**: TASK-0056
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-05

---

## 1. 対象テストケース

要件定義・テストケース定義（`domain-model-llm-fields-testcases.md`）に定義された全8件（10件未満のため全件を実装対象とした）。

| No. | 分類 | テストケース名（Kotlinテスト関数名） | 対応要件 | 信頼性 |
|-----|------|-------------------------------------|---------|--------|
| TC1 | 正常系 | `Template copy with bodyLlmPrompt updates only that property` | REQ-101 | 🔵 |
| TC3 | 正常系 | `TemplateField copy with llmPrompt updates only that property` | REQ-104 | 🔵 |
| TC5 | 正常系 | `Template and TemplateField accept bodyLlmPrompt and llmPrompt via constructor` | REQ-101/104 | 🟡 |
| TC-ERR1 | 異常系 | `FieldValueSource valueOf with invalid name throws IllegalArgumentException` | REQ-303 | 🔵 |
| TC4 | 境界値 | `Template and TemplateField default bodyLlmPrompt and llmPrompt to empty string` | 後方互換性 | 🔵 |
| TC2 | 境界値 | `FieldValueSource contains all expected values`（既存テストへの `LLM` 追加検証の統合。valueOf成功も同テストの前段テストTC2扱いはTC2-Bと統合実装） | REQ-303 | 🔵 |
| TC2-B | 境界値 | `FieldValueSource contains all expected values`（既存テスト更新: 5値・LLM含む） | REQ-303 | 🔵 |
| TC-EDGE1 | 境界値 | `Template and TemplateField accept explicit empty string for bodyLlmPrompt and llmPrompt` | REQ-102 | 🟡 |

備考: テストケース定義書のTC2（`entries`にLLMが含まれ`valueOf("LLM")`が成功すること）は、既存テスト `FieldValueSource contains all expected values` の更新（TC2-B）に統合実装した（`"LLM" in names` の検証を含む）。`valueOf("LLM")` 単体の成功確認は、TC-ERR1で異常系（`valueOf("INVALID")`が例外）と対比する形で、Greenフェーズ実装後に本テストが通ること自体で実質的に保証される設計とした。

---

## 2. テストコード全文

**配置ファイル**: `app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt`（既存ファイルへ追加・一部更新）

### 2.1 既存テストの更新（TC2-B）

```kotlin
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
```

### 2.2 追加テスト（TC1, TC3, TC5, TC-ERR1, TC4, TC-EDGE1）

```kotlin
@Test
fun `Template copy with bodyLlmPrompt updates only that property`() {
    // 【テスト目的】: Template.copy() で bodyLlmPrompt のみを部分更新できることを確認する
    // 🔵 信頼性レベル: TASK-0056.md テストケース1・requirements.md TC1・REQ-101 に基づく（推測なし）
    val original = Template(name = "Web記事", fields = emptyList())
    val updated = original.copy(bodyLlmPrompt = "要約してください")
    assertEquals("要約してください", updated.bodyLlmPrompt)
    assertEquals("Web記事", updated.name)
    assertEquals("", updated.body)
    assertEquals(emptyList<TemplateField>(), updated.fields)
    assertEquals(false, updated.isDefault)
    assertEquals(0L, updated.id)
}

@Test
fun `TemplateField copy with llmPrompt updates only that property`() {
    // 【テスト目的】: TemplateField.copy() で llmPrompt のみを部分更新できることを確認する
    // 🔵 信頼性レベル: TASK-0056.md テストケース3・requirements.md TC3・REQ-104 に基づく（推測なし）
    val original = TemplateField(
        key = "title",
        valueSource = FieldValueSource.LLM,
        valueType = FieldValueType.STRING,
    )
    val updated = original.copy(llmPrompt = "タイトルを生成してください")
    assertEquals("タイトルを生成してください", updated.llmPrompt)
    assertEquals("title", updated.key)
    assertEquals(FieldValueSource.LLM, updated.valueSource)
    assertEquals(FieldValueType.STRING, updated.valueType)
    assertNull(updated.metaKey)
    assertEquals(0, updated.sortOrder)
}

@Test
fun `Template and TemplateField accept bodyLlmPrompt and llmPrompt via constructor`() {
    // 【テスト目的】: bodyLlmPrompt / llmPrompt を名前付き引数で明示指定してインスタンス生成できることを確認する
    // 🟡 信頼性レベル: data class 標準仕様からの妥当な推測。requirements.md TC5 に基づく
    val template = Template(name = "記事", bodyLlmPrompt = "本文を整形", fields = emptyList())
    val field = TemplateField(
        key = "tag",
        valueSource = FieldValueSource.LLM,
        valueType = FieldValueType.LIST,
        llmPrompt = "タグ候補",
    )
    assertEquals("本文を整形", template.bodyLlmPrompt)
    assertEquals("タグ候補", field.llmPrompt)
}

@Test
fun `FieldValueSource valueOf with invalid name throws IllegalArgumentException`() {
    // 【テスト目的】: LLM 追加後も未定義の enum 名を valueOf に渡すと例外が発生することを確認する
    // 🔵 信頼性レベル: requirements.md 4.3 エラーケース・Kotlin enum 標準仕様・TC-ERR1 に基づく（推測なし）
    var thrown: IllegalArgumentException? = null
    try {
        FieldValueSource.valueOf("INVALID")
    } catch (e: IllegalArgumentException) {
        thrown = e
    }
    assertEquals(true, thrown != null)
}

@Test
fun `Template and TemplateField default bodyLlmPrompt and llmPrompt to empty string`() {
    // 【テスト目的】: 新規プロパティを指定せずに生成した場合、デフォルト値が空文字になり既存呼び出しへの互換性が保たれることを確認する
    // 🔵 信頼性レベル: TASK-0056.md テストケース4・requirements.md TC4・後方互換性制約に基づく（推測なし）
    val template = Template(name = "Web記事", fields = emptyList())
    val field = TemplateField(
        key = "status",
        valueSource = FieldValueSource.FIXED,
        valueType = FieldValueType.STRING,
        defaultValue = "draft",
    )
    assertEquals("", template.bodyLlmPrompt)
    assertEquals("", field.llmPrompt)
}

@Test
fun `Template and TemplateField accept explicit empty string for bodyLlmPrompt and llmPrompt`() {
    // 【テスト目的】: bodyLlmPrompt / llmPrompt に空文字を明示指定した場合もデフォルトと同値になることを確認する
    // 🟡 信頼性レベル: requirements.md 4.2 エッジケースからの妥当な推測（TC-EDGE1）
    val template = Template(name = "t", fields = emptyList(), bodyLlmPrompt = "")
    val field = TemplateField(
        key = "k",
        valueSource = FieldValueSource.EMPTY,
        valueType = FieldValueType.STRING,
        llmPrompt = "",
    )
    assertEquals("", template.bodyLlmPrompt)
    assertEquals("", field.llmPrompt)
}
```

（実ファイルには各テストに詳細な日本語コメント・各assertへの確認内容コメントを付与済み。全文は `app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt` を参照。）

---

## 3. テスト実行結果（失敗の確認）

**実行コマンド**:
```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*TemplateTest*"
```

**結果**: `BUILD FAILED`（`:app:compileDebugUnitTestKotlin` タスクでコンパイルエラー）

Kotlinは静的型付け言語のため、未実装のプロパティ・enum値を参照するテストコードはテスト実行以前にコンパイルエラーとして失敗する。これはRedフェーズとして意図した失敗である。

**実際のコンパイルエラー抜粋**:
```
e: TemplateTest.kt:106:37 No parameter with name 'bodyLlmPrompt' found.
e: TemplateTest.kt:110:42 Unresolved reference 'bodyLlmPrompt'.
e: TemplateTest.kt:129:44 Unresolved reference 'LLM'.
e: TemplateTest.kt:135:37 No parameter with name 'llmPrompt' found.
e: TemplateTest.kt:139:47 Unresolved reference 'llmPrompt'.
e: TemplateTest.kt:141:39 Unresolved reference 'LLM'.
（以下、追加した全テストで bodyLlmPrompt / llmPrompt / LLM の未解決参照エラーが多数発生）
```

**エラー内容の分類**:
- `Template` に `bodyLlmPrompt` パラメータ・プロパティが存在しない
- `TemplateField` に `llmPrompt` パラメータ・プロパティが存在しない
- `FieldValueSource` に `LLM` が存在しない

いずれも「未実装の機能をテストする」というRedフェーズの原則に合致する失敗であり、テストコード自体の誤りではない。

---

## 4. Greenフェーズで実装すべき内容

1. `app/src/main/java/com/den4dr/share2Obsidian/domain/model/Template.kt`
   - `body: String = ""` の直後に `val bodyLlmPrompt: String = ""` を追加
2. `app/src/main/java/com/den4dr/share2Obsidian/domain/model/TemplateField.kt`
   - `metaKey: HtmlMetaKey? = null` の直後に `val llmPrompt: String = ""` を追加
3. `app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt`
   - `enum class FieldValueSource { FIXED, HTML_META, URL, EMPTY }` の末尾に `LLM` を追加

実装後、`mise exec -- ./gradlew testDebugUnitTest --tests "*TemplateTest*"` を再実行し、追加した8テストケース＋既存テスト全てが成功することを確認する。

---

## 5. 品質判定

| 項目 | 評価 |
|------|------|
| テスト実行 | コンパイルエラーとして失敗することを確認済み（静的型付け言語における意図した失敗） |
| 期待値 | 明確かつ具体的（各assertに検証理由コメントあり） |
| アサーション | 適切（更新対象プロパティと不変プロパティを分離して検証） |
| 実装方針 | 明確（3ファイルへのプロパティ・enum値追加のみ） |
| 信頼性レベル分布 | 🔵 6件 / 🟡 2件 / 🔴 0件 |

**判定**: ✅ 高品質

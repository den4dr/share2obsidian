# TDDテストケース定義書: CustomFieldState拡張・TemplateApplicator.buildCustomFields()のLLM対応

- **機能名**: custom-field-llm-support
- **タスクID**: TASK-0070
- **要件名**: llm-memo-rewrite
- **フェーズ**: Phase 6 - カスタムフィールドのLLM生成（Could Have）
- **作成日**: 2026-07-09
- **対象実装**:
  - `app/src/main/java/com/den4dr/share2Obsidian/domain/model/CustomFieldState.kt`（`valueSource`/`llmPrompt` 追加）
  - `app/src/main/java/com/den4dr/share2Obsidian/TemplateApplicator.kt`（`buildCustomFields()` の `CustomFieldState` 生成拡張）
- **テストファイル**: `app/src/test/java/com/den4dr/share2Obsidian/TemplateApplicatorTest.kt`（既存に追記）

---

## 信頼性レベルの凡例

- 🔵 **青信号**: 元の資料（要件定義・既存実装・設計文書）を参考にしてほぼ推測していない
- 🟡 **黄信号**: 元の資料から妥当に推測している
- 🔴 **赤信号**: 元の資料にない推測

---

## 1. 正常系テストケース（基本的な動作）

### TC-0070-N01: LLMフィールドが空文字・LLM由来・llmPromptを保持して生成される 🔵

- **テスト名**: LLMフィールドの変換で value が空文字・valueSource=LLM・llmPrompt がテンプレート値で生成される
  - **何をテストするか**: `FieldValueSource.LLM` の `TemplateField` を `buildCustomFields()` に渡したとき、`CustomFieldState` が「値は空文字・生成方法と生成プロンプトを保持した状態」で生成されること
  - **期待される動作**: テンプレート適用時点ではLLM生成を実行せず（REQ-304）、`value = ""` としつつ `valueSource`/`llmPrompt` を後続処理（EditScreen）へ橋渡しする
- **入力値**:
  - `Template.fields = [ TemplateField(key="summary", valueSource=LLM, valueType=STRING, llmPrompt="要約を作成してください") ]`
  - `ProcessedContent(body="body", contentType=TEXT)`
  - **入力データの意味**: 要件定義書 4-1 の基本使用パターンおよび TASK-0070 テストケース1に対応する代表入力
- **期待される結果**:
  - `customFields.size == 1`
  - `customFields[0].key == "summary"`
  - `customFields[0].value == ""`
  - `customFields[0].valueSource == FieldValueSource.LLM`
  - `customFields[0].llmPrompt == "要約を作成してください"`
  - **期待結果の理由**: REQ-304 によりテンプレート適用時はLLM生成を行わない（値は空文字）。`valueSource`/`llmPrompt` は EditScreen 側のボタン表示判定・生成実行に必要なため保持する（要件定義書 2-2 の値算出テーブル）
- **テストの目的**: 本タスクの中核であるLLMフィールド変換ロジックの確認
  - **確認ポイント**: 値が空文字であること、かつ生成に必要なメタ情報（`valueSource`/`llmPrompt`）が欠落せず引き継がれること
- 🔵 信頼性レベル: TASK-0070 テストケース1・要件定義書 TC1・REQ-304 に基づく（推測なし）

### TC-0070-N02: FIXED/HTML_META/URL/EMPTY の既存ロジックが維持され、valueSource/llmPrompt も引き継がれる 🔵

- **テスト名**: 既存4種（FIXED/HTML_META/URL/EMPTY）の value 算出が回帰せず、valueSource/llmPrompt が正しく設定される
  - **何をテストするか**: 既存の4種の `FieldValueSource` について、`value` 算出ロジックが変更されていないこと、加えて新規追加した `valueSource`/`llmPrompt` が各フィールドの値と一致すること
  - **期待される動作**: `FIXED`→`defaultValue`、`HTML_META`→`metadata[metaKey]`、`URL`→`sourceUrl`、`EMPTY`→`""` を維持し、各 `CustomFieldState` に対応する `valueSource`/`llmPrompt` を設定
- **入力値**:
  - `Template.fields` に以下4フィールドを混在させる:
    - `TemplateField(key="tag", valueSource=FIXED, valueType=STRING, defaultValue="memo")`
    - `TemplateField(key="title", valueSource=HTML_META, valueType=STRING, metaKey=OG_TITLE)`
    - `TemplateField(key="source", valueSource=URL, valueType=STRING)`
    - `TemplateField(key="note", valueSource=EMPTY, valueType=STRING)`
  - `ProcessedContent(body="body", contentType=URL, metadata=mapOf(OG_TITLE to "記事タイトル"), sourceUrl="https://example.com")`
  - **入力データの意味**: 要件定義書 2-2 の値算出テーブル全行（LLMを除く4種）を1テストで網羅する混在テンプレート（要件定義書 4-1 混在テンプレート）
- **期待される結果**:
  - `FIXED` → `value == "memo"`, `valueSource == FIXED`, `llmPrompt == ""`
  - `HTML_META` → `value == "記事タイトル"`, `valueSource == HTML_META`
  - `URL` → `value == "https://example.com"`, `valueSource == URL`
  - `EMPTY` → `value == ""`, `valueSource == EMPTY`
  - 各 `llmPrompt` は対応する `TemplateField.llmPrompt`（この入力では既定の `""`）と一致
  - **期待結果の理由**: 完了条件「既存のFIXED/HTML_META/URL/EMPTYの挙動が変更されない（回帰なし）」を担保。同時に本タスク差分（`valueSource`/`llmPrompt` の付与）を検証（要件定義書 制約条件・2-2）
- **テストの目的**: 回帰防止と新フィールド付与の同時確認
  - **確認ポイント**: 既存 `value` ロジックの不変性と、新規メタ情報が正しく各フィールドへ紐づくこと
- 🔵 信頼性レベル: TASK-0070 テストケース2・要件定義書 TC2・既存 TemplateApplicatorTest.kt（TC-3/TC-4）に基づく（推測なし）

### TC-0070-N03: 既存3引数コンストラクタ呼び出しの後方互換 🔵

- **テスト名**: CustomFieldState を3引数（key, value, valueType）で生成でき、valueSource/llmPrompt が既定値になる
  - **何をテストするか**: `valueSource`/`llmPrompt` にデフォルト値を付与したことで、既存の3引数コンストラクタ呼び出しがコンパイル・動作継続すること
  - **期待される動作**: `CustomFieldState("k", "v", FieldValueType.STRING)` が生成でき、`valueSource == FIXED`, `llmPrompt == ""` になる
- **入力値**: `CustomFieldState(key="k", value="v", valueType=FieldValueType.STRING)`
  - **入力データの意味**: 既存呼び出し箇所（NoteComposer/EditScreenViewModel 等）が壊れないことを保証する最小ケース（要件定義書 TC6）
- **期待される結果**:
  - `state.value == "v"`
  - `state.valueSource == FieldValueSource.FIXED`
  - `state.llmPrompt == ""`
  - **期待結果の理由**: 要件定義書 2-1「後方互換性」により、デフォルト値付き追加フィールドで既存の3引数呼び出しを維持する
- **テストの目的**: data class 拡張による既存コード互換性の確認
  - **確認ポイント**: デフォルト値が意図どおり（`FIXED` / `""`）に適用されること
- 🔵 信頼性レベル: 要件定義書 TC6・2-1「後方互換性」に基づく（推測なし）

---

## 2. 異常系テストケース（エラーハンドリング）

> 本タスクは純粋なデータ変換のみで、例外送出・エラーハンドリングは新規に発生しない（要件定義書 4-3）。LLM API起因のエラー処理は後続タスク（TASK-0072/0073）の範囲。したがって「不正入力で安全に既定値へフォールバックする」挙動を異常系相当として確認する。

### TC-0070-E01: HTML_META で metadata に該当キーが無い場合は空文字へフォールバック 🔵

- **テスト名**: HTML_META フィールドで metadata に該当キーが存在しない場合、value が空文字になる
  - **エラーケースの概要**: メタデータ取得元に期待キーが無い（共有元ページにメタ情報が欠落している）状況
  - **エラー処理の重要性**: メタ情報欠落時にクラッシュや null 混入を起こさず、空文字で安全に継続する必要がある
- **入力値**:
  - `TemplateField(key="title", valueSource=HTML_META, valueType=STRING, metaKey=OG_TITLE)`
  - `ProcessedContent(body="body", contentType=URL, metadata=emptyMap())`
  - **不正な理由**: `metaKey` に対応する値が `metadata` に存在しない
  - **実際の発生シナリオ**: OGタグを持たないページや、テキスト共有（メタ情報なし）でHTML_METAフィールドが定義されている場合
- **期待される結果**:
  - `customFields[0].value == ""`
  - `customFields[0].valueSource == FieldValueSource.HTML_META`
  - **エラーメッセージの内容**: 例外は発生せず、空文字で継続（ユーザー向けエラー表示は不要）
  - **システムの安全性**: Elvis演算子 `?: ""` により null 非混入で安全に継続
- **テストの目的**: 既存フォールバック挙動の回帰確認（要件定義書 4-2 エッジケース）
  - **品質保証の観点**: メタ情報欠落という実運用で頻出する条件下での堅牢性を担保
- 🔵 信頼性レベル: 既存実装（`processed.metadata[field.metaKey] ?: ""`）・要件定義書 4-2 に基づく（推測なし）

### TC-0070-E02: URL フィールドで sourceUrl が null の場合は空文字へフォールバック 🔵

- **テスト名**: URL フィールドで ProcessedContent.sourceUrl が null の場合、value が空文字になる
  - **エラーケースの概要**: URL 情報を持たない共有コンテンツに対して URL フィールドが定義されている状況
  - **エラー処理の重要性**: `sourceUrl` が null でも安全に空文字へフォールバックし、後続処理へ null を伝播させない
- **入力値**:
  - `TemplateField(key="source", valueSource=URL, valueType=STRING)`
  - `ProcessedContent(body="body", contentType=TEXT, sourceUrl=null)`
  - **不正な理由**: URL フィールドが要求する `sourceUrl` が欠落（null）
  - **実際の発生シナリオ**: プレーンテキスト共有（URLを含まない）でURLフィールドが定義されているテンプレート
- **期待される結果**:
  - `customFields[0].value == ""`
  - `customFields[0].valueSource == FieldValueSource.URL`
  - **システムの安全性**: `processed.sourceUrl ?: ""` により null 非混入で継続
- **テストの目的**: 既存 null 安全フォールバックの回帰確認
  - **品質保証の観点**: URL欠落という現実的な条件下でも一貫した空文字結果を保証
- 🔵 信頼性レベル: 既存実装（`processed.sourceUrl ?: ""`）・要件定義書 制約条件（null安全性）に基づく（推測なし）

---

## 3. 境界値テストケース（最小値、最大値、null等）

### TC-0070-B01: template が null の場合は空リストを返す 🔵

- **テスト名**: buildCustomFields に template=null を渡すと空リストが返る
  - **境界値の意味**: `template` が存在しない（null）という入力の下限境界
  - **境界値での動作保証**: null 入力でも例外を出さず、空リストという安全な結果を返す
- **入力値**: `buildCustomFields(null, ProcessedContent(body="body", contentType=TEXT))`
  - **境界値選択の根拠**: `Template?` が nullable であり、null は明示的に扱うべき境界（要件定義書 2-2 出力仕様）
  - **実際の使用場面**: テンプレート未選択・未設定状態で共有フローが実行される場合
- **期待される結果**:
  - 戻り値が空リスト（`customFields.isEmpty() == true`）
  - **境界での正確性**: `?.fields?.map { ... } ?: emptyList()` により null 安全に空リストへ帰着
  - **一貫した動作**: 既存挙動（既存テスト TC-5）と完全一致
- **テストの目的**: null 境界の回帰確認
  - **堅牢性の確認**: null 入力での安定動作
- 🔵 信頼性レベル: TASK-0070 テストケース3・要件定義書 TC3・既存 TemplateApplicatorTest.kt（TC-5）に基づく（推測なし）

### TC-0070-B02: fields が空リストの場合は空リストを返す 🟡

- **テスト名**: template.fields が空リストのとき空の CustomFieldState リストが返る
  - **境界値の意味**: テンプレートは存在するがフィールドが0件という下限境界（null と非空の中間）
  - **境界値での動作保証**: フィールド0件でも `map` が空を返し、例外を起こさない
- **入力値**:
  - `Template(id=1L, name="t", body="", fields=emptyList())`
  - `ProcessedContent(body="body", contentType=TEXT)`
  - **境界値選択の根拠**: `null` と「1件以上」の間の境界。要件定義書 4-2 で明示されたエッジケース
  - **実際の使用場面**: カスタムフィールドを一切定義していないテンプレートを適用する場合
- **期待される結果**:
  - 戻り値が空リスト（`customFields.isEmpty() == true`）
  - **境界での正確性**: `emptyList().map { }` は空リスト
  - **一貫した動作**: null ケース（B01）と同じく空リストで一貫
- **テストの目的**: 空フィールド境界の確認
  - **堅牢性の確認**: 0件テンプレートでの安定動作
- 🟡 信頼性レベル: 要件定義書 TC4・4-2（fieldsが空）に基づく妥当な推測（既存テストには明示なし）

### TC-0070-B03: LLMフィールドで llmPrompt が空文字でも valueSource=LLM を保持 🟡

- **テスト名**: LLMフィールドで llmPrompt="" のとき value=""・valueSource=LLM・llmPrompt="" が保持される
  - **境界値の意味**: LLMフィールドだがプロンプト未設定（空文字）という境界。プロンプトの有無で挙動が分岐しないことを確認
  - **境界値での動作保証**: 本タスクではプロンプト未設定のバリデーションを行わず、空文字のまま保持する
- **入力値**:
  - `TemplateField(key="summary", valueSource=LLM, valueType=STRING, llmPrompt="")`
  - `ProcessedContent(body="body", contentType=TEXT)`
  - **境界値選択の根拠**: `llmPrompt` の下限（空文字＝未設定）。生成実行の責務は後続タスクであり、本タスクでは無検証で通すことを確認（要件定義書 4-2）
  - **実際の使用場面**: LLM生成を選んだがプロンプト未入力のままテンプレートを適用した場合
- **期待される結果**:
  - `customFields[0].value == ""`
  - `customFields[0].valueSource == FieldValueSource.LLM`
  - `customFields[0].llmPrompt == ""`
  - **境界での正確性**: プロンプト空文字でも LLM フィールドとして分類が保持される
  - **一貫した動作**: TC-0070-N01（プロンプトあり）と挙動が分岐しない（値は常に空文字、分類は常にLLM）
- **テストの目的**: プロンプト未設定境界での挙動確認（バリデーションを行わないことの明示）
  - **堅牢性の確認**: 未設定プロンプトでもクラッシュせず後続へ橋渡し可能な状態を維持
- 🟡 信頼性レベル: 要件定義書 TC5・4-2（llmPrompt が空文字）に基づく妥当な推測

---

## 4. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: 既存プロジェクトの実装言語であり、対象クラス（`CustomFieldState`/`TemplateApplicator`）が Kotlin で実装済み
  - **テストに適した機能**: data class の構造的等価性、バックティック記法によるテストメソッド命名、when 式の網羅性チェック
- **テストフレームワーク**: JUnit 4（+ 必要時 MockK）
  - **フレームワーク選択の理由**: 既存テスト（`TemplateApplicatorTest.kt` ほか）が JUnit 4 + `org.junit.Assert.*` を採用しており、統一性を保つ。本タスクは純粋関数のためモックはほぼ不要（`TemplateApplicator` は object）
  - **テスト実行環境**: `app/src/test/java/`（ローカルJVMユニットテスト）。`mise exec -- ./gradlew test` で実行
- 🔵 信頼性レベル: note.md「技術スタック」「テスト関連ルール」・既存 TemplateApplicatorTest.kt に基づく（推測なし）

---

## 5. テストケース実装時の日本語コメント指針

各テストは Given / When / Then 構造で記述し、以下のコメントを含める。

### テストケース開始時のコメント（例: TC-0070-N01）

```kotlin
// 【テスト目的】: LLMフィールドが value="" かつ valueSource=LLM・llmPrompt保持で変換されることを確認
// 【テスト内容】: FieldValueSource.LLM の TemplateField を buildCustomFields に渡して結果を検証
// 【期待される動作】: テンプレート適用時はLLM生成せず、生成方法とプロンプトのみ橋渡しする
// 🔵 信頼性レベル: TASK-0070 テストケース1・REQ-304 に基づく
@Test
fun `buildCustomFields_llm_setsEmptyValueAndKeepsPrompt`() {
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
    // 【検証項目】: value / valueSource / llmPrompt
    // 🔵 信頼性レベル: 要件定義書 2-2 値算出テーブルに基づく
    assertEquals(1, customFields.size)       // 【確認内容】: LLMフィールドが1件変換される
    assertEquals("", customFields[0].value)  // 【確認内容】: テンプレート適用時は値未生成（空文字）
    assertEquals(FieldValueSource.LLM, customFields[0].valueSource) // 【確認内容】: 生成方法がLLMとして保持
    assertEquals("要約を作成してください", customFields[0].llmPrompt) // 【確認内容】: プロンプトが引き継がれる
}
```

### セットアップ・クリーンアップ

- `TemplateApplicator` は `object`（状態を持たない純粋関数）のため、`beforeEach`/`afterEach` によるインスタンス生成・破棄は不要。
- 各テストはローカル変数のみで完結し、テスト間で共有状態を持たない（相互干渉なし）。

---

## 6. 要件定義との対応関係

- **参照した機能概要**: 要件定義書「1. 機能の概要」（CustomFieldStateへの valueSource/llmPrompt 追加、buildCustomFields のLLM対応）
- **参照した入力・出力仕様**: 要件定義書「2. 入力・出力の仕様」（2-1 CustomFieldState データ構造、2-2 buildCustomFields 値算出テーブル）
- **参照した制約条件**: 要件定義書「3. 制約条件」（回帰なし・純粋関数維持・null安全・when網羅性・後方互換）
- **参照した使用例**: 要件定義書「4. 想定される使用例」（4-1 基本パターン、4-2 エッジケース、4-3 エラーケース）
- **要件テスト観点との対応**:

| 要件観点 | 対応テストケース | 分類 |
|---|---|---|
| TC1: LLMフィールドの変換 | TC-0070-N01 | 正常系 |
| TC2: FIXED/HTML_META/URL/EMPTY の回帰 | TC-0070-N02 | 正常系 |
| TC3: template=null の回帰 | TC-0070-B01 | 境界値 |
| TC4: fields=空 の回帰 | TC-0070-B02 | 境界値 |
| TC5: LLMフィールドで llmPrompt="" | TC-0070-B03 | 境界値 |
| TC6: 既存3引数コンストラクタ互換 | TC-0070-N03 | 正常系 |
| （4-2）HTML_META メタ欠落フォールバック | TC-0070-E01 | 異常系 |
| （制約・null安全）URL null フォールバック | TC-0070-E02 | 異常系 |

---

## 7. テストケース一覧サマリー

| # | テストケースID | 分類 | 概要 | 信頼性 |
|---|---|---|---|---|
| 1 | TC-0070-N01 | 正常系 | LLMフィールド→ value=""・valueSource=LLM・llmPrompt保持 | 🔵 |
| 2 | TC-0070-N02 | 正常系 | FIXED/HTML_META/URL/EMPTY 回帰＋メタ情報付与 | 🔵 |
| 3 | TC-0070-N03 | 正常系 | 3引数コンストラクタ後方互換 | 🔵 |
| 4 | TC-0070-E01 | 異常系 | HTML_META メタ欠落→空文字フォールバック | 🔵 |
| 5 | TC-0070-E02 | 異常系 | URL sourceUrl=null→空文字フォールバック | 🔵 |
| 6 | TC-0070-B01 | 境界値 | template=null→空リスト | 🔵 |
| 7 | TC-0070-B02 | 境界値 | fields=空→空リスト | 🟡 |
| 8 | TC-0070-B03 | 境界値 | LLMフィールドで llmPrompt=""→分類保持 | 🟡 |

### 信頼性レベル分布

- 🔵 青信号: 6件（75%）
- 🟡 黄信号: 2件（25%）
- 🔴 赤信号: 0件（0%）

---

## 品質判定

- **テストケース分類**: 正常系（3）・異常系（2）・境界値（3）を網羅 ✅
- **期待値定義**: 各テストケースで具体的な期待値（`value`/`valueSource`/`llmPrompt`/リストサイズ）を明記 ✅
- **技術選択**: Kotlin 2.2.10 + JUnit 4 で確定（既存テストと統一）✅
- **実装可能性**: 現行 `TemplateApplicatorTest.kt` の延長で実装可能。対象は純粋関数で外部依存なし ✅
- **信頼性レベル**: 🔵 75% / 🟡 25% / 🔴 0% ✅

**総合判定**: ✅ 高品質。要件定義書 TC1〜TC6 を全カバーし、null安全フォールバックの異常系（E01/E02）を追加。実装着手可能。

# TDD要件定義書: CustomFieldState拡張・TemplateApplicator.buildCustomFields()のLLM対応

- **機能名**: custom-field-llm-support
- **タスクID**: TASK-0070
- **要件名**: llm-memo-rewrite
- **フェーズ**: Phase 6 - カスタムフィールドのLLM生成（Could Have）
- **作成日**: 2026-07-09

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: `CustomFieldState`（EditScreenで各カスタムフィールドの編集状態を表すドメインモデル）に `valueSource: FieldValueSource` と `llmPrompt: String` を追加し、`TemplateApplicator.buildCustomFields()` が `FieldValueSource.LLM` のフィールドを「値は空文字・`valueSource`と`llmPrompt`を保持した状態」で初期化するよう対応する。
- 🔵 **どのような問題を解決するか**: LLM生成対象のカスタムフィールドについて、テンプレート適用時点では値を生成せず（REQ-304の設計判断）、EditScreen上のボタン押下時に生成するための「橋渡し情報」（生成方法＝`valueSource`、プロンプト＝`llmPrompt`）を`CustomFieldState`に持たせる。これにより後続タスク（TASK-0072/0073）でEditScreen側がLLM生成ボタンの表示判定・生成実行を行えるようになる。
- 🔵 **想定されるユーザー**: メモをObsidianに共有する際、テンプレートで定義したカスタムフィールドの値をLLMに生成させたいエンドユーザー（間接的な受益者）。本タスクの直接的な利用者は後続のEditScreen実装コード。
- 🔵 **システム内での位置づけ**: ドメインモデル層（`CustomFieldState`）とテンプレート適用ロジック（`TemplateApplicator`）の変更。MVVM + Repositoryアーキテクチャにおける Model 層と、テンプレート→編集状態の変換を担う純粋関数の拡張。実際のLLM API呼び出し（データ層）や UI（View層）は本タスクの対象外。
- **参照したEARS要件**: REQ-104, REQ-304, REQ-303
- **参照した設計文書**: architecture.md「変更が必要な既存コンポーネント」, interfaces.kt「CustomFieldState（変更後）」

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 2-1. `CustomFieldState`（データ構造の変更） 🟡

**変更前**（現状 `domain/model/CustomFieldState.kt`）:
```kotlin
data class CustomFieldState(
    val key: String,
    val value: String,
    val valueType: FieldValueType,
)
```

**変更後**:
```kotlin
data class CustomFieldState(
    val key: String,
    val value: String,
    val valueType: FieldValueType,
    val valueSource: FieldValueSource = FieldValueSource.FIXED, // 新規追加
    val llmPrompt: String = "",                                // 新規追加
)
```

- 🟡 `valueSource`: 追加フィールド。デフォルト値 `FieldValueSource.FIXED`。EditScreen側でLLM生成ボタン表示判定（`valueSource == LLM`）に使用する。
- 🟡 `llmPrompt`: 追加フィールド。デフォルト値 `""`（空文字＝未設定）。`valueSource == LLM` の場合にLLM呼び出しへ渡すプロンプト。
- 🔵 **後方互換性**: 両フィールドともデフォルト値付きのため、既存の3引数コンストラクタ呼び出し（`CustomFieldState(key, value, valueType)`）はコンパイル互換を維持する。

**参照したEARS要件**: REQ-104, REQ-304
**参照した設計文書**: interfaces.kt L77-83「CustomFieldState（変更後）」

### 2-2. `TemplateApplicator.buildCustomFields()`（関数の入出力） 🔵

**シグネチャ（変更なし）**:
```kotlin
fun buildCustomFields(template: Template?, processed: ProcessedContent): List<CustomFieldState>
```

- 🔵 **入力**:
  - `template: Template?` — カスタムフィールド定義（`fields: List<TemplateField>`）を持つテンプレート。`null` 可。
  - `processed: ProcessedContent` — 共有コンテンツの処理結果。`metadata: Map<String, String>`, `sourceUrl: String?` を保持。
- 🔵 **出力**: `List<CustomFieldState>` — 各 `TemplateField` を変換した編集状態のリスト。`template == null` の場合は空リスト。
- 🔵 **入出力の関係性（`valueSource` ごとの `value` 算出、既存ロジック維持）**:

| `field.valueSource` | 生成される `value` | `valueSource` | `llmPrompt` |
|---|---|---|---|
| `FIXED` | `field.defaultValue` | `field.valueSource` | `field.llmPrompt` |
| `HTML_META` | `processed.metadata[field.metaKey] ?: ""` | 同上 | 同上 |
| `URL` | `processed.sourceUrl ?: ""` | 同上 | 同上 |
| `EMPTY` | `""` | 同上 | 同上 |
| `LLM` | `""`（テンプレート適用時は生成しない） | 同上 | 同上 |

- 🔵 **本タスクでの差分**: `CustomFieldState` 生成時に、これまで渡していなかった `field.valueSource` と `field.llmPrompt` を追加で渡す。`LLM` ケースの `value` は従来どおり空文字。

**データフロー**: `Template.fields` → `map` で各 `TemplateField` を評価 → `CustomFieldState` へ変換 → `List<CustomFieldState>`

**参照したEARS要件**: REQ-304
**参照した設計文書**: architecture.md「変更が必要な既存コンポーネント」（TemplateApplicator.kt 行）, 現行 `TemplateApplicator.kt` L35-50

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **回帰なし（互換性要件）**: `FIXED`/`HTML_META`/`URL`/`EMPTY` の `value` 算出ロジック、および `template == null` の空リスト返却は変更しないこと。既存の単体テストがすべて通ること（REQ相当の完了条件）。
- 🔵 **アーキテクチャ制約**: `TemplateApplicator` は `object`（純粋関数の集合）。副作用・非同期処理・LLM API呼び出しを本関数に持ち込まない。実際のLLM生成はEditScreen上のボタン押下時（TASK-0072/0073）に行う（REQ-304の設計判断、design-interview.md Q2）。
- 🔵 **null安全性**: `TemplateField.llmPrompt` はデフォルト `""` の非null `String`。`metaKey` が `null` の `HTML_META` は Elvis演算子で `""` にフォールバック（既存パターン踏襲）。
- 🔵 **when式の網羅性**: `FieldValueSource` は `sealed`ではなく`enum`。全5値（`FIXED`/`HTML_META`/`URL`/`EMPTY`/`LLM`）を明示的に分岐すること。
- 🔵 **言語・ビルド制約**: Kotlin 2.2.10 / Java互換11 / ktlint準拠。data classは`copy()`で不変更新。
- 🟡 **パフォーマンス要件**: 本タスク固有の数値要件は要件定義書に明記なし。テンプレート適用時にLLM API呼び出しを行わないこと自体が、共有フロー高速化への寄与（REQ-304の設計意図）。
- **参照したEARS要件**: REQ-304, REQ-303
- **参照した設計文書**: architecture.md「変更が必要な既存コンポーネント」, design-interview.md Q2

---

## 4. 想定される使用例（EARS Edgeケース・データフローベース）

### 4-1. 基本的な使用パターン 🔵

- 🔵 **LLMフィールドの変換**: `valueSource = LLM`, `llmPrompt = "要約を作成してください"` の `TemplateField` を含む `Template` を渡すと、`value = ""`, `valueSource = LLM`, `llmPrompt = "要約を作成してください"` の `CustomFieldState` が生成される。
- 🔵 **既存4種の変換**: `FIXED`/`HTML_META`/`URL`/`EMPTY` の各フィールドは、対応する `value`（`defaultValue`/`metadata[metaKey]`/`sourceUrl`/空文字）で生成され、`valueSource`・`llmPrompt` も正しく引き継がれる。
- 🔵 **混在テンプレート**: 複数の `valueSource` を持つフィールドが混在する `Template` でも、各フィールドが独立に正しく変換される。

### 4-2. エッジケース 🔵🟡

- 🔵 **template が null**: `buildCustomFields(null, processed)` は空リストを返す（既存挙動）。
- 🔵 **fields が空リスト**: 空の `List<CustomFieldState>` を返す。
- 🟡 **LLM フィールドで llmPrompt が空文字**: `llmPrompt = ""`（未設定）でも `value = ""`, `valueSource = LLM`, `llmPrompt = ""` の `CustomFieldState` が生成される（本タスクではプロンプト未設定のバリデーションは行わない。生成実行時＝TASK-0072/0073の責務）。
- 🟡 **HTML_META で metaKey が null / metadata に該当なし**: `value = ""` にフォールバック（既存挙動、回帰確認対象）。

### 4-3. エラーケース 🔵

- 🔵 本タスクは純粋なデータ変換のみで、例外送出・エラーハンドリングは新規に発生しない。LLM API起因のエラー処理は本タスクの範囲外（後続タスク）。

**参照したEARS要件**: REQ-304
**参照した設計文書**: dataflow.md（テンプレート適用フロー）, 現行 `TemplateApplicator.kt`

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: カスタムフィールドのLLM生成（Could Have、requirements.md 概要）
- **参照した機能要件**:
  - REQ-104: カスタムフィールドのLLM生成が有効な場合、テンプレート編集画面で当該フィールド用プロンプト入力欄を表示（🔵）
  - REQ-304: `FieldValueSource` が `LLM` のカスタムフィールドについて、テンプレート適用時にLLMで値を生成してもよい（生成タイミングはEditScreen上のボタン押下時に統一）（🟡）
  - REQ-303: `FieldValueSource` に `LLM` を追加してもよい（🔵、TASK-0056で追加済み）
- **参照した非機能要件**: 明示的なNFR番号の直接対応なし（回帰なし・アーキテクチャ純粋関数維持は完了条件・設計方針由来）
- **参照したEdgeケース**: 要件定義書に本タスク固有のEDGE番号なし。エッジケースは完了条件・既存実装から導出
- **参照した受け入れ基準**: acceptance-criteria.md TC-104-01（プロンプト入力欄表示）, TC-104-02（テンプレート適用時の生成）※本タスクはこれらの前提となるモデル拡張
- **参照した設計文書**:
  - **アーキテクチャ**: architecture.md「変更が必要な既存コンポーネント」（`CustomFieldState.kt` / `TemplateApplicator.kt` 行）
  - **型定義**: interfaces.kt L67-83「CustomFieldState（変更後）」
  - **設計ヒアリング**: design-interview.md Q2（LLM生成タイミング）
  - **実装参考**: 現行 `TemplateApplicator.kt`, `TemplateField.kt`, `FieldValueSource.kt`

---

## 6. テスト観点（要件由来の受け入れ確認項目）

| # | 観点 | 期待結果 | 信頼性 |
|---|------|---------|--------|
| TC1 | LLMフィールドの変換 | `value=""`, `valueSource=LLM`, `llmPrompt=<field値>` | 🔵 |
| TC2 | FIXED/HTML_META/URL/EMPTY の回帰 | 既存ロジックで `value` 算出、`valueSource`/`llmPrompt` を正しく引き継ぐ | 🔵 |
| TC3 | template=null の回帰 | 空リストを返す | 🔵 |
| TC4 | fields=空 の回帰 | 空リストを返す | 🟡 |
| TC5 | LLMフィールドで llmPrompt="" | `value=""`, `valueSource=LLM`, `llmPrompt=""` を保持 | 🟡 |
| TC6 | 既存3引数コンストラクタ互換 | 既存呼び出し（NoteComposerTest等）がコンパイル・動作継続 | 🔵 |

---

## 7. 実装対象ファイル

- `app/src/main/java/com/den4dr/share2Obsidian/domain/model/CustomFieldState.kt`（`valueSource`/`llmPrompt` 追加）
- `app/src/main/java/com/den4dr/share2Obsidian/TemplateApplicator.kt`（`buildCustomFields()` の `CustomFieldState` 生成に `field.valueSource`, `field.llmPrompt` を渡す）
- テスト: `app/src/test/java/com/den4dr/share2Obsidian/`（`TemplateApplicator` 用テスト。既存の `TemplateTest.kt` / `NoteComposerTest.kt` / `EditScreenViewModelTest.kt` の互換確認を含む）

---

## 品質判定

- 要件の曖昧さ: なし（データ変換の入出力が明確、生成タイミングは設計判断で確定）
- 入出力定義: 完全（型・`valueSource`別の値算出テーブルを明記）
- 制約条件: 明確（回帰なし・純粋関数維持・null安全・後方互換）
- 実装可能性: 確実（現行実装からの差分が小さく、依存タスクTASK-0056/0057完了済み）
- 信頼性レベル分布: 🔵 中心（設計判断由来の`valueSource`/`llmPrompt`拡張と一部エッジケースが🟡）

**総合判定**: ✅ 高品質（実装着手可能）。設計上の拡張（`CustomFieldState`への2フィールド追加）が🟡だが、要件REQ-104/REQ-304・interfaces.kt・architecture.mdから論理的に導出されており、実装リスクは低い。

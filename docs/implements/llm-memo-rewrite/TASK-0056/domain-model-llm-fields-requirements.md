# TASK-0056 要件定義書: Template/TemplateField/FieldValueSource ドメインモデル変更

**機能名（feature_name）**: domain-model-llm-fields
**タスクID**: TASK-0056
**要件名**: llm-memo-rewrite
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0056/domain-model-llm-fields-requirements.md`
**作成日**: 2026-07-05
**フェーズ**: Phase 1 - 基盤構築

---

## 【信頼性レベル凡例】

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: LLMによるメモ更改機能の基盤として、既存ドメインモデルへLLM関連プロパティ／enum値を追加する。具体的には (1) `Template` にテンプレート単位の本文リライト用プロンプト `bodyLlmPrompt` を追加、(2) `TemplateField` にフィールド単位のLLM生成用プロンプト `llmPrompt` を追加、(3) `FieldValueSource` enum にカスタムフィールドのLLM生成を表す値 `LLM` を追加する。
- 🔵 **どのような問題を解決するか**: 後続タスク（本文リライトUI・タグ提案・カスタムフィールドLLM生成）がLLMプロンプトや値取得方法を保持・参照できるようにするための、ドメイン層の器を用意する。この変更なしには、テンプレート・フィールドにLLMプロンプトを紐付けて永続化・適用することができない。
- 🔵 **想定されるユーザー**: 直接のエンドユーザーはおらず、本タスクは開発者向けの内部基盤変更。最終的な機能利用者は、Obsidianへ共有するメモをLLMで整形したいアプリ利用者（user-stories.md 参照）。
- 🔵 **システム内での位置づけ**: MVVM + Repository アーキテクチャの Domain 層（`domain/model`）。UI層・Repository層・TemplateApplicator など既存の呼び出し元から参照される中核データモデル。本タスクは「既存フィールドを保持したまま追加のみ」を行い、既存呼び出し元への影響をデフォルト値で最小化する（architecture.md「変更が必要な既存コンポーネント」テーブル）。

- **参照したEARS要件**: REQ-101（本文用LLMプロンプト）、REQ-104（カスタムフィールド用プロンプト入力欄）、REQ-303（`FieldValueSource` に `LLM` 追加）
- **参照した設計文書**: `docs/design/llm-memo-rewrite/interfaces.kt`「Domain モデル変更」（行20〜65）、`docs/design/llm-memo-rewrite/architecture.md`

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

本タスクはデータモデル（Kotlin data class / enum）の定義変更であり、関数的な入出力ではなく「型の構造」が仕様となる。

### 2.1 Template（変更後） 🔵

**信頼性**: 🔵 *interfaces.kt 行28〜35・REQ-101より*

```kotlin
data class Template(
    val id: Long = 0,
    val name: String,
    val body: String = "",
    val bodyLlmPrompt: String = "",   // 新規追加: 本文リライト用プロンプト（REQ-101）
    val fields: List<TemplateField>,
    val isDefault: Boolean = false,
)
```

| プロパティ | 型 | デフォルト | 制約・意味 |
|-----------|----|-----------|-----------|
| `bodyLlmPrompt` | `String` | `""`（空文字） | 🔵 追加プロパティ。位置は `body` の直後・`fields` の前。空文字は「本文用プロンプト未設定」を意味する（REQ-102 でボタン非活性判定に利用）。範囲・文字数上限は本タスクでは規定しない。 |

**実装ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/domain/model/Template.kt`

### 2.2 TemplateField（変更後） 🔵

**信頼性**: 🔵 *interfaces.kt 行55〜65・REQ-104より*

```kotlin
data class TemplateField(
    val id: Long = 0,
    val templateId: Long = 0,
    val key: String,
    val valueSource: FieldValueSource,
    val valueType: FieldValueType,
    val defaultValue: String = "",
    val metaKey: HtmlMetaKey? = null,
    val llmPrompt: String = "",   // 新規追加: valueSource == LLM の場合のみ使用（REQ-104）
    val sortOrder: Int = 0,
)
```

| プロパティ | 型 | デフォルト | 制約・意味 |
|-----------|----|-----------|-----------|
| `llmPrompt` | `String` | `""`（空文字） | 🔵 追加プロパティ。位置は `metaKey` の直後・`sortOrder` の前。`valueSource == LLM` の場合のみ意味を持つ。空文字は「フィールド用プロンプト未設定」。 |

**実装ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/domain/model/TemplateField.kt`

### 2.3 FieldValueSource（変更後） 🔵

**信頼性**: 🔵 *interfaces.kt 行45・REQ-303より*

```kotlin
enum class FieldValueSource { FIXED, HTML_META, URL, EMPTY, LLM }
```

| 項目 | 内容 |
|------|------|
| 追加値 | 🔵 `LLM`（既存の `FIXED, HTML_META, URL, EMPTY` の末尾に追加） |
| 参照方法 | `FieldValueSource.entries` に含まれること、`FieldValueSource.valueOf("LLM")` が正常動作すること |

**実装ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt`

### 2.4 入出力の関係性・データフロー 🔵

- 🔵 `Template.bodyLlmPrompt` / `TemplateField.llmPrompt` は永続化対象。後続 TASK-0057 で `TemplateEntity` / `TemplateFieldEntity` にカラム追加・Room Migration(2→3) が行われ、Repository マッピングで相互変換される（本タスクのドメインモデルはその写像元）。
- 🔵 `FieldValueSource.LLM` は TASK-0070/0071/0072 でカスタムフィールドのLLM生成UI・適用ロジックの分岐に使用される。
- 🔵 本タスク自体は他コンポーネントの挙動を変更せず、型の器を追加するのみ。データフロー上の実利用は後続タスク。

- **参照したEARS要件**: REQ-101, REQ-104, REQ-303
- **参照した設計文書**: `interfaces.kt`（`Template` / `TemplateField` / `FieldValueSource` 定義）、`docs/design/llm-memo-rewrite/database-schema.kt`（後続TASK-0057で使用）

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **後方互換性（最重要制約）**: 追加プロパティ `bodyLlmPrompt` / `llmPrompt` は必ずデフォルト値 `""` を指定し、既存の `Template` / `TemplateField` コンストラクタ呼び出し（引数指定なし）がコンパイルエラーを起こさないこと。既存呼び出し元（`TemplateApplicator`、`TemplateRepositoryImpl` 等）への影響を最小化する。 *（TASK-0056.md 完了条件・note.md 注意事項2より）*
- 🔵 **アーキテクチャ制約**: 変更対象は Domain 層（`domain/model`）のみ。data class / enum class の基本仕様（自動生成 `copy()`、`enum.entries`）に準拠する。命名は camelCase（既存コードに準拠）。 *（note.md コーディング規約より）*
- 🔵 **言語・SDK制約**: Kotlin 2.2.10、minSdk 33、targetSdk 36、Java互換性 11。 *（CLAUDE.md・note.md 技術スタックより）*
- 🟡 **プロパティ位置制約**: `bodyLlmPrompt` は `body` の直後、`llmPrompt` は `metaKey` の直後に配置する。data class は名前付き引数で呼び出せば位置非依存だが、interfaces.kt の設計に合わせ可読性・後続Entityマッピングとの対応のため位置を統一する。 *（interfaces.kt の記載順に基づく妥当な推測）*
- 🔵 **本タスクで対象外の非機能要件（参考）**: NFR-001（LLMタイムアウト30秒）、NFR-101（APIキー暗号化）、NFR-102（機微情報の非ログ出力）は本タスクでは直接影響しない（後続 TASK-0058 以降で実装）。

- **参照したEARS要件**: REQ-101, REQ-104, REQ-303（MUST/MAY）、NFR-001, NFR-101, NFR-102（参考・対象外）
- **参照した設計文書**: `architecture.md`（アーキテクチャパターン・変更対象テーブル）、`interfaces.kt`（プロパティ順序）

---

## 4. 想定される使用例（Edgeケース・データフローベース）

### 4.1 基本的な使用パターン 🔵

- 🔵 **本文プロンプトの部分更新**: 既存 `Template` に対し `template.copy(bodyLlmPrompt = "要約してください")` を呼び、他プロパティを保持したまま本文プロンプトのみ設定する。
- 🔵 **フィールドプロンプトの部分更新**: 既存 `TemplateField` に対し `field.copy(llmPrompt = "タイトルを生成してください")` を呼び、他プロパティを保持したままフィールドプロンプトのみ設定する。
- 🔵 **LLM値ソースの列挙・変換**: `FieldValueSource.entries` から `LLM` を選択、または `FieldValueSource.valueOf("LLM")` で文字列から enum へ変換する（Repository のマッピングやUI選択で使用）。

### 4.2 エッジケース 🔵

- 🔵 **未設定（空文字）ケース**: `bodyLlmPrompt` / `llmPrompt` を指定せず生成した場合、両者は `""`。REQ-102 に基づき後続UIで「メモを更改」ボタンを非活性化する判定材料となる（本タスクでは判定は実装しない）。
- 🔵 **既存コード互換ケース**: `Template(name = "Web記事", fields = emptyList())` のように新規プロパティ未指定で生成しても正常にコンパイル・生成される。
- 🟡 **enum 網羅ケース**: `FieldValueSource` を `when` で分岐する既存コードがある場合、`LLM` 追加により網羅性の警告が出る可能性がある。本タスクでは新規モデル追加のみのため直接の分岐修正は対象外だが、後続タスクで対応する。 *（enum 追加時の一般的挙動からの妥当な推測）*

### 4.3 エラーケース 🔵

- 🔵 **存在しない enum 値の変換**: `FieldValueSource.valueOf("XXX")` は `IllegalArgumentException` を投げる（Kotlin enum 標準仕様）。本タスクでは `valueOf("LLM")` が例外を投げないことを保証する。
- 🔵 本タスクはデータモデル定義のため、実行時のI/Oエラーや外部依存エラーは発生しない。

- **参照したEARS要件**: REQ-101, REQ-102, REQ-104, REQ-303
- **参照した設計文書**: `dataflow.md`（LLM呼び出しシーケンス・後続タスクで具体化）、`interfaces.kt`

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: `docs/spec/llm-memo-rewrite/user-stories.md`（LLMによるメモ更改）
- **参照した機能要件**:
  - REQ-101 — テンプレートの本文用LLMプロンプトをLLMリクエストに含める（`Template.bodyLlmPrompt` の根拠）
  - REQ-104 — カスタムフィールドLLM生成でフィールド用プロンプト入力欄を表示（`TemplateField.llmPrompt` の根拠）
  - REQ-303 — `FieldValueSource` に `LLM` を追加（`FieldValueSource.LLM` の根拠）
  - REQ-102（参考）— プロンプト未設定時のボタン非活性（`bodyLlmPrompt == ""` 判定の後続利用）
- **参照した非機能要件**: NFR-001, NFR-101, NFR-102（いずれも本タスクでは対象外・後続タスクで実装）
- **参照したEdgeケース**: 明示的なEDGE-XXXの割当なし。デフォルト値未設定・既存コード互換・enum網羅は data class / enum 標準仕様および後方互換性要件から導出。
- **参照した受け入れ基準**: `docs/spec/llm-memo-rewrite/acceptance-criteria.md`（本文リライト・カスタムフィールドLLM生成の前提となるモデル定義）
- **参照した設計文書**:
  - **アーキテクチャ**: `docs/design/llm-memo-rewrite/architecture.md`（変更が必要な既存コンポーネント）
  - **データフロー**: `docs/design/llm-memo-rewrite/dataflow.md`（LLM呼び出しシーケンス）
  - **型定義**: `docs/design/llm-memo-rewrite/interfaces.kt`（`Template` 行20-35・`FieldValueSource` 行37-45・`TemplateField` 行47-65）
  - **データベース**: `docs/design/llm-memo-rewrite/database-schema.kt`（TASK-0057で使用）
  - **API仕様**: `docs/design/llm-memo-rewrite/api-endpoints.md`（本タスクでは対象外）

---

## 6. 完了条件（TASK-0056.md より）

- [ ] `Template.bodyLlmPrompt: String = ""` が追加されている 🔵
- [ ] `TemplateField.llmPrompt: String = ""` が追加されている 🔵
- [ ] `FieldValueSource.LLM` が追加されている 🔵
- [ ] 既存の単体テストがすべて通る 🔵

---

## 7. 単体テスト要件（TASK-0056.md 由来・testcases フェーズで詳細化）

1. 🔵 **TC1**: `Template` の `copy()` で `bodyLlmPrompt` のみ変更でき、他プロパティは不変（REQ-101・data class仕様）
2. 🔵 **TC2**: `FieldValueSource.entries` に `LLM` が含まれ、`valueOf("LLM")` が例外なく `LLM` を返す（REQ-303）
3. 🔵 **TC3**: `TemplateField` の `copy()` で `llmPrompt` のみ変更でき、他プロパティは不変（REQ-104・data class仕様）
4. 🔵 **TC4**: 新規プロパティ未指定で `Template` / `TemplateField` を生成するとデフォルト値 `""` となり、コンパイルエラーが発生しない（後方互換性）

**テスト配置**: `app/src/test/java/com/den4dr/share2Obsidian/domain/model/`（既存 `TemplateTest.kt` を参考）

---

## 8. 品質判定

| 評価項目 | 結果 |
|---------|------|
| 要件の曖昧さ | なし（設計文書に確定コード例あり） |
| 入出力定義 | 完全（3モデルの型・デフォルト・制約を明記） |
| 制約条件 | 明確（後方互換性・配置・SDK） |
| 実装可能性 | 確実（既存パターン踏襲・3ファイル追加のみ） |

### 信頼性レベル分布

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 |
|---------|-------|-------|-------|
| 概要（第1章） | 4 | 0 | 0 |
| 入出力（第2章） | 4 | 0 | 0 |
| 制約（第3章） | 4 | 1 | 0 |
| 使用例（第4章） | 6 | 2 | 0 |
| **合計** | **18** | **3** | **0** |

- 🔵 青信号: 18項目 (86%)
- 🟡 黄信号: 3項目 (14%)
- 🔴 赤信号: 0項目 (0%)

**総合品質評価**: ✅ 高品質

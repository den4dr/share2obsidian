# TDD開発コンテキスト: TASK-0070

**タスク**: CustomFieldState拡張・TemplateApplicator.buildCustomFields()のLLM対応
**タスクID**: TASK-0070
**推定工数**: 6時間
**フェーズ**: Phase 6 - カスタムフィールドのLLM生成（Could Have）
**信頼性レベル**: 🟡（interfaces.kt「CustomFieldState（変更後）」・architecture.mdより）

---

## 1. 技術スタック

### 言語・フレームワーク
- **言語**: Kotlin 2.2.10
- **ビルドシステム**: Gradle 9.2.1 (Kotlin DSL)
- **ターゲットSDK**: Android 13+ (minSdk 33)
- **UIフレームワーク**: Jetpack Compose（BOM 2024.09.00）
- **デザインシステム**: Material 3

### アーキテクチャパターン
- **パターン**: MVVM（Model-View-ViewModel）+ Repository
- **依存性注入**: Hilt 2.59.2
- **ライフサイクル**: AndroidX Lifecycle（ViewModel, StateFlow）
- **非同期処理**: Kotlin Coroutines + viewModelScope

### 主要ライブラリバージョン
| ライブラリ | バージョン | 用途 |
|-----------|-----------|------|
| junit | 4.13.2 | ユニットテスト |
| kotlinx-coroutines-test | 1.9.0 | 非同期テスト |
| mockk | 1.13.12 | モック・スタブ |
| room | 2.7.1 | ORM/永続化 |
| datastore | 1.1.7 | 設定保存 |
| ktor-client-core | 3.3.1 | HTTP通信 |
| kotlinx-serialization-json | 1.9.0 | JSON シリアライズ |
| androidx-security-crypto | 1.1.0 | APIキー暗号化保存 |
| robolectric | 4.14.1 | ユニットテストの依存関係 |

参照元: gradle/libs.versions.toml

---

## 2. 開発ルール

### コーディング規約
- **Kotlin バージョン**: 2.2.10 以上
- **Java互換性**: 11
- **命名規則**: camelCase（変数・関数）、PascalCase（クラス）
- **Package構成**: com.den4dr.share2Obsidian.[feature]
- **フォーマット**: ktlint準拠

### プロジェクト固有のルール
- **Build コマンド**: `mise exec -- ./gradlew ...` で実行（Java 管理のため）
- **アーキテクチャ**: 単一Activity + Compose UIの継続
- **DI**: Hilt を使用。ViewModel は @HiltViewModel で注釈
- **ViewModel スコープ**: viewModelScope.launch で非同期処理を実行

### テスト関連ルール
- **テストフレームワーク**: JUnit 4 + MockK
- **テストディレクトリ**: app/src/test/java/
- **テスト命名**: Class名 + Test.kt （例: TemplateApplicatorTest.kt）
- **テストメソッド命名**: バックティック記法で日本語・英語混用可（例: `` `LLMフィールドが空文字で初期化されること`() ``）
- **アサーション**: org.junit.Assert.* を使用
- **モック**: MockK の `mockk()`, `every {}`, `verify {}` を使用
- **非同期テスト**: runBlocking { } または @get:Rule val instantExecutorRule でテスト

参照元: docs/tech-stack.md, 既存テストファイル群

---

## 3. 関連実装

### 既存のカスタムフィールド処理
| ファイル | 役割 | 参照ポイント |
|---------|------|-----------|
| app/src/main/java/com/den4dr/share2Obsidian/domain/model/CustomFieldState.kt | CustomFieldState ドメインモデル | 現在: key, value, valueType のみ。本タスクで valueSource, llmPrompt を追加 |
| app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt | フィールド値取得元 enum | FIXED, HTML_META, URL, EMPTY, LLM を列挙。LLM は既に追加済み（TASK-0056） |
| app/src/main/java/com/den4dr/share2Obsidian/domain/model/TemplateField.kt | テンプレートフィールド定義 | valueSource, llmPrompt を含む（既存実装で追加済み） |
| app/src/main/java/com/den4dr/share2Obsidian/TemplateApplicator.kt | テンプレート適用ロジック | buildCustomFields() で FieldValueSource.LLM ケースを処理（現在は暫定実装で空文字返却） |

### 実装パターン
- **値処理パターン**: when 式で FieldValueSource を分岐（FIXED/HTML_META/URL/EMPTY）
- **null安全性**: Elvis operator (?:) を使用してデフォルト値を指定
- **不変性**: data class のコピー（copy()）で新インスタンスを生成

参照元: app/src/main/java/com/den4dr/share2Obsidian/TemplateApplicator.kt（現在の実装）

---

## 4. 設計文書

### 要件定義
- **ファイル**: docs/spec/llm-memo-rewrite/requirements.md
- **対象要件**: REQ-101（本文LLMプロンプト）, REQ-104（カスタムフィールドLLM）, REQ-304（テンプレート適用時のLLM生成）

### 受け入れ基準
- **ファイル**: docs/spec/llm-memo-rewrite/acceptance-criteria.md
- **対象基準**: TC-104-01（プロンプト入力欄表示）, TC-104-02（テンプレート適用時の生成）

### アーキテクチャ設計
- **ファイル**: docs/design/llm-memo-rewrite/architecture.md
- **関連セクション**:
  - 「変更が必要な既存コンポーネント」: CustomFieldState の拡張定義
  - 「EditScreenViewModel 設計変更」: ViewModel内部で sourceContent/bodyLlmPrompt を保持
  - 「ProcessedContent 保持設計」: LLM入力用の元コンテンツ保持方法

### インターフェース・型定義
- **ファイル**: docs/design/llm-memo-rewrite/interfaces.kt
- **対象型**:
  - CustomFieldState（変更後）: valueSource, llmPrompt を追加
  - LlmRewriteResult: 呼び出し結果（Success/Failure）

参照元: docs/spec/llm-memo-rewrite/requirements.md, docs/spec/llm-memo-rewrite/acceptance-criteria.md, docs/design/llm-memo-rewrite/architecture.md, docs/design/llm-memo-rewrite/interfaces.kt

---

## 5. タスク概要と実装詳細

### 目的
CustomFieldState に valueSource（フィールド値取得方法）と llmPrompt（LLM呼び出し用プロンプト）を追加し、TemplateApplicator.buildCustomFields() が FieldValueSource.LLM フィールドを空文字で初期化するよう対応する。

実際のLLM生成はテンプレート適用時ではなく、EditScreen上のボタン押下時に行う（TASK-0072で実装）。

### 変更対象ファイル

#### 1. CustomFieldState.kt
**現在**:
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
    val valueSource: FieldValueSource = FieldValueSource.FIXED,  // 新規追加
    val llmPrompt: String = "",                                  // 新規追加
)
```

**実装ファイル**: app/src/main/java/com/den4dr/share2Obsidian/domain/model/CustomFieldState.kt

#### 2. TemplateApplicator.buildCustomFields()
**現在の実装**（行47）:
```kotlin
FieldValueSource.LLM -> ""
```

**変更後**:
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
        FieldValueSource.LLM -> ""  // LLM生成は EditScreen上のボタン押下時に行う（REQ-304）
    }
    CustomFieldState(field.key, value, field.valueType, field.valueSource, field.llmPrompt)
} ?: emptyList()
```

**重要**: CustomFieldState 生成時に field.valueSource と field.llmPrompt を渡すことで、EditScreen側でLLM生成ボタンの表示判定に使用可能にする。

**実装ファイル**: app/src/main/java/com/den4dr/share2Obsidian/TemplateApplicator.kt

### 完了条件
- [ ] CustomFieldState に valueSource: FieldValueSource = FieldValueSource.FIXED と llmPrompt: String = "" が追加される
- [ ] buildCustomFields() が FieldValueSource.LLM の場合、値は空文字、valueSource と llmPrompt（テンプレートの TemplateField.llmPrompt）を正しく設定した CustomFieldState を生成する
- [ ] 既存の FIXED/HTML_META/URL/EMPTY の挙動が変更されない（回帰なし）
- [ ] 既存の単体テストがすべて通る

参照元: docs/tasks/llm-memo-rewrite/TASK-0070.md

---

## 6. テスト関連情報

### テストフレームワーク・設定
- **テストツール**: JUnit 4 + MockK
- **テストディレクトリ**: app/src/test/java/com/den4dr/share2Obsidian/
- **Gradle設定**: app/build.gradle.kts に testImplementation 設定済み

### 既存テストのディレクトリ構成・命名パターン
```
app/src/test/java/com/den4dr/share2Obsidian/
├── content/
│   ├── ContentTypeDetectorTest.kt
│   ├── TextContentProcessorTest.kt
│   ├── HtmlContentProcessorTest.kt
│   └── ...
├── data/
│   └── llm/
│       ├── ChatCompletionDtoTest.kt
│       ├── LlmRewriteRepositoryImplTest.kt
│       └── ...
└── ...
```

### テストユーティリティ・モック設定パターン

**基本的なテスト構造** (参照: app/src/test/java/com/den4dr/share2Obsidian/content/TextContentProcessorTest.kt):
```kotlin
class TextContentProcessorTest {
    private val processor = TextContentProcessor()
    
    @Test
    fun `テスト説明`() = runBlocking {
        // Given
        val content = ShareContent.Text(...)
        
        // When
        val result = processor.process(content)
        
        // Then
        assertEquals(expected, result.property)
    }
}
```

**MockK使用パターン**:
- `mockk()`: モックオブジェクト生成
- `every { ... } returns ...`: メソッド返却値設定
- `verify { ... }`: メソッド呼び出し検証

**非同期テスト**:
- `runBlocking { }`: suspend 関数をテスト内で同期的に実行

### テスト関連ファイル
- gradle/libs.versions.toml: テスト依存関係定義
- app/build.gradle.kts: testImplementation ブロック

参照元: gradle/libs.versions.toml, app/src/test/java 以下の既存テストファイル群

---

## 7. 単体テスト要件

### テストケース1: FieldValueSource.LLMのフィールドが空文字・正しいvalueSource・llmPromptで生成されること
**Given**: valueSource = FieldValueSource.LLM, llmPrompt = "要約を作成してください" の TemplateField を含む Template
**When**: TemplateApplicator.buildCustomFields(template, processed) を呼び出す
**Then**: 該当フィールドの CustomFieldState が value = "", valueSource = FieldValueSource.LLM, llmPrompt = "要約を作成してください" を持つ

### テストケース2: 既存のFIXED/HTML_META/URL/EMPTYの挙動が変更されないこと（回帰確認）
**Given**: valueSource が FIXED/HTML_META/URL/EMPTY である各 TemplateField を含む Template と、対応する ProcessedContent
**When**: TemplateApplicator.buildCustomFields(template, processed) を呼び出す
**Then**: 各フィールドの value が既存実装と同じロジックで算出され、valueSource は各フィールドの FieldValueSource と一致し、llmPrompt は該当 TemplateField.llmPrompt の値と一致する

### テストケース3: templateがnullの場合は空リストが返されること（回帰確認）
**Given**: template = null
**When**: TemplateApplicator.buildCustomFields(null, processed) を呼び出す
**Then**: 空リストが返される

参照元: docs/tasks/llm-memo-rewrite/TASK-0070.md（単体テスト要件セクション）

---

## 8. 注意事項

### 技術的制約
- **null安全性**: TemplateField.llmPrompt が null の場合は空文字 "" をデフォルトとする（既存パターン踏襲）
- **データクラスの拡張**: CustomFieldState に新フィールドを追加時、既存のコンストラクタ呼び出しをすべて確認・更新する必要あり
- **不変性**: data class は copy() メソッドで新インスタンス生成すること

### 設計上の注意点
- **LLM生成タイミング**: 本タスクではテンプレート適用時に LLM生成は行わない（値は空文字）。実際の生成はEditScreen上のボタン押下時（TASK-0072）に行う（REQ-304, design-interview.md Q2）
- **EditScreen統合**: CustomFieldState に valueSource/llmPrompt を含めることで、EditScreen側でLLM生成ボタンの表示・活性判定が可能になる
- **元コンテンツ保持**: EditScreenViewModel が sourceContent（テンプレート適用前の元コンテンツ）を保持するため、TemplateApplicator レベルではこれを考慮不要

### 回帰テストの重要性
- FIXED/HTML_META/URL/EMPTY の既存ロジックは変更しない
- template = null の場合の挙動（空リスト返却）は変更しない
- 既存単体テストが全て通ることを確認（TASK-0056完了条件）

### 信頼性レベル
- CustomFieldState の拡張は設計上の推測だが、EditScreen側でのボタン表示判定に必須（🟡レベル）
- buildCustomFields() の LLM ハンドリングは REQ-304 から直接導出（🔵レベル）

参照元: docs/design/llm-memo-rewrite/architecture.md（「変更が必要な既存コンポーネント」セクション）, docs/spec/llm-memo-rewrite/requirements.md（REQ-304）, docs/design/llm-memo-rewrite/design-interview.md（Q2）

---

## 9. 実装手順（TDD）

### ステップ1: 詳細要件定義
```bash
/tsumiki:tdd-requirements TASK-0070
```

### ステップ2: テストケース洗い出し
```bash
/tsumiki:tdd-testcases
```

### ステップ3: テスト実装（失敗）
```bash
/tsumiki:tdd-red
```

### ステップ4: 最小実装（成功）
```bash
/tsumiki:tdd-green
```

### ステップ5: リファクタリング
```bash
/tsumiki:tdd-refactor
```

### ステップ6: 品質確認
```bash
/tsumiki:tdd-verify-complete
```

---

## 10. 依存関係・前提条件

### 前提タスク（完了済み）
- TASK-0056: Template/TemplateField/FieldValueSource ドメインモデル変更
  - CustomFieldState は既存実装済み（本タスクで拡張）
  - FieldValueSource.LLM は既に追加済み
  - TemplateField.llmPrompt は既に追加済み

### 後続タスク
- TASK-0071: EditFormState・EditScreenViewModel の LLM対応
- TASK-0072: EditScreen の「生成」ボタン実装
- TASK-0073: CustomFieldValue の LLM生成実装

参照元: docs/tasks/llm-memo-rewrite/TASK-0070.md（依存タスクセクション）

---

## 参照ドキュメント一覧

### 主要設計文書
- docs/spec/llm-memo-rewrite/requirements.md
- docs/spec/llm-memo-rewrite/acceptance-criteria.md
- docs/design/llm-memo-rewrite/architecture.md
- docs/design/llm-memo-rewrite/interfaces.kt
- docs/tasks/llm-memo-rewrite/TASK-0070.md

### 実装参考ファイル
- app/src/main/java/com/den4dr/share2Obsidian/domain/model/CustomFieldState.kt
- app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt
- app/src/main/java/com/den4dr/share2Obsidian/domain/model/TemplateField.kt
- app/src/main/java/com/den4dr/share2Obsidian/TemplateApplicator.kt
- app/src/test/java/com/den4dr/share2Obsidian/content/TextContentProcessorTest.kt（テストパターン参考）

### プロジェクト基盤
- docs/tech-stack.md
- gradle/libs.versions.toml
- app/build.gradle.kts

---

**生成日時**: 2026-07-09
**生成ツール**: tsumiki:tdd-tasknote

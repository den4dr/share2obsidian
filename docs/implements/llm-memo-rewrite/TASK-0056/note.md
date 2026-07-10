# TASK-0056 開発コンテキストノート

**タスクID**: TASK-0056  
**要件名**: llm-memo-rewrite  
**作成日**: 2026-07-05  
**TDD開発対象**: ドメインモデル変更（Template/TemplateField/FieldValueSource）

---

## 1. 技術スタック

### 言語・フレームワーク
- **言語**: Kotlin 2.2.10
- **最低SDK**: API 33 (Android 13)
- **ターゲットSDK**: API 36
- **Java互換性**: 11

### プロジェクト構成
- **アーキテクチャパターン**: 単一アクティビティ + Compose UI + MVVM + Repository
- **DI**: Hilt（既存パターン踏襲）
- **非同期処理**: Kotlin Coroutines + viewModelScope

### 依存関係（既にインストール済み）
- `Ktor Client (CIO)` - HTTP通信用
- `kotlinx-serialization` - JSON シリアライズ用
- `androidx-security-crypto` - APIキー暗号化ストレージ用
- `JUnit 4` - ユニットテスト用
- `MockK` - モック・スタブ用
- `Robolectric` - Android リソース依存テスト用

### 参照元
- `gradle/libs.versions.toml` - バージョンカタログ
- `app/build.gradle.kts` - 依存関係定義

---

## 2. 開発ルール

### TDD開発フロー
1. `/tsumiki:tdd-requirements TASK-0056` - 詳細要件定義
2. `/tsumiki:tdd-testcases` - テストケース作成（4個の確定テストケース）
3. `/tsumiki:tdd-red` - テスト実装（失敗状態）
4. `/tsumiki:tdd-green` - 最小実装（テスト通過）
5. `/tsumiki:tdd-refactor` - リファクタリング（品質向上）
6. `/tsumiki:tdd-verify-complete` - 品質確認・完了

### コーディング規約
- **データクラス**: `data class` を使用。自動生成される `copy()` メソッドはテストで重要
- **デフォルト値**: 新規プロパティはデフォルト値を必ず指定（後方互換性）
- **Enum定義**: `enum class` の場合、`.entries` で全値を列挙可能
- **命名規約**: camelCase（既存コードに準じる）

### テスト命名規約（既存パターンから）
- `${対象クラス}Test.kt` ファイル内に複数のテストメソッドを配置
- テスト名: ``function `説明的なテスト名`() {}`` （バッククォートで日本語使用可）
- Arrange-Act-Assert（AAA）パターン

### 参照元
- `docs/tasks/llm-memo-rewrite/TASK-0056.md` - タスク定義（4テストケース指定）
- 既存テスト例: `app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt`

---

## 3. 関連実装

### 変更対象ファイル（既存）

#### 1. Template.kt
**現在**:
```kotlin
data class Template(
    val id: Long = 0,
    val name: String,
    val body: String = "",
    val fields: List<TemplateField>,
    val isDefault: Boolean = false,
)
```

**変更内容**: `bodyLlmPrompt: String = ""` を追加（REQ-101）
- 位置: `body` フィールドの直後、`fields` の前
- テンプレート単位の本文用LLMプロンプト保存用

**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/domain/model/Template.kt`

#### 2. TemplateField.kt
**現在**:
```kotlin
data class TemplateField(
    val id: Long = 0,
    val templateId: Long = 0,
    val key: String,
    val valueSource: FieldValueSource,
    val valueType: FieldValueType,
    val defaultValue: String = "",
    val metaKey: HtmlMetaKey? = null,
    val sortOrder: Int = 0,
)
```

**変更内容**: `llmPrompt: String = ""` を追加（REQ-104）
- 位置: `metaKey` フィールドの直後、`sortOrder` の前
- `valueSource == LLM` の場合のみ使用（初期値は空文字）

**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/domain/model/TemplateField.kt`

#### 3. FieldValueSource.kt
**現在**:
```kotlin
enum class FieldValueSource { FIXED, HTML_META, URL, EMPTY }
```

**変更内容**: `LLM` を追加（REQ-303）
- 新規値: `LLM`（カスタムフィールドのLLM生成用）
- enum の `.entries` に自動的に含まれる

**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt`

### 関連ドメインモデル（参照用）
- `FieldValueType.kt`: `STRING`, `LIST` の2値enum
- `HtmlMetaKey.kt`: HTML メタデータキーのenum（6値）
- `CustomFieldState.kt`: UI用状態クラス（後続タスクで拡張予定）

**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/domain/model/`

### 参照元
- `docs/design/llm-memo-rewrite/interfaces.kt` （行 20-104）- 詳細な型定義とコメント
- `docs/design/llm-memo-rewrite/architecture.md` （テーブル）- 変更対象一覧

---

## 4. 設計文書

### 主要設計ドキュメント

#### interfaces.kt
**内容**: Kotlin インターフェース・型定義（全体的な設計青写真）
- Domain モデル変更（Template, TemplateField, FieldValueSource）- 行 20-104
- LLM設定関連インターフェース
- UI State 変更
- ViewModel シグネチャ
- Hilt Module定義

**信頼性**: 🔵 青信号 100% - 要件定義・ユーザヒアリング・既存実装に基づく確実な定義

**ファイルパス**: `docs/design/llm-memo-rewrite/interfaces.kt`

#### architecture.md
**内容**: システムアーキテクチャ・コンポーネント関係図
- システム概要（REQ-001〜REQ-406の大枠）
- アーキテクチャパターン（MVVM + Repository + Hilt）
- 新規追加コンポーネント（LlmSettings, LlmRepository 等）
- **変更が必要な既存コンポーネント** （テーブル形式）
- ProcessedContent 保持設計（重要な変更点）

**特に参考**: 変更対象のテーブル（行 66-89）

**ファイルパス**: `docs/design/llm-memo-rewrite/architecture.md`

#### 関連設計ドキュメント
- `dataflow.md` - データフロー図（LLM呼び出し時のシーケンス）
- `database-schema.kt` - DB スキーマ（TASK-0057で使用）

**ファイルパス**: `docs/design/llm-memo-rewrite/`

### 参照元
- `docs/spec/llm-memo-rewrite/requirements.md` （関連要件）- REQ-101, REQ-104, REQ-303
- `docs/spec/llm-memo-rewrite/user-stories.md` （ユーザストーリー）
- `docs/spec/llm-memo-rewrite/acceptance-criteria.md` （受け入れ基準）

---

## 5. テスト関連情報

### テストフレームワーク設定

#### ユニットテスト
- **フレームワーク**: JUnit 4（Kotlin対応）
- **モック・スタブ**: MockK（Kotlin ネイティブ）
- **非同期テスト**: kotlinx-coroutines-test
- **Android リソース**: Robolectric

#### テストディレクトリ構成
- **ユニットテスト**: `app/src/test/java/com/den4dr/share2Obsidian/domain/model/`
- **インストルメント化テスト**: `app/src/androidTest/java/com/den4dr/share2Obsidian/`

#### Gradle 設定
- `testImplementation(libs.junit)` - JUnit 4
- `testImplementation(libs.mockk)` - MockK
- `testImplementation(libs.kotlinx.coroutines.test)` - Coroutines テスト
- `testImplementation(libs.robolectric)` - Android リソース

**ファイルパス**: `app/build.gradle.kts` （テスト依存関係）

### 既存テストパターン

#### TemplateTest.kt - 参考実装
**ファイルパス**: `app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt`

**パターン1: data class のデフォルト値テスト**
```kotlin
@Test
fun `Template defaults - id is 0 and isDefault is false`() {
    val template = Template(
        name = "Web記事",
        fields = emptyList(),
    )
    assertEquals(0L, template.id)
    assertEquals(false, template.isDefault)
}
```

**パターン2: data class の copy() メソッドテスト**
```kotlin
@Test
fun `Template copy with updated isDefault`() {
    val original = Template(name = "Test", fields = emptyList())
    val updated = original.copy(isDefault = true)
    assertEquals(true, updated.isDefault)
    assertEquals(original.name, updated.name)
}
```

**パターン3: Enum の値確認テスト**
```kotlin
@Test
fun `FieldValueSource contains all expected values`() {
    val names = FieldValueSource.entries.map { it.name }
    assert("FIXED" in names)
    assert("HTML_META" in names)
    assert("URL" in names)
    assert("EMPTY" in names)
    assertEquals(4, names.size)
}
```

### テストケース（TASK-0056で実装予定）

#### テストケース1: Template の copy() で bodyLlmPrompt のみ変更できること
```
Given: bodyLlmPrompt = "" の Template インスタンス
When:  template.copy(bodyLlmPrompt = "要約してください") を呼び出す
Then:  bodyLlmPrompt が変更され、他のプロパティは変わらない
信頼性: 🔵 data class 仕様・REQ-101
```

#### テストケース2: FieldValueSource.entries に LLM が含まれること
```
Given: FieldValueSource enum が定義されている
When:  FieldValueSource.entries を参照する
Then:  LLM が含まれ、valueOf("LLM") が正常に動作する
信頼性: 🔵 REQ-303
```

#### テストケース3: TemplateField の copy() で llmPrompt のみ変更できること
```
Given: llmPrompt = "" の TemplateField インスタンス
When:  field.copy(llmPrompt = "タイトルを生成してください") を呼び出す
Then:  llmPrompt が変更され、他のプロパティは変わらない
信頼性: 🔵 data class 仕様・REQ-104
```

#### テストケース4: 既存デフォルト値の後方互換性
```
Given: 既存コードと同様に bodyLlmPrompt/llmPrompt を指定しないで Template/TemplateField を生成
When:  インスタンスを生成する
Then:  デフォルト値 "" になり、コンパイルエラーは発生しない
信頼性: 🔵 後方互換性・NFR-001
```

**ファイルパス**: `docs/tasks/llm-memo-rewrite/TASK-0056.md` （108-141行）

### テスト実行コマンド
```bash
# ユニットテスト実行
mise exec -- ./gradlew test

# 特定のテストクラスのみ実行
mise exec -- ./gradlew test --tests "*TemplateTest*"

# インストルメント化テスト実行（デバイス/エミュレータ必須）
mise exec -- ./gradlew connectedAndroidTest
```

---

## 6. 注意事項

### 実装時の重要なポイント

#### 1. Data Class の copy() メソッド
- Kotlin data class は自動的に `copy()` メソッドを生成
- テスト: 新規フィールドの copy() で部分更新可能か確認必須
- `bodyLlmPrompt`, `llmPrompt` はいずれも新規フィールド

#### 2. デフォルト値の指定（後方互換性）
- **必須**: 新規フィールドは全てデフォルト値 `""` を指定
- 既存呼び出し元（`TemplateApplicator`, `TemplateRepositoryImpl`）への影響を最小化
- Kotlin のデフォルト値は引数省略時に自動適用

#### 3. Enum への新規値追加
- `FieldValueSource.LLM` を追加
- `.entries` プロパティで全値列挙可能（テスト確認すること）
- `valueOf("LLM")` でも正常に参照可能

#### 4. テストファイルの作成・配置
- パスには `TASK-0056` のタスク ID を含める
- `app/src/test/java/com/den4dr/share2Obsidian/domain/model/` に配置
- 既存の `TemplateTest.kt` と別ファイル、または同一ファイル内に新規テスト追加

#### 5. 後続タスクとの関係（参考）
- **TASK-0057**: TemplateEntity/TemplateFieldEntity への `bodyLlmPrompt`, `llmPrompt` カラム追加（DB Migration）
- **TASK-0058〜TASK-0073**: LlmSettings/LlmRewrite 関連の実装

### セキュリティ・パフォーマンス要件（参考）

**NFR-101**: APIキーは暗号化ストレージに保存（TASK-0056では直接影響なし。TASK-0058+で実装）

**NFR-102**: ログにAPIキー等の機微情報を出力しない

### 技術的制約

**REQ-406**: LLM入力用に、EditScreen表示後も `ProcessedContent`（共有/取得直後の元テキスト）をテンプレート適用・ユーザー編集と独立して保持する設計
- TASK-0056では直接影響なし（後続タスクで EditScreenViewModel が実装）

### ビルド・実行手順（参考）

Java は `.mise.toml` で管理されているため、Gradle コマンドは必ず `mise exec --` 経由で実行：

```bash
# ビルド
mise exec -- ./gradlew build

# ユニットテスト実行
mise exec -- ./gradlew test

# 全体チェック（Lint + Build + Test）
mise exec -- ./gradlew clean lint build test
```

---

## 7. ファイル一覧（参照用）

### 対象変更ファイル
| ファイル | 変更内容 |
|---------|---------|
| `app/src/main/java/com/den4dr/share2Obsidian/domain/model/Template.kt` | `bodyLlmPrompt: String = ""` 追加 |
| `app/src/main/java/com/den4dr/share2Obsidian/domain/model/TemplateField.kt` | `llmPrompt: String = ""` 追加 |
| `app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt` | `LLM` enum値追加 |

### テストファイル
| ファイル | 説明 |
|---------|------|
| `app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt` | 既存テスト例・参考実装 |
| `app/src/test/` | テスト配置ディレクトリ |

### 参照ドキュメント
| ファイル | 概要 |
|---------|------|
| `docs/design/llm-memo-rewrite/interfaces.kt` | 型定義・インターフェース設計 |
| `docs/design/llm-memo-rewrite/architecture.md` | アーキテクチャ・コンポーネント関係 |
| `docs/spec/llm-memo-rewrite/requirements.md` | 機能要件定義（EARS記法） |
| `docs/tasks/llm-memo-rewrite/TASK-0056.md` | タスク定義・テストケース仕様 |
| `docs/tasks/llm-memo-rewrite/overview.md` | プロジェクト全体概要（19タスク） |
| `app/build.gradle.kts` | Gradle設定・テスト依存関係 |
| `gradle/libs.versions.toml` | バージョンカタログ |

---

**このノートは TDD RED フェーズ開始前のコンテキスト情報集約です。実装時に参照。**

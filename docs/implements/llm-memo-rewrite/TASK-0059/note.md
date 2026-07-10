# TASK-0059 開発コンテキストノート

**タスクID**: TASK-0059  
**要件名**: llm-memo-rewrite  
**作成日**: 2026-07-06  
**TDD開発対象**: LlmRewriteResult・ChatCompletion DTO実装

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
- **HTTP通信**: Ktor Client (CIO)
- **シリアライズ**: kotlinx-serialization

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
1. `/tsumiki:tdd-requirements TASK-0059` - 詳細要件定義
2. `/tsumiki:tdd-testcases` - テストケース作成（3個の確定テストケース）
3. `/tsumiki:tdd-red` - テスト実装（失敗状態）
4. `/tsumiki:tdd-green` - 最小実装（テスト通過）
5. `/tsumiki:tdd-refactor` - リファクタリング（品質向上）
6. `/tsumiki:tdd-verify-complete` - 品質確認・完了

### コーディング規約
- **Sealed class**: `LlmRewriteResult` は sealed class として定義
  - Sealed class の利点: 全てのサブクラスがコンパイル時に既知であり、exhaustive when が使用可能
  - 失敗ケースは sealed class 内でネストした abstract property を持つ
- **Data class**: DTO は `@Serializable` アノテーション付き data class を使用
- **kotlinx.serialization**: `@Serializable` アノテーションで自動JSON変換を実現
- **命名規約**: camelCase（既存コードに準じる）

### テスト命名規約（既存パターンから）
- `${対象クラス}Test.kt` ファイル内に複数のテストメソッドを配置
- テスト名: ``function `説明的なテスト名`() {}`` （バッククォートで日本語使用可）
- Arrange-Act-Assert（AAA）パターン

### 参照元
- `docs/tasks/llm-memo-rewrite/TASK-0059.md` - タスク定義
- 既存テスト例: `app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt`

---

## 3. 関連実装

### 新規実装ファイル

#### 1. LlmRewriteResult.kt
**概要**: LLM呼び出し結果を表す sealed class

**実装位置**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResult.kt`

**型定義**:
```kotlin
sealed class LlmRewriteResult {
    data class Success(val text: String) : LlmRewriteResult()
    
    sealed class Failure(abstract val messageResId: Int) : LlmRewriteResult() {
        data class NetworkError(override val messageResId: Int) : Failure(messageResId)
        data class AuthError(override val messageResId: Int) : Failure(messageResId)
        data class Timeout(override val messageResId: Int) : Failure(messageResId)
        data class EmptyOrInvalidResponse(override val messageResId: Int) : Failure(messageResId)
        data class Unknown(override val messageResId: Int) : Failure(messageResId)
    }
}
```

**対応要件**: EDGE-001〜004、REQ-003

#### 2. ChatCompletionDto.kt
**概要**: OpenAI互換 Chat Completions API用の DTO

**実装位置**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/dto/ChatCompletionDto.kt`

**型定義**:
```kotlin
@Serializable
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<ChatMessageDto>,
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChoiceDto>,
)

@Serializable
data class ChoiceDto(
    val message: ChatMessageDto,
)
```

**対応要件**: REQ-402

### 変更対象ファイル（前提タスク TASK-0055で実装）
- `gradle/libs.versions.toml` - Ktor・kotlinx-serialization・androidx-security-crypto追加
- `app/build.gradle.kts` - 上記の依存関係追加

**参考元**: `docs/tasks/llm-memo-rewrite/TASK-0055.md`

---

## 4. 設計文書

### 主要設計ドキュメント

#### architecture.md
**内容**: システムアーキテクチャ・コンポーネント関係図
- 新規追加コンポーネント一覧（LlmRewriteResult を含む）
- OpenAI互換 Chat Completions設計
- エラーハンドリング設計

**特に参考**: 行 51-60（新規追加コンポーネント表）

**ファイルパス**: `docs/design/llm-memo-rewrite/architecture.md`

#### api-endpoints.md
**内容**: LLM API連携仕様
- リクエスト/レスポンス形式
- エラーレスポンス・例外マッピング
- 認証スキーム（Bearer token）

**特に参考**: リクエスト仕様・レスポンス仕様・エラーレスポンスマッピング

**ファイルパス**: `docs/design/llm-memo-rewrite/api-endpoints.md`

#### dataflow.md
**内容**: データフロー図（LLM呼び出し時のシーケンス）

**ファイルパス**: `docs/design/llm-memo-rewrite/dataflow.md`

#### design-interview.md
**内容**: ユーザーヒアリング記録
- Q1: HTTPクライアント・JSONライブラリ選定 → Ktor Client + kotlinx-serialization (確定)
- エラー伝達パターン設計

**ファイルパス**: `docs/design/llm-memo-rewrite/design-interview.md`

### 参照元（要件・仕様）
- `docs/spec/llm-memo-rewrite/requirements.md` - 機能要件定義（EARS記法）
- `docs/spec/llm-memo-rewrite/acceptance-criteria.md` - 受け入れ基準・テスト項目
- `docs/spec/llm-memo-rewrite/user-stories.md` - ユーザストーリー

---

## 5. テスト関連情報

### テストフレームワーク設定

#### ユニットテスト
- **フレームワーク**: JUnit 4（Kotlin対応）
- **モック・スタブ**: MockK（Kotlin ネイティブ）
- **シリアライズテスト**: kotlinx.serialization の Json.encodeToString()/decodeFromString()

#### テストディレクトリ構成
- **ユニットテスト**: `app/src/test/java/com/den4dr/share2Obsidian/data/llm/`
- **対象クラス**: LlmRewriteResult, ChatCompletionDto

#### Gradle 設定
- `testImplementation(libs.junit)` - JUnit 4
- `testImplementation(libs.kotlinx.serialization.json)` - kotlinx-serialization JSON

**ファイルパス**: `app/build.gradle.kts` （テスト依存関係）

### テストケース（TASK-0059で実装予定）

#### テストケース1: ChatCompletionRequestDto エンコード
```
Given: model = "gpt-4o", messages = [
  ChatMessageDto("system", "プロンプト"), 
  ChatMessageDto("user", "元コンテンツ")
]
When: Json.encodeToString<ChatCompletionRequestDto>() でエンコードする
Then: {"model":"gpt-4o","messages":[{"role":"system","content":"プロンプト"},{"role":"user","content":"元コンテンツ"}]}
信頼性: 🔵 api-endpoints.md リクエスト仕様より
```

#### テストケース2: ChatCompletionResponseDto デコード
```
Given: {"choices":[{"message":{"role":"assistant","content":"書き換え結果"}}]} JSON文字列
When: Json.decodeFromString<ChatCompletionResponseDto>() でデコードする
Then: choices[0].message.content == "書き換え結果"
信頼性: 🔵 api-endpoints.md レスポンス仕様より
```

#### テストケース3: 空choices配列のデコード
```
Given: {"choices":[]} JSON文字列
When: Json.decodeFromString<ChatCompletionResponseDto>() でデコードする
Then: 例外なくデコード可能、choices が空リスト
信頼性: 🟡 EDGE-004 空応答時の扱い（Failureへのマッピングは TASK-0060で扱う）
```

### テスト実行コマンド
```bash
# ユニットテスト実行
mise exec -- ./gradlew test

# 特定のテストクラスのみ実行
mise exec -- ./gradlew test --tests "*LlmRewriteResult*"

# DTOテスト
mise exec -- ./gradlew test --tests "*ChatCompletion*"
```

---

## 6. 注意事項

### 実装時の重要なポイント

#### 1. Sealed Class の構造
- `LlmRewriteResult` は sealed class（コンパイル時に全サブクラス既知）
- `Success` は直接のサブクラス
- `Failure` は sealed class で、その下に複数の失敗種別を定義
- このパターンにより、when式で exhaustive（漏れがない）になる

#### 2. MessageResId の保持
- 全ての `Failure` サブクラスが `messageResId: Int` を保持
- これは Android の string resource ID（`R.string.*`）
- エラー表示用に EditScreenViewModel で Toast に表示される
- 各失敗種別は異なる message ID を持つ必要がある（後続の TASK-0060で決定）

#### 3. kotlinx.serialization の @Serializable
- DTO全てに `@Serializable` アノテーションを付与
- `Json.encodeToString()`/`Json.decodeFromString()` で自動化される
- プロパティ名がそのまま JSON キーになる（snake_case 変換なし）

#### 4. DTO の単純さ
- `ChatCompletionRequestDto` は OpenAI互換の最小構成
  - `model` と `messages` のみ（`temperature` 等は指定しない）
- `ChatCompletionResponseDto` は `choices` のみを持つ
- `ChoiceDto` は `message` のみを持つ（`finish_reason` 等は使用しない）

#### 5. テストでのシリアライズ確認
- JSON エンコード/デコード時のプロパティ名が正確であることを確認
- 実際のAPI呼び出しは TASK-0060（LlmRewriteRepositoryImpl）で実施

#### 6. 後続タスク（TASK-0060）との関係
- LlmRewriteResult は TASK-0060 で LlmRewriteRepositoryImpl により return される
- ChatCompletionDto は TASK-0060 の Ktor Client リクエスト/レスポンス処理で使用される
- 本タスクは「型定義と基本的なシリアライズテスト」のみ

### セキュリティ・パフォーマンス要件（参考）

**NFR-101**: APIキーは暗号化ストレージに保存（本タスクでは直接影響なし）

**NFR-102**: ログにAPIキー等の機微情報を出力しない（例外オブジェクトのログ出力は避ける）

### 技術的制約

**REQ-406**: LLM入力用の元コンテンツ保持（本タスクでは直接影響なし。TASK-0061+で実装）

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

### 新規実装ファイル
| ファイル | 説明 |
|---------|------|
| `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResult.kt` | LLM呼び出し結果型定義 |
| `app/src/main/java/com/den4dr/share2Obsidian/data/llm/dto/ChatCompletionDto.kt` | OpenAI互換 Chat Completions DTO |

### テストファイル
| ファイル | 説明 |
|---------|------|
| `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResultTest.kt` | 追加予定 |
| `app/src/test/java/com/den4dr/share2Obsidian/data/llm/ChatCompletionDtoTest.kt` | 追加予定 |

### 参照ドキュメント
| ファイル | 概要 |
|---------|------|
| `docs/design/llm-memo-rewrite/architecture.md` | アーキテクチャ・コンポーネント関係 |
| `docs/design/llm-memo-rewrite/api-endpoints.md` | LLM API連携仕様 |
| `docs/design/llm-memo-rewrite/dataflow.md` | データフロー図 |
| `docs/design/llm-memo-rewrite/design-interview.md` | ユーザーヒアリング記録 |
| `docs/spec/llm-memo-rewrite/requirements.md` | 機能要件定義（EARS記法） |
| `docs/spec/llm-memo-rewrite/acceptance-criteria.md` | 受け入れ基準・テスト項目 |
| `docs/tasks/llm-memo-rewrite/TASK-0059.md` | タスク定義・詳細仕様 |
| `docs/tasks/llm-memo-rewrite/overview.md` | プロジェクト全体概要（19タスク） |
| `app/build.gradle.kts` | Gradle設定・テスト依存関係 |
| `gradle/libs.versions.toml` | バージョンカタログ |

### 前提タスク完了ファイル
| ファイル | 概要 |
|---------|------|
| `docs/implements/llm-memo-rewrite/TASK-0055/setup-report.md` | 依存関係追加・セットアップ完了報告 |
| `docs/implements/llm-memo-rewrite/TASK-0056/note.md` | ドメインモデル変更コンテキスト |
| `docs/implements/llm-memo-rewrite/TASK-0057/note.md` | DBマイグレーションコンテキスト |
| `docs/implements/llm-memo-rewrite/TASK-0058/note.md` | LlmSettingsRepository実装コンテキスト |

---

**このノートは TDD RED フェーズ開始前のコンテキスト情報集約です。実装時に参照。**

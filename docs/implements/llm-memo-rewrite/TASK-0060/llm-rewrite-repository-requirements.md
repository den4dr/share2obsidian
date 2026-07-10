# TASK-0060 TDD要件定義書: LlmRewriteRepository・LlmRewriteRepositoryImpl

**機能名**: LlmRewriteRepository・LlmRewriteRepositoryImpl実装
**タスクID**: TASK-0060
**要件名**: llm-memo-rewrite
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0060/llm-rewrite-repository-requirements.md`
**作成日**: 2026-07-06
**フェーズ**: Phase 2 - LLM呼び出しロジック・DI設定

---

## 信頼性レベル凡例

各項目について、元の資料（EARS要件定義書・設計文書）との照合状況を以下の信号で示す:

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: ユーザーが設定した外部LLM API（OpenAI互換 Chat Completions形式）を呼び出し、プロンプトと元コンテンツを送信して書き換え/生成テキストを取得する、データ層のリポジトリを実装する。Ktor Client (CIOエンジン) を用いてHTTP通信を行い、成功・各種失敗を `LlmRewriteResult` として返す。
- 🔵 **どのような問題を解決するか**: 本文リライト（Must Have）・タグ提案（Should Have）・カスタムフィールドLLM生成（Could Have）の各機能が共通で利用するLLM呼び出しの抽象化を提供し、上位のViewModelがHTTP詳細やエラーマッピングを意識せずにリライト結果だけを扱えるようにする。
- 🔵 **想定されるユーザー（直接の利用者）**: 上位レイヤーの `EditScreenViewModel`（`rewriteBody()` / `suggestTags()` / `generateCustomFieldValue()`）。エンドユーザーはEditScreen上のボタン押下を通じて間接的に本機能を利用する。
- 🔵 **システム内での位置づけ**: MVVM + Repository構成のデータ層に属する。`EditScreenViewModel` → `LlmRewriteRepository.rewrite()` → Ktor `HttpClient` → 外部LLM API という呼び出し経路上のクライアント側実装。
- ⚠️ **本タスクのスコープ外**: プロンプト内容の組み立て（テンプレートの `bodyLlmPrompt` やタグ提案用固定プロンプトの決定）、`sourceContent` の保持、ローディング/Toast表示は上位タスク（TASK-0062/0063/0065/0068等）の責務であり、本タスクは `rewrite(settings, prompt, content)` 呼び出しに対するHTTP実行とエラーマッピングのみを担う。

- **参照したEARS要件**: REQ-402（OpenAI互換Chat Completions形式）, REQ-004（プロバイダー切替可能）, REQ-002/REQ-302（元コンテンツ入力）
- **参照した設計文書**: `docs/design/llm-memo-rewrite/architecture.md`「LLMリクエスト/レスポンス設計」, `docs/design/llm-memo-rewrite/api-endpoints.md`, `docs/design/llm-memo-rewrite/interfaces.kt`（L154-168 `LlmRewriteRepository`）

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 2.1 インターフェース定義 🔵

**信頼性**: 🔵 *interfaces.kt L158-168・REQ-402より*

```kotlin
// app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt
interface LlmRewriteRepository {
    suspend fun rewrite(settings: LlmSettings, prompt: String, content: String): LlmRewriteResult
}
```

### 2.2 入力パラメータ 🔵

**信頼性**: 🔵 *api-endpoints.md「リクエスト仕様」表・note.md「関連実装」より*

| パラメータ | 型 | 説明 | 制約 |
|-----------|-----|------|------|
| `settings` | `LlmSettings` | 接続情報（`endpointUrl`, `apiKey`, `model`） | 各フィールドは空文字許容（誤設定は実行時エラーとして判明する。REQ-404） |
| `prompt` | `String` | system ロールに載せるプロンプト（本文用 `bodyLlmPrompt` またはタグ提案用固定プロンプト） | 空文字も許容 |
| `content` | `String` | user ロールに載せる元コンテンツ（`sourceContent` / `ProcessedContent`） | 空文字も許容（EDGE-101: 空文字のまま送信する） |

`LlmSettings` の構造（TASK-0058で実装済み）:

```kotlin
data class LlmSettings(
    val endpointUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)
```

### 2.3 出力値 🔵

**信頼性**: 🔵 *interfaces.kt L127-151（TASK-0059実装済み）・api-endpoints.md「エラーレスポンスとマッピング」より*

戻り値は `LlmRewriteResult`（sealed class）:

```kotlin
sealed class LlmRewriteResult {
    data class Success(val text: String) : LlmRewriteResult()
    sealed class Failure(open val messageResId: Int) : LlmRewriteResult() {
        data class NetworkError(override val messageResId: Int) : Failure(messageResId)
        data class AuthError(override val messageResId: Int) : Failure(messageResId)
        data class Timeout(override val messageResId: Int) : Failure(messageResId)
        data class EmptyOrInvalidResponse(override val messageResId: Int) : Failure(messageResId)
        data class Unknown(override val messageResId: Int) : Failure(messageResId)
    }
}
```

| 状況 | 戻り値 | messageResId |
|------|--------|--------------|
| 正常応答（`choices[0].message.content` が非空） | `Success(content)` | - |
| 接続不能（`IOException`, `UnresolvedAddressException` 等） | `Failure.NetworkError` | `R.string.error_llm_network` |
| 401/403 応答 | `Failure.AuthError` | `R.string.error_llm_auth` |
| 30秒タイムアウト（`HttpRequestTimeoutException`） | `Failure.Timeout` | `R.string.error_llm_timeout` |
| `choices` 空 / `content` が null・空文字 | `Failure.EmptyOrInvalidResponse` | `R.string.error_llm_empty_response` |
| その他予期しない例外 | `Failure.Unknown` | `R.string.error_llm_unknown` |

### 2.4 送信リクエスト仕様 🔵

**信頼性**: 🔵 *api-endpoints.md「認証」「リクエスト仕様」より（messages[].role の "system"/"user" 固定は🟡）*

- HTTPメソッド: `POST {settings.endpointUrl}`
- ヘッダ: `Authorization: Bearer {settings.apiKey}` / `Content-Type: application/json`
- ボディ（`ChatCompletionRequestDto`、TASK-0059実装済み）:

```json
{
  "model": "{settings.model}",
  "messages": [
    { "role": "system", "content": "{prompt}" },
    { "role": "user",   "content": "{content}" }
  ]
}
```

- 🟡 `messages[0].role="system"` / `messages[1].role="user"` の順序と固定値はOpenAI Chat Completionsの一般的慣習からの妥当な推測（api-endpoints.md 表 messages[0]/[1] 参照、直接ヒアリング無し）。
- 🟡 `temperature` 等の生成パラメータは指定しない（サービス側デフォルトに委ねる。api-endpoints.md「備考」）。

### 2.5 レスポンス解析 🔵

**信頼性**: 🔵 *api-endpoints.md「レスポンス仕様」より*

`ChatCompletionResponseDto`（TASK-0059実装済み）へデシリアライズし、`choices.firstOrNull()?.message?.content` を取得する。

### 2.6 データフロー 🔵

**信頼性**: 🔵 *dataflow.md・note.md「参考ドキュメント関連図」より*

```
EditScreenViewModel.rewriteBody() / suggestTags()
  ↓
LlmRewriteRepository.rewrite(settings, prompt, content)  ← 本タスク
  ├→ HttpClient.post(endpointUrl){ Authorization, Content-Type, body }
  ├→ レスポンス: ChatCompletionResponseDto
  ├→ choices[0].message.content 抽出
  └→ LlmRewriteResult へマッピング（Success / Failure×5）
```

- **参照したEARS要件**: REQ-402, REQ-401, REQ-003, EDGE-101, EDGE-004
- **参照した設計文書**: `api-endpoints.md`（リクエスト/レスポンス/エラーマッピング）, `interfaces.kt`（`LlmRewriteRepository`, `LlmRewriteResult`, DTO）

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

### 3.1 パフォーマンス要件 🔵

**信頼性**: 🔵 *NFR-001, REQ-202, api-endpoints.md「タイムアウト設定」より*

- LLM呼び出しのタイムアウトは **30秒（30,000ms）**。`HttpTimeout` プラグインの `requestTimeoutMillis = 30_000` で設定する。
- ⚠️ **設計判断ポイント**: タイムアウトを含む `HttpClient` の生成場所。api-endpoints.md/interfaces.kt では `HttpClient(CIO){ install(HttpTimeout){ requestTimeoutMillis = 30_000 } }` を示すが、TASK-0060の実装詳細では `HttpClient` をコンストラクタ注入（`LlmRewriteRepositoryImpl(httpClient)`）とし、`HttpTimeout` 設定はDIモジュール側（TASK-0061 LlmModule）で行う構成が示唆されている。単体テストではMockKで注入HttpClientを差し替えるため、タイムアウト設定の実機能検証は統合テスト（MockEngine）で担保する。 🟡 *クライアント生成場所はTASK-0061との責務分担に依存する妥当な推測*

### 3.2 セキュリティ要件 🔵

**信頼性**: 🔵 *NFR-102, REQ-401より*

- 例外オブジェクト自体（APIキーを含むリクエストヘッダ情報等を含みうる）を **ログ・クラッシュレポートに出力してはならない**（NFR-102）。出力する場合は「認証エラーが発生しました」等の定型メッセージのみ。
- APIキーは `settings.apiKey`（TASK-0058で暗号化ストレージから取得済みの値）をヘッダに載せるのみで、本クラスでの永続化・平文保存は行わない（REQ-401）。

### 3.3 互換性・API制約 🔵

**信頼性**: 🔵 *REQ-402, REQ-004より*

- リクエストは OpenAI互換 Chat Completions形式（`model` + `messages[]`）で送信しなければならない（REQ-402 MUST）。
- エンドポイントURL・モデル名はユーザー設定値であり、本家OpenAI・互換プロキシ・ローカルLLMサーバーのいずれも許容する（REQ-004）。
- 429（レート制限）専用ハンドリングは行わず `Unknown` として扱う 🟡（api-endpoints.md「レート制限」、要件に明記なし）。
- 接続テスト（疎通確認）は行わない。設定誤りは実行時エラーで判明する（REQ-404）。
- 本文文字数上限チェック・APIコスト対策は本リリース対象外（REQ-403）。

### 3.4 アーキテクチャ制約 🔵

**信頼性**: 🔵 *note.md「技術スタック」「開発ルール」・architecture.md より*

- `rewrite()` は非同期処理のため `suspend fun` で定義する（Kotlin Coroutines）。
- HTTP通信は Ktor Client (CIOエンジン) + Content Negotiation + kotlinx-serialization を使用する。
- `HttpClient` は再利用可能なインスタンスとしてコンストラクタ注入し、リソースリークを回避する（TASK-0061でHilt provide）。
- Coroutinesスコープ内での実行を保証（上位は `viewModelScope` を想定）。

### 3.5 依存リソース制約（実装前提の外部依存） 🟡

**信頼性**: 🟡 *strings.xml 現状調査に基づく。エラー文字列は TASK-0064 で追加予定*

- 実装で参照する `R.string.error_llm_network` / `error_llm_auth` / `error_llm_timeout` / `error_llm_empty_response` / `error_llm_unknown` は、現時点の `app/src/main/res/values/strings.xml` に **未定義**（TASK-0064で追加予定）。TASK-0060をコンパイル可能にするには、これらの文字列リソースを先行して最小定義する必要がある。テスト側はリソースID（`Int`）の一致のみを検証するため、値の文言は暫定でよい。
- **参照**: `docs/tasks/llm-memo-rewrite/TASK-0064.md`（strings.xml新規文字列追加）, NFR-201（日本語Toast）

- **参照したEARS要件**: NFR-001, NFR-102, REQ-401, REQ-402, REQ-403, REQ-404, REQ-004, REQ-202, NFR-201
- **参照した設計文書**: `api-endpoints.md`（タイムアウト設定・エラーマッピング・レート制限・事前検証）, `architecture.md`, `interfaces.kt`

---

## 4. 想定される使用例（EARS Edgeケース・データフローベース）

### 4.1 基本的な使用パターン（正常系） 🔵

**信頼性**: 🔵 *REQ-002, REQ-003, api-endpoints.md「レスポンス仕様」より*

- **本文リライト**: `rewrite(settings, bodyLlmPrompt, sourceContent)` → LLMが本文を書き換え → `Success("書き換え結果")` を返し、上位が本文フィールドを上書き（REQ-003）。
- **タグ提案**: `rewrite(settings, TAG_SUGGESTION_PROMPT, sourceContent)` → `Success(タグ候補テキスト)` を返し、上位がタグ入力欄に追加（REQ-302）。

### 4.2 エッジケース（境界値） 🔵

**信頼性**: 🔵 *EDGE-101より*

- **入力が空文字**: `content`（`ProcessedContent`）が空文字でも、ボタンを非活性化せず空文字のままLLM APIへ送信する（応答内容はプロンプト次第）。本リポジトリは `content` の中身を検証・加工しない。

### 4.3 エラーケース 🔵

**信頼性**: 🔵 *EDGE-001〜004・api-endpoints.md「エラーレスポンスとマッピング」より（EDGE-004は🟡）*

| ケース | トリガ | 期待結果 | 対応要件 |
|--------|--------|----------|----------|
| ネットワーク接続不能 | `IOException` / `UnresolvedAddressException` | `Failure.NetworkError`、本文は変更しない | EDGE-001 🔵 |
| 認証エラー | `ClientRequestException`（401/403） | `Failure.AuthError` | EDGE-002 🔵 |
| タイムアウト | `HttpRequestTimeoutException`（30秒） | `Failure.Timeout` | EDGE-003, NFR-001 🔵 |
| 空/不正応答 | `choices` 空 or `content` が null/空文字 | `Failure.EmptyOrInvalidResponse`、本文は上書きしない | EDGE-004 🟡 |
| その他予期しない例外 | 上記以外の `Exception` | `Failure.Unknown` | 一般方針からの推測 🟡 |

- 🟡 **例外分類の注意点**: `ClientRequestException` は `IOException` を継承しない（Ktorの例外階層）が、`ServerResponseException`（5xx）や 401/403以外の4xxは `Failure.Unknown` に落ちる想定。catch順序は `HttpRequestTimeoutException` → `ClientRequestException` → `IOException` → `Exception` とし、より具体的な例外を先に捕捉する（TASK-0060実装詳細のcatch順に準拠）。

- **参照したEARS要件**: EDGE-001, EDGE-002, EDGE-003, EDGE-004, EDGE-101, REQ-003, REQ-302
- **参照した設計文書**: `api-endpoints.md`「エラーレスポンスとマッピング」表, `dataflow.md`

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: 本文リライト（Must Have）／タグ提案（Should Have）の共通LLM呼び出し基盤（`docs/spec/llm-memo-rewrite/user-stories.md`）
- **参照した機能要件**:
  - REQ-002（元コンテンツ＋本文プロンプト送信）, REQ-003（応答で本文上書き）, REQ-004（プロバイダー切替可能）
  - REQ-101（プロンプトをリクエストに含める）, REQ-302（タグ提案の入力ソース統一）
  - REQ-202（30秒でキャンセル）
  - REQ-401（APIキー暗号化）, REQ-402（OpenAI互換形式）, REQ-403（上限チェック不要）, REQ-404（事前検証不要）, REQ-406（元コンテンツ保持）
- **参照した非機能要件**: NFR-001（30秒タイムアウト）, NFR-102（機微情報のログ出力禁止）, NFR-201（日本語Toast）
- **参照したEdgeケース**: EDGE-001（ネットワーク）, EDGE-002（認証）, EDGE-003（タイムアウト）, EDGE-004（空/不正応答）, EDGE-101（空文字入力）
- **参照した受け入れ基準**: `docs/spec/llm-memo-rewrite/acceptance-criteria.md`（本文リライト成功/失敗パターン）
- **参照した設計文書**:
  - **アーキテクチャ**: `docs/design/llm-memo-rewrite/architecture.md`「新規追加コンポーネント」「LLMリクエスト/レスポンス設計」
  - **データフロー**: `docs/design/llm-memo-rewrite/dataflow.md`（rewriteBody/suggestTagsフロー）
  - **型定義**: `docs/design/llm-memo-rewrite/interfaces.kt`（L127-151 `LlmRewriteResult`、L154-168 `LlmRewriteRepository`、L380-417 実装スケッチ）
  - **API仕様**: `docs/design/llm-memo-rewrite/api-endpoints.md`（認証・リクエスト・レスポンス・エラーマッピング・タイムアウト）
  - **データベース**: 本タスクは該当なし（HTTP通信のみ、DB非依存）

---

## 6. 実装・テスト対象ファイル

### 新規作成
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt`（インターフェース）
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImpl.kt`（実装）
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImplTest.kt`（単体テスト、MockK）
- `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryIntegrationTest.kt`（統合テスト、Ktor MockEngine）

### 既存（前提・TASK-0059/0058で実装済み）
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResult.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/dto/ChatCompletionDto.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettings.kt`

### 要事前対応（外部依存）
- `app/src/main/res/values/strings.xml` — `error_llm_*` 5リソースの先行最小定義（本来はTASK-0064）

---

## 7. テスト要件サマリー（tdd-testcasesで詳細化）

### 単体テスト（JUnit4 + MockK, 5ケース）
1. 🔵 正常応答時に `Success(text)` を返す（REQ-003）
2. 🔵 401/403応答時に `AuthError` を返す（EDGE-002）
3. 🔵 `HttpRequestTimeoutException` 発生時に `Timeout` を返す（EDGE-003, NFR-001）
4. 🔵 `IOException` 発生時に `NetworkError` を返す（EDGE-001）
5. 🟡 `choices` 空応答時に `EmptyOrInvalidResponse` を返す（EDGE-004）

### 統合テスト（Ktor MockEngine, 2ケース）
1. 🔵 リクエストの `Authorization` / `Content-Type` ヘッダ・ボディJSON（`model`, `messages[0].role="system"`, `messages[1].role="user"`）が仕様通り（api-endpoints.md）
2. 🔵 30秒超遅延応答で `HttpTimeout` により実際にタイムアウトし `Timeout` を返す（NFR-001, REQ-202）

---

## 8. 品質判定

```
✅ 高品質:
- 要件の曖昧さ: なし（入出力・エラーマッピングが api-endpoints.md 表で確定）
- 入出力定義: 完全（interfaces.kt・DTO・LlmRewriteResult が実装済みで型確定）
- 制約条件: 明確（30秒タイムアウト・ログ出力禁止・OpenAI互換形式）
- 実装可能性: 確実（前提のTASK-0058/0059完了済み、依存ライブラリ導入済み）
- 信頼性レベル: 🔵 が多数（実装詳細3/5・単体テスト4/5・統合テスト2/2が青）
```

### 信頼性レベル分布

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| 機能概要 | 4 | 0 | 0 | 4 |
| 入出力仕様 | 5 | 2 | 0 | 7 |
| 制約条件 | 5 | 2 | 0 | 7 |
| 使用例 | 5 | 2 | 0 | 7 |
| **合計** | **19** | **6** | **0** | **25** |

- 🔵 青信号: 19項目 (76%)
- 🟡 黄信号: 6項目 (24%)
- 🔴 赤信号: 0項目 (0%)

**品質評価**: ✅ 高品質

### 🟡 黄信号項目（実装時の確認ポイント）
1. `messages[]` の role="system"/"user" 順序・固定値（OpenAI慣習からの推測、api-endpoints.md 表準拠）
2. `temperature` 等生成パラメータ非指定（最小構成の設計判断）
3. `HttpClient` 生成場所とタイムアウト設定の責務（TASK-0061 LlmModule との分担）
4. `EmptyOrInvalidResponse` の扱い（EDGE-004、空応答時の扱いは未確認）
5. 429・その他例外を `Unknown` に集約（要件に明記なし）
6. `error_llm_*` 文字列リソースの先行定義（本来TASK-0064）

---

## 次のステップ

**次のお勧めステップ**: `/tsumiki:tdd-testcases llm-memo-rewrite TASK-0060` でテストケースの洗い出しを行う。

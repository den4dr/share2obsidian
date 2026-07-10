# TASK-0059 TDD要件定義書: LlmRewriteResult・ChatCompletion DTO実装

**機能名**: llm-rewrite-dto
**タスクID**: TASK-0059
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0059/llm-rewrite-dto-requirements.md`

---

## 信頼性レベル凡例

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測をした
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測をした

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: LLM呼び出しの「結果」を表す sealed class `LlmRewriteResult` と、OpenAI互換 Chat Completions 形式のリクエスト/レスポンスを表す kotlinx.serialization DTO（`ChatCompletionRequestDto`, `ChatMessageDto`, `ChatCompletionResponseDto`, `ChoiceDto`）を定義する型実装タスクである。
  - *interfaces.kt「LLM 呼び出し結果（新規）」・api-endpoints.md「リクエスト仕様」「レスポンス仕様」より*
- 🔵 **どのような問題を解決するか**: 後続の LLM 呼び出しロジック（TASK-0060 `LlmRewriteRepositoryImpl`）が、外部LLM APIとの通信結果を型安全に扱い、成功/各種失敗を exhaustive な `when` で分岐できるようにするための土台を提供する。DTOはKtor Client + kotlinx.serialization によるJSONエンコード/デコードの契約を規定する。
  - *TASK-0059.md「タスク概要」・note.md「6.注意事項」より*
- 🔵 **想定されるユーザー**: 直接のエンドユーザーはなく、本タスクの利用者は後続タスク（TASK-0060以降）を実装する開発者およびアプリ内部の Repository 層コードである。
  - *overview.md「Phase 2: LLM呼び出しロジック・DI設定」より*
- 🔵 **システム内での位置づけ**: data層のLLMクライアントサブシステム（`data/llm` パッケージ）に属する純粋な型定義。MVVM + Repository アーキテクチャにおける Repository の入出力データ型として機能する。
  - *architecture.md「新規追加コンポーネント」・note.md「1.技術スタック」より*
- **参照したEARS要件**: REQ-402（LLM API連携）, REQ-003（応答テキストの使用）, EDGE-001〜004
- **参照した設計文書**: `interfaces.kt`「LLM 呼び出し結果（新規）」（127-151行）, `api-endpoints.md`「リクエスト仕様」「レスポンス仕様」

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

本タスクは「型定義」であり、実行時の入出力ではなく、型のシリアライズ契約が入出力仕様となる。

### 2.1 LlmRewriteResult（sealed class）🔵

*信頼性: 🔵 interfaces.kt 127-151行より*

```kotlin
sealed class LlmRewriteResult {
    data class Success(val text: String) : LlmRewriteResult()
    sealed class Failure : LlmRewriteResult() {
        abstract val messageResId: Int
        data class NetworkError(override val messageResId: Int) : Failure()
        data class AuthError(override val messageResId: Int) : Failure()
        data class Timeout(override val messageResId: Int) : Failure()
        data class EmptyOrInvalidResponse(override val messageResId: Int) : Failure()
        data class Unknown(override val messageResId: Int) : Failure()
    }
}
```

| メンバ | 型 | 意味 | 信頼性 |
|--------|-----|------|--------|
| `Success.text` | String | 書き換え/生成されたテキスト（REQ-003） | 🔵 |
| `Failure.messageResId` | Int (abstract) | 表示用 string resource ID（`R.string.*`） | 🔵 |
| `NetworkError` | Failure | ネットワーク未接続・接続エラー（EDGE-001） | 🔵 |
| `AuthError` | Failure | APIキー不正・認証エラー（HTTP 401/403）（EDGE-002） | 🔵 |
| `Timeout` | Failure | 30秒タイムアウト（EDGE-003, NFR-001） | 🔵 |
| `EmptyOrInvalidResponse` | Failure | 空応答・パース不能なレスポンス（EDGE-004） | 🟡 |
| `Unknown` | Failure | 上記以外の予期しないエラー | 🟡 一般的なエラーハンドリング方針からの推測 |

- 🔵 **note.md構造との差異**: note.md 82行には `Failure(abstract val messageResId: Int)` という記述があるが、Kotlin文法上 sealed class のプライマリコンストラクタに `abstract val` は宣言できない。正式な型定義は interfaces.kt（133-134行）に準拠し、`Failure` は本体で `abstract val messageResId: Int` を宣言し、各サブクラスが `override val` を持つ形とする。🔵 *interfaces.kt 133-150行が正*

### 2.2 ChatCompletionRequestDto / ChatMessageDto（出力: リクエストJSON）🔵

*信頼性: 🔵 api-endpoints.md「リクエスト仕様」45-71行より*

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
```

- **入力**: `model`（`LlmSettings.model`）, `messages`（`system`/`user` ロールの2要素想定）
- **出力（エンコード結果）**: `{"model":"gpt-4o","messages":[{"role":"system","content":"..."},{"role":"user","content":"..."}]}`
- 🔵 プロパティ名がそのまま JSON キーになる（snake_case 変換なし）。`temperature` 等の生成パラメータは含めない（最小構成）。*api-endpoints.md 71行「備考」より*

### 2.3 ChatCompletionResponseDto / ChoiceDto（入力: レスポンスJSON）🔵

*信頼性: 🔵 api-endpoints.md「レスポンス仕様」75-96行より*

```kotlin
@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChoiceDto>,
)

@Serializable
data class ChoiceDto(
    val message: ChatMessageDto,
)
```

- **入力（デコード対象JSON）**: `{"choices":[{"message":{"role":"assistant","content":"..."}}]}`
- **出力（デコード結果）**: `choices[0].message.content` から応答テキストを取得可能
- 🔵 `finish_reason` 等の未使用フィールドは定義しない。API応答に余分なキーが含まれる可能性があるため、デコード時は未知キーを無視する設定（`ignoreUnknownKeys = true`）の Json を使用する前提とする 🟡 *OpenAI互換API一般の慣習からの妥当な推測。厳密設定はTASK-0060のKtor ContentNegotiation設定で確定*

### 2.4 入出力の関係性・データフロー 🔵

*信頼性: 🔵 dataflow.md・api-endpoints.md・interfaces.kt 388-421行より*

1. TASK-0060 の Repository が `ChatCompletionRequestDto` を構築 → kotlinx.serialization でJSONエンコード → Ktor で POST
2. LLM API のレスポンスJSON → `ChatCompletionResponseDto` にデコード → `choices[0].message.content` を抽出
3. 抽出結果が非空なら `LlmRewriteResult.Success(text)`、そうでなければ `Failure.EmptyOrInvalidResponse`、例外時は例外種別に応じた `Failure` サブクラスへマッピング
- **参照したEARS要件**: REQ-402, REQ-003, REQ-004
- **参照した設計文書**: `interfaces.kt` 388-421行（`LlmRewriteRepositoryImpl.rewrite`）, `api-endpoints.md`「エラーレスポンスとマッピング」

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **言語・SDK制約**: Kotlin 2.2.10 / minSdk 33 / targetSdk 36 / Java 11 互換。*note.md「1.技術スタック」より*
- 🔵 **シリアライズ制約**: DTOはすべて `@Serializable` アノテーション付き data class とし、kotlinx.serialization の `Json.encodeToString()` / `Json.decodeFromString()` で変換可能であること。*note.md 255-258行より*
- 🔵 **JSONキー制約**: プロパティ名を JSON キーとしてそのまま使用（`model`, `messages`, `role`, `content`, `choices`, `message`）。OpenAI互換 Chat Completions 形式に一致すること。*api-endpoints.md「リクエスト/レスポンス仕様」より*
- 🔵 **アーキテクチャ制約**: `LlmRewriteResult` は sealed class とし、`when` 式が exhaustive になること（分岐漏れをコンパイル時に検出可能にする）。*note.md 50-52, 243-247行より*
- 🔵 **セキュリティ制約（NFR-102）**: 本タスクの型自体は機微情報を持たないが、`Failure` は例外オブジェクトを保持せず `messageResId: Int` のみを保持する設計とし、APIキーを含む例外情報がUI/ログへ漏れない構造とする。*api-endpoints.md 112行, note.md 279行より*
- 🔵 **タイムアウト制約（NFR-001）**: `Timeout` サブクラスは30秒タイムアウト（EDGE-003）に対応する。実際のタイムアウト設定はTASK-0060/0061で行う。*api-endpoints.md「タイムアウト設定」より*
- 🟡 **スコープ制約**: 本タスクは型定義と基本的なシリアライズテストのみ。実際のAPI呼び出し・例外→Failureマッピング・messageResId の具体値割当はTASK-0060で扱う。*note.md 266-273行より*
- **参照したEARS要件**: NFR-001, NFR-102, NFR-201, REQ-402, REQ-403
- **参照した設計文書**: `api-endpoints.md`「エラーレスポンスとマッピング」「タイムアウト設定」, `note.md`「6.注意事項」

### ファイル配置制約 🔵

| 種別 | パス |
|------|------|
| 実装（結果型） | `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResult.kt` |
| 実装（DTO） | `app/src/main/java/com/den4dr/share2Obsidian/data/llm/dto/ChatCompletionDto.kt` |
| テスト（結果型） | `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResultTest.kt` |
| テスト（DTO） | `app/src/test/java/com/den4dr/share2Obsidian/data/llm/ChatCompletionDtoTest.kt` |

*note.md「7.ファイル一覧」より 🔵*

---

## 4. 想定される使用例（Edgeケース・データフローベース）

### 4.1 基本的な使用パターン 🔵

*信頼性: 🔵 api-endpoints.md「リクエスト/レスポンス仕様」より*

- **リクエスト生成**: `ChatCompletionRequestDto("gpt-4o", listOf(ChatMessageDto("system", prompt), ChatMessageDto("user", content)))` をエンコードし、API仕様どおりのJSONを得る。
- **レスポンス解釈**: 成功レスポンスJSONをデコードし `choices[0].message.content` を取り出す。
- **成功結果表現**: `LlmRewriteResult.Success("書き換え結果")` を返し、呼び出し側が `body` に反映する（REQ-003）。

### 4.2 エッジケース 🟡

*信頼性: 🟡 EDGE-004・api-endpoints.md 95, 108行より（空応答時のマッピングは直接確認していない）*

- **空 choices 配列**: `{"choices":[]}` は例外を投げずにデコードでき、`choices` は空リストになる（Failureへのマッピングは TASK-0060 で実施）。
- **content が null/空文字**: `message.content` が空の場合、TASK-0060 で `EmptyOrInvalidResponse` にマッピングされる（本タスクでは型がデコード可能であることの確認まで）。

### 4.3 エラーケース（本タスクでは型の存在のみ確認）🔵

*信頼性: 🔵 api-endpoints.md「エラーレスポンスとマッピング」99-109行, EDGE-001〜004より*

| 状況 | 対応する Failure サブクラス | 対応要件 |
|------|--------------------------|---------|
| 接続不能（IOException 等） | `NetworkError` | EDGE-001 |
| 401/403 | `AuthError` | EDGE-002 |
| 30秒タイムアウト | `Timeout` | EDGE-003, NFR-001 |
| choices空 / content null・空 | `EmptyOrInvalidResponse` | EDGE-004 🟡 |
| その他予期しない例外 | `Unknown` | 🟡 |

- **参照したEARS要件**: EDGE-001, EDGE-002, EDGE-003, EDGE-004
- **参照した設計文書**: `api-endpoints.md`「エラーレスポンスとマッピング」, `dataflow.md`（LLM呼び出しシーケンス）

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: 「メモ本文をLLMで書き換える」「タグを提案してもらう」（`user-stories.md`）— 本タスクはその基盤型
- **参照した機能要件**:
  - REQ-402（OpenAI互換 Chat Completions API連携）
  - REQ-003（応答テキストを書き換え結果として使用）
  - REQ-004（endpointUrl/apiKey/model 設定）
- **参照した非機能要件**:
  - NFR-001（30秒タイムアウト → `Timeout`）
  - NFR-102（機微情報をログに出さない → `Failure` は例外を保持しない）
  - NFR-201（エラーメッセージは strings.xml に定義 → `messageResId: Int`）
- **参照したEdgeケース**: EDGE-001（ネットワーク）, EDGE-002（認証）, EDGE-003（タイムアウト）, EDGE-004（空応答）
- **参照した受け入れ基準**: シリアライズ結果が api-endpoints.md 記載JSON形式と一致すること（`acceptance-criteria.md`）
- **参照した設計文書**:
  - **アーキテクチャ**: `architecture.md`「新規追加コンポーネント」（`data/llm`）
  - **データフロー**: `dataflow.md`（LLM呼び出しシーケンス）
  - **型定義**: `interfaces.kt` 119-151行（`LlmRewriteResult`）, `LlmRewriteRepositoryImpl`（388-421行, DTO利用箇所）
  - **API仕様**: `api-endpoints.md`「リクエスト仕様」「レスポンス仕様」「エラーレスポンスとマッピング」

---

## 6. テストケース（要件確認用サマリー）

| # | 内容 | 期待 | 信頼性 |
|---|------|------|--------|
| 1 | `ChatCompletionRequestDto` エンコード | api-endpoints.md記載JSON `{"model":"gpt-4o","messages":[{"role":"system",...},{"role":"user",...}]}` と一致 | 🔵 |
| 2 | `ChatCompletionResponseDto` デコード | `{"choices":[{"message":{"role":"assistant","content":"書き換え結果"}}]}` → `choices[0].message.content == "書き換え結果"` | 🔵 |
| 3 | 空 choices 配列のデコード | `{"choices":[]}` → 例外なく `choices` が空リスト | 🟡 |

（詳細は次フェーズ `/tsumiki:tdd-testcases` で洗い出す）

---

## 7. 品質判定

| 観点 | 評価 |
|------|------|
| 要件の曖昧さ | なし（型定義が interfaces.kt に明記済み） |
| 入出力定義の完全性 | 完全（型・JSON形式・シリアライズ契約を明記） |
| 制約条件の明確性 | 明確（言語/シリアライズ/配置/セキュリティ制約を列挙） |
| 実装可能性 | 確実（前提TASK-0055で依存導入済み、型定義のみ） |

### 信頼性レベル分布

- 🔵 青信号: 大半（機能概要・主要型定義・リクエスト/レスポンスDTO・基本テスト2件）
- 🟡 黄信号: 一部（`EmptyOrInvalidResponse`/`Unknown` の位置づけ、空応答時マッピング、ignoreUnknownKeys 前提、スコープ境界）
- 🔴 赤信号: なし

**総合品質判定**: ✅ 高品質（🔵優勢、🔴ゼロ）

---

## 次のステップ

`/tsumiki:tdd-testcases llm-memo-rewrite TASK-0059` でテストケースの洗い出しを行う。

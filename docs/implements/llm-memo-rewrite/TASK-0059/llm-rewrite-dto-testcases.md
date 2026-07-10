# TASK-0059 TDDテストケース定義書: LlmRewriteResult・ChatCompletion DTO実装

**機能名**: llm-rewrite-dto
**タスクID**: TASK-0059
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0059/llm-rewrite-dto-testcases.md`

---

## 信頼性レベル凡例

- 🔵 **青信号**: 元の資料（要件定義・設計文書・既存実装）を参考にしてほぼ推測していない
- 🟡 **黄信号**: 元の資料から妥当な推測をした
- 🔴 **赤信号**: 元の資料にない推測をした

---

## 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: 本プロジェクトの実装言語であり、sealed class・data class・kotlinx.serialization を用いた型安全な結果表現とJSONシリアライズが自然に書ける。*note.md「1.技術スタック」より*
  - **テストに適した機能**: バッククォート識別子による日本語テスト名、data class の構造的等価比較（`equals`）、`when` の exhaustive チェックが型検証に有利。
- **テストフレームワーク**: JUnit 4 + kotlinx.serialization Json
  - **フレームワーク選択の理由**: 既存ユニットテスト（`TemplateTest.kt`）が JUnit 4 で書かれており統一する。シリアライズ検証は `Json.encodeToString()` / `Json.decodeFromString()` を直接使用。MockK は本タスクでは不要（純粋な型定義のためモック対象なし）。*note.md「5.テスト関連情報」より*
  - **テスト実行環境**: JVM ユニットテスト（`app/src/test/`）。Robolectric/Android実機は不要。実行コマンド: `mise exec -- ./gradlew test --tests "*ChatCompletion*"` / `--tests "*LlmRewriteResult*"`。
- 🔵 信頼性レベル: note.md・既存 `TemplateTest.kt` に基づく（推測なし）

### テスト対象ファイル

| 種別 | パス |
|------|------|
| 実装（結果型） | `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResult.kt` |
| 実装（DTO） | `app/src/main/java/com/den4dr/share2Obsidian/data/llm/dto/ChatCompletionDto.kt` |
| テスト（結果型） | `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteResultTest.kt` |
| テスト（DTO） | `app/src/test/java/com/den4dr/share2Obsidian/data/llm/ChatCompletionDtoTest.kt` |

---

## テストケース一覧（サマリー）

| # | 分類 | テスト名 | 対象 | 信頼性 |
|---|------|---------|------|--------|
| TC-N-01 | 正常系 | ChatCompletionRequestDto エンコード結果がAPI仕様と一致 | DTO | 🔵 |
| TC-N-02 | 正常系 | ChatCompletionResponseDto をデコードし content を取得 | DTO | 🔵 |
| TC-N-03 | 正常系 | ChatMessageDto 単体のエンコード/デコード往復一致 | DTO | 🔵 |
| TC-N-04 | 正常系 | LlmRewriteResult.Success が text を保持 | 結果型 | 🔵 |
| TC-N-05 | 正常系 | 各 Failure サブクラスが messageResId を保持 | 結果型 | 🔵 |
| TC-N-06 | 正常系 | Failure は LlmRewriteResult のサブタイプであり when で分岐可能 | 結果型 | 🔵 |
| TC-E-01 | 異常系 | 必須フィールド欠落JSONのデコードで例外 | DTO | 🟡 |
| TC-E-02 | 異常系 | 不正な形式のJSONのデコードで例外 | DTO | 🟡 |
| TC-E-03 | 異常系 | 未知キーを含むレスポンスを ignoreUnknownKeys でデコード可能 | DTO | 🟡 |
| TC-B-01 | 境界値 | choices が空配列のレスポンスを例外なくデコード | DTO | 🟡 |
| TC-B-02 | 境界値 | messages が空リストの Request をエンコード | DTO | 🟡 |
| TC-B-03 | 境界値 | content が空文字のメッセージをエンコード/デコード | DTO | 🟡 |
| TC-B-04 | 境界値 | Success.text が空文字でも保持できる | 結果型 | 🟡 |
| TC-B-05 | 境界値 | data class の等価性（同一内容の Success/Failure は equals） | 結果型 | 🟡 |

---

## 1. 正常系テストケース（基本的な動作）

### TC-N-01: ChatCompletionRequestDto のエンコード結果がAPI仕様と一致する

- **テスト名**: ChatCompletionRequestDto を JSON エンコードすると api-endpoints.md 記載のリクエスト形式になる
  - **何をテストするか**: `ChatCompletionRequestDto` を kotlinx.serialization でエンコードした結果が、OpenAI互換 Chat Completions のリクエストボディ形式（`model` + `messages` 配列、各要素が `role`/`content`）と一致すること。
  - **期待される動作**: プロパティ名がそのまま JSON キーとなり（snake_case 変換なし）、`temperature` 等の余分なキーが含まれない最小構成のJSONが生成される。
- **入力値**: `ChatCompletionRequestDto(model = "gpt-4o", messages = listOf(ChatMessageDto("system", "プロンプト"), ChatMessageDto("user", "元コンテンツ")))`
  - **入力データの意味**: api-endpoints.md「リクエスト仕様」のJSON例に対応する典型的な2メッセージ（system/user）構成。実運用の本文リライトリクエストを代表する。
- **期待される結果**: エンコード結果を再パースした `JsonObject` が以下と構造的に一致する。
  - `{"model":"gpt-4o","messages":[{"role":"system","content":"プロンプト"},{"role":"user","content":"元コンテンツ"}]}`
  - **期待結果の理由**: api-endpoints.md 53-61行のリクエストボディ例と REQ-402（OpenAI互換連携）に基づき、この形式がAPI契約として正しいため。
- **テストの目的**: DTOのシリアライズ契約がAPI仕様に一致することの確認。
  - **確認ポイント**: キー名（`model`/`messages`/`role`/`content`）が正確であること、配列順序（system→user）が保持されること、余分なキーが出力されないこと。文字列比較はキー順序に依存するため、`Json.parseToJsonElement()` で構造比較する。
- 🔵 信頼性レベル: api-endpoints.md「リクエスト仕様」・TASK-0059.md テストケース1・requirements.md 2.2 に基づく（推測なし）

### TC-N-02: サンプルレスポンスJSONをデコードして content を取得できる

- **テスト名**: ChatCompletionResponseDto をデコードすると choices[0].message.content を取得できる
  - **何をテストするか**: API成功レスポンス形式のJSON文字列を `ChatCompletionResponseDto` へデコードし、`choices[0].message.content` から応答テキストを取り出せること。
  - **期待される動作**: ネストした `choices` → `message` → `content` が正しくマッピングされる。
- **入力値**: `{"choices":[{"message":{"role":"assistant","content":"書き換え結果"}}]}`
  - **入力データの意味**: api-endpoints.md「レスポンス仕様」81-92行の成功レスポンス例。実運用でLLMから返る典型的な単一 choice のレスポンスを代表する。
- **期待される結果**: `result.choices` のサイズが 1、`result.choices[0].message.content == "書き換え結果"`、`result.choices[0].message.role == "assistant"`。
  - **期待結果の理由**: REQ-003 により `choices[0].message.content` を書き換え結果として使用するため、この経路でのデコードが正しく行える必要がある。
- **テストの目的**: レスポンスDTOのデコード契約がAPI仕様に一致することの確認。
  - **確認ポイント**: ネスト構造（List → data class → data class）が正しくデコードされること、`role` フィールドも欠落なく取得できること。
- 🔵 信頼性レベル: api-endpoints.md「レスポンス仕様」・TASK-0059.md テストケース2・requirements.md 2.3 に基づく（推測なし）

### TC-N-03: ChatMessageDto 単体のエンコード/デコード往復で内容が一致する

- **テスト名**: ChatMessageDto をエンコードして再デコードすると元の値と一致する（ラウンドトリップ）
  - **何をテストするか**: `ChatMessageDto` を `encodeToString` → `decodeFromString` した結果が元インスタンスと `equals` で一致すること。
  - **期待される動作**: `role`/`content` の双方向シリアライズがロスなく行われる。
- **入力値**: `ChatMessageDto(role = "user", content = "テスト本文")`
  - **入力データの意味**: リクエスト/レスポンス双方で再利用される最小構成のメッセージDTO。往復一致は DTO 全体の健全性を代表する。
- **期待される結果**: `Json.decodeFromString<ChatMessageDto>(Json.encodeToString(original)) == original` が true。
  - **期待結果の理由**: data class の構造的等価性と `@Serializable` の対称性から、キー名が一致していれば往復で同値になるはずである。
- **テストの目的**: エンコードとデコードの対称性（キー名の一貫性）確認。
  - **確認ポイント**: エンコード側とデコード側でキー名の不一致がないこと。
- 🔵 信頼性レベル: kotlinx.serialization 標準仕様・requirements.md 2.2 に基づく（推測なし）

### TC-N-04: LlmRewriteResult.Success が保持するテキストを取得できる

- **テスト名**: LlmRewriteResult.Success は生成テキストを text プロパティで保持する
  - **何をテストするか**: `LlmRewriteResult.Success(text)` を生成し、`text` プロパティで元の文字列を取得できること。
  - **期待される動作**: コンストラクタで渡した文字列がそのまま保持される。
- **入力値**: `LlmRewriteResult.Success(text = "書き換え結果")`
  - **入力データの意味**: REQ-003 の「応答テキストを書き換え結果として使用」を代表する成功結果。
- **期待される結果**: `result.text == "書き換え結果"`、かつ `result is LlmRewriteResult`（サブタイプであること）。
  - **期待結果の理由**: interfaces.kt 130行の `Success(val text: String) : LlmRewriteResult()` 定義に基づく。
- **テストの目的**: 成功結果型がテキストを正しく保持することの確認。
  - **確認ポイント**: `text` の格納・取得、`LlmRewriteResult` サブタイプであること。
- 🔵 信頼性レベル: interfaces.kt 130行・requirements.md 2.1・REQ-003 に基づく（推測なし）

### TC-N-05: 各 Failure サブクラスが messageResId を保持する

- **テスト名**: NetworkError/AuthError/Timeout/EmptyOrInvalidResponse/Unknown が messageResId を保持する
  - **何をテストするか**: 5種の `Failure` サブクラスそれぞれについて、コンストラクタで渡した `messageResId: Int` を `messageResId` プロパティ（`Failure.messageResId` として抽象宣言、各サブクラスで override）で取得できること。
  - **期待される動作**: 各サブクラスが独立して異なる messageResId を保持できる。
- **入力値**:
  - `LlmRewriteResult.Failure.NetworkError(messageResId = 1001)`
  - `LlmRewriteResult.Failure.AuthError(messageResId = 1002)`
  - `LlmRewriteResult.Failure.Timeout(messageResId = 1003)`
  - `LlmRewriteResult.Failure.EmptyOrInvalidResponse(messageResId = 1004)`
  - `LlmRewriteResult.Failure.Unknown(messageResId = 1005)`
  - **入力データの意味**: 各失敗種別（EDGE-001〜004 + Unknown）を代表するダミーの string resource ID。実際の `R.string.*` 値の割当は TASK-0060 の範囲だが、Int を保持できることは本タスクで確認する。
- **期待される結果**: 各インスタンスの `messageResId` がそれぞれ 1001〜1005 と一致し、いずれも `LlmRewriteResult.Failure`（および `LlmRewriteResult`）のサブタイプである。
  - **期待結果の理由**: interfaces.kt 133-149行で `Failure` が `abstract val messageResId: Int` を宣言し、各サブクラスが `override val messageResId` を持つ設計のため。
- **テストの目的**: 全失敗種別が定義され、messageResId を保持することの網羅確認。
  - **確認ポイント**: 5サブクラスすべての存在、`messageResId` の格納・取得、`Failure` 経由（アップキャスト後）でも `messageResId` にアクセス可能なこと。
- 🔵 信頼性レベル: interfaces.kt 133-149行・requirements.md 2.1・EDGE-001〜004 に基づく（EmptyOrInvalidResponse/Unknown の位置づけのみ🟡だが型の存在確認としては🔵）

### TC-N-06: LlmRewriteResult を when で exhaustive に分岐できる

- **テスト名**: LlmRewriteResult を Success / 各 Failure で網羅的に when 分岐できる
  - **何をテストするか**: `LlmRewriteResult` インスタンスに対し `when` 式で `Success` と各 `Failure` サブクラスを分岐し、`else` なしでコンパイル・実行できること（sealed class の exhaustive 性）。
  - **期待される動作**: 与えた具体型に対応する分岐が選択され、期待した値が返る。
- **入力値**: `Success("ok")` および `Failure.Timeout(2003)` の2ケースを `when` に通す。
  - **入力データの意味**: 成功系と失敗系の代表を通し、sealed 階層の分岐が機能することを代表的に確認する。
- **期待される結果**: `Success("ok")` は分岐で `"ok"` を、`Failure.Timeout(2003)` は分岐で `2003` を取り出せる（`else` 節を書かなくてもコンパイルが通る＝exhaustive）。
  - **期待結果の理由**: note.md 243-247行・requirements.md 制約条件で「`when` が exhaustive になること」が制約として明記されているため。
- **テストの目的**: sealed class 構造が exhaustive な分岐を可能にするアーキテクチャ制約の充足確認。
  - **確認ポイント**: `else` 節なしで全分岐が網羅されていること（コンパイル自体がこの制約のテストを兼ねる）。
- 🔵 信頼性レベル: note.md 243-247行・requirements.md 3章 アーキテクチャ制約に基づく（推測なし）

---

## 2. 異常系テストケース（エラーハンドリング）

### TC-E-01: 必須フィールドが欠落したレスポンスJSONのデコードで例外が発生する

- **テスト名**: message フィールドを欠く choice のデコードで SerializationException が発生する
  - **エラーケースの概要**: 必須プロパティ（`ChoiceDto.message`）を含まないJSONをデコードしようとしたケース。
  - **エラー処理の重要性**: 契約に反する不完全なレスポンスを黙って通すと、後続で NullPointer 等の予期しない失敗を招くため、デコード段階で例外として検出される必要がある。
- **入力値**: `{"choices":[{}]}`（`choice` オブジェクトに `message` がない）
  - **不正な理由**: `ChoiceDto.message` はデフォルト値を持たない必須プロパティであり、欠落は kotlinx.serialization の契約違反となる。
  - **実際の発生シナリオ**: エンドポイントが OpenAI 非互換の応答（プロキシ/ローカルLLMの独自形式）を返した場合に起こり得る。
- **期待される結果**: `Json.decodeFromString<ChatCompletionResponseDto>(...)` が `kotlinx.serialization.SerializationException`（`MissingFieldException` を含む）をスローする。
  - **エラーメッセージの内容**: 欠落フィールド名を含む例外。ユーザー向けではなく、TASK-0060 側で catch して `EmptyOrInvalidResponse`/`Unknown` にマッピングする前提。
  - **システムの安全性**: 例外により不正データが型に入り込まず、安全に失敗経路へ倒せる。
- **テストの目的**: 必須フィールド欠落時にデコードが確実に失敗することの確認。
  - **品質保証の観点**: 不完全レスポンスを検出できることで、後続のFailureマッピング（TASK-0060）の前提条件を保証する。
- 🟡 信頼性レベル: kotlinx.serialization の必須フィールド仕様からの妥当な推測。EDGE-004 に関連するが、例外種別の詳細は資料に明記なし。

### TC-E-02: 構文的に不正なJSONのデコードで例外が発生する

- **テスト名**: JSONとして壊れた文字列のデコードで SerializationException が発生する
  - **エラーケースの概要**: そもそもJSONとしてパースできない文字列を渡したケース。
  - **エラー処理の重要性**: パース不能な応答（HTMLエラーページ等）を受け取っても、例外として捕捉可能な形で失敗する必要がある。
- **入力値**: `"not a json {"`（閉じ括弧のない不正な文字列）
  - **不正な理由**: JSON文法に違反しており、`decodeFromString` がパースできない。
  - **実際の発生シナリオ**: エンドポイントURL誤設定でHTMLやプレーンテキストが返る、プロキシのエラーページが返る等。
- **期待される結果**: `Json.decodeFromString<ChatCompletionResponseDto>(...)` が `SerializationException`（パースエラー）をスローする。
  - **エラーメッセージの内容**: パース位置を含む例外。TASK-0060 で catch し `EmptyOrInvalidResponse`/`Unknown` へマッピングする前提。
  - **システムの安全性**: アプリがクラッシュせず、呼び出し側で捕捉して安全に失敗表示へ倒せる。
- **テストの目的**: パース不能入力に対する堅牢性の確認。
  - **品質保証の観点**: EDGE-004（パース不能なレスポンス）の前段として、デコードが例外で失敗することを保証する。
- 🟡 信頼性レベル: EDGE-004「パース不能なレスポンス」からの妥当な推測。例外種別の詳細は資料に明記なし。

### TC-E-03: 未知キーを含むレスポンスを ignoreUnknownKeys 設定でデコードできる

- **テスト名**: finish_reason 等の未定義キーを含むレスポンスを ignoreUnknownKeys=true でデコードできる
  - **エラーケースの概要**: DTOに定義していない余分なキー（`id`, `finish_reason`, `usage` 等）を含む実APIレスポンスを扱うケース。
  - **エラー処理の重要性**: OpenAI互換APIは DTO で定義していない多数のフィールドを返すため、未知キーで失敗すると正常応答すらデコードできなくなる。
- **入力値**: `{"id":"x","choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"結果"}}],"usage":{"total_tokens":5}}` を、`Json { ignoreUnknownKeys = true }` でデコード。
  - **不正な理由**: 厳密には DTO 未定義のキーを含むが、`ignoreUnknownKeys = true` により許容される想定。
  - **実際の発生シナリオ**: 本番の OpenAI/互換API応答は常に未定義キーを含むため、実質すべての実応答がこのケースに該当する。
- **期待される結果**: 例外を投げず、`choices[0].message.content == "結果"` が取得できる。
  - **エラーメッセージの内容**: なし（正常デコード）。
  - **システムの安全性**: 実API応答を安定してデコードでき、余分キーによる誤失敗を防ぐ。
- **テストの目的**: 未知キー無視設定でのデコード互換性の確認。
  - **品質保証の観点**: 実運用のレスポンス互換性を担保する。ただし ignoreUnknownKeys の最終確定は TASK-0060 の Ktor ContentNegotiation 設定で行うため、本タスクでは DTO がその設定下でデコード可能であることの確認に留める。
- 🟡 信頼性レベル: requirements.md 2.3（ignoreUnknownKeys 前提）・OpenAI互換API一般慣習からの妥当な推測。デフォルト Json では未知キーで例外となるため本ケースは明示的に ignoreUnknownKeys を指定する。

---

## 3. 境界値テストケース（最小値、最大値、null等）

### TC-B-01: choices が空配列のレスポンスを例外なくデコードできる

- **テスト名**: choices が空配列のレスポンスを例外なくデコードでき、空リストになる
  - **境界値の意味**: `choices` が「0件」という下限の境界。LLMが有効な候補を返さなかった状態を表す。
  - **境界値での動作保証**: 空配列でも型としてはデコード可能であり（Failureマッピングは行わない）、後続タスクが空を検出できる前提を保証する。
- **入力値**: `{"choices":[]}`
  - **境界値選択の根拠**: api-endpoints.md 95行・EDGE-004 で「`choices` が空」の扱いが言及されており、空配列がデコード可能であることが後続の `EmptyOrInvalidResponse` マッピングの前提になる。
  - **実際の使用場面**: LLMサービスが候補ゼロを返す異常応答時。
- **期待される結果**: 例外を投げずにデコードでき、`result.choices` が空リスト（`isEmpty() == true`, `size == 0`）。
  - **境界での正確性**: 空配列が「空リスト」として正しくマッピングされる。
  - **一貫した動作**: 1件以上（TC-N-02）と 0件（本ケース）でデコード成否が一貫（いずれも成功）し、件数のみが異なる。
- **テストの目的**: 空応答の型レベルでの取り扱い確認（Failureマッピングは TASK-0060 の範囲）。
  - **堅牢性の確認**: 極端な（0件）応答でもデコードが破綻しないこと。
- 🟡 信頼性レベル: TASK-0059.md テストケース3・api-endpoints.md 95行・EDGE-004 に基づく（空応答時のFailureマッピングは直接確認していないため🟡）

### TC-B-02: messages が空リストの Request をエンコードできる

- **テスト名**: messages が空リストの ChatCompletionRequestDto をエンコードすると空配列になる
  - **境界値の意味**: `messages` が「0件」という下限の境界。
  - **境界値での動作保証**: 空リストでもエンコードが破綻せず、`"messages":[]` として出力される。
- **入力値**: `ChatCompletionRequestDto(model = "gpt-4o", messages = emptyList())`
  - **境界値選択の根拠**: `List<ChatMessageDto>` の最小要素数（0）を検証し、コレクションプロパティのシリアライズ堅牢性を確認する。
  - **実際の使用場面**: 通常は system/user の2件だが、呼び出し側実装の不具合等で空になった場合の型としての振る舞いを規定する。
- **期待される結果**: エンコード結果に `"model":"gpt-4o"` と `"messages":[]` が含まれる（`messages` が空JSON配列）。
  - **境界での正確性**: 空リストが `[]` として出力される。
  - **一貫した動作**: 非空（TC-N-01）と空（本ケース）でキー構造が一貫し、要素数のみ異なる。
- **テストの目的**: コレクションプロパティの境界（空）でのエンコード確認。
  - **堅牢性の確認**: 空コレクションでシリアライズが失敗しないこと。
- 🟡 信頼性レベル: kotlinx.serialization のコレクション標準仕様からの妥当な推測（要件に空リスト明記はない）

### TC-B-03: content が空文字のメッセージをエンコード/デコードできる

- **テスト名**: content が空文字の ChatMessageDto をエンコード/デコードしても値が保持される
  - **境界値の意味**: 文字列プロパティ `content` の下限（空文字 `""`）。null ではなく空文字である点が境界。
  - **境界値での動作保証**: 空文字が欠落扱いにならず、`""` として往復で保持される。
- **入力値**: `ChatMessageDto(role = "user", content = "")`
  - **境界値選択の根拠**: `content` が空になり得る（EDGE-004 の「content 空文字」）ため、空文字がシリアライズで正しく扱われることを確認する。
  - **実際の使用場面**: LLMが空文字を返すケース、あるいは入力コンテンツが空のケース。
- **期待される結果**: エンコード結果に `"content":""` が含まれ、デコードで復元した `content == ""`（欠落や null 化されない）。
  - **境界での正確性**: 空文字が省略されず明示的に出力・復元される。
  - **一貫した動作**: 非空文字列と空文字列でシリアライズ経路が一貫する。
- **テストの目的**: 空文字境界でのシリアライズ正確性の確認。
  - **堅牢性の確認**: 空文字が null や欠落と混同されないこと。
- 🟡 信頼性レベル: EDGE-004（content 空文字）・kotlinx.serialization 標準仕様からの妥当な推測

### TC-B-04: LlmRewriteResult.Success が空文字テキストを保持できる

- **テスト名**: LlmRewriteResult.Success("") が空文字を保持する
  - **境界値の意味**: `Success.text` の下限（空文字）。成功だが本文が空という境界状態。
  - **境界値での動作保証**: 空文字でも `Success` として生成・保持でき、`text == ""` を返す。
- **入力値**: `LlmRewriteResult.Success(text = "")`
  - **境界値選択の根拠**: 空応答時に Success ではなく EmptyOrInvalidResponse を返すのは TASK-0060 の判断であり、本タスクの型としては空文字 Success を保持できる必要がある（型に空文字制約はない）。
  - **実際の使用場面**: 型定義段階での境界確認。実際の空応答分岐は後続タスクで実装。
- **期待される結果**: `result.text == ""`、`result is LlmRewriteResult.Success`。
  - **境界での正確性**: 空文字が保持される。
  - **一貫した動作**: 非空文字（TC-N-04）と空文字で保持挙動が一貫する。
- **テストの目的**: 成功結果型の文字列境界の確認。
  - **堅牢性の確認**: 空文字でも型が破綻しないこと。
- 🟡 信頼性レベル: interfaces.kt 130行の型定義からの妥当な推測（空文字の明示要件はない）

### TC-B-05: 同一内容の Success / Failure は data class として等価になる

- **テスト名**: 同一 text の Success 同士、同一 messageResId の同種 Failure 同士が equals で等しい
  - **境界値の意味**: data class の構造的等価性の境界確認（同値・別インスタンス）。テストのアサーション基盤にもなる性質。
  - **境界値での動作保証**: 値が同じなら別インスタンスでも `==` が true、種別または値が異なれば false。
- **入力値**:
  - `Success("a") == Success("a")` → true
  - `Failure.NetworkError(1) == Failure.NetworkError(1)` → true
  - `Failure.NetworkError(1) == Failure.AuthError(1)` → false（種別が異なる）
  - `Success("a") == Success("b")` → false（値が異なる）
  - **境界値選択の根拠**: data class の `equals` に依存する他テストの前提を明示的に検証する。種別違い（同一 messageResId）と値違いの両側から境界を突く。
  - **実際の使用場面**: 後続タスク・テストで結果比較を行う際の基盤性質。
- **期待される結果**: 上記4比較がそれぞれ true / true / false / false。
  - **境界での正確性**: 同値判定が種別と値の双方を考慮すること。
  - **一貫した動作**: sealed 階層内で `equals` が型（種別）も区別すること。
- **テストの目的**: data class 生成による構造的等価性の確認。
  - **堅牢性の確認**: 型の等価判定が期待どおりに働くこと。
- 🟡 信頼性レベル: Kotlin data class 標準仕様からの妥当な推測（要件に等価性の明記はないが note.md で data class 採用が確定）

---

## 4. テストケース実装時の日本語コメント指針

各テストメソッドには既存 `TemplateTest.kt` に倣い、以下の日本語コメントを付与する。

### テストメソッド冒頭

```kotlin
// 【テスト目的】: [このテストで何を確認するかを日本語で明記]
// 【テスト内容】: [具体的にどのような処理をテストするかを説明]
// 【期待される動作】: [正常に動作した場合の結果を説明]
// 🔵🟡🔴 信頼性レベル: [参照資料と推測有無]
```

### Given（準備フェーズ）

```kotlin
// 【テストデータ準備】: [なぜこのデータを用意するかの理由]
// 【初期条件設定】: [テスト実行前の状態を説明]
```

### When（実行フェーズ）

```kotlin
// 【実際の処理実行】: [どの機能/メソッドを呼び出すかを説明]
// 【処理内容】: [実行される処理の内容を日本語で説明]
```

### Then（検証フェーズ）

```kotlin
// 【結果検証】: [何を検証するかを具体的に説明]
// 【期待値確認】: [期待される結果とその理由を説明]
assertEquals("書き換え結果", result.choices[0].message.content) // 【確認内容】: content が正しくデコードされること 🔵
```

### 実装スケルトン例（TC-N-01）

```kotlin
package com.den4dr.share2Obsidian.data.llm

import com.den4dr.share2Obsidian.data.llm.dto.ChatCompletionRequestDto
import com.den4dr.share2Obsidian.data.llm.dto.ChatMessageDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCompletionDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ChatCompletionRequestDto をエンコードすると API 仕様の JSON になる`() {
        // 【テスト目的】: リクエストDTOのエンコード結果が OpenAI 互換仕様と一致することを確認する
        // 【テスト内容】: model + messages(system/user) をエンコードし、構造比較する
        // 【期待される動作】: {"model":"gpt-4o","messages":[{"role":"system",...},{"role":"user",...}]}
        // 🔵 信頼性レベル: api-endpoints.md「リクエスト仕様」に基づく

        // 【テストデータ準備】: system/user の2メッセージ構成の Request を用意する
        val request = ChatCompletionRequestDto(
            model = "gpt-4o",
            messages = listOf(
                ChatMessageDto("system", "プロンプト"),
                ChatMessageDto("user", "元コンテンツ"),
            ),
        )

        // 【実際の処理実行】: kotlinx.serialization でエンコードする
        val encoded = json.encodeToString(request)

        // 【結果検証】: キー順序に依存しないよう JsonElement へ再パースして構造比較する
        val expected = Json.parseToJsonElement(
            """{"model":"gpt-4o","messages":[{"role":"system","content":"プロンプト"},{"role":"user","content":"元コンテンツ"}]}""",
        )
        assertEquals(expected, Json.parseToJsonElement(encoded)) // 【確認内容】: エンコード結果が仕様JSONと構造一致すること 🔵
    }
}
```

---

## 5. 要件定義との対応関係

- **参照した機能概要**: `llm-rewrite-dto-requirements.md` 1章（LlmRewriteResult・ChatCompletion DTO の型定義タスク）
- **参照した入力・出力仕様**: 同 2章（2.1 LlmRewriteResult / 2.2 Request DTO / 2.3 Response DTO / 2.4 データフロー）
- **参照した制約条件**: 同 3章（言語/シリアライズ/JSONキー/アーキテクチャ exhaustive/セキュリティ/配置 制約）
- **参照した使用例**: 同 4章（4.1 基本使用 / 4.2 エッジケース 空choices・空content / 4.3 エラーマッピング）
- **参照した設計文書**:
  - `interfaces.kt` 119-151行（`LlmRewriteResult` 型定義）
  - `api-endpoints.md`「リクエスト仕様」53-71行 / 「レスポンス仕様」81-95行 / 「エラーレスポンスとマッピング」103-112行
- **参照したEARS要件**: REQ-402, REQ-003, REQ-004 / NFR-001, NFR-102, NFR-201 / EDGE-001〜004
- **参照した既存実装**: `app/src/test/java/com/den4dr/share2Obsidian/domain/model/TemplateTest.kt`（テスト命名・日本語コメント様式）

---

## 6. 品質判定

### テストケース分類の網羅性

| 分類 | 件数 | 対象 |
|------|------|------|
| 正常系 | 6件（TC-N-01〜06） | エンコード一致・デコード取得・往復・Success保持・Failure×5保持・exhaustive分岐 |
| 異常系 | 3件（TC-E-01〜03） | 必須欠落例外・不正JSON例外・未知キー許容 |
| 境界値 | 5件（TC-B-01〜05） | 空choices・空messages・空content・空Success・data class等価 |
| 合計 | 14件 | DTO 10件 / 結果型 4件（TC-N-05 は5サブクラスを内包） |

### 信頼性レベル分布

- 🔵 青信号: 6件（TC-N-01〜06。型定義・API仕様に直接基づく）
- 🟡 黄信号: 8件（TC-E-01〜03, TC-B-01〜05。例外種別詳細・空/未知キー扱い・等価性など妥当な推測）
- 🔴 赤信号: 0件

### 品質判定結果

| 観点 | 評価 |
|------|------|
| テストケース分類 | ✅ 正常系・異常系・境界値を網羅 |
| 期待値定義 | ✅ 各ケースで具体的な期待値を明記 |
| 技術選択 | ✅ Kotlin + JUnit 4 + kotlinx.serialization Json で確定 |
| 実装可能性 | ✅ 前提TASK-0055で依存導入済み、純粋な型定義で実現可能 |
| 信頼性レベル | ✅ 🔵優勢＋妥当な🟡、🔴ゼロ |

**総合品質判定**: ✅ 高品質

---

## 次のステップ

`/tsumiki:tdd-red llm-memo-rewrite TASK-0059` でRedフェーズ（失敗テスト作成）を開始する。

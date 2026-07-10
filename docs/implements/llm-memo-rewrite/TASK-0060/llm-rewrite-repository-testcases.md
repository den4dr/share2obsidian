# TASK-0060 TDDテストケース定義書: LlmRewriteRepository・LlmRewriteRepositoryImpl

**機能名**: LlmRewriteRepository・LlmRewriteRepositoryImpl実装
**タスクID**: TASK-0060
**要件名**: llm-memo-rewrite
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0060/llm-rewrite-repository-testcases.md`
**作成日**: 2026-07-06
**フェーズ**: Phase 2 - LLM呼び出しロジック・DI設定

---

## 信頼性レベル凡例

各テストケースについて、元の資料（要件定義書・設計文書・既存実装）との照合状況を以下の信号で示す:

- 🔵 **青信号**: 元の資料を参考にしてほぼ推測していない
- 🟡 **黄信号**: 元の資料からの妥当な推測
- 🔴 **赤信号**: 元の資料にない推測

---

## テスト対象と技術方針の要点

- **対象クラス**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImpl.kt`（新規）
- **対象メソッド**: `suspend fun rewrite(settings: LlmSettings, prompt: String, content: String): LlmRewriteResult`
- **依存注入**: `HttpClient`（Ktor CIO）をコンストラクタ注入。テストではエンジンを差し替える。
- **エラーマッピング**（api-endpoints.md「エラーレスポンスとマッピング」表より 🔵）:

  | トリガ | 戻り値 | messageResId |
  |-------|--------|--------------|
  | `choices[0].message.content` 非空 | `Success(content)` | - |
  | `IOException` / `UnresolvedAddressException` | `Failure.NetworkError` | `R.string.error_llm_network` |
  | `ClientRequestException`（401/403） | `Failure.AuthError` | `R.string.error_llm_auth` |
  | `HttpRequestTimeoutException`（30秒） | `Failure.Timeout` | `R.string.error_llm_timeout` |
  | `choices` 空 / `content` が null・空文字 | `Failure.EmptyOrInvalidResponse` | `R.string.error_llm_empty_response` |
  | 上記以外（5xx・401/403以外の4xx・その他） | `Failure.Unknown` | `R.string.error_llm_unknown` |

### 🟡 テスト実装上の重要な設計判断（モック方式）

- 🟡 **信頼性**: 要件定義（note.md / TASK-0060.md）では「MockK で `HttpClient` をモックする」と記載されているが、Ktor の `HttpClient.post { }` および `HttpResponse.body()` は **拡張関数・inline を多用しており MockK での安定したモックが困難** である。実務上は **単体テストでも Ktor `MockEngine` で `HttpClient` を構築し、期待するレスポンス/例外を返す方式** が安定かつ推奨される（統合テストと同じ仕組みを異なる粒度で使用）。
  - 本テストケース定義は「どの入力条件で何を返すか」という **論理仕様** を規定するものであり、モック実装手段（MockK vs MockEngine）は Red フェーズで最終決定する。以降の各ケースでは実現手段の候補を併記する。
  - `HttpRequestTimeoutException` / `ClientRequestException` は MockEngine のレスポンス（遅延・ステータスコード）から自然に発生させられるため、MockEngine 方式が例外系でも有利。

---

## 1. 正常系テストケース（基本的な動作）

### TC-01: 正常応答時に Success(text) を返す

- **テスト名**: 正常応答で書き換え結果テキストを Success として返す
  - **何をテストするか**: LLM API が `choices[0].message.content` に有効テキストを返したとき、その文字列を `LlmRewriteResult.Success` に包んで返すこと
  - **期待される動作**: HTTP POST が 200 応答し、レスポンス JSON を `ChatCompletionResponseDto` にデシリアライズ、content を抽出して Success を返す
- **入力値**:
  - `settings = LlmSettings(endpointUrl="https://api.example.com/v1/chat/completions", apiKey="sk-test", model="gpt-4o-mini")`
  - `prompt = "以下の本文を読みやすく整形してください"`
  - `content = "元の本文テキスト"`
  - レスポンス: `{"choices":[{"message":{"role":"assistant","content":"書き換え結果"}}]}`
  - **入力データの意味**: 本文リライトの代表的成功パターン（REQ-002/REQ-003）を再現する
- **期待される結果**: `LlmRewriteResult.Success("書き換え結果")`
  - **期待結果の理由**: api-endpoints.md「レスポンス仕様」で `choices[0].message.content` を書き換え結果として使用すると定義（REQ-003）
- **テストの目的**: 正常系のレスポンス解析と Success マッピングの確認
  - **確認ポイント**: 返却型が `Success` であること、`text` が応答 content と完全一致すること
- 🔵 *TASK-0060.md 単体テストケース1・requirements.md 7章・api-endpoints.md「レスポンス仕様」より*

### TC-02: タグ提案用途でも同一経路で Success を返す

- **テスト名**: 異なるプロンプト（タグ提案）でも Success テキストを返す
  - **何をテストするか**: `prompt` がタグ提案用固定プロンプトの場合でも、同じ `rewrite()` 経路で応答テキストを Success として返すこと
  - **期待される動作**: プロンプト内容に依存せず、応答 content をそのまま Success に包む（本リポジトリは content を検証・加工しない）
- **入力値**:
  - `prompt = "この文章に適したタグをカンマ区切りで提案してください"`
  - `content = "元コンテンツ"`
  - レスポンス content = `"kotlin, android, tdd"`
  - **入力データの意味**: 本機能が本文リライトとタグ提案で共通利用される（requirements.md 4.1）ことを代表する
- **期待される結果**: `LlmRewriteResult.Success("kotlin, android, tdd")`
  - **期待結果の理由**: リポジトリはプロンプト用途を区別せず応答テキストを返す責務のみを持つ（requirements.md スコープ）
- **テストの目的**: プロンプト用途非依存で成功経路が動作することの確認
  - **確認ポイント**: プロンプト差異で分岐が発生しないこと
- 🟡 *requirements.md 4.1「タグ提案」より妥当な推測（同一メソッド利用は明記、テキスト内容は例示）*

---

## 2. 異常系テストケース（エラーハンドリング）

### TC-03: 401 応答時に AuthError を返す

- **テスト名**: 401 Unauthorized で AuthError を返す
  - **エラーケースの概要**: APIキー誤り等で LLM API が 401 を返し、Ktor が `ClientRequestException` を送出する状況
  - **エラー処理の重要性**: ユーザーへ「APIキーが正しくない」旨を通知し、本文を破壊しないため（EDGE-002）
- **入力値**: レスポンスステータス `401 Unauthorized`（→ `ClientRequestException`）
  - **不正な理由**: 認証失敗を示す HTTP ステータス
  - **実際の発生シナリオ**: SettingsScreen で誤ったAPIキーを保存したままリライトを実行
- **期待される結果**: `LlmRewriteResult.Failure.AuthError`（`messageResId == R.string.error_llm_auth`）
  - **エラーメッセージの内容**: 「APIキーが正しくありません」（NFR-201, TASK-0064で定義）
  - **システムの安全性**: 例外オブジェクト・APIキーをログ出力せず、本文は変更しない
- **テストの目的**: 401 → AuthError マッピングの確認
  - **品質保証の観点**: 認証失敗の明示的ハンドリングでユーザーが原因を特定できる
- 🔵 *TASK-0060.md 単体テストケース2・EDGE-002・api-endpoints.md 表より*

### TC-04: 403 応答時に AuthError を返す

- **テスト名**: 403 Forbidden で AuthError を返す
  - **エラーケースの概要**: 権限不足等で 403 が返る状況。401 と同じ AuthError へ集約されるべき
  - **エラー処理の重要性**: 401/403 双方を認証系エラーとして統一的に扱う（api-endpoints.md 表）
- **入力値**: レスポンスステータス `403 Forbidden`（→ `ClientRequestException`）
  - **不正な理由**: アクセス拒否を示す HTTP ステータス
  - **実際の発生シナリオ**: 失効・権限外のAPIキー、プロキシ側のアクセス制限
- **期待される結果**: `LlmRewriteResult.Failure.AuthError`（`messageResId == R.string.error_llm_auth`）
  - **エラーメッセージの内容**: 401 と同一の認証エラーメッセージ
  - **システムの安全性**: 本文変更なし、機微情報のログ出力なし
- **テストの目的**: 403 も 401 と同一分岐（AuthError）へ入ることの確認
  - **品質保証の観点**: `status == 401 || status == 403` の分岐網羅
- 🔵 *api-endpoints.md 表「401/403 → AuthError」・TASK-0060.md 実装詳細4より*

### TC-05: HttpRequestTimeoutException 発生時に Timeout を返す

- **テスト名**: タイムアウト例外で Timeout を返す
  - **エラーケースの概要**: 30秒以内に応答が返らず `HttpRequestTimeoutException` が送出される状況
  - **エラー処理の重要性**: 応答無限待機を防ぎ、ユーザーへタイムアウトを通知（EDGE-003, NFR-001）
- **入力値**: `HttpRequestTimeoutException` を送出する条件（単体では例外送出、統合では30秒超遅延）
  - **不正な理由**: 応答遅延がタイムアウト閾値（30秒）を超過
  - **実際の発生シナリオ**: LLMサーバー高負荷・ネットワーク遅延・エンドポイント無応答
- **期待される結果**: `LlmRewriteResult.Failure.Timeout`（`messageResId == R.string.error_llm_timeout`）
  - **エラーメッセージの内容**: 「LLMからの応答がタイムアウトしました」
  - **システムの安全性**: タイムアウトで処理中断、本文変更なし
- **テストの目的**: `HttpRequestTimeoutException` → Timeout マッピングの確認
  - **品質保証の観点**: catch 順序で `HttpRequestTimeoutException` が `IOException`/`Exception` より先に捕捉される
- 🔵 *TASK-0060.md 単体テストケース3・EDGE-003・NFR-001より*

### TC-06: IOException 発生時に NetworkError を返す

- **テスト名**: 接続不能（IOException）で NetworkError を返す
  - **エラーケースの概要**: 名前解決失敗・接続拒否など `IOException`/`UnresolvedAddressException` が送出される状況
  - **エラー処理の重要性**: オフライン・不到達を明示し、本文を保持する（EDGE-001）
- **入力値**: `IOException`（または `UnresolvedAddressException`）を送出する条件
  - **不正な理由**: ソケット接続・名前解決が確立できない
  - **実際の発生シナリオ**: 機内モード・圏外・誤ったホスト名のエンドポイント
- **期待される結果**: `LlmRewriteResult.Failure.NetworkError`（`messageResId == R.string.error_llm_network`）
  - **エラーメッセージの内容**: 「ネットワークに接続できませんでした」
  - **システムの安全性**: 接続失敗時も本文は変更されない
- **テストの目的**: `IOException` → NetworkError マッピングの確認
  - **品質保証の観点**: `ClientRequestException` は `IOException` を継承しないため、この分岐に誤って落ちないこと（catch 順序の妥当性）
- 🔵 *TASK-0060.md 単体テストケース4・EDGE-001・api-endpoints.md 表より*

### TC-07: 500 応答（ServerResponseException）時に Unknown を返す

- **テスト名**: サーバーエラー(5xx)で Unknown を返す
  - **エラーケースの概要**: LLM サーバー内部エラーで 500 が返り `ServerResponseException` が送出される状況
  - **エラー処理の重要性**: 認証・ネットワーク・タイムアウト以外は Unknown に集約し、予期しない状態でも安全に失敗する
- **入力値**: レスポンスステータス `500 Internal Server Error`（→ `ServerResponseException`）
  - **不正な理由**: サーバー側の一時的/恒久的障害
  - **実際の発生シナリオ**: LLMプロバイダー側の障害・互換プロキシの不具合
- **期待される結果**: `LlmRewriteResult.Failure.Unknown`（`messageResId == R.string.error_llm_unknown`）
  - **エラーメッセージの内容**: 「予期しないエラーが発生しました」
  - **システムの安全性**: 未分類の失敗でもクラッシュせず Failure を返す
- **テストの目的**: 5xx が AuthError 等に誤分類されず Unknown へ落ちることの確認
  - **品質保証の観点**: 例外分類の網羅性（catch(Exception) フォールバック）
- 🟡 *api-endpoints.md「その他の予期しない例外 → Unknown」・requirements.md 4.3 表より妥当な推測*

### TC-08: 429（レート制限）応答時に Unknown を返す

- **テスト名**: レート制限(429)で Unknown を返す
  - **エラーケースの概要**: 429 Too Many Requests を専用ハンドリングせず Unknown として扱う設計の確認
  - **エラー処理の重要性**: 本リリースでは 429 専用処理を行わない設計判断（api-endpoints.md）を明示的に固定する
- **入力値**: レスポンスステータス `429 Too Many Requests`（→ `ClientRequestException`、status != 401/403）
  - **不正な理由**: リクエスト過多によるレート制限
  - **実際の発生シナリオ**: 短時間の連続リライト実行、共有APIキーの上限到達
- **期待される結果**: `LlmRewriteResult.Failure.Unknown`（`messageResId == R.string.error_llm_unknown`）
  - **エラーメッセージの内容**: 「予期しないエラーが発生しました」
  - **システムの安全性**: 429 を AuthError と誤認しないこと（401/403 のみ AuthError）
- **テストの目的**: 401/403 以外の 4xx が AuthError ではなく Unknown へ入る分岐の確認
  - **品質保証の観点**: `ClientRequestException` 内の `status == 401 || 403` else 分岐の網羅
- 🟡 *api-endpoints.md「レート制限」節（429 → Unknown）・requirements.md 3.3より妥当な推測*

### TC-09: 例外オブジェクト・APIキーをログ出力しない（NFR-102）

- **テスト名**: 失敗時に APIキーを含む機微情報をログへ出力しない
  - **エラーケースの概要**: 任意の失敗経路で、APIキーや例外オブジェクト全体がログ・出力に漏れないこと
  - **エラー処理の重要性**: APIキー漏洩防止はセキュリティ必須要件（NFR-102, REQ-401）
- **入力値**: 認証失敗（TC-03相当）を発生させ、`settings.apiKey = "sk-secret-should-not-leak"` を設定
  - **不正な理由**: 例外にはリクエストヘッダ（Authorization: Bearer sk-...）が含まれうる
  - **実際の発生シナリオ**: エラー時に例外を安易に `Log.e(tag, e)` するとAPIキーがlogcatへ露出する
- **期待される結果**: 返却は `Failure.AuthError`。かつ実装がログ出力する場合でも apiKey 文字列を含まない
  - **エラーメッセージの内容**: 定型メッセージのみ（例外オブジェクトそのものは出力しない）
  - **システムの安全性**: logcat・クラッシュレポートに機微情報が残らない
- **テストの目的**: NFR-102（機微情報のログ出力禁止）の遵守確認
  - **品質保証の観点**: 検証手段は Red フェーズで確定（例: `Log` を Robolectric ShadowLog で捕捉し apiKey 非包含を assert、あるいは実装レビューで担保）。🟡 実装がそもそもログ出力しない場合は「apiKey を含む出力が存在しない」ことの確認に置き換える
- 🟡 *NFR-102・requirements.md 3.2・TASK-0060.md 完了条件より（検証手段は要件に未規定のため推測）*

---

## 3. 境界値テストケース（最小値・最大値・null等）

### TC-10: choices が空配列のとき EmptyOrInvalidResponse を返す

- **テスト名**: choices 空応答で EmptyOrInvalidResponse を返す
  - **境界値の意味**: 「応答は 200 成功だが有効な生成結果が 0 件」という成功と失敗の境界
  - **境界値での動作保証**: HTTP 成功でも中身が空なら失敗として扱う一貫性
- **入力値**: レスポンス `{"choices":[]}`（200 成功）
  - **境界値選択の根拠**: `choices.firstOrNull()` が null になる最小ケース
  - **実際の使用場面**: プロバイダーが空の choices を返す異常応答
- **期待される結果**: `LlmRewriteResult.Failure.EmptyOrInvalidResponse`（`messageResId == R.string.error_llm_empty_response`）
  - **境界での正確性**: `choices.firstOrNull()?.message?.content` が null → 空判定で失敗へ
  - **一貫した動作**: content 有無で Success/Failure が切り替わる
- **テストの目的**: 空 choices の EmptyOrInvalidResponse マッピング確認
  - **堅牢性の確認**: 200 成功でも空応答でクラッシュせず適切に失敗を返す
- 🟡 *TASK-0060.md 単体テストケース5・EDGE-004（空応答時の扱いは直接確認していない）より*

### TC-11: content が null のとき EmptyOrInvalidResponse を返す

- **テスト名**: message.content が null で EmptyOrInvalidResponse を返す
  - **境界値の意味**: choices は存在するが content が null という、DTO の nullable 境界（`MessageDto.content: String?`）
  - **境界値での動作保証**: null content を Success として扱わない
- **入力値**: レスポンス `{"choices":[{"message":{"role":"assistant","content":null}}]}`
  - **境界値選択の根拠**: `MessageDto.content` は nullable のため null が正当に到達しうる
  - **実際の使用場面**: ツール呼び出し応答等で content が null になるプロバイダー挙動
- **期待される結果**: `LlmRewriteResult.Failure.EmptyOrInvalidResponse`
  - **境界での正確性**: `text.isNullOrEmpty()` が true → 失敗
  - **一貫した動作**: 空文字と null が同一の失敗分岐へ
- **テストの目的**: null content の失敗マッピング確認
  - **堅牢性の確認**: NPE を発生させず安全に Failure を返す
- 🟡 *TASK-0060.md 実装詳細5（`isNullOrEmpty()`）・ChatCompletionDto の `content: String?` より妥当な推測*

### TC-12: content が空文字のとき EmptyOrInvalidResponse を返す

- **テスト名**: message.content が空文字で EmptyOrInvalidResponse を返す
  - **境界値の意味**: null ではないが長さ 0 という空文字境界
  - **境界値での動作保証**: 空文字も有効テキストとみなさない
- **入力値**: レスポンス `{"choices":[{"message":{"role":"assistant","content":""}}]}`
  - **境界値選択の根拠**: `isNullOrEmpty()` の空文字側の分岐を明示的に突く
  - **実際の使用場面**: プロバイダーが空文字を返す異常応答
- **期待される結果**: `LlmRewriteResult.Failure.EmptyOrInvalidResponse`
  - **境界での正確性**: 空文字を Success("") にしない
  - **一貫した動作**: null（TC-11）と空文字が同一結果
- **テストの目的**: 空文字 content の失敗マッピング確認
  - **堅牢性の確認**: 空文字を後段の本文上書きに流さない
- 🟡 *TASK-0060.md 実装詳細5・api-endpoints.md「content が null/空文字 → EmptyOrInvalidResponse」より*

### TC-13: 入力 content が空文字でもそのまま送信し Success を返す（EDGE-101）

- **テスト名**: 空文字 content を検証せず送信する
  - **境界値の意味**: 入力側（引数 `content`）が空文字という境界。応答側の空（TC-10〜12）とは別方向の境界
  - **境界値での動作保証**: リポジトリは入力 content を検証・加工せず、空文字のまま API へ送る
- **入力値**: `content = ""`、`prompt = "何か生成してください"`、応答 content = `"生成結果"`
  - **境界値選択の根拠**: EDGE-101「入力が空文字でもボタン非活性化せず空文字のまま送信」
  - **実際の使用場面**: ProcessedContent が空のまま生成系プロンプトを実行
- **期待される結果**: `LlmRewriteResult.Success("生成結果")`。送信ボディの `messages[1].content` が `""`
  - **境界での正確性**: 入力空文字を早期リターン・例外にせず送信する
  - **一貫した動作**: 入力の空はリポジトリでは弾かない（応答の空のみ失敗）
- **テストの目的**: 入力バリデーションを行わない責務境界の確認
  - **堅牢性の確認**: 空入力で例外や NetworkError を発生させない
- 🔵 *requirements.md 4.2・EDGE-101より*

---

## 4. 統合テストケース（Ktor MockEngine）

### IT-01: 送信リクエストのヘッダ・ボディが仕様通り（End-to-End）

- **テスト名**: Authorization/Content-Type ヘッダと messages ボディが仕様と一致する
  - **何をテストするか**: `rewrite()` 呼び出しで実際に送出される HTTP リクエストの中身
  - **期待される動作**: MockEngine が受け取った `request` を検査し、仕様通りのヘッダ・JSON ボディであること
- **入力値**:
  - `settings = LlmSettings("https://api.example.com/v1/chat/completions", "sk-int-test", "gpt-4o")`
  - `prompt = "system-prompt"`、`content = "user-content"`
  - **入力データの意味**: 認証・モデル・system/user メッセージ順序を全て確認できる代表値
- **期待される結果**:
  - `Authorization` ヘッダ == `Bearer sk-int-test`
  - `Content-Type` == `application/json`
  - HTTPメソッド == POST、URL == `settings.endpointUrl`
  - ボディ JSON: `model == "gpt-4o"`、`messages[0].role == "system"` かつ `content == "system-prompt"`、`messages[1].role == "user"` かつ `content == "user-content"`
  - **期待結果の理由**: api-endpoints.md「認証」「リクエスト仕様」表（messages[0]=system/[1]=user）に準拠
- **テストの目的**: リクエスト構築の正確性を実通信経路で保証
  - **確認ポイント**: role の順序・固定値、Bearer トークン形式、Content-Type 交渉
- 🔵 *TASK-0060.md 統合テスト1・api-endpoints.md「認証」「リクエスト仕様」より（role 固定値は🟡）*

### IT-02: 30秒超の遅延応答で実際にタイムアウトし Timeout を返す

- **テスト名**: HttpTimeout により 30秒超で Timeout を返す
  - **何をテストするか**: `HttpTimeout` プラグイン `requestTimeoutMillis = 30_000` の実機能
  - **期待される動作**: MockEngine が閾値超の遅延応答を返すと `HttpRequestTimeoutException` が発生し Timeout へマッピングされる
- **入力値**: MockEngine で `requestTimeoutMillis` を超える遅延を発生させる応答
  - **入力データの意味**: 実際のタイムアウト発火を再現（TC-05 は例外注入、本ケースは設定値の実効性検証）
  - **実運用シナリオ**: 応答が返らないエンドポイント
- **期待される結果**: `LlmRewriteResult.Failure.Timeout`（`messageResId == R.string.error_llm_timeout`）
  - **期待結果の理由**: NFR-001（30秒でキャンセル）・REQ-202 の実装保証
  - **テスト高速化の注意**: 実30秒待機は非現実的なため、MockEngine 側の遅延と `HttpTimeout` の閾値を短縮設定して相対検証する構成を許容する（閾値=実装値/テスト用に注入可能とする）🟡
- **テストの目的**: タイムアウト設定が実際に機能することの確認
  - **確認ポイント**: 遅延 > 閾値 で Timeout、遅延 < 閾値 で Success（対比が取れると尚良い）
- 🔵 *TASK-0060.md 統合テスト2・NFR-001・REQ-202・api-endpoints.md「タイムアウト設定」より（高速化手法は🟡）*

---

## 5. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: 本プロジェクトの実装言語（note.md 技術スタック）。sealed class による網羅的な結果分岐、`suspend fun` によるコルーチン非同期が本機能に適合
  - **テストに適した機能**: バッククォート日本語テストメソッド名、data class の構造的等価性による結果 assert
- **テストフレームワーク**:
  - 単体: **JUnit 4 + MockK + kotlinx-coroutines-test**（`runTest`/`runBlocking`）。Ktor モックは MockK 困難のため **Ktor `MockEngine` で HttpClient を構築**する方式を推奨（前述の設計判断参照）
  - 統合: **Ktor `MockEngine`**（`androidTest`、`AndroidJUnitRunner`）
  - **フレームワーク選択の理由**: note.md「テスト関連情報」でユニット=JUnit4+MockK+Coroutines Test、統合=Ktor MockEngine と規定
  - **テスト実行環境**: 単体はローカル JVM（`./gradlew test`、必要に応じ Robolectric `@Config(sdk=[34])`）、統合はデバイス/エミュレータ（`./gradlew connectedAndroidTest`）
- 🔵 *note.md「1. 技術スタック」「5. テスト関連情報」より（MockEngineによる単体テスト方式の採用は🟡）*

---

## 6. テストケース実装時の日本語コメント指針

各テストは Arrange-Act-Assert（AAA）で構成し、既存 `LlmSettingsRepositoryImplTest.kt` の命名・コメント慣習に合わせる。

### テストケース開始時のコメント（例）

```kotlin
// 【テスト目的】: 正常応答時に choices[0].message.content を Success として返すことを確認
// 【テスト内容】: 有効な content を含む 200 応答を返す HttpClient で rewrite() を呼び出す
// 【期待される動作】: LlmRewriteResult.Success("書き換え結果") が返る
// 🔵 信頼性レベル: TASK-0060.md 単体テストケース1・api-endpoints.md より
```

### Given / When / Then コメント（例）

```kotlin
@Test
fun rewrite_withValidResponse_returnsSuccess() = runTest {
    // 【テストデータ準備】: content="書き換え結果" を返す MockEngine ベースの HttpClient を用意
    // 【初期条件設定】: 有効な endpointUrl/apiKey/model を持つ LlmSettings
    val json = """{"choices":[{"message":{"role":"assistant","content":"書き換え結果"}}]}"""
    val httpClient = HttpClient(MockEngine { respond(json, HttpStatusCode.OK, jsonHeaders) }) {
        install(ContentNegotiation) { json() }
    }
    val repository = LlmRewriteRepositoryImpl(httpClient)

    // 【実際の処理実行】: rewrite() を呼び出す
    // 【処理内容】: prompt/content を messages に載せて POST し、レスポンスを解析する
    val result = repository.rewrite(settings, "prompt", "content")

    // 【結果検証】: 返却型と保持テキストを確認する
    // 【期待値確認】: Success かつ text が応答 content と一致
    assertEquals(LlmRewriteResult.Success("書き換え結果"), result) // 【確認内容】: 正常系マッピングの正確性
}
```

### セットアップ・クリーンアップのコメント（例）

```kotlin
@After
fun tearDown() {
    // 【テスト後処理】: 生成した HttpClient を close しリソース（コルーチン/エンジン）を解放する
    // 【状態復元】: 次テストへエンジンやディスパッチャの状態を持ち越さない
    httpClient.close()
}
```

---

## 7. 要件定義との対応関係

- **参照した機能概要**: requirements.md 1章（LLM呼び出し抽象化のデータ層リポジトリ）
- **参照した入力・出力仕様**: requirements.md 2.1〜2.5（インターフェース・入力パラメータ・`LlmRewriteResult`・リクエスト/レスポンス仕様）、api-endpoints.md「認証」「リクエスト仕様」「レスポンス仕様」
- **参照した制約条件**: requirements.md 3章（30秒タイムアウト NFR-001、ログ出力禁止 NFR-102、OpenAI互換形式 REQ-402、事前検証不要 REQ-404）
- **参照した使用例**: requirements.md 4章（正常系4.1、境界4.2 EDGE-101、エラー4.3 EDGE-001〜004）
- **参照したタスク定義**: TASK-0060.md 単体テスト要件（TC1〜5）・統合テスト要件（IT1〜2）・実装詳細4/5（catch順序・空応答判定）

---

## 8. テストケース一覧と要件トレーサビリティ

| No. | 種別 | テスト名 | 期待結果 | 対応要件 | 信頼性 |
|-----|------|---------|---------|---------|--------|
| TC-01 | 正常系 | 正常応答で Success | `Success(text)` | REQ-003 | 🔵 |
| TC-02 | 正常系 | タグ提案プロンプトで Success | `Success(text)` | REQ-302, 4.1 | 🟡 |
| TC-03 | 異常系 | 401 で AuthError | `Failure.AuthError` | EDGE-002 | 🔵 |
| TC-04 | 異常系 | 403 で AuthError | `Failure.AuthError` | EDGE-002 | 🔵 |
| TC-05 | 異常系 | Timeout例外で Timeout | `Failure.Timeout` | EDGE-003, NFR-001 | 🔵 |
| TC-06 | 異常系 | IOException で NetworkError | `Failure.NetworkError` | EDGE-001 | 🔵 |
| TC-07 | 異常系 | 500 で Unknown | `Failure.Unknown` | 4.3, api-endpoints | 🟡 |
| TC-08 | 異常系 | 429 で Unknown | `Failure.Unknown` | 3.3, api-endpoints | 🟡 |
| TC-09 | 異常系 | 機微情報をログ出力しない | APIキー非露出 | NFR-102, REQ-401 | 🟡 |
| TC-10 | 境界値 | choices空で EmptyOrInvalidResponse | `Failure.EmptyOrInvalidResponse` | EDGE-004 | 🟡 |
| TC-11 | 境界値 | content=null で EmptyOrInvalidResponse | `Failure.EmptyOrInvalidResponse` | EDGE-004 | 🟡 |
| TC-12 | 境界値 | content=空文字で EmptyOrInvalidResponse | `Failure.EmptyOrInvalidResponse` | EDGE-004 | 🟡 |
| TC-13 | 境界値 | 入力content空文字でも送信し Success | `Success` + 空contentを送信 | EDGE-101 | 🔵 |
| IT-01 | 統合 | リクエストのヘッダ・ボディ検証 | 仕様一致 | REQ-402, api-endpoints | 🔵 |
| IT-02 | 統合 | 30秒超遅延で Timeout | `Failure.Timeout` | NFR-001, REQ-202 | 🔵 |

---

## 9. 品質判定

```
✅ 高品質:
- テストケース分類: 正常系(2) / 異常系(7) / 境界値(4) / 統合(2) を網羅
- 期待値定義: 各ケースで返却型と messageResId まで明確
- 技術選択: Kotlin + JUnit4/MockK/Coroutines Test（単体）・Ktor MockEngine（統合）で確定
- 実装可能性: 前提 TASK-0058/0059 完了・依存ライブラリ導入済みで実現可能
- 信頼性レベル: 🔵 7件 / 🟡 8件 / 🔴 0件（赤信号ゼロ）
```

### 信頼性レベル分布

| 種別 | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|------|-------|-------|-------|------|
| 正常系 | 1 | 1 | 0 | 2 |
| 異常系 | 4 | 3 | 0 | 7 |
| 境界値 | 1 | 3 | 0 | 4 |
| 統合 | 2 | 0 | 0 | 2 |
| **合計** | **8** | **7** | **0** | **15** |

- 🔵 青信号: 8件 (53%)
- 🟡 黄信号: 7件 (47%)
- 🔴 赤信号: 0件 (0%)

**品質評価**: ✅ 高品質（要件・設計文書に基づき網羅、赤信号なし）

### 🟡 黄信号項目（Red/実装フェーズでの確認ポイント）

1. **モック方式**: MockK での `HttpClient` モックは困難。単体でも Ktor MockEngine 採用を推奨（Red フェーズで確定）
2. **TC-07/08（Unknown集約）**: 5xx・429 を Unknown に落とす分岐は要件から妥当だが直接明記は薄い
3. **TC-09（ログ非出力）**: 検証手段（ShadowLog 捕捉 or レビュー担保）が要件に未規定。実装がログ出力しない前提なら「apiKey を含む出力が存在しない」検証に置換
4. **TC-10〜12（空応答）**: EDGE-004 の空応答時扱いは直接ヒアリング未確認（requirements.md 記載通り 🟡）
5. **IT-02（タイムアウト高速化）**: 実30秒待機を避けるため閾値/遅延を短縮注入する構成を許容。閾値をテスト注入可能にするか設計判断が必要（TASK-0061 の HttpClient 生成場所と関連）
6. **strings.xml 依存**: `error_llm_*` 5リソースはコンパイルのため先行最小定義が必要（本来 TASK-0064）

---

## 次のステップ

**次のお勧めステップ**: `/tsumiki:tdd-red llm-memo-rewrite TASK-0060` で Red フェーズ（失敗テスト作成）を開始する。

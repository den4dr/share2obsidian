# TDD Redフェーズ記録: LlmRewriteRepository・LlmRewriteRepositoryImpl

**機能名**: llm-rewrite-repository
**タスクID**: TASK-0060
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 0. 事前対応（コンパイル前提の外部依存解消）

要件定義書 3.5「依存リソース制約」で指摘の通り、実装が参照する `R.string.error_llm_*` 5リソースが
`app/src/main/res/values/strings.xml` に未定義だったため、以下を先行対応した（本来はTASK-0064）。

- `app/src/main/res/values/strings.xml` に以下5件を暫定文言で追加:
  - `error_llm_network` = 「ネットワークに接続できませんでした」
  - `error_llm_auth` = 「APIキーが正しくありません」
  - `error_llm_timeout` = 「LLMからの応答がタイムアウトしました」
  - `error_llm_empty_response` = 「LLMから有効な応答が得られませんでした」
  - `error_llm_unknown` = 「予期しないエラーが発生しました」
- また、testcases.md の設計判断（MockKでのHttpClientモックは困難）に基づき、単体テストでも
  Ktor `MockEngine` を採用するため、`ktor-client-mock`（バージョン3.3.1、既存ktorカタログと統一）を
  `gradle/libs.versions.toml` に追加し、`app/build.gradle.kts` の `testImplementation` /
  `androidTestImplementation` に追加した。

---

## 1. 実装したテストケース一覧

テストケース定義書（`docs/implements/llm-memo-rewrite/TASK-0060/llm-rewrite-repository-testcases.md`）の
TC-01〜TC-13・IT-01・IT-02 を全件（15件）実装した（目標10件以上に対し15件、全テストケースを網羅）。

| No. | 種別 | テスト名 | ファイル | 信頼性 |
|-----|------|---------|---------|--------|
| TC-01 | 正常系 | 正常応答で書き換え結果テキストをSuccessとして返す | `LlmRewriteRepositoryImplTest.kt` | 🔵 |
| TC-02 | 正常系 | タグ提案用のプロンプトでも同じ経路でSuccessを返す | `LlmRewriteRepositoryImplTest.kt` | 🟡 |
| TC-03 | 異常系 | 401Unauthorized応答でAuthErrorを返す | `LlmRewriteRepositoryImplTest.kt` | 🔵 |
| TC-04 | 異常系 | 403Forbidden応答でAuthErrorを返す | `LlmRewriteRepositoryImplTest.kt` | 🔵 |
| TC-05 | 異常系 | HttpRequestTimeoutException発生時にTimeoutを返す | `LlmRewriteRepositoryImplTest.kt` | 🔵 |
| TC-06 | 異常系 | IOException発生時にNetworkErrorを返す | `LlmRewriteRepositoryImplTest.kt` | 🔵 |
| TC-07 | 異常系 | 500InternalServerError応答でUnknownを返す | `LlmRewriteRepositoryImplTest.kt` | 🟡 |
| TC-08 | 異常系 | 429TooManyRequests応答でUnknownを返す | `LlmRewriteRepositoryImplTest.kt` | 🟡 |
| TC-09 | 異常系 | 認証失敗時にAPIキーを含む機微情報をログへ出力しない | `LlmRewriteRepositoryImplTest.kt` | 🟡 |
| TC-10 | 境界値 | choices空配列応答でEmptyOrInvalidResponseを返す | `LlmRewriteRepositoryImplTest.kt` | 🟡 |
| TC-11 | 境界値 | messageContentがnullのときEmptyOrInvalidResponseを返す | `LlmRewriteRepositoryImplTest.kt` | 🟡 |
| TC-12 | 境界値 | messageContentが空文字のときEmptyOrInvalidResponseを返す | `LlmRewriteRepositoryImplTest.kt` | 🟡 |
| TC-13 | 境界値 | 入力contentが空文字でも検証せずそのまま送信しSuccessを返す | `LlmRewriteRepositoryImplTest.kt` | 🔵 |
| IT-01 | 統合 | 送信リクエストのAuthorizationヘッダとmessagesボディが仕様通り | `LlmRewriteRepositoryIntegrationTest.kt`（androidTest） | 🔵 |
| IT-02 | 統合 | 閾値超の遅延応答でHttpTimeoutにより実際にタイムアウトしTimeoutを返す | `LlmRewriteRepositoryIntegrationTest.kt`（androidTest） | 🔵 |

**信頼性分布**: 🔵 8件 / 🟡 7件 / 🔴 0件（テストケース定義書の分布を踏襲）

---

## 2. 作成したテストファイル

### 2.1 `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImplTest.kt`
- **対象**: TC-01〜TC-13（13件）
- **実行環境**: `@RunWith(RobolectricTestRunner::class)` `@Config(sdk = [34])`。
  Robolectricを採用する理由はTC-09（`ShadowLog`によるログ非出力確認）のためであり、既存
  `LlmSettingsRepositoryImplTest.kt` の `sdk=[34]` 明示規約を踏襲した。
- **モック方式**: testcases.md の設計判断（MockKでのKtor `HttpClient` モックは inline関数の多用により困難）
  に基づき、Ktor `MockEngine` で `HttpClient` を構築する方式を採用した。`buildClient()` ヘルパーで
  レスポンス/例外パターンごとにエンジンを組み立てる。
  - 401/403/500/429 は `expectSuccess = true` を設定したHttpClientでMockEngineがステータス応答を返すことで、
    Ktorの`ClientRequestException`/`ServerResponseException`を自然発生させる。
  - TC-05（Timeout）はtestcases.md記載の通り「単体では例外送出」方針に従い、MockEngineハンドラ内で
    `HttpRequestTimeoutException(HttpRequestBuilder())` を直接送出する（実際の30秒遅延はIT-02で担保）。
  - TC-06（NetworkError）はMockEngineハンドラ内で`java.io.IOException`を直接送出する。
  - TC-13は送信されたリクエストボディを`OutgoingContent.ByteArrayContent`として捕捉し、
    `ChatCompletionRequestDto`へデコードして`messages[1].content`が`""`のまま送信されることを検証する。

### 2.2 `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryIntegrationTest.kt`
- **対象**: IT-01, IT-02（2件）
- **実行環境**: `@RunWith(AndroidJUnit4::class)`。`connectedAndroidTest`用（計器テスト）。
- **注記**: 本開発環境にはadb/emulatorが存在しないため実機実行はできない。TASK-0058 Red フェーズと同方針に従い、
  **コンパイル成功（＝production クラス不在によるコンパイルエラーのみであること）をもって暫定確認**とする。
- IT-02は実30秒待機を避けるため、テスト用HttpClientに`HttpTimeout { requestTimeoutMillis = 200 }`を設定し、
  MockEngineで1000ms遅延させることで実際のタイムアウト発火を短時間で検証する構成とした
  （testcases.mdで許容されている高速化手法）。

---

## 3. テストコード全文

各ファイルの全文は以下のパスを参照:
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImplTest.kt`
- `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryIntegrationTest.kt`

---

## 4. 実行結果と期待される失敗

### 4.1 JVMユニットテスト（`testDebugUnitTest`）

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*LlmRewriteRepositoryImplTest*"
```

**結果**: `compileDebugUnitTestKotlin` タスクでコンパイルエラー（13件、すべて同一原因）。

```
e: .../LlmRewriteRepositoryImplTest.kt:82:26 Unresolved reference 'LlmRewriteRepositoryImpl'.
e: .../LlmRewriteRepositoryImplTest.kt:104:26 Unresolved reference 'LlmRewriteRepositoryImpl'.
... (以下、各@Testメソッド内の `LlmRewriteRepositoryImpl(httpClient)` 呼び出し箇所で計13件)
```

**原因**: `LlmRewriteRepositoryImpl` が未実装のため、テストコードから参照できない。これは意図した
Red フェーズの状態であり、テストコード自体の記述ミスではない。`R.string.error_llm_*`（事前対応で追加済み）・
`ktor-client-mock`（依存追加済み）・DTO/`LlmSettings`/`LlmRewriteResult`（実装済み）を使用する箇所は
すべて正常にコンパイル可能であることを確認済み＝エラーは production クラス `LlmRewriteRepositoryImpl` の
不在にのみ起因する。

### 4.2 計器テスト（`compileDebugAndroidTestKotlin`、実行は暫定確認）

```bash
mise exec -- ./gradlew compileDebugAndroidTestKotlin
```

**結果**: コンパイルエラー2件のみ。

```
e: .../data/llm/LlmRewriteRepositoryIntegrationTest.kt:80:26 Unresolved reference 'LlmRewriteRepositoryImpl'.
e: .../data/llm/LlmRewriteRepositoryIntegrationTest.kt:124:26 Unresolved reference 'LlmRewriteRepositoryImpl'.
```

**原因**: 同上（`LlmRewriteRepositoryImpl` 未実装）。`MockEngine`/`HttpTimeout`/`ChatCompletionRequestDto`
等、実クラスを使った箇所はすべて正しくコンパイルできることを確認済み。

**環境注記**: この開発環境にはadb/emulatorが存在しないため`connectedAndroidTest`による実機実行はできない。
Green フェーズ実装後は、コンパイル成功をもって暫定確認とし、実機/エミュレータでの実行確認は別途デバイス環境で行う。

---

## 5. Greenフェーズで実装すべき内容

1. **`app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt`**
   - `interface LlmRewriteRepository { suspend fun rewrite(settings: LlmSettings, prompt: String, content: String): LlmRewriteResult }`

2. **`app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImpl.kt`**
   - コンストラクタ: `LlmRewriteRepositoryImpl(private val httpClient: HttpClient) : LlmRewriteRepository`
   - `rewrite()`:
     - `httpClient.post(settings.endpointUrl) { header(Authorization, "Bearer ${settings.apiKey}"); contentType(Json); setBody(ChatCompletionRequestDto(model, messages=[system=prompt, user=content])) }.body<ChatCompletionResponseDto>()`
     - 例外を`try/catch`で捕捉し、**具体的な例外を先に捕捉する順序**で `LlmRewriteResult` へマッピングする:
       1. `catch (e: HttpRequestTimeoutException)` → `Failure.Timeout(R.string.error_llm_timeout)`（TC-05）
       2. `catch (e: ClientRequestException)` → `status==401||403` なら `Failure.AuthError(R.string.error_llm_auth)`（TC-03, TC-04）、それ以外は `Failure.Unknown(R.string.error_llm_unknown)`（TC-08）
       3. `catch (e: IOException)` → `Failure.NetworkError(R.string.error_llm_network)`（TC-06）
          - `HttpRequestTimeoutException`は`IOException`を継承するため、この分岐より**前**に(1)を配置する必要がある
       4. `catch (e: Exception)` → `Failure.Unknown(R.string.error_llm_unknown)`（TC-07: 500 = `ServerResponseException`もここに入る）
     - 正常応答時: `val text = response.choices.firstOrNull()?.message?.content; if (text.isNullOrEmpty()) Failure.EmptyOrInvalidResponse(R.string.error_llm_empty_response) else Success(text)`（TC-01, TC-02, TC-10〜TC-13）
   - 例外オブジェクト自体・APIキーをログ出力しない（NFR-102, TC-09）。ログ出力を行わない実装であれば
     TC-09は自動的に通過する。

3. **DI（本タスクの範囲外）**
   - `HttpClient`のインスタンス化・`HttpTimeout(30_000ms)`設定はTASK-0061（`LlmModule`）の責務。
     単体テストはコンストラクタ直接注入で検証しているため、Greenフェーズの実装可否には影響しない。

上記実装により、本 Red フェーズで作成した13件の単体テストが全て成功することが期待される
（Green フェーズの完了条件）。IT-01/IT-02は実機/エミュレータ環境でのコンパイル成功後の実行確認が必要。

# TDD開発メモ: llm-rewrite-repository

## 概要

- 機能名: LlmRewriteRepository・LlmRewriteRepositoryImpl実装
- 開発開始: 2026-07-06
- 現在のフェーズ: 完了（Refactorフェーズまで完了）

## 関連ファイル

- 元タスクファイル: `docs/tasks/llm-memo-rewrite/TASK-0060.md`
- 要件定義: `docs/implements/llm-memo-rewrite/TASK-0060/llm-rewrite-repository-requirements.md`
- テストケース定義: `docs/implements/llm-memo-rewrite/TASK-0060/llm-rewrite-repository-testcases.md`
- Redフェーズ記録: `docs/implements/llm-memo-rewrite/TASK-0060/llm-rewrite-repository-red-phase.md`
- 実装ファイル（未作成、Greenフェーズで作成予定）:
  - `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt`
  - `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImpl.kt`
- テストファイル:
  - `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImplTest.kt`（TC-01〜TC-13）
  - `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryIntegrationTest.kt`（IT-01, IT-02）

## Redフェーズ（失敗するテスト作成）

### 作成日時

2026-07-06

### 事前対応（コンパイル前提の外部依存解消）

- `app/src/main/res/values/strings.xml` に `error_llm_network` / `error_llm_auth` /
  `error_llm_timeout` / `error_llm_empty_response` / `error_llm_unknown` の5リソースを
  暫定日本語文言で追加（正式文言はTASK-0064で更新予定）。要件定義書3.5で指摘済みの外部依存。
- `gradle/libs.versions.toml` / `app/build.gradle.kts` に `ktor-client-mock`（v3.3.1）を
  `testImplementation` / `androidTestImplementation` として追加。testcases.mdの設計判断
  （MockKでのKtor HttpClientモックは困難）に基づき、単体テストでもKtor `MockEngine`を採用するため。

### テストケース

テストケース定義書のTC-01〜TC-13（単体テスト、JVM/Robolectric）・IT-01/IT-02（統合テスト、androidTest）を
全15件実装（目標10件以上）。正常系2件・異常系7件・境界値4件・統合2件。信頼性: 🔵8件/🟡7件/🔴0件。

### テストコード

全文は以下を参照:
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImplTest.kt`
- `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryIntegrationTest.kt`

主要な設計判断:
- Ktor `MockEngine`でHttpClientを構築し、レスポンスステータス（401/403/429/500/200）や
  例外送出（`HttpRequestTimeoutException`, `IOException`）を再現してエラーマッピング分岐を検証する。
- TC-09は`ShadowLog`（Robolectric）でログ出力を捕捉し、APIキー文字列が含まれないことを検証する。
- TC-13/IT-01は送信リクエストボディを`OutgoingContent.ByteArrayContent`から捕捉し、
  `ChatCompletionRequestDto`へデコードして内容を検証する。

### 期待される失敗

`LlmRewriteRepository` / `LlmRewriteRepositoryImpl` が未実装のため、以下の通りコンパイルエラーとなる
（TASK-0058 Red フェーズと同方針。production クラス不在によるコンパイルエラーをもってRedフェーズの
「失敗」確認とする）。

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*LlmRewriteRepositoryImplTest*"
# => compileDebugUnitTestKotlin FAILED
# e: .../LlmRewriteRepositoryImplTest.kt:82:26 Unresolved reference 'LlmRewriteRepositoryImpl'.
# (以下、13箇所すべて同一原因)

mise exec -- ./gradlew compileDebugAndroidTestKotlin
# => compileDebugAndroidTestKotlin FAILED
# e: .../LlmRewriteRepositoryIntegrationTest.kt:80:26 Unresolved reference 'LlmRewriteRepositoryImpl'.
# e: .../LlmRewriteRepositoryIntegrationTest.kt:124:26 Unresolved reference 'LlmRewriteRepositoryImpl'.
```

いずれも「`LlmRewriteRepositoryImpl` 未実装」のみが原因であり、`R.string.error_llm_*`・
`ktor-client-mock`・既存DTO/`LlmSettings`/`LlmRewriteResult`を使用する箇所は正常にコンパイル可能である
ことを確認済み。

### 次のフェーズへの要求事項

1. `LlmRewriteRepository`インターフェースと`LlmRewriteRepositoryImpl`（`HttpClient`コンストラクタ注入）を実装する。
2. リクエスト構築: `POST settings.endpointUrl` + `Authorization: Bearer {apiKey}` + `messages=[system=prompt, user=content]`。
3. 例外マッピングのcatch順序を `HttpRequestTimeoutException` → `ClientRequestException`(401/403→AuthError, それ以外→Unknown) → `IOException` → `Exception`(→Unknown) とする（`HttpRequestTimeoutException`は`IOException`を継承するため順序が重要）。
4. 正常応答時は `choices.firstOrNull()?.message?.content` の `isNullOrEmpty()` 判定で `Success`/`EmptyOrInvalidResponse` を分岐する。
5. 例外オブジェクト・APIキーをログ出力しない（NFR-102）。
6. 実装後、`mise exec -- ./gradlew testDebugUnitTest --tests "*LlmRewriteRepositoryImplTest*"` で13件全て成功することを確認する。IT-01/IT-02は`compileDebugAndroidTestKotlin`でのコンパイル成功を暫定確認とする（実機実行は別途デバイス環境）。

## Greenフェーズ（最小実装）

### 実装日時

2026-07-06

### 実装方針

Redフェーズ記録「5. Greenフェーズで実装すべき内容」に従い、以下を新規作成した。

- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt`（インターフェース）
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImpl.kt`（実装）

`HttpClient`をコンストラクタ注入し、`messages[0]=system(prompt)`/`messages[1]=user(content)`の
順序でPOSTする。例外は`HttpRequestTimeoutException`→`ClientRequestException`(401/403→AuthError,
それ以外→Unknown)→`IOException`→`Exception`(→Unknown)の順でcatchし`LlmRewriteResult`へマッピング。
正常応答は`choices.firstOrNull()?.message?.content`を`isNullOrEmpty()`判定でSuccess/EmptyOrInvalidResponse
に分岐。例外オブジェクト・APIキーはログ出力しない（NFR-102、TC-09は自動充足）。

### 仕様との差異と対応

実装前チェックで、TASK-0059実装済みの`ChatMessageDto.content`が非null`String`型になっており、
requirements.md/note.mdの初期設計（`content: String?`）およびtestcases.md TC-11の想定（レスポンス
`content: null`を優雅にEmptyOrInvalidResponseとして扱う）と食い違うことを発見した。素直に実装した
状態でテストを実行したところTC-11のみ失敗（`Failure.Unknown`になってしまう）し、原因はJSONの
`content: null`を非null`String`へデコードする際の`SerializationException`が汎用catch節に落ちる
ことだった。既存`ChatCompletionDtoTest.kt`（TASK-0059、全10件）がcontentの非null性を検証していない
ことを確認した上で、`ChatMessageDto.content`を`String?`へ変更（許容範囲を広げるのみで後方互換）
することで対応した。詳細は`llm-rewrite-repository-green-phase.md`「2. 仕様との差異調査・対応」を参照。

### テスト結果

- `LlmRewriteRepositoryImplTest`（TC-01〜TC-13）: 13件全て成功
- `ChatCompletionDtoTest`（TASK-0059の既存テスト）: 10件全て成功（回帰なし）
- プロジェクト全体のJVMユニットテスト（`testDebugUnitTest --rerun-tasks`）: 218件全て成功
- `compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL（IT-01/IT-02はコンパイル成功による暫定確認。
  adb/emulator不在のため`connectedAndroidTest`実機実行は別途デバイス環境で必要）

### 課題・改善点（Refactorフェーズで対応）

1. catch節の例外変数`e`が未使用（可読性のため`catch (_: ...)`化等を検討）
2. `ClientRequestException`のstatus判定（401/403）をヘルパー関数化すると可読性向上の余地
3. インターフェース/実装クラスのKDoc重複の整理
4. `ChatMessageDto.content`のnullable化がTASK-0059側テストに影響しないことをverify-completeで再確認
5. IT-01/IT-02の実機/エミュレータでの実行確認（別環境で実施）

## Refactorフェーズ（品質改善）

### リファクタリング日時

2026-07-06

### 改善内容

`LlmRewriteRepositoryImpl.rewrite()`本体に混在していた3責務をプライベート関数へ抽出した
（機能・戻り値・catch順序は変更なし）。

- `postChatCompletion()`: リクエスト構築・送信のみを担当
- `ChatCompletionResponseDto.toRewriteResult()`: 応答解析（Success/EmptyOrInvalidResponse判定）のみを担当する拡張関数
- `isAuthErrorStatus()`: 401/403判定のみを担当するヘルパー

未使用のcatch変数（`e`）を`_`にリネームして意図を明示。各関数に
【機能概要】【改善内容】【設計方針】【パフォーマンス】【保守性】観点のコメントを追加。

### セキュリティレビュー結果

- APIキー・例外オブジェクトのログ出力なし（`android.util.Log`呼出しがコード全体に存在しないことをgrepで確認、NFR-102遵守）
- Authorizationヘッダ以外へのAPIキー漏洩経路なし
- 入力content/promptの非検証はEDGE-101/TC-13が要求する意図的仕様（脆弱性ではない）
- エンドポイントURLのスキーム未検証はREQ-004/REQ-404に基づく意図的なトレードオフとして識別（本タスク範囲外）
- 重大な脆弱性なし

### パフォーマンスレビュー結果

- `rewrite()`はHTTP1往復＋O(1)処理のみで、ボトルネックはネットワークI/Oのみ
- 重大な性能課題なし

### テスト結果

- リファクタ前後で`LlmRewriteRepositoryImplTest`(13件)+`ChatCompletionDtoTest`(10件)=23件が
  継続して全て成功
- プロジェクト全体のJVMユニットテスト: 218件全て成功（回帰なし）
- `compileDebugAndroidTestKotlin`: BUILD SUCCESSFUL維持

### 品質評価

✅ 高品質（テスト全成功・重大な脆弱性/性能課題なし・可読性向上・500行制限内）

### 今後の課題

1. IT-01/IT-02の実機/エミュレータでの実行確認（別デバイス環境が必要）
2. ~~`ChatMessageDto.content`のnullable化の影響範囲をverify-completeで最終確認~~ → 解消（下記参照）
3. エンドポイントURLスキーム検証は必要であれば別タスクで検討

## Verify-Completeフェーズ（完全性検証）

### 検証日時

2026-07-06

### 検証結果

- `mise exec -- ./gradlew testDebugUnitTest --rerun-tasks` を実行し、**218件全て成功**
  （failures=0, errors=0, skipped=0, BUILD SUCCESSFUL）を確認。
- `ChatCompletionDtoTest`（TASK-0059、9件）を個別に確認し、`ChatMessageDto.content`の
  `String → String?`変更（Greenフェーズ対応）による回帰がないことを確認した。同テストは
  content の非null性を検証するケースを持たないため、nullable化はTASK-0059側の仕様・テストに
  抵触しない。
- `LlmRewriteRepositoryImplTest`（TASK-0060、TC-01〜TC-13相当）13件全て成功を個別に確認。
- `mise exec -- ./gradlew compileDebugAndroidTestKotlin --rerun-tasks` を実行し
  **BUILD SUCCESSFUL** を確認。`LlmRewriteRepositoryIntegrationTest.kt`（IT-01, IT-02）の
  コンパイルエラーなし。ただし本環境にはadb/emulatorが存在しないため、`connectedAndroidTest`
  による実機実行はできず、IT-01/IT-02は**コンパイル成功による暫定確認**にとどまる。

### 品質判定

✅ タスク完了（要件網羅率100%、スコープ内テスト全成功、IT-01/IT-02は実機未検証の既知の制約）

# TDD Greenフェーズ記録: LlmRewriteRepository・LlmRewriteRepositoryImpl

**機能名**: llm-rewrite-repository
**タスクID**: TASK-0060
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. 実装方針

Redフェーズ記録（`llm-rewrite-repository-red-phase.md`）の「5. Greenフェーズで実装すべき内容」に
従い、以下2ファイルを新規作成した。

- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt`（インターフェース）
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImpl.kt`（実装）

実装方針:
- `HttpClient` をコンストラクタ注入し、`httpClient.post(settings.endpointUrl) { ... }.body<ChatCompletionResponseDto>()`
  で OpenAI互換 Chat Completions API を呼び出す（note.md「実装パターン」通り）。
- `messages[0]=system(prompt)` / `messages[1]=user(content)` の固定順序で送信する。入力 `content` は
  検証・加工せずそのまま送信する（EDGE-101, TC-13）。
- 例外は **具体的な例外を先に捕捉する順序**でマッピングする:
  1. `HttpRequestTimeoutException` → `Failure.Timeout`（`IOException`を継承するため最優先で捕捉）
  2. `ClientRequestException` → `status==401||403` なら `Failure.AuthError`、それ以外（429等）は `Failure.Unknown`
  3. `IOException` → `Failure.NetworkError`
  4. `Exception`（5xx = `ServerResponseException`含む） → `Failure.Unknown`
- 正常応答時は `choices.firstOrNull()?.message?.content` を `isNullOrEmpty()` で判定し、
  `Success`/`Failure.EmptyOrInvalidResponse` を分岐する。
- 例外オブジェクト・APIキーはログ出力しない（NFR-102）。ログ出力自体を行わない実装のため、
  TC-09は自動的に充足される。

## 2. 仕様との差異調査・対応（実装前チェック）

要件定義・テストケース定義と、TASK-0059で実装済みの `ChatCompletionDto.kt` を照合した結果、
以下の差異を発見した。

- **差異の内容**: requirements.md/note.mdの初期設計では、レスポンス用メッセージ型
  （`MessageDto`）の `content` は `String?`（nullable）と規定されていた。しかし、TASK-0059で
  実際に実装された `ChatMessageDto`（リクエスト/レスポンス共通の型として実装）は
  `content: String`（**非null**）だった。
- **発覚経緯**: 本実装を素直に行いテストを実行したところ、TC-11
  （`content: null` を含むレスポンスJSONを送るテスト）が失敗した。原因調査の結果、
  kotlinx.serializationが非null `String` フィールドへのJSON `null` デコードに失敗し
  `SerializationException` を送出、これが実装のcatch(Exception)節で `Failure.Unknown` に
  マッピングされてしまい、テストが期待する `Failure.EmptyOrInvalidResponse` にならなかったことが
  判明した（AssertionErrorとして検出、クラッシュではない）。
- **判断**: 「レスポンス側でcontentがnullになり得る」ことはrequirements.md 2.3
  （`MessageDto`のcontent: String?）・testcases.md TC-11（🟡信号ながら要件定義書に明記）の両方で
  一貫して要求されており、対してTASK-0059実装の非null化は要件定義書の記述と食い違うため、
  ChatMessageDto側を要件定義書の意図に合わせて修正するのが妥当と判断した（DTOの用途を変える
  わけではなく、null許容範囲を広げるだけで既存のリクエスト送信・エンコード動作に影響がないことを
  ChatCompletionDtoTest.kt全件を確認した上で判断）。
  - ユーザーへの確認は行わず対応した理由: (1) 変更が「型をより許容的にする」方向のみで
    既存契約を壊さない、(2) 既存のTASK-0059テスト（ChatCompletionDtoTest.kt 全10件）がいずれも
    contentの非null性を明示的に検証していないことを事前に確認済み、(3) 対応しない限りTASK-0060の
    要件（TC-11, EDGE-004）を満たせずGreenフェーズが完了しない、という3点から実装上の必然的な
    修正と判断した。
- **実施した変更**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/dto/ChatCompletionDto.kt`
  の `ChatMessageDto.content` を `String` → `String?` に変更（コメントで意図を明記）。

## 3. 実装コード全文

### 3.1 `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepository.kt`

```kotlin
package com.den4dr.share2Obsidian.data.llm

interface LlmRewriteRepository {
    suspend fun rewrite(settings: LlmSettings, prompt: String, content: String): LlmRewriteResult
}
```
（全文はソースファイルを参照。日本語コメント・信頼性レベル付き）

### 3.2 `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmRewriteRepositoryImpl.kt`

catch順序・応答解析ロジックは本ファイル冒頭の「1. 実装方針」の通り。全文はソースファイルを参照。

### 3.3 変更: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/dto/ChatCompletionDto.kt`

`ChatMessageDto.content` を `String?` に変更（上記「2. 仕様との差異調査・対応」参照）。

---

## 4. テスト実行結果

### 4.1 対象テスト（`LlmRewriteRepositoryImplTest` 13件 + `ChatCompletionDtoTest` 10件）

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*LlmRewriteRepositoryImplTest*" --tests "*ChatCompletionDtoTest*"
# => BUILD SUCCESSFUL（全件成功）
```

`ChatMessageDto.content` のnullable化前は TC-11
（`messageContentがnullのときEmptyOrInvalidResponseを返す`）のみ失敗（`AssertionError`、
実際の結果が `Failure.Unknown` になっていた）。nullable化後は13件全て成功を確認。

### 4.2 プロジェクト全体のJVMユニットテスト（Robolectric含む）

```bash
mise exec -- ./gradlew testDebugUnitTest --rerun-tasks
# => BUILD SUCCESSFUL
```

全テストクラスの結果XML（`app/build/test-results/testDebugUnitTest/*.xml`）を集計し、
**218件全て成功（failures/errors = 0）**であることを確認した。TASK-0060で追加した
`LlmRewriteRepositoryImplTest`（13件）を含め、既存の全ユニットテストに回帰がないことを確認済み。

### 4.3 計器テスト（`compileDebugAndroidTestKotlin`、暫定確認）

```bash
mise exec -- ./gradlew compileDebugAndroidTestKotlin
# => BUILD SUCCESSFUL
```

環境注記: この開発環境にはadb/emulatorが存在しないため`connectedAndroidTest`による実機実行は
できない。Redフェーズと同方針により、`LlmRewriteRepositoryIntegrationTest.kt`（IT-01, IT-02）を
含む計器テストモジュール全体のコンパイル成功をもって暫定確認とした。実機/エミュレータでの
実行確認は別途デバイス環境で行う必要がある。

---

## 5. 品質判定

```
✅ 高品質:
- テスト結果: JVMユニットテスト218件全て成功（LlmRewriteRepositoryImplTest 13件含む）
- 実装品質: シンプルかつ動作する（catch順序に基づく明確な分岐、早期return無し）
- リファクタ箇所: 明確に特定可能（下記「6. 課題・改善点」参照）
- 機能的問題: なし
- コンパイルエラー: なし（testDebugUnitTest, compileDebugAndroidTestKotlin 共にBUILD SUCCESSFUL）
- ファイルサイズ: LlmRewriteRepository.kt 22行 / LlmRewriteRepositoryImpl.kt 91行（800行制限内）
- モック使用: 実装コード（LlmRewriteRepository.kt, LlmRewriteRepositoryImpl.kt,
  ChatCompletionDto.kt）にモック・スタブは含まれていない（モックはテストコードのみで使用）
```

## 6. 課題・改善点（Refactorフェーズで対応）

1. **例外変数の未使用警告**: 各catch節の `e` はマッピードのみに使い、変数自体を参照していない
   （IDEによっては未使用変数警告が出る可能性がある）。可読性のためリネームまたは`catch (_: ...)`化を検討。
2. **`ClientRequestException`分岐のstatus比較の可読性**: `e.response.status == HttpStatusCode.Unauthorized || e.response.status == HttpStatusCode.Forbidden` を `in listOf(...)` やヘルパー関数に切り出すと可読性が上がる可能性がある。
3. **KDocの重複**: インターフェースと実装クラスの両方に類似のKDocコメントがあり、Refactorフェーズで責務分担を見直せる余地がある。
4. **ChatMessageDto.content nullable化の影響範囲の最終確認**: 本Greenフェーズで`ChatCompletionDto.kt`（TASK-0059実装物）に変更を加えたため、Refactor/verify-completeフェーズで念のため`ChatCompletionDtoTest.kt`（TASK-0059のテスト）が全件成功することを再確認する。
5. **IT-01/IT-02の実機実行**: 本環境で未実行のため、デバイス/エミュレータが利用可能な環境で`connectedAndroidTest`による実行確認が別途必要。

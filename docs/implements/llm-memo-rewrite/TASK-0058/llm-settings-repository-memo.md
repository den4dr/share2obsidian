# TDD開発メモ: llm-settings-repository

## 概要

- 機能名: LlmSettings・LlmSettingsRepository・LlmSettingsRepositoryImpl（LLM API設定の暗号化分離保存）
- 開発開始: 2026-07-06
- 現在のフェーズ: 完了（Red → Green → Refactor 完了。次は `/tsumiki:tdd-verify-complete` で完全性検証）

## 関連ファイル

- 元タスクファイル: `docs/tasks/llm-memo-rewrite/TASK-0058.md`
- 要件定義: `docs/implements/llm-memo-rewrite/TASK-0058/llm-settings-repository-requirements.md`
- テストケース定義: `docs/implements/llm-memo-rewrite/TASK-0058/llm-settings-repository-testcases.md`
- Redフェーズ記録: `docs/implements/llm-memo-rewrite/TASK-0058/llm-settings-repository-red-phase.md`
- 実装ファイル（Greenフェーズで作成予定）:
  - `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettings.kt`
  - `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepository.kt`
  - `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImpl.kt`
- テストファイル:
  - `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsTest.kt`
  - `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt`
  - `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplInteractionTest.kt`
  - `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt`

## Redフェーズ（失敗するテスト作成）

### 作成日時

2026-07-06

### テストケース

テストケース定義書の TC-01〜TC-13 全13件を実装（目標10件以上を達成）。
- 正常系6件（TC-01, 02, 03, 04, 05, 06）
- 異常系3件（TC-07, 08, 09）
- 境界値4件（TC-10, 11, 12, 13）

DataStore（endpointUrl/model）は Robolectric 上の実ファイルI/Oで検証し、EncryptedSharedPreferences（apiKey）は状態検証を `FakeSharedPreferences`、インタラクション検証（保存先ルーティング・リスナー解除）を MockK モックで分離した。暗号化の実効性確認（TC-08）のみ実 EncryptedSharedPreferences を用いる計器テストとした。

### テストコード

各ファイル全文は以下を参照:
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsTest.kt`
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt`
- `app/src/test/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplInteractionTest.kt`
- `app/src/androidTest/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImplTest.kt`

### 期待される失敗

`mise exec -- ./gradlew testDebugUnitTest --tests "*LlmSettings*"` はコンパイルエラーで失敗する。
`LlmSettings`・`LlmSettingsRepository`・`LlmSettingsRepositoryImpl` が未実装のため `Unresolved reference` エラーが発生する（テストコード自体の記述には問題なし。Fake/Mock関連コードは正常にコンパイル可能なことを確認済み）。

`mise exec -- ./gradlew compileDebugAndroidTestKotlin` も同様に `LlmSettingsRepositoryImpl` 未解決の1件のみのコンパイルエラーで失敗する。

**環境注記**: この開発環境には adb/emulator が存在しないため `connectedAndroidTest`（TC-08）は実機実行できない。Green フェーズ完了後はコンパイル成功をもって暫定確認とする。通常のJVMユニットテスト（TC-01〜07, 09〜13、Robolectric含む）は `testDebugUnitTest` で通常通り実行・確認する。

### 次のフェーズへの要求事項

Green フェーズでは以下を最小実装する:
1. `LlmSettings` data class（endpointUrl/apiKey/model、全デフォルト値 `""`）
2. `LlmSettingsRepository` interface（getSettings/saveEndpointUrl/saveApiKey/saveModel）
3. `LlmSettingsRepositoryImpl`
   - DataStore: `stringPreferencesKey("llm_endpoint_url")` / `stringPreferencesKey("llm_model")`
   - EncryptedSharedPreferences: キー `"llm_api_key"`、`saveApiKey()` は `withContext(Dispatchers.IO)`
   - `encryptedApiKeyFlow()`: `callbackFlow` + `OnSharedPreferenceChangeListener`、`awaitClose` でリスナー解除必須
   - `getSettings()`: DataStore Flow と `encryptedApiKeyFlow()` を `combine()` して合成

上記により本フェーズで作成した13件のテストを全てグリーンにすることが目標。`LlmModule`（Hilt DI）は本タスクのテスト対象外のため Green フェーズでは必須ではないが、後続タスクで必要になる。

## Greenフェーズ（最小実装）

### 実装日時

2026-07-06

### 実装方針

Red フェーズ記録・note.md・TASK-0058.md の実装案をそのまま採用（仕様との差異なし）。
- `LlmSettings`: 全フィールド `String = ""` の data class。
- `LlmSettingsRepository`: `getSettings()`/`saveEndpointUrl()`/`saveApiKey()`/`saveModel()` のインターフェース。
- `LlmSettingsRepositoryImpl`: 既存 `NoteSettingsRepositoryImpl` の DataStore パターンを踏襲し、apiKey のみ `EncryptedSharedPreferences` 相当（`SharedPreferences` 型で注入）に分離。`encryptedApiKeyFlow()` を `callbackFlow` + `OnSharedPreferenceChangeListener` で実装し、DataStore の `Flow` と `combine()`。
- `LlmModule`（Hilt DI）は本タスクのテスト対象外のため未実装（後続タスクで対応）。

### 実装コード

- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettings.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepository.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImpl.kt`

全文・信頼性レベルは `llm-settings-repository-green-phase.md` を参照。

### テスト結果

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*LlmSettings*"   # BUILD SUCCESSFUL（14 tests、3回再実行で安定）
mise exec -- ./gradlew testDebugUnitTest                          # BUILD SUCCESSFUL（プロジェクト全体、既存テストへの影響なし）
mise exec -- ./gradlew compileDebugAndroidTestKotlin               # BUILD SUCCESSFUL（TC-08含め計器テストのコンパイル成功）
```

TC-08（計器テスト）は adb/emulator 不在のため実機実行不可。ユーザー指示に従いコンパイル成功をもって暫定確認とした。

### テスト側の修正（実装ではなくテストコードの不具合）

1. `LlmSettingsRepositoryImplTest.kt` / `LlmSettingsRepositoryImplInteractionTest.kt` に `@Config(sdk = [34])` が抜けており、targetSdk(36) 既定解決で Robolectric が `IllegalArgumentException` を送出していた → 追加して解消（既存の他 Robolectric テストと同一パターンに統一）。
2. TC-09（`getSettingsCollectCancelled_unregistersSharedPreferenceListener`）は launch 直後に cancelAndJoin していたため register 呼び出し前にキャンセルされる競合状態があり、3回連続で決定的に失敗していた → `CompletableDeferred` で register 完了を待ってからキャンセルするよう修正（検証内容自体は変更なし）。

### 課題・改善点

- `encryptedApiKeyFlow()` の可読性・関数分割の余地（Refactorフェーズ対応）。
- `LlmModule`（Hilt DI）は後続タスクで実装。

## Refactorフェーズ（品質改善）

### 実施日時

2026-07-06

### 改善内容

- `LlmSettingsRepositoryImpl.kt` に DataStore側フォールバック処理の重複（`?: ""`）を `Preferences.stringOrEmpty()` 拡張関数へ抽出。
- EncryptedSharedPreferences側の現在値取得ロジックの重複（`getString(...) ?: ""`）を `currentApiKey()` ヘルパーへ抽出。
- 各関数のKDocに【改善内容】【設計方針】【パフォーマンス】【保守性】【セキュリティ】観点のコメントを追加し、非機能要件との対応関係を明示。
- 機能的な変更は行っていない（新機能追加なし、既存テストを無改修のまま継続成功）。

### セキュリティレビュー結果

- apiKey のログ・例外出力なし（NFR-102）、DataStoreへの平文混入経路なし（REQ-401）、リスナー解除漏れなし（awaitCloseで保証）。重大な脆弱性は発見されなかった。詳細は `llm-settings-repository-refactor-phase.md` §2 を参照。

### パフォーマンスレビュー結果

- 全メソッドがO(1)のキー読み書きのみ。重い計算・ループなし。リスナー登録はcollectのライフサイクルに紐づき解放される。重大な性能課題は発見されなかった。詳細は `llm-settings-repository-refactor-phase.md` §3 を参照。

### テスト結果（リファクタ後）

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*LlmSettings*"   # BUILD SUCCESSFUL（14 tests、継続成功）
mise exec -- ./gradlew testDebugUnitTest                          # BUILD SUCCESSFUL（プロジェクト全体）
mise exec -- ./gradlew compileDebugAndroidTestKotlin               # BUILD SUCCESSFUL
mise exec -- ./gradlew lint                                        # BUILD SUCCESSFUL
```

TC-09（3.328秒）はMockKの初回動的計装コストと判断し、実装側の性能課題ではないため対応不要とした（詳細はrefactor-phase.md §1）。

### 最終コード

`app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettingsRepositoryImpl.kt`（147行）。全文・信頼性レベルは `llm-settings-repository-refactor-phase.md` を参照。

### 品質評価

✅ 高品質: テスト全継続成功・重大な脆弱性/性能課題なし・DRY改善達成・lint成功・800行/500行制限に対し十分小さい・実装コードにモック/スタブなし。

### 残課題（後続タスクへ引き継ぎ）

- `LlmModule`（Hilt DI）未実装（後続タスクで対応）。
- TC-08（計器テスト）は実機/エミュレータでの実行確認が未了。

## 🎯 最終結果（Verify-Complete, 2026-07-06）

- **実装率**: 100%（13/13テストケース、TC-01〜TC-13すべて実装）
- **成功率**: 100%（スコープ内13件、プロジェクト全体191件とも全成功、失敗・エラー0）
- **要件網羅率**: 100%（REQ-004, REQ-401, NFR-101, NFR-102 すべて実装・テスト対応済み）
- **品質判定**: 合格（高品質）
- **TODO更新**: ✅完了マーク追加（overview.md・TASK-0058.mdの完了条件チェックボックス）

### テスト実行結果詳細

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*LlmSettings*" --rerun
# BUILD SUCCESSFUL（LlmSettingsTest 2件 + LlmSettingsRepositoryImplTest 9件 + LlmSettingsRepositoryImplInteractionTest 2件 = 13件、全成功）

mise exec -- ./gradlew testDebugUnitTest --rerun
# BUILD SUCCESSFUL（プロジェクト全体191件、失敗0・エラー0、総実行時間 6.92秒）

mise exec -- ./gradlew compileDebugAndroidTestKotlin --rerun
# BUILD SUCCESSFUL（TC-08含む計器テストのコンパイル成功、deprecation警告のみ）
```

**環境制約（ユーザー指示により明記）**: この検証環境には adb/emulator が存在しないため、TC-08（`LlmSettingsRepositoryImplTest` androidTest、実 EncryptedSharedPreferences の暗号化確認）は `connectedAndroidTest` で実機実行できていない。コンパイル成功のみを暫定確認とした。ユニットテスト（TC-01〜07, 09〜13）は `testDebugUnitTest` でフル実行し全件成功を確認済み。

### スコープ外テスト

なし（プロジェクト全体191件すべて成功）。テスト実行時間は最長でも2.645秒（`MainActivityEditFlowTest`、スコープ外）で、閾値（2秒）をわずかに超えるが総実行時間6.92秒は30秒を大きく下回るため改善提案は不要と判断。

## 💡 重要な技術学習（Verify-Complete時点での補足）

- テストケース定義書（testcases.md）のTC-01〜TC-13と実装済みテスト（`LlmSettingsTest.kt`/`LlmSettingsRepositoryImplTest.kt`/`LlmSettingsRepositoryImplInteractionTest.kt`/androidTest版`LlmSettingsRepositoryImplTest.kt`）のコード内コメントで1対1のトレーサビリティが確保されており、検証時の照合が容易だった。
- `LlmModule`（Hilt DI）はTASK-0058の完了条件（4項目）に含まれておらず、Red/Green/Refactorの各フェーズで一貫して「後続タスクスコープ」と明記されているため、未実装のままでも本タスクの完了判定には影響しない。

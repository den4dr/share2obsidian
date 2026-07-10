# TDD開発メモ: settings-viewmodel-llm

## 概要

- 機能名: SettingsViewModel LLM設定対応
- 開発開始: 2026-07-06
- 現在のフェーズ: 完了

## 関連ファイル

- 元タスクファイル: `docs/tasks/llm-memo-rewrite/TASK-0066.md`
- 要件定義: `docs/implements/llm-memo-rewrite/TASK-0066/settings-viewmodel-llm-requirements.md`
- テストケース定義: `docs/implements/llm-memo-rewrite/TASK-0066/settings-viewmodel-llm-testcases.md`
- 実装ファイル: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`
- テストファイル: `app/src/test/java/com/den4dr/share2Obsidian/ui/SettingsViewModelTest.kt`

## Redフェーズ（失敗するテスト作成）

### 作成日時

2026-07-06

### テストケース

`SettingsViewModelTest.kt` に以下10メソッドを実装（既存2メソッドの2引数コンストラクタ移行＋新規8メソッド）:

1. `uiState_reflectsRepositorySettings`（既存・2引数化） 🟡
2. `updateVault_savesToRepository`（既存・2引数化） 🟡
3. `updateLlmEndpointUrl_savesToRepository`（TC-N-01） 🔵
4. `updateLlmApiKey_savesToRepository`（TC-N-02） 🔵
5. `updateLlmModel_savesToRepository`（TC-N-03） 🔵
6. `uiState_reflectsBothNoteAndLlmRepositorySettings`（TC-N-04） 🔵
7. `uiState_reflectsNoteSettingsWhenLlmSettingsAreDefault`（TC-N-05） 🟡
8. `updateLlmApiKey_savesEmptyStringAsIs`（TC-B-01） 🟡
9. `uiState_isAllEmptyWhenBothRepositoriesReturnDefaults`（TC-B-02） 🟡
10. `updateLlmModel_calledTwice_savesTwice`（TC-B-03） 🟡

TC-E-01（save系例外ハンドリング）は要件定義書・テストケース定義書で「本タスク範囲外」と明示されているため未実装（意図的）。

### テストコード

`app/src/test/java/com/den4dr/share2Obsidian/ui/SettingsViewModelTest.kt` を参照（全文は `settings-viewmodel-llm-red-phase.md` に記載）。

### 期待される失敗

`mise exec -- ./gradlew testDebugUnitTest --tests "*SettingsViewModelTest*"` はコンパイルエラーで `BUILD FAILED` となる。

主なエラー:
- `Too many arguments for 'constructor(noteSettingsRepository: NoteSettingsRepository): SettingsViewModel'`（2引数コンストラクタ未実装）
- `Unresolved reference 'updateLlmEndpointUrl'` / `'updateLlmApiKey'` / `'updateLlmModel'`（update系関数未実装）
- `No parameter with name 'llmEndpointUrl' found'` 等（`SettingsUiState` のLLMフィールド未実装）

いずれも「まだ実装されていない機能をテストする」というRedフェーズの原則に沿った失敗である。

### 次のフェーズへの要求事項

`SettingsViewModel.kt` に対して以下を実装する（詳細は `settings-viewmodel-llm-red-phase.md` §4参照）:

1. `SettingsUiState` に `llmEndpointUrl`/`llmApiKey`/`llmModel`（各 `String = ""`）を追加
2. コンストラクタに `private val llmSettingsRepository: LlmSettingsRepository` を追加注入
3. `uiState` を `map` から `combine(noteSettingsRepository.getSettings(), llmSettingsRepository.getSettings())` に変更
4. `updateLlmEndpointUrl()`/`updateLlmApiKey()`/`updateLlmModel()` を追加し、各々 `viewModelScope.launch(Dispatchers.IO)` で対応する save 系関数を呼ぶ

実装後、全10テストが成功することを確認する。

## Greenフェーズ（最小実装）

### 実装日時

2026-07-06

### 実装方針

Redフェーズ記録（`settings-viewmodel-llm-red-phase.md` §4）の実装案をそのまま採用し、最小実装で全テストを通した。
- `SettingsUiState` に `llmEndpointUrl`/`llmApiKey`/`llmModel`（各 `String = ""`）を追加
- `SettingsViewModel` コンストラクタに `llmSettingsRepository: LlmSettingsRepository` を追加注入
- `uiState` を `map` から `combine(noteSettingsRepository.getSettings(), llmSettingsRepository.getSettings())` に変更
- `updateLlmEndpointUrl()`/`updateLlmApiKey()`/`updateLlmModel()` を追加、各々 `viewModelScope.launch(Dispatchers.IO)` で対応するsave系を呼ぶ

仕様（要件定義書・テストケース定義書）と実装前コードとの差異はなし。

### 実装コード

`app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`（全文は `settings-viewmodel-llm-green-phase.md` 参照）

### 副次的な修正

`SettingsViewModel` の2引数コンストラクタ化により、`app/src/androidTest/java/com/den4dr/share2Obsidian/ui/SettingsScreenTest.kt` の1引数呼び出しがコンパイルエラーとなることが判明（Redフェーズドキュメント未記載・実装時に検出）。`FakeLlmSettingsRepository` を追加し、`createViewModel()` を2引数呼び出しに修正した。

### テスト結果

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*SettingsViewModelTest*"  # BUILD SUCCESSFUL（全10テスト成功）
mise exec -- ./gradlew test                                                 # BUILD SUCCESSFUL（既存テスト回帰なし）
mise exec -- ./gradlew compileDebugAndroidTestKotlin                        # BUILD SUCCESSFUL
```

### 課題・改善点（Refactorフェーズで対応）

- `updateLlmEndpointUrl`/`updateLlmApiKey`/`updateLlmModel` の3関数が `viewModelScope.launch(Dispatchers.IO) { llmSettingsRepository.saveXxx(...) }` という同一パターンを繰り返しており、共通化の余地がある。
- KDocの「信頼性レベル」記載の重複を簡潔化できる余地がある。

### 品質判定

高品質（テスト全成功・実装シンプル・ファイルサイズ112行・実装コードにモックなし）。次はRefactorフェーズへ進行可能。

## Refactorフェーズ（品質改善）

### 実施日時

2026-07-06

### 改善内容

Greenフェーズで指摘された課題に対応した（機能追加なし、挙動不変のリファクタリングのみ）。

- `updateVault`/`updateFolder`/`updateLlmEndpointUrl`/`updateLlmApiKey`/`updateLlmModel` の5関数に重複していた
  `viewModelScope.launch(Dispatchers.IO) { ... }` を private fun `launchOnIo(action: suspend () -> Unit)` ヘルパーへ抽出（DRY原則）。
  Repositoryに依存しない形で抽出したため、vault/folder（NoteSettingsRepository）とLLM設定（LlmSettingsRepository）という
  異なるRepository間でも無理なく共通化できた。
- クラス・関数のKDocを整理し、「改善内容」「設計方針」「保守性」の観点を明記。信頼性レベル記載の冗長な重複を解消。

### セキュリティレビュー結果

- APIキーはViewModel内で恒久保持されず、`launchOnIo`経由でRepositoryへ委譲されるのみ。暗号化保存の責務分界は変更なし。
- ログ出力等の追加なし。入力検証は要件上「本タスク範囲外」と明示済みのため対象外。
- 重大な脆弱性なし。

### パフォーマンスレビュー結果

- `launchOnIo`抽出による計算量・メモリ使用量の増加は実質なし（単純な委譲）。
- `combine()`のO(1)マッピングは変更なし。重大な性能課題なし。

### 最終コード

`app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`（全文は `settings-viewmodel-llm-refactor-phase.md` 参照、120行）

### テスト結果

```bash
mise exec -- ./gradlew :app:testDebugUnitTest --tests "*SettingsViewModelTest*" --info  # BUILD SUCCESSFUL（全10テスト成功）
mise exec -- ./gradlew test                                                              # BUILD SUCCESSFUL（回帰なし）
mise exec -- ./gradlew compileDebugAndroidTestKotlin                                     # BUILD SUCCESSFUL
mise exec -- ./gradlew lint                                                              # BUILD SUCCESSFUL（エラーなし）
```

2秒以上かかるテストなし。テスト無効化・除外設定なし。一時ファイルなし。

### 品質判定

高品質（テスト全成功・セキュリティ/性能課題なし・DRY改善達成・120行・KDoc改善済み）。次は完全性検証フェーズへ進行可能。

## 完全性検証フェーズ（tdd-verify-complete）

### 実施日時

2026-07-06

### テスト実行結果

```bash
mise exec -- ./gradlew test --rerun-tasks   # BUILD SUCCESSFUL
```

- `SettingsViewModelTest`（スコープ内）: 10/10 成功（0.183秒）
- 全体テストスイート: 248/248 成功、失敗0・エラー0（総実行時間 8.775秒、30秒未満のため速度改善提案は不要）
- 2秒以上かかるテストファイル: `MainActivityEditFlowTest`（4.156秒）のみ。スコープ外かつ既存の計装/統合系テストであり、本タスクの変更による影響ではない（参考記録のみ、対応不要）。
- コンパイルエラーなし。

### 要件・テストケース網羅性

- TASK-0066.md 完了条件5項目: 5/5 達成（コンストラクタ拡張・SettingsUiStateフィールド追加・combine()構築・update系3関数・`./gradlew test`成功）。
- testcases.md 定義の10テストケースID中 9/10 を独立したテストメソッドとして実装（TC-N-01〜05, TC-B-01〜03 の8件を専用メソッドで実装、TC-E-02は既存2テストの2引数移行で検証）。
- TC-E-01（save例外ハンドリング）は要件定義書 §4.4・テストケース定義書で「本タスク範囲外」と明示された意図的な未実装であり、欠陥ではない。

### 品質判定

✅ 高品質（完全達成）。要件網羅率100%、テスト成功率100%、未実装重要要件0件。TASK-0066は完了とし、元タスクファイルに完了マークを追記した。

## 💡 重要な技術学習

### 実装パターン
- 既存Repositoryパターン（vault/folder）を踏襲し、combine()で複数Flowを単一StateFlowに集約する手法はLLM設定にもそのまま拡張できた。
- update系関数の共通処理（viewModelScope.launch(Dispatchers.IO)）は`launchOnIo`ヘルパーへの抽出でDRY化でき、Repository横断の再利用が可能。

### テスト設計
- MockK + `relaxed = true` と `coEvery { ... } just Runs` の使い分けで、委譲経路のみを検証するテスト（coVerify）とuiState集約を検証するテスト（assertEquals on data class）を明確に分離できた。
- コンストラクタのシグネチャ変更（1引数→2引数）は既存テスト・計装テスト（SettingsScreenTest等）双方への影響確認が必須（Redフェーズ文書に未記載でも実装時に検出された）。

### 品質保証
- 要件定義書・テストケース定義書で「範囲外」と明示したエラーケースを実装しない判断（YAGNI）は、完了条件との整合性チェックで正当性を確認できた。
- リファクタリングは公開シグネチャ・挙動を変えない範囲に限定し、既存テストを無修正のまま全通過させることで回帰がないことを担保した。

# TDD開発メモ: template-edit-llm-ui

## 概要

- 機能名: TemplateEditViewModel/TemplateEditScreen/FieldAddDialog へのLLM UI追加（llm-memo-rewrite / TASK-0071）
- 開発開始: 2026-07-09
- 現在のフェーズ: Verify-complete（完全性検証）完了

## 関連ファイル

- 元タスクファイル: `docs/tasks/llm-memo-rewrite/TASK-0071.md`
- 要件定義: `docs/implements/llm-memo-rewrite/TASK-0071/template-edit-llm-ui-requirements.md`
- テストケース定義: `docs/implements/llm-memo-rewrite/TASK-0071/template-edit-llm-ui-testcases.md`
- Redフェーズ記録: `docs/implements/llm-memo-rewrite/TASK-0071/template-edit-llm-ui-red-phase.md`
- Greenフェーズ記録: `docs/implements/llm-memo-rewrite/TASK-0071/template-edit-llm-ui-green-phase.md`（tdd-verify-complete実行時に事後補完）
- Refactorフェーズ記録: `docs/implements/llm-memo-rewrite/TASK-0071/template-edit-llm-ui-refactor-phase.md`
- 実装ファイル（Greenフェーズで変更予定）:
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModel.kt`
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/template/TemplateEditScreen.kt`
  - `app/src/main/res/values/strings.xml`
- テストファイル（Redフェーズで追記済み）:
  - `app/src/test/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModelTest.kt`
  - `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/template/TemplateEditScreenTest.kt`

## Redフェーズ（失敗するテスト作成）

### 作成日時

2026-07-09

### テストケース

`template-edit-llm-ui-testcases.md` の全12ケース（正常系6・異常系3・境界値3）を実装した。

- 単体テスト（8件）: `TemplateEditViewModelTest.kt` に追記
  - `updateBodyLlmPrompt_updatesUiState`（TC-N-01）
  - `save_reflectsBodyLlmPromptAndFieldLlmPrompt`（TC-N-02）
  - `loadTemplate_restoresBodyLlmPromptAndFieldLlmPrompt`（TC-N-03）
  - `loadTemplate_missingTemplate_bodyLlmPromptStaysDefault`（TC-E-01）
  - `save_withBodyLlmPrompt_stillSetsIsSavedRegression`（TC-E-03）
  - `save_bodyLlmPromptEmptyByDefault_savesEmptyString`（TC-B-01）
  - `save_llmFieldWithEmptyPrompt_preservesEmptyLlmPrompt`（TC-B-02）
  - `save_multipleFieldsWithLlmMixed_preservesLlmPromptMapping`（TC-B-03）
- 統合テスト（4件）: `TemplateEditScreenTest.kt` に追記
  - `llmSource_showsLlmPromptField`（TC-N-04 / 受け入れ基準 TC-104-01）
  - `bodyLlmPromptInput_updatesViewModel`（TC-N-05）
  - `saveAndReload_restoresBodyLlmPromptAndFieldLlmPrompt`（TC-N-06、`PersistingFakeEditRepository` を新規追加）
  - `nonLlmSource_discardsLlmPrompt`（TC-E-02）

### テストコード

全文は `template-edit-llm-ui-red-phase.md` を参照。要点は以下。

- 単体テストは既存 `TemplateEditViewModelTest.kt` の `createViewModel()` ヘルパーとモック `repository`（MockK）を再利用し、`updateBodyLlmPrompt()` / `TemplateFieldEditState(llmPrompt = ...)` / `Template.bodyLlmPrompt` / `TemplateField.llmPrompt` を参照する形で記述（いずれも本タスクで新規追加予定のAPI）。
- 統合テストは既存 `TemplateEditScreenTest.kt` の `createViewModel()` / `FakeEditRepository` パターンを踏襲しつつ、往復検証用に保存内容を保持する `PersistingFakeEditRepository` を新設。`currentTemplateId`（`mutableStateOf`）の変更で `TemplateEditScreen` の `LaunchedEffect(templateId)` を経由した再読込を誘発する構成にした。

### 期待される失敗

`TemplateEditUiState.bodyLlmPrompt` / `TemplateFieldEditState.llmPrompt` / `TemplateEditViewModel.updateBodyLlmPrompt()` が未実装のため、**コンパイルエラー**で失敗することを確認した（TASK-0068 の前例と同様のRedパターン）。

- 単体テスト: `mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.template.TemplateEditViewModelTest"` → `compileDebugUnitTestKotlin FAILED`（Unresolved reference 'updateBodyLlmPrompt' / 'bodyLlmPrompt' / No parameter with name 'llmPrompt' 等、計10箇所）
- 統合テスト: `mise exec -- ./gradlew compileDebugAndroidTestKotlin` → `compileDebugAndroidTestKotlin FAILED`（Unresolved reference 'bodyLlmPrompt' / 'llmPrompt'、計5箇所）

統合テストはコンパイル段階で失敗するため、実機/エミュレータ（`emulator-5554`）での実行確認は Green フェーズの実装完了後に行う。

### 次のフェーズへの要求事項

Greenフェーズで実装すべき内容（詳細は `template-edit-llm-ui-red-phase.md` §5）:

1. `TemplateEditViewModel.kt`:
   - `TemplateEditUiState.bodyLlmPrompt: String = ""` 追加
   - `TemplateFieldEditState.llmPrompt: String = ""` 追加
   - `updateBodyLlmPrompt(prompt: String)` 追加
   - `loadTemplate()` / `save()` に両プロンプトの往復マッピングを追加
2. `TemplateEditScreen.kt`:
   - 本文用LLMプロンプト入力欄を `template_body_field` の下に追加
   - `FieldAddDialog` の値取得方法リストに「LLM」を追加、選択時にプロンプト入力欄を表示
   - `onAdd` で非LLM選択時に `llmPrompt = ""` を強制する分岐を実装（TC-E-02）
3. `strings.xml`: `field_source_llm` / `field_llm_prompt_label` / `template_body_llm_prompt_label` を追加

実装後、以下がすべて成功することがGreenフェーズのゴール。

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.template.TemplateEditViewModelTest"
mise exec -- ./gradlew connectedAndroidTest --tests "com.den4dr.share2Obsidian.ui.template.TemplateEditScreenTest"
```

## Greenフェーズ（最小実装）

Greenフェーズの記録ファイル（`template-edit-llm-ui-green-phase.md`）は本セッション開始時点で未作成だったが、実装ファイル（`TemplateEditViewModel.kt`/`TemplateEditScreen.kt`/`strings.xml`）には対応するLLM UI実装が既に反映されており、Redフェーズで追加した単体テスト8件・統合テスト4件がいずれも成功する状態だった。Refactorフェーズ開始時にこの状態を確認し、既存実装をGreenフェーズ成果物とみなしてRefactorを実施した。

**2026-07-09追記**: `tdd-verify-complete`実行時に記録欠落を検知し、実装コードとテスト結果を突合して`template-edit-llm-ui-green-phase.md`を事後補完した（実装方針・実装コード抜粋・テスト結果・品質判定を記載。一次記録ではなく事後補完である旨を明記）。

## Refactorフェーズ（品質改善）

### 実施日時

2026-07-09

### リファクタ前のテスト確認（Taskツール経由）

- 単体テスト（TemplateEditViewModelTest）: 14 tests / 0 failures
- 統合テスト（TemplateEditScreenTest, emulator-5554）: 11 tests / 0 failures
- プロジェクト全体単体テスト: 271 tests / 0 failures
- 2秒以上かかる統合テスト6件を検出（Compose初期描画コストが主因、アルゴリズム上の問題ではないと判断）
- テスト除外・一時ファイルの問題なし

### セキュリティレビュー結果

外部入力を直接SQL/URI/HTMLへ埋め込む処理はなく、永続化はRoom経由のパラメータバインディング。重大な脆弱性なし。

### パフォーマンスレビュー結果

`FieldAddDialog` の値取得方法選択肢リストが、テキスト入力による再コンポーズのたびに `stringResource()` 呼び出し込みで再構築されていた点を軽微な改善対象として特定。それ以外にアルゴリズム上のボトルネックは検出されず。

### 実施した改善

`TemplateEditScreen.kt` の `FieldAddDialog` 内、値取得方法選択肢リスト（`FieldValueSource` → ラベルの `Pair` リスト）を `remember` 化し、再コンポーズごとの不要な再生成を回避した。選択肢の内容・順序（FIXED → HTML_META → URL → EMPTY → LLM）は変更なし。詳細・Before/Afterコードは `template-edit-llm-ui-refactor-phase.md` を参照。

### リファクタ後のテスト確認（Taskツール経由、emulator-5554実機再確認込み）

- 単体テスト（TemplateEditViewModelTest）: 14 tests / 0 failures
- 統合テスト（TemplateEditScreenTest, emulator-5554）: 11 tests / 0 failures
- プロジェクト全体単体テスト: 271 tests / 0 failures
- リファクタ前後で件数・結果に差異なし（回帰なし）

### 品質評価

✅ 高品質（テスト全件成功、重大なセキュリティ/パフォーマンス課題なし、ファイルサイズ制限内、日本語コメント整備済み）

## Verify-completeフェーズ（完全性検証）

### 実施日時

2026-07-09

### 検証結果（@task経由、emulator-5554実機込み）

- 単体テスト（TemplateEditViewModelTest、スコープ内）: 14 tests / 0 failures（既存6＋TASK-0071新規8）
- プロジェクト全体単体テスト（スコープ外含む）: 271 tests / 0 failures
- 統合テスト（TemplateEditScreenTest、emulator-5554実機、スコープ内）: 11 tests / 0 failures（既存7＋TASK-0071新規4）
- テストケース定義書（12ケース: 正常系6・異常系3・境界値3）を全件実装・全件成功で確認
- 元タスクファイルの完了条件7項目をすべて実装コードで確認（`TemplateEditViewModel.kt`/`TemplateEditScreen.kt`/`strings.xml`）
- 2秒以上の遅いテスト: 統合テスト側で最大4.019秒（`newMode_showsCreateTitle`等）。Compose初期描画コストが主因でTASK-0071固有の性能課題ではないとRefactorフェーズで判断済み

### 記録欠落の補完

- `template-edit-llm-ui-green-phase.md`が未作成だったため、実装コードとテスト結果を突合して事後補完した（実装当時の一次記録ではない旨を明記）

### 最終品質判定

✅ **高品質（完全達成）** — 要件網羅率100%（完了条件7/7）、テスト成功率100%（スコープ内14+11、スコープ外含む全体271）、未実装重要要件0件。元タスクファイル（`TASK-0071.md`）・`overview.md`の完了マークを更新済み。

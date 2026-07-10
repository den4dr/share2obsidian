# TDD開発メモ: edit-custom-field-llm-generation

## 概要

- 機能名: EditScreenViewModel `generateCustomFieldValue()`・EditScreen UI「生成」ボタン追加
- 開発開始: 2026-07-09
- 現在のフェーズ: 完了（Red → Green → Refactor → 品質確認済み）

## 関連ファイル

- 元タスクファイル: `docs/tasks/llm-memo-rewrite/TASK-0072.md`
- 要件定義: `docs/implements/llm-memo-rewrite/TASK-0072/edit-custom-field-llm-generation-requirements.md`
- テストケース定義: `docs/implements/llm-memo-rewrite/TASK-0072/edit-custom-field-llm-generation-testcases.md`
- Redフェーズ記録: `docs/implements/llm-memo-rewrite/TASK-0072/edit-custom-field-llm-generation-red-phase.md`
- 実装ファイル（Greenフェーズで追加予定）:
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt`
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`（別途 UI 実装）
- テストファイル: `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelGenerateCustomFieldValueTest.kt`

## Redフェーズ（失敗するテスト作成）

### 作成日時

2026-07-09

### テストケース

`EditScreenViewModelGenerateCustomFieldValueTest.kt` に ViewModel 単体テスト13件（正常系4・異常系5・境界値4）を実装した。

- 正常系: TC-0072-N01（対象インデックスのみ更新・他フィールド不変）、N02（sourceContent を入力とし formState.body を使わない）、N03（対象 index の llmPrompt を使用）、N04（generatingFieldIndex による個別ローディング状態遷移）
- 異常系: TC-0072-E01〜E05（`LlmRewriteResult.Failure` の全5種別で value 不変・対応する messageResId を1件 emit）
- 境界値: TC-0072-B01（index=0）、B02（index=末尾）、B03（Success("") で空文字更新・エラー扱いにしない）、B04（sourceContent 空文字でもガードしない）

UI 統合テスト（`valueSource==LLM` のみボタン表示等）はテストケース定義書で「本タスクの単体テスト範囲外・別途実装」と明記されているため、本 Red フェーズの対象外とした。

### テストコード

`app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelGenerateCustomFieldValueTest.kt` を参照（全文保存済み）。既存 `EditScreenViewModelRewriteBodyTest.kt` のテスト戦略（MockK コンストラクタ注入、`StandardTestDispatcher` 共有、`collectErrorEvents` ヘルパー）をそのまま踏襲。

### 期待される失敗

`EditScreenViewModel.generateCustomFieldValue(index: Int)` メソッドと `EditFormState.generatingFieldIndex` プロパティが未実装のため、`compileDebugUnitTestKotlin` タスクが `Unresolved reference` のコンパイルエラーで失敗する（15箇所）。実行コマンド:

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelGenerateCustomFieldValueTest"
```

### 次のフェーズへの要求事項

1. `EditFormState` に `generatingFieldIndex: Int? = null` を追加する。
2. `EditScreenViewModel` に `generateCustomFieldValue(index: Int)` を追加し、以下の流れを実装する:
   - `viewModelScope.launch` 内で `formState.value.customFields[index].llmPrompt` を取得
   - `generatingFieldIndex = index` に更新（ローディング開始）
   - `llmSettingsRepository.getSettings().first()` で設定取得
   - `llmRewriteRepository.rewrite(settings, prompt, sourceContent)` を呼び出し（`sourceContent` は空文字でもガードしない）
   - 成功時: 既存 `updateCustomField(index, result.text)` を再利用
   - 失敗時: 対象 value は変更せず `_errorEvents.emit(result.messageResId)`
   - 最後に `generatingFieldIndex = null` に戻す
3. 全13件のテストが成功することを Green フェーズのゴールとする。
4. EditScreen への「生成」ボタン UI 実装（`valueSource == LLM` の場合のみ表示）は本タスクの範囲内だが、単体テストでは検証しないため、Green/Refactor フェーズで実装のみ行う（別途 Compose UI Test は対象外・参考項目）。

## Greenフェーズ（最小実装）

### 実装日時

2026-07-09〜2026-07-10

### 実装方針

- `EditFormState` に `generatingFieldIndex: Int? = null` を追加（フィールド単位のローディング状態）。
- `EditScreenViewModel.generateCustomFieldValue(index: Int)` を追加。入力は常に `sourceContent`（REQ-002/REQ-406）、空文字ガードなし（EDGE-101）、成功時は既存 `updateCustomField(index, value)` 再利用、失敗時は `errorEvents` に `messageResId` を emit。
- `EditScreen.kt` のカスタムフィールドループに `field.valueSource == FieldValueSource.LLM` の場合のみ「生成」`IconButton` を表示（生成中は該当フィールドのみ `CircularProgressIndicator`、`isGenerating = formState.generatingFieldIndex == index`）。

### テスト結果

- `EditScreenViewModelGenerateCustomFieldValueTest`: 13/13 成功
- 全ユニットテストスイート: 284件成功・失敗0
- 実機（emulator-5554）の既存 `EditScreenTest` 32件成功（回帰なし）

詳細は `edit-custom-field-llm-generation-green-phase.md` 参照。

### 課題・改善点

- `generateCustomFieldValue()` が `runLlmRequest()` を再利用せず、設定取得・成功/失敗分岐が重複していた → Refactorフェーズで解消。

## Refactorフェーズ（品質改善）

### リファクタ日時

2026-07-10

### 改善内容

- `generateCustomFieldValue()` を `runLlmRequest()` 共通ヘルパー再利用に統一。`generatingFieldIndex: Int?` と `setLoading: (Boolean) -> Unit` の型の違いは、`index` を捕捉したクロージャ（`if (loading) index else null`）で吸収した。設定取得・LLM呼び出し・成功/失敗分岐・ローディング終了の重複コード（約20行）を削減。
- `runLlmRequest()` の KDoc を3呼び出し元（rewriteBody/suggestTags/generateCustomFieldValue）対応に更新。

### セキュリティレビュー

- 変更は内部リファクタのみ。APIキー等の取り扱いに変更なし。入力は引き続き `sourceContent` のみで、ユーザー編集後の本文が LLM に送信されることはない（REQ-406 維持）。

### パフォーマンスレビュー

- 動作は等価（クロージャ1個分のアロケーション増のみで無視できる）。ローディング状態遷移（index 設定→null 復帰）はテスト TC-0072-N04 で検証済み。

### 最終テスト結果

- リファクタ後の全ユニットテストスイート: **284件成功・失敗0・エラー0**（`EditScreenViewModelGenerateCustomFieldValueTest` 13/13 含む）

### 品質判定

- ✅ テスト: 全284件成功（回帰なし）
- ✅ 重複コード解消: LLM呼び出し3系統すべてが `runLlmRequest()` に集約
- ✅ ファイルサイズ: 800行制限内
- ✅ モック混入なし（実装コード）

**総合判定**: ✅ 高品質 — TASK-0072 完了

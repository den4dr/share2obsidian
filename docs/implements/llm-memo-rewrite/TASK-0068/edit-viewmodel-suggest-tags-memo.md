# TDD開発メモ: edit-viewmodel-suggest-tags

## 概要

- 機能名: EditScreenViewModel suggestTags()実装
- 開発開始: 2026-07-08
- 現在のフェーズ: 完了（Red→Green→Refactor）

## 関連ファイル

- 元タスクファイル: `docs/tasks/llm-memo-rewrite/TASK-0068.md`
- 要件定義: `docs/implements/llm-memo-rewrite/TASK-0068/edit-viewmodel-suggest-tags-requirements.md`
- テストケース定義: `docs/implements/llm-memo-rewrite/TASK-0068/edit-viewmodel-suggest-tags-testcases.md`
- 実装ファイル（予定）:
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt`
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`
  - `app/src/main/res/values/strings.xml`
- テストファイル: `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelSuggestTagsTest.kt`

## Redフェーズ（失敗するテスト作成）

### 作成日時

2026-07-08

### テストケース

`edit-viewmodel-suggest-tags-testcases.md` の全7ケース（必須5・補完2）を実装。

- TC-0068-N01: 成功時に既存 tagsText へ追加連結
- TC-0068-N02: 既存 tagsText 空文字なら生成結果のみ設定
- TC-0068-N03（補完）: 入力ソースが sourceContent（body/tagsText 編集の影響を受けない）
- TC-0068-E01: 失敗時 tagsText 不変・errorEvents 発行
- TC-0068-B01: isSuggestingTags の true→false 状態遷移
- TC-0068-B02: sourceContent 空文字でもガードされず1回呼ばれる
- TC-0068-B03（補完）: 成功結果が空文字の場合のマージ挙動

### テストコード

`app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelSuggestTagsTest.kt` 参照（全文保存済み）。
TASK-0063 の `EditScreenViewModelRewriteBodyTest.kt` のテストパターン（MockK・共有 StandardTestDispatcher・errorEvents 購読ヘルパー・CompletableDeferred によるローディング中間観測）を踏襲。

### 期待される失敗

`suggestTags()` メソッドおよび `EditFormState.isSuggestingTags` プロパティが未実装のため、`compileDebugUnitTestKotlin` タスクでコンパイルエラーとなる（`Unresolved reference` 9件）。

実行コマンド:
```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelSuggestTagsTest"
```

### 次のフェーズへの要求事項

Greenフェーズでは以下を最小実装する：

1. `EditFormState` に `isSuggestingTags: Boolean = false` を追加
2. `strings.xml` に `llm_tag_suggestion_prompt` を追加
3. `EditScreenViewModel` に `TAG_SUGGESTION_PROMPT` プロパティと `suggestTags()` メソッドを追加
   - `rewriteBody()` と同一の非同期パターン（`viewModelScope.launch` + ローディングフラグ + `when` 分岐 + `errorEvents.emit`）
   - 成功時のタグマージ式: `if (current.isBlank()) result.text else "$current, ${result.text}"`
   - `sourceContent` の空文字ガードは行わない（EDGE-101）

詳細は `edit-viewmodel-suggest-tags-red-phase.md` の「Greenフェーズで実装すべき内容」参照。

## Greenフェーズ（最小実装）

### 実装日時

2026-07-08

### 実装方針

`rewriteBody()`（TASK-0063）と同一の非同期処理パターンをそのまま踏襲した。

- `EditFormState` に `isSuggestingTags: Boolean = false` を追加
- `EditScreenViewModel` に `suggestTags()` を追加：`isSuggestingTags=true` → `getSettings().first()` → `rewrite(settings, TAG_SUGGESTION_PROMPT, sourceContent)` → 成功時は `current.isBlank()` で分岐してタグをマージ／失敗時は `errorEvents.emit()` → `isSuggestingTags=false`
- `TAG_SUGGESTION_PROMPT` は `companion object` の `private const val` として実装（Redフェーズのテストが `EditScreenViewModel(mockRewrite, mockSettings)` と2引数コンストラクタのみを呼んでおり `Context` を注入していないため、`Context.getString()` 方式は採用不可と判断）
- `strings.xml` に同一文言の `llm_tag_suggestion_prompt` を追加（TASK-0068.md の実装対象ファイルとして明記されているため。現時点ではViewModelから未参照）

### 実装コード

`edit-viewmodel-suggest-tags-green-phase.md` に全文保存済み。

### テスト結果

`EditScreenViewModelSuggestTagsTest` 全7ケース成功（tests=7, failures=0, errors=0）。`ui` パッケージ配下の既存テスト（`EditScreenViewModelRewriteBodyTest` 等）も含めて回帰なしを確認。

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelSuggestTagsTest"
# BUILD SUCCESSFUL
```

### 課題・改善点（Refactorフェーズへ）

1. `rewriteBody()` と `suggestTags()` のローディング・設定取得の枠組みが重複している（共通化候補）
2. `TAG_SUGGESTION_PROMPT`（Kotlin定数）と `llm_tag_suggestion_prompt`（string resource）が二重管理になっている
3. `Success("")` 時に `tagsText` 末尾へ不要なスペースが残る挙動（`"private, "`）の要否検討

## Refactorフェーズ（品質改善）

### リファクタリング日時

2026-07-08

### 改善内容

`rewriteBody()` と `suggestTags()` の重複ロジック（ローディング開始→設定取得→LLM呼び出し→成功/失敗分岐→ローディング終了）を `private suspend fun runLlmRequest(prompt, setLoading, onSuccess)` へ機械的に抽出（DRY原則、振る舞い変更なし）。`rewriteBody()`・`suggestTags()` はそれぞれプロンプト・ローディングフラグの反映方法・成功時の状態更新方法のみを渡す形に単純化した。

見送った改善候補（将来タスクへ申し送り）:
- `TAG_SUGGESTION_PROMPT`（Kotlin定数）と `llm_tag_suggestion_prompt`（string resource）の二重管理解消（`Context` 注入方式の確定が前提の機能的変更のためRefactor対象外）
- `Success("")` 時の `tagsText` 末尾スペース挙動（TC-0068-B03 で明示的に期待される仕様のため対象外）

### セキュリティレビュー結果

重大な脆弱性なし。既存 `rewriteBody()` と同一の認可・入力経路を使用し、機微情報はログ・例外メッセージへ出力しない（NFR-102準拠）。

### パフォーマンスレビュー結果

重大な性能課題なし。`tagsText` マージは O(n) の文字列結合のみ。単体クラス実行時に `TC-0068-B02` が3秒台かかる事象を検出したが、`ui` パッケージ全体で実行すると7ケース合計0.167秒であり、JVM/Robolectricの初回クラスロードコストと特定（実装起因ではない）。

### 最終コード

`edit-viewmodel-suggest-tags-refactor-phase.md` に全文（Before/After）を保存済み。

### テスト結果

`EditScreenViewModelSuggestTagsTest`（7件）・`EditScreenViewModelRewriteBodyTest`（14件）・`EditScreenViewModelTest`（19件）・`EditScreenViewModelInitializeTest`（8件）全て継続成功。プロジェクト全体 `mise exec -- ./gradlew test` および `mise exec -- ./gradlew lint` も `BUILD SUCCESSFUL`。

### 品質評価

高品質（テスト全継続成功・セキュリティ/パフォーマンス問題なし・DRY原則適用によりコード品質向上・ファイルサイズ約357行で500行制限内）

## 検証フェーズ（完全性確認）

### 実施日時

2026-07-08

### テスト状態

- `mise exec -- ./gradlew test --rerun-tasks` を実行し、プロジェクト全体で **255テスト全成功**（失敗0、エラー0）、総実行時間 8.55秒（30秒未満）
- スコープ内（`EditScreenViewModelSuggestTagsTest`）: 7/7成功（0.123秒）
- スコープ外失敗: なし
- 遅いテストファイル（2秒以上）: `MainActivityEditFlowTest`（4.1秒、TASK-0068スコープ外の既存テスト）。総実行時間が30秒未満のため対応不要と判断

### 実装率・要件網羅率

- テストケース実装率: 7/7 = 100%（必須5・補完2すべて実装済み）
- 要件網羅率: 100%（REQ-103, REQ-201, REQ-301, REQ-302, REQ-406, NFR-102, NFR-201, EDGE-004, EDGE-101 すべて対応）
- 未実装重要要件: 0個

### 🎯 最終結果 (2026-07-08)
- **実装率**: 100% (7/7テストケース)
- **品質判定**: 合格（高品質・完全達成）
- **TODO更新**: ✅完了マーク追加

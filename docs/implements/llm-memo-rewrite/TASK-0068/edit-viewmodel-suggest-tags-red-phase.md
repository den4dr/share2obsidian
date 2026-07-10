# TDD Redフェーズ - TASK-0068: EditScreenViewModel suggestTags()実装

- **機能名**: edit-viewmodel-suggest-tags（EditScreenViewModel タグ提案）
- **タスクID**: TASK-0068
- **要件名**: llm-memo-rewrite
- **作成日**: 2026-07-08
- **テストファイル**: `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelSuggestTagsTest.kt`

## 1. 作成したテストケース一覧

`docs/implements/llm-memo-rewrite/TASK-0068/edit-viewmodel-suggest-tags-testcases.md` に定義された全7ケース（必須5＋補完2）をすべて実装した。

| TC ID | カテゴリ | テスト内容 | 信頼性 |
|-------|---------|-----------|--------|
| TC-0068-N01 | 正常系 | suggestTags 成功時に既存 tagsText へ生成タグが追加連結される | 🔵 REQ-302 |
| TC-0068-N02 | 正常系 | 既存 tagsText が空文字の場合は生成結果のみが設定される | 🔵 完了条件 |
| TC-0068-N03（補完） | 正常系 | suggestTags は sourceContent を入力とし body/tagsText を入力にしない | 🟡 REQ-406/REQ-302 |
| TC-0068-E01 | 異常系 | suggestTags 失敗時 tagsText 不変で messageResId が emit される | 🔵 EDGE-004/NFR-201 |
| TC-0068-B01 | 境界値 | suggestTags 実行中は isSuggestingTags が true になり完了後 false に戻る | 🔵 REQ-201 |
| TC-0068-B02 | 境界値 | sourceContent が空文字でも suggestTags がガードされず rewrite が1回呼ばれる | 🔵 EDGE-101 |
| TC-0068-B03（補完） | 境界値 | suggestTags 成功結果が空文字の場合のマージ結果を確認する | 🟡 REQ-302境界 |

## 2. テストコード

`app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelSuggestTagsTest.kt` に全文を保存済み。

構成は TASK-0063 の `EditScreenViewModelRewriteBodyTest.kt` を踏襲：
- MockK で `LlmRewriteRepository` / `LlmSettingsRepository` をスタブ化し、Hilt を起動せずコンストラクタ注入
- `StandardTestDispatcher` を `Dispatchers.setMain()` と `runTest(testDispatcher)` の両方に共有
- `errorEvents`（`SharedFlow<Int>`）は `suggestTags()` 呼び出し前に通常の `launch` で購読し、検証後に `job.cancel()`
- ローディング状態の中間観測は `CompletableDeferred` で `rewrite()` の完了を外部制御

## 3. テスト実行コマンド

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelSuggestTagsTest"
```

## 4. 実際の失敗結果（確認済み）

`suggestTags()` メソッドと `EditFormState.isSuggestingTags` プロパティが未実装のため、**コンパイルエラー**で失敗することを確認した。

```
e: EditScreenViewModelSuggestTagsTest.kt:132:19 Unresolved reference 'suggestTags'.
e: EditScreenViewModelSuggestTagsTest.kt:166:19 Unresolved reference 'suggestTags'.
e: EditScreenViewModelSuggestTagsTest.kt:200:19 Unresolved reference 'suggestTags'.
e: EditScreenViewModelSuggestTagsTest.kt:242:19 Unresolved reference 'suggestTags'.
e: EditScreenViewModelSuggestTagsTest.kt:284:19 Unresolved reference 'suggestTags'.
e: EditScreenViewModelSuggestTagsTest.kt:290:39 Unresolved reference 'isSuggestingTags'.
e: EditScreenViewModelSuggestTagsTest.kt:300:39 Unresolved reference 'isSuggestingTags'.
e: EditScreenViewModelSuggestTagsTest.kt:323:19 Unresolved reference 'suggestTags'.
e: EditScreenViewModelSuggestTagsTest.kt:356:19 Unresolved reference 'suggestTags'.

> Task :app:compileDebugUnitTestKotlin FAILED
```

すべて「未実装の関数・プロパティ呼び出し」に起因する失敗であり、想定通りの Red 状態である。

## 5. Greenフェーズで実装すべき内容

TASK-0068.md「実装詳細」に基づき、以下をそのまま実装対象とする：

1. **`EditFormState.kt`**: `isSuggestingTags: Boolean = false` を追加（`isRewritingBody` と同パターン）。
2. **`strings.xml`**: `<string name="llm_tag_suggestion_prompt">…</string>` を追加（アプリ内固定プロンプト）。
3. **`EditScreenViewModel.kt`**:
   - `TAG_SUGGESTION_PROMPT` プロパティ追加（`strings.xml` から取得。Context 注入方式は Green フェーズで確定する）。
   - `suggestTags()` メソッド追加:
     - `isSuggestingTags = true` に更新
     - `llmSettingsRepository.getSettings().first()` で設定取得
     - `llmRewriteRepository.rewrite(settings, TAG_SUGGESTION_PROMPT, sourceContent)` を呼び出し（`sourceContent` は空文字でもガードしない：EDGE-101）
     - `Success`: `current.isBlank()` なら `result.text` のみ、そうでなければ `"$current, ${result.text}"` で `tagsText` を更新
     - `Failure`: `tagsText` を変更せず `_errorEvents.emit(result.messageResId)`
     - 最後に `isSuggestingTags = false` に戻す

実装後、`mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelSuggestTagsTest"` が全件成功することを Green フェーズのゴールとする。

## 6. 品質判定

- ✅ テスト実行: 実行可能でコンパイルエラーにより失敗することを確認済み
- ✅ 期待値: 明確で具体的（tagsText の厳密文字列・errorEvents の resId・呼び出し回数）
- ✅ アサーション: 適切（既存テストパターン踏襲）
- ✅ 実装方針: 明確（TASK-0068.md 実装詳細と1:1対応）
- 信頼性レベル分布: 🔵 5件（必須ケース）、🟡 2件（補完ケース）、🔴 0件

**総合判定**: 高品質

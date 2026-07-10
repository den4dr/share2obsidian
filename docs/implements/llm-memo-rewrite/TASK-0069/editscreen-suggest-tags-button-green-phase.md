# TASK-0069 Greenフェーズ記録: EditScreen「タグを提案」ボタンUI追加

**機能名**: editscreen-suggest-tags-button
**タスクID**: TASK-0069
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-08

---

## 1. 実装方針

- Redフェーズで失敗していた6件（TC-11, TC-12, TC-13, TC-14, BC-11, BC-12）を通す最小実装を行った。
- `EditFormState.isSuggestingTags` と `EditScreenViewModel.suggestTags()` / `errorEvents` は TASK-0068/TASK-0065 で実装済みのため変更不要（要件定義書・Redフェーズ記録の通り）。
- 変更が必要だったのは以下の2ファイルのみ。
  1. `app/src/main/res/values/strings.xml` — `button_suggest_tags`（「タグを提案」）を新規追加
  2. `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt` — タグ入力欄直下に「タグを提案」`Button` を追加
- 実装前に要件定義（`editscreen-suggest-tags-button-requirements.md`）・テストケース（`-testcases.md`）・現在の実装コードを照合し、差異は発見されなかった（strings.xml 未追加のみが既知のギャップとして事前に文書化済みだった）。

---

## 2. 実装コード

### 2.1 `app/src/main/res/values/strings.xml`（差分）

```xml
<!-- LLM書き換え機能: ボタンラベル -->
<string name="button_rewrite_body">メモを更改</string>
<!-- LLMタグ提案機能: ボタンラベル（TASK-0069） -->
<string name="button_suggest_tags">タグを提案</string>
```

- 🔵 信頼性レベル: requirements.md §6・testcases.md 5.1-1（`button_suggest_tags`=「タグを提案」、未追加を確認済みとの記載）より

### 2.2 `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`（差分、タグ入力欄直下に追加）

```kotlin
OutlinedTextField(
    value = formState.tagsText,
    onValueChange = { viewModel.updateTagsText(it) },
    label = { Text(stringResource(R.string.label_tags)) },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
)
// 【機能概要】: 「タグを提案」ボタン。押下で viewModel.suggestTags() を呼び出し、
// 元コンテンツ（ProcessedContent）を入力に LLM がタグ候補を生成し既存タグへ追加する
// 【実装方針】: 「メモを更改」ボタン（rewrite_body_button）と同一のローディング表示パターンを踏襲する。
// ただし活性条件は isSuggestingTags のみ（rewriteBodyEnabled 相当のガードは持たない、EDGE-101）
// 【テスト対応】: TC-11, TC-12, TC-13, BC-11, BC-12（editscreen-suggest-tags-button-testcases.md）
// 🔵 信頼性レベル: REQ-103, REQ-201, NFR-202, EDGE-101 より
Button(
    onClick = { viewModel.suggestTags() },
    enabled = !formState.isSuggestingTags,
    modifier = Modifier
        .fillMaxWidth()
        .testTag("suggest_tags_button"),
) {
    if (formState.isSuggestingTags) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(16.dp)
                .testTag("suggest_tags_progress"),
            strokeWidth = 2.dp,
        )
    } else {
        Text(stringResource(R.string.button_suggest_tags))
    }
}
formState.customFields.forEachIndexed { index, field -> ... }
```

- 活性式は `!formState.isSuggestingTags` のみとし、`rewriteBodyEnabled` 等の他フラグを参照しない（BC-11 で保証、要件定義書§3「ボタン活性化制約（重要）」より）。
- `errorEvents` 購読・Toast表示は EditScreen 冒頭の既存 `LaunchedEffect` がそのまま `suggestTags()` 失敗も処理するため追加実装なし（TC-14 で非クラッシュを確認）。
- 🔵 信頼性レベル: Redフェーズ記録「4. Greenフェーズで実装すべき内容」・requirements.md §3・testcases.md TC-11〜BC-12 より

---

## 3. テスト実行結果

### 3.1 Instrumented Test（実機・エミュレータ）

```bash
mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.den4dr.share2Obsidian.ui.EditScreenTest
```

- 実行環境: エミュレータ `Medium_Phone_API_36.1(AVD)`（`emulator-5554`）
- 結果: `Finished 32 tests on Medium_Phone_API_36.1(AVD) - 16` — **32件中32件成功（0 failed, 0 skipped）**
  - 新規6件（TC-11, TC-12, TC-13, TC-14, BC-11, BC-12）すべて成功
  - 既存26件も回帰なく全件成功
- `BUILD SUCCESSFUL`

### 3.2 ユニットテスト全体（回帰確認）

```bash
mise exec -- ./gradlew test
```

- `BUILD SUCCESSFUL`（全件成功）

### 3.3 ビルド・Lint確認

```bash
mise exec -- ./gradlew assembleDebug   # BUILD SUCCESSFUL
mise exec -- ./gradlew lint            # BUILD SUCCESSFUL（警告レベルエラーなし）
```

---

## 4. 品質判定

```
✅ 高品質
- テスト結果: Taskツール（実機エミュレータ）による実行で全て成功（32/32、回帰なし）
- 実装品質: シンプルかつ動作する（既存 rewrite_body_button パターンを踏襲）
- リファクタ箇所: rewrite_body_button と suggest_tags_button の
  CircularProgressIndicator/Text 分岐ロジックが重複しており、
  共通 Composable（例: LoadingButton）への抽出が可能
- 機能的問題: なし
- コンパイルエラー: なし
- ファイルサイズ: EditScreen.kt 約230行（800行制限内、分割不要）
- モック使用: 実装コード（strings.xml, EditScreen.kt）にモック・スタブは含まれていない
```

---

## 5. 課題・改善点（Refactorフェーズで対応）

1. **ローディングボタンパターンの重複**: `rewrite_body_button` と `suggest_tags_button` で
   `if (isX) CircularProgressIndicator(...) else Text(...)` という同一構造が2箇所に存在する。
   共通の `LoadingButton` Composable（ラベル・進捗testTag・enabled・onClick を引数化）への抽出を検討。
2. **testTag 命名の一貫性**: `rewrite_body_button`/`rewrite_body_progress` と
   `suggest_tags_button`/`suggest_tags_progress` は命名パターンが揃っており、将来のボタン追加時も
   同一命名規則（`{feature}_button`/`{feature}_progress`）を踏襲するとよい。

---

**次のお勧めステップ**: `/tsumiki:tdd-refactor llm-memo-rewrite TASK-0069` でRefactorフェーズ（品質改善）を開始します。

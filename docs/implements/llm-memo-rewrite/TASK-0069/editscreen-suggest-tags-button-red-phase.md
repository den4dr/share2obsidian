# TASK-0069 Redフェーズ記録: EditScreen「タグを提案」ボタンUI追加

**機能名**: editscreen-suggest-tags-button
**タスクID**: TASK-0069
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-08

---

## 1. 作成したテストケース一覧

対象テストケース定義: `docs/implements/llm-memo-rewrite/TASK-0069/editscreen-suggest-tags-button-testcases.md`（全6件を実装。利用可能なテストケースが10件未満のため全件実装）

| ID | 分類 | 内容 | 信頼性 |
|----|------|------|--------|
| TC-11 | 正常系 | `isSuggestingTags=false` のとき「タグを提案」ボタンが表示され活性・ラベル表示 | 🔵 |
| TC-12 | 正常系 | ボタン押下で `viewModel.suggestTags()` が1回呼ばれる | 🔵 |
| TC-13 | 状態系 | `isSuggestingTags=true` のときローディング表示かつ非活性・通常ラベル非表示 | 🔵 |
| TC-14 | 異常系 | `errorEvents` emit時に購読が機能し画面がクラッシュしない | 🟡 |
| BC-11 | 境界値 | 活性は `isSuggestingTags` のみに依存（`rewriteBodyEnabled=false` でも活性） | 🔵 |
| BC-12 | 境界値 | 元コンテンツ（本文）が空文字でもボタンは非活性化しない（EDGE-101） | 🔵 |

- 追加ファイル: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt`（既存ファイルに追記）
- 既存の共通ヘルパー `mockViewModel()` を拡張し、`isSuggestingTags: Boolean = false` パラメータ・`tagsText` パラメータ・`every { vm.suggestTags() } just Runs` を追加（既存呼び出し元は既定値のまま後方互換）

---

## 2. テストコード全文（追加部分）

```kotlin
private fun mockViewModel(
    rewriteBodyEnabled: Boolean = true,
    isRewritingBody: Boolean = false,
    isSuggestingTags: Boolean = false, // TASK-0069 で追加
    body: String = "テスト本文",
    tagsText: String = "shared",
    errorEvents: MutableSharedFlow<Int> = MutableSharedFlow(extraBufferCapacity = 1),
): EditScreenViewModel {
    val vm = mockk<EditScreenViewModel>(relaxed = true)
    every { vm.formState } returns MutableStateFlow(
        EditFormState(
            vault = "testVault",
            title = "テストタイトル",
            body = body,
            tagsText = tagsText,
            folder = "70_clippings",
            isRewritingBody = isRewritingBody,
            rewriteBodyEnabled = rewriteBodyEnabled,
            isSuggestingTags = isSuggestingTags, // TASK-0069 で追加
        )
    )
    every { vm.errorEvents } returns errorEvents
    every { vm.rewriteBody() } just Runs
    every { vm.suggestTags() } just Runs // TASK-0069 で追加
    return vm
}

// TC-11: isSuggestingTags=false のとき「タグを提案」ボタンが表示され活性である
@Test
fun `TC-11 isSuggestingTagsがfalse のとき タグを提案ボタンが表示され活性である`() {
    val viewModel = mockViewModel(isSuggestingTags = false)
    composeTestRule.setContent {
        EditScreen(viewModel = viewModel, onSend = {}, onCancel = {}, onNavigateToSettings = {})
    }
    composeTestRule.onNodeWithTag("suggest_tags_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("suggest_tags_button").assertIsEnabled()
    composeTestRule.onNodeWithText("タグを提案").assertIsDisplayed()
}

// TC-12: ボタン押下で viewModel.suggestTags() が1回呼ばれる
@Test
fun `TC-12 活性状態でボタンを押下すると suggestTags が1回呼ばれる`() {
    val viewModel = mockViewModel(isSuggestingTags = false)
    composeTestRule.setContent {
        EditScreen(viewModel = viewModel, onSend = {}, onCancel = {}, onNavigateToSettings = {})
    }
    composeTestRule.onNodeWithTag("suggest_tags_button").performClick()
    verify(exactly = 1) { viewModel.suggestTags() }
}

// TC-13: isSuggestingTags=true のときローディング表示かつ非活性
@Test
fun `TC-13 isSuggestingTagsがtrue のときローディング表示かつボタンが非活性である`() {
    val viewModel = mockViewModel(isSuggestingTags = true)
    composeTestRule.setContent {
        EditScreen(viewModel = viewModel, onSend = {}, onCancel = {}, onNavigateToSettings = {})
    }
    composeTestRule.onNodeWithTag("suggest_tags_progress").assertIsDisplayed()
    composeTestRule.onNodeWithTag("suggest_tags_button").assertIsNotEnabled()
    composeTestRule.onNodeWithText("タグを提案").assertDoesNotExist()
}

// TC-14: errorEvents 発行時に購読が機能し画面がクラッシュしない
@Test
fun `TC-14 errorEvents emit時にタグ提案ボタン関連の購読が機能し画面がクラッシュしない`() {
    val errorEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val viewModel = mockViewModel(errorEvents = errorEvents)
    composeTestRule.setContent {
        EditScreen(viewModel = viewModel, onSend = {}, onCancel = {}, onNavigateToSettings = {})
    }
    composeTestRule.runOnUiThread {
        errorEvents.tryEmit(com.den4dr.share2Obsidian.R.string.error_llm_network)
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("suggest_tags_button").assertExists()
}

// BC-11: 活性は isSuggestingTags のみに依存する
@Test
fun `BC-11 rewriteBodyEnabledがfalseでもisSuggestingTagsがfalseならタグ提案ボタンは活性`() {
    val viewModel = mockViewModel(rewriteBodyEnabled = false, isSuggestingTags = false)
    composeTestRule.setContent {
        EditScreen(viewModel = viewModel, onSend = {}, onCancel = {}, onNavigateToSettings = {})
    }
    composeTestRule.onNodeWithTag("suggest_tags_button").assertIsEnabled()
}

// BC-12: EDGE-101 元コンテンツが空文字でもボタンは活性のまま
@Test
fun `BC-12 元コンテンツ本文が空文字でもタグ提案ボタンは非活性化しない`() {
    val viewModel = mockViewModel(isSuggestingTags = false, body = "")
    composeTestRule.setContent {
        EditScreen(viewModel = viewModel, onSend = {}, onCancel = {}, onNavigateToSettings = {})
    }
    composeTestRule.onNodeWithTag("suggest_tags_button").assertIsEnabled()
}
```

---

## 3. テスト実行結果（期待される失敗の確認）

**実行コマンド**:
```bash
mise exec -- ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.den4dr.share2Obsidian.ui.EditScreenTest
```

**実行環境**: エミュレータ `Medium_Phone_API_36.1(AVD)`（`adb devices` で `emulator-5554 device` を確認済み）

**結果**: `EditScreenTest` 全32件中、新規追加6件がすべて失敗（既存26件は全てパスし回帰なし）。

| テスト | 失敗内容 |
|--------|---------|
| TC-11 | `AssertionError: The component with TestTag = 'suggest_tags_button' is not displayed!` |
| TC-12 | `AssertionError: Failed to inject touch input. ... could not find any node that satisfies: (TestTag = 'suggest_tags_button')` |
| TC-13 | `AssertionError: The component with TestTag = 'suggest_tags_progress' is not displayed!` |
| TC-14 | `AssertionError: Failed: assertExists. ... could not find any node that satisfies: (TestTag = 'suggest_tags_button')` |
| BC-11 | `AssertionError: Failed to assert the following: (is enabled) ... could not find any node that satisfies: (TestTag = 'suggest_tags_button')` |
| BC-12 | `AssertionError: Failed to assert the following: (is enabled) ... could not find any node that satisfies: (TestTag = 'suggest_tags_button')` |

すべて「`EditScreen.kt` に `suggest_tags_button`/`suggest_tags_progress` testTag を持つ Composable が未実装」という単一の原因（未実装機能に対するテスト）で失敗しており、Red フェーズとして正常な状態。

---

## 4. Greenフェーズで実装すべき内容

1. **`app/src/main/res/values/strings.xml`** に `button_suggest_tags`（値「タグを提案」）を新規追加する。
2. **`app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`** のタグ入力欄（`tagsText` の `OutlinedTextField`）付近に「タグを提案」`Button` を追加する。
   - `onClick = { viewModel.suggestTags() }`
   - `enabled = !formState.isSuggestingTags`（`rewriteBodyEnabled` 等の他フラグに依存させない。BC-11 で保証）
   - `Modifier.testTag("suggest_tags_button")`
   - ボタン内: `isSuggestingTags == true` のとき `CircularProgressIndicator(Modifier.size(16.dp).testTag("suggest_tags_progress"), strokeWidth = 2.dp)`、`false` のとき `Text(stringResource(R.string.button_suggest_tags))`
   - 本文リライトボタン（`rewrite_body_button`）と同一のローディング表示パターンを踏襲する。
3. 実装済み・変更不要: `EditFormState.isSuggestingTags`、`EditScreenViewModel.suggestTags()`、`errorEvents` 購読（いずれも TASK-0068/TASK-0065 で実装済み）。

---

**次のお勧めステップ**: `/tsumiki:tdd-green` でGreenフェーズ（最小実装）を開始します。

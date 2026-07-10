# TDD Redフェーズ - TASK-0072: EditScreenViewModel generateCustomFieldValue()・EditScreen UI「生成」ボタン追加

- **機能名**: edit-custom-field-llm-generation（EditScreenViewModel カスタムフィールドLLM生成）
- **タスクID**: TASK-0072
- **要件名**: llm-memo-rewrite
- **作成日**: 2026-07-09
- **テストファイル**: `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelGenerateCustomFieldValueTest.kt`

## 1. 作成したテストケース一覧

`docs/implements/llm-memo-rewrite/TASK-0072/edit-custom-field-llm-generation-testcases.md` に定義された ViewModel 単体テスト全13ケース（正常系4・異常系5・境界値4）をすべて実装した。UI 統合テスト（UI-01〜03）は同ドキュメント上で「本タスクの単体テスト範囲外・別途実装」と明記されているため対象外とした。

| TC ID | カテゴリ | テスト内容 | 信頼性 |
|-------|---------|-----------|--------|
| TC-0072-N01 | 正常系 | 対象インデックスの value のみ更新され、他フィールドは全プロパティ不変 | 🔵 完了条件 |
| TC-0072-N02 | 正常系 | 入力に sourceContent を使い formState.body を使わない | 🔵 REQ-002/REQ-406 |
| TC-0072-N03 | 正常系 | 対象フィールドの llmPrompt が rewrite の prompt 引数に渡される | 🔵 実装詳細 |
| TC-0072-N04 | 正常系 | 生成中は generatingFieldIndex が対象 index、完了後は null に戻る | 🟡 UI/UX要件（オプション） |
| TC-0072-E01 | 異常系 | NetworkError: value 不変・error_llm_network を emit | 🔵 EDGE-001/NFR-201 |
| TC-0072-E02 | 異常系 | AuthError: value 不変・error_llm_auth を emit | 🔵 EDGE-002/NFR-201 |
| TC-0072-E03 | 異常系 | Timeout: value 不変・error_llm_timeout を emit | 🔵 EDGE-003/NFR-001 |
| TC-0072-E04 | 異常系 | EmptyOrInvalidResponse: value 不変・error_llm_empty_response を emit | 🔵 EDGE-004/NFR-201 |
| TC-0072-E05 | 異常系 | Unknown: value 不変・error_llm_unknown を emit | 🔵 NFR-201 |
| TC-0072-B01 | 境界値 | index=0（先頭）で更新 | 🟡 updateCustomField 実装 |
| TC-0072-B02 | 境界値 | index=末尾で更新 | 🟡 updateCustomField 実装 |
| TC-0072-B03 | 境界値 | Success("")で空文字更新・errorEvents 無し | 🟡 既存 TC-0063-B04 同型 |
| TC-0072-B04 | 境界値 | sourceContent 空文字でもガードせず rewrite | 🟡 EDGE-101 |

## 2. テストコード

`app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelGenerateCustomFieldValueTest.kt` に全文を保存済み。

構成は `EditScreenViewModelRewriteBodyTest.kt` を踏襲：
- MockK で `LlmRewriteRepository` / `LlmSettingsRepository` をスタブ化し、Hilt を起動せずコンストラクタ注入
- `StandardTestDispatcher` を `Dispatchers.setMain()` と `runTest(testDispatcher)` の両方に共有
- `errorEvents`（`SharedFlow<Int>`）は `generateCustomFieldValue()` 呼び出し前に通常の `launch` で購読し、検証後に `job.cancel()`
- ローディング状態の中間観測（TC-0072-N04）は `CompletableDeferred` で `rewrite()` の完了を外部制御
- 局所更新の検証は `CustomFieldState` の `data class` 構造的等価比較（`assertEquals(fieldFixed, ...)`）で他フィールド不変を確認

## 3. テスト実行コマンド

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelGenerateCustomFieldValueTest"
```

## 4. 実際の失敗結果（確認済み）

`EditScreenViewModel.generateCustomFieldValue(index: Int)` メソッドと `EditFormState.generatingFieldIndex` プロパティが未実装のため、**コンパイルエラー**で失敗することを確認した。

```
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:142:19 Unresolved reference 'generateCustomFieldValue'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:183:19 Unresolved reference 'generateCustomFieldValue'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:218:19 Unresolved reference 'generateCustomFieldValue'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:250:19 Unresolved reference 'generateCustomFieldValue'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:257:39 Unresolved reference 'generatingFieldIndex'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:267:39 Unresolved reference 'generatingFieldIndex'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:304:19 Unresolved reference 'generateCustomFieldValue'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:344:19 Unresolved reference 'generateCustomFieldValue'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:384:19 Unresolved reference 'generateCustomFieldValue'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:424:19 Unresolved reference 'generateCustomFieldValue'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:464:19 Unresolved reference 'generateCustomFieldValue'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:505:19 Unresolved reference 'generateCustomFieldValue'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:542:19 Unresolved reference 'generateCustomFieldValue'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:577:19 Unresolved reference 'generateCustomFieldValue'.
e: EditScreenViewModelGenerateCustomFieldValueTest.kt:612:19 Unresolved reference 'generateCustomFieldValue'.

> Task :app:compileDebugUnitTestKotlin FAILED
```

すべて「未実装の関数・プロパティ呼び出し」に起因する失敗であり、想定通りの Red 状態である（`EditScreenViewModelSuggestTagsTest.kt`（TASK-0068）と同一パターン）。

## 5. Greenフェーズで実装すべき内容

TASK-0072.md「実装詳細」・要件定義書 §2/§3 に基づき、以下をそのまま実装対象とする：

1. **`EditFormState.kt`**: `generatingFieldIndex: Int? = null` を追加（`isRewritingBody`/`isSuggestingTags` と同様の個別ローディング状態）。
2. **`EditScreenViewModel.kt`**:
   - `generateCustomFieldValue(index: Int)` メソッド追加:
     - `viewModelScope.launch` で非同期実行
     - `formState.value.customFields[index].llmPrompt` を prompt として取得
     - ローディング開始: `generatingFieldIndex = index` に更新
     - `llmSettingsRepository.getSettings().first()` で設定取得
     - `llmRewriteRepository.rewrite(settings, prompt, sourceContent)` を呼び出し（`sourceContent` は空文字でもガードしない：EDGE-101）
     - `Success`: 既存 `updateCustomField(index, result.text)` を再利用して対象 value のみ更新
     - `Failure`: 対象 value を変更せず `_errorEvents.emit(result.messageResId)`
     - 最後に `generatingFieldIndex = null` に戻す
   - 実装方針としては既存 `runLlmRequest()` ヘルパー（`setLoading`/`onSuccess` を差し替え可能な設計）をそのまま再利用するか、`generatingFieldIndex` が `Int?`（対象 index 保持）である点が `Boolean` の `isRewritingBody`/`isSuggestingTags` と異なるため、`setLoading` のシグネチャ拡張または専用の非同期処理を追加するかは Green フェーズで確定する。
3. **`EditScreen.kt`**（本タスクの単体テスト範囲外・別途 Compose UI Test で検証）: `formState.customFields.forEachIndexed` ループ内で `field.valueSource == FieldValueSource.LLM` の場合のみ「生成」ボタンを表示し、クリック時に `viewModel.generateCustomFieldValue(index)` を呼び出す。
4. **`strings.xml`**（UI実装時）: `field_generate_button`（"生成"）・`field_generating`（"生成中..."）を追加。

実装後、`mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelGenerateCustomFieldValueTest"` が全13件成功することを Green フェーズのゴールとする。

## 6. 品質判定

- ✅ テスト実行: 実行可能でコンパイルエラーにより失敗することを確認済み
- ✅ 期待値: 明確で具体的（対象/非対象フィールドの厳密比較・errorEvents の resId・rewrite 呼び出し引数）
- ✅ アサーション: 適切（既存 `EditScreenViewModelRewriteBodyTest` パターン踏襲）
- ✅ 実装方針: 明確（TASK-0072.md 実装詳細・要件定義書 §2/§3 と1:1対応）
- 信頼性レベル分布: 🔵 8件（62%）、🟡 5件（38%）、🔴 0件（0%）

**総合判定**: 高品質

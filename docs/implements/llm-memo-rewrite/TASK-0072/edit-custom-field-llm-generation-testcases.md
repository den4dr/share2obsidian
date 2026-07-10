# TASK-0072 テストケース定義書

**機能名**: EditScreenViewModel `generateCustomFieldValue()`・EditScreen UI「生成」ボタン追加
**タスクID**: TASK-0072
**要件名**: llm-memo-rewrite
**タスクタイプ**: TDD（Phase 6 - カスタムフィールドのLLM生成、Could Have）
**作成日**: 2026-07-09
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0072/edit-custom-field-llm-generation-testcases.md`

---

## 信頼性レベル凡例

- 🔵 **青信号**: EARS要件定義書・設計文書・既存実装を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書・既存実装から妥当な推測をしている
- 🔴 **赤信号**: 元資料にない推測

---

## 0. テスト対象と方針

### テスト対象

- **実装対象**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt` の `generateCustomFieldValue(index: Int)`
- **テストクラス（新規）**: `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelGenerateCustomFieldValueTest.kt`
- **参考テスト**: `EditScreenViewModelRewriteBodyTest.kt`, `EditScreenViewModelSuggestTagsTest.kt`

### テスト方針（既存パターン踏襲）🔵

- Hilt を起動せず、`LlmRewriteRepository` / `LlmSettingsRepository` を MockK でスタブし `EditScreenViewModel(mockRewrite, mockSettings)` で直接インスタンス化する。
- `Dispatchers.setMain(testDispatcher)`（`StandardTestDispatcher`）で `viewModelScope.launch` を制御し、`advanceUntilIdle()` で非同期完了を待つ。
- `errorEvents`（`SharedFlow<Int>`）は `collectErrorEvents(viewModel)` ヘルパーで emit 前に先行購読し、検証後 `job.cancel()` する。
- テスト名は `` `TC-0072-XXX 説明` `` 形式（バッククォートによる日本語メソッド名）。

**参照元**: `EditScreenViewModelRewriteBodyTest.kt`（クラス doc・ヘルパー・`@Config(sdk=[34])`・`RobolectricTestRunner`）

### テスト用フィクスチャの前提 🔵

- `FieldValueSource` = `{ FIXED, HTML_META, URL, EMPTY, LLM }`（`app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt`）
- `CustomFieldState(key, value, valueType, valueSource = FIXED, llmPrompt = "")`（`app/src/main/java/com/den4dr/share2Obsidian/domain/model/CustomFieldState.kt`）
- `initialize(processed, config, customFields, sourceContent, bodyLlmPrompt)` に `customFields` で複数フィールドを渡し、少なくとも1つを `valueSource = LLM, llmPrompt = "…"` とする。

---

## 1. 正常系テストケース（基本的な動作）

### TC-0072-N01: 該当インデックスの value のみ更新され、他フィールドは全プロパティ不変

- **テスト名**: `generateCustomFieldValue` 成功時に対象インデックスの value のみ生成結果へ更新され、他インデックスのフィールドは変化しない
  - **何をテストするか**: `generateCustomFieldValue(index)` が `customFields[index].value` のみを LLM 応答テキストで上書きし、他インデックスの `CustomFieldState` に一切影響しないこと
  - **期待される動作**: 対象フィールドの `value` が生成結果に置き換わり、他フィールドは `value` を含む全プロパティが呼び出し前と同一
- **入力値**:
  - `customFields = [ CustomFieldState("author", "既存A", TEXT, FIXED), CustomFieldState("summary", "既存S", TEXT, LLM, "要約プロンプト") ]`
  - `rewrite(...)` スタブ → `LlmRewriteResult.Success("生成結果")`
  - 呼び出し: `generateCustomFieldValue(1)`（LLM フィールドの index）
  - **入力データの意味**: 非LLM(FIXED)フィールドとLLMフィールドを混在させ、生成が対象のみに限定されることを代表的に検証する
- **期待される結果**:
  - `formState.value.customFields[1].value == "生成結果"`
  - `formState.value.customFields[0] == CustomFieldState("author", "既存A", TEXT, FIXED)`（不変）
  - **期待結果の理由**: 完了条件「該当インデックスの `value` のみ更新／他フィールドに影響しない」。実装は既存 `updateCustomField(index, value)` を再利用し `copy(value=…)` で対象のみ差し替えるため
- **テストの目的**: フィールド単位の局所更新の正確性
  - **確認ポイント**: 他インデックスの `value` だけでなく `key/valueType/valueSource/llmPrompt` も不変であること
- 🔵 信頼性レベル: TASK-0072.md 単体テスト要件テストケース1・完了条件より

### TC-0072-N02: 入力に sourceContent を使い formState.body を使わない

- **テスト名**: `generateCustomFieldValue` は編集後の formState.body ではなく sourceContent を rewrite の入力に渡す
  - **何をテストするか**: ユーザーが `updateBody()` で本文を編集した後でも、`rewrite` の content 引数が初期化時の `sourceContent` であること
  - **期待される動作**: `rewrite(settings, prompt, "元コンテンツ")` が呼ばれ、編集後 body では呼ばれない
- **入力値**:
  - `initialize(..., sourceContent = "元コンテンツ")` 後に `updateBody("ユーザーが編集した本文")`
  - LLM フィールドの `llmPrompt = "要約プロンプト"`
  - 呼び出し: `generateCustomFieldValue(index)`
  - **入力データの意味**: `sourceContent` と `formState.body` に確実な差分を作り、入力ソースの独立性（REQ-406）を切り分ける
- **期待される結果**:
  - `coVerify { mockRewrite.rewrite(any(), "要約プロンプト", "元コンテンツ") }`
  - `coVerify(exactly = 0) { mockRewrite.rewrite(any(), any(), "ユーザーが編集した本文") }`
  - **期待結果の理由**: `rewriteBody()`/`suggestTags()` と同一方針で入力は常に `sourceContent`（REQ-002/REQ-406）
- **テストの目的**: 入力ソース固定（本タスクの中核設計）の検証
  - **確認ポイント**: content 引数が `sourceContent` であることと、prompt 引数が対象フィールドの `llmPrompt` であることの両方
- 🔵 信頼性レベル: TASK-0072.md 単体テスト要件テストケース3・REQ-002/REQ-406、既存 TC-0063-N04 と同型より

### TC-0072-N03: 対象フィールドの llmPrompt が rewrite の prompt 引数に渡される

- **テスト名**: `generateCustomFieldValue(index)` は customFields[index].llmPrompt を LLM 呼び出しの prompt に使用する
  - **何をテストするか**: 複数の LLM フィールドが異なる `llmPrompt` を持つ場合、対象インデックスの `llmPrompt` が選択されること
  - **期待される動作**: `rewrite(settings, "対象フィールドのプロンプト", sourceContent)` が呼ばれる
- **入力値**:
  - `customFields = [ CustomFieldState("a", "", TEXT, LLM, "プロンプトA"), CustomFieldState("b", "", TEXT, LLM, "プロンプトB") ]`
  - 呼び出し: `generateCustomFieldValue(1)`
  - **入力データの意味**: プロンプト取り違え（index 誤り）を検出できるよう、2つの LLM フィールドで異なるプロンプトを与える
- **期待される結果**:
  - `coVerify { mockRewrite.rewrite(any(), "プロンプトB", any()) }`
  - **期待結果の理由**: 実装は `formState.value.customFields[index].llmPrompt` を取得して渡す
- **テストの目的**: index → llmPrompt の対応が正しいこと
  - **確認ポイント**: index=0 のプロンプトが誤って使われないこと
- 🔵 信頼性レベル: TASK-0072.md 実装詳細（`prompt = formState.value.customFields[index].llmPrompt`）より

### TC-0072-N04: 生成中はフィールド単位のローディングが立ち、完了後に解除される（オプション実装時）

- **テスト名**: `generateCustomFieldValue` 実行中は generatingFieldIndex が対象 index、完了後は null に戻る
  - **何をテストするか**: `EditFormState.generatingFieldIndex` を導入した場合の、生成中→完了のローディング状態遷移（null→index→null）
  - **期待される動作**: 呼び出し中は `generatingFieldIndex == index`、完了後は `generatingFieldIndex == null`
- **入力値**:
  - `CompletableDeferred<LlmRewriteResult>` で `rewrite` の完了タイミングを外部制御
  - 呼び出し: `generateCustomFieldValue(1)` → 完了前検証 → `deferred.complete(Success("結果"))` → 完了後検証
  - **入力データの意味**: 応答完了前後の2時点を観測してローディング表示の一貫性を確認する
- **期待される結果**:
  - 完了前: `formState.value.generatingFieldIndex == 1`
  - 完了後: `formState.value.generatingFieldIndex == null` かつ `customFields[1].value == "結果"`
  - **期待結果の理由**: UI/UX要件「生成中はそのフィールドのみローディング表示」（フィールド単位ローディング）
- **テストの目的**: フィールド単位ローディング状態の管理
  - **確認ポイント**: 他フィールドの入力継続可能性（`generatingFieldIndex` が単一 index に限定される）
  - **注記**: `generatingFieldIndex` は要件定義書 §2 でオプション扱い（🟡）。実装しない場合は本テストをスキップし、代わりにローディング状態を持たない設計であることを明記する
- 🟡 信頼性レベル: TASK-0072.md UI/UX要件・要件定義書 §2「UI 状態（オプション）」より妥当な推測（新規UI要素）

---

## 2. 異常系テストケース（エラーハンドリング）

> `generateCustomFieldValue` は既存 `runLlmRequest()` ヘルパー（成功→`updateCustomField`／失敗→`_errorEvents.emit`）を再利用する想定のため、失敗系は `LlmRewriteResult.Failure` の全5種別で「対象 value 不変＋対応 messageResId を1件 emit」を検証する（`rewriteBody` の TC-0063-E01〜E05 と同型）。

### TC-0072-E01: NetworkError 時 value 不変・error_llm_network を emit

- **テスト名**: `generateCustomFieldValue` が NetworkError を返すと対象 value 不変で error_llm_network が emit される
  - **エラーケースの概要**: ネットワーク未接続・接続失敗（EDGE-001）
  - **エラー処理の重要性**: 失敗時に既存フィールド値を破壊せず、再試行可能な状態を保つ
- **入力値**: `rewrite(...)` → `LlmRewriteResult.Failure.NetworkError(R.string.error_llm_network)`、対象フィールド初期値 `value = "既存値"`
  - **不正な理由**: 外部 API 呼び出しが到達しないネットワーク層の失敗
  - **実際の発生シナリオ**: 機内モード・圏外・DNS 失敗時に生成ボタン押下
- **期待される結果**:
  - `formState.value.customFields[index].value == "既存値"`（不変）
  - `received == listOf(R.string.error_llm_network)`
  - **エラーメッセージの内容**: エラー種別に対応する string resource ID
  - **システムの安全性**: フィールド値・他状態を変更しない
- **テストの目的**: 失敗時の状態非破壊とエラー通知
  - **品質保証の観点**: NFR-201（エラー時のユーザー通知）
- 🔵 信頼性レベル: TASK-0072.md 単体テスト要件テストケース2・EDGE-001・NFR-201、既存 TC-0063-E01 と同型より

### TC-0072-E02: AuthError 時 value 不変・error_llm_auth を emit

- **テスト名**: `generateCustomFieldValue` が AuthError を返すと対象 value 不変で error_llm_auth が emit される
  - **エラーケースの概要**: APIキー不正・認証失敗（HTTP 401/403, EDGE-002）
- **入力値**: `rewrite(...)` → `LlmRewriteResult.Failure.AuthError(R.string.error_llm_auth)`
  - **実際の発生シナリオ**: APIキー誤設定・期限切れ
- **期待される結果**: 対象 `value` 不変、`received == listOf(R.string.error_llm_auth)`
- **テストの目的**: 認証失敗時のエラーハンドリング
- 🔵 信頼性レベル: TASK-0072.md テストケース2・EDGE-002・NFR-201 より

### TC-0072-E03: Timeout 時 value 不変・error_llm_timeout を emit

- **テスト名**: `generateCustomFieldValue` が Timeout を返すと対象 value 不変で error_llm_timeout が emit される
  - **エラーケースの概要**: 30秒タイムアウト（EDGE-003, NFR-001）
- **入力値**: `rewrite(...)` → `LlmRewriteResult.Failure.Timeout(R.string.error_llm_timeout)`
  - **実際の発生シナリオ**: LLM API 応答遅延
- **期待される結果**: 対象 `value` 不変、`received == listOf(R.string.error_llm_timeout)`
- **テストの目的**: 応答遅延時の明示的エラー化
- 🔵 信頼性レベル: TASK-0072.md テストケース2・EDGE-003・NFR-001 より

### TC-0072-E04: EmptyOrInvalidResponse 時 value 不変・error_llm_empty_response を emit

- **テスト名**: `generateCustomFieldValue` が EmptyOrInvalidResponse を返すと対象 value 不変で error_llm_empty_response が emit される
  - **エラーケースの概要**: 空応答・パース不能なレスポンス（EDGE-004）
- **入力値**: `rewrite(...)` → `LlmRewriteResult.Failure.EmptyOrInvalidResponse(R.string.error_llm_empty_response)`
  - **実際の発生シナリオ**: LLM が JSON 崩れ・choices 欠落を返す
- **期待される結果**: 対象 `value` 不変、`received == listOf(R.string.error_llm_empty_response)`
- **テストの目的**: 不正レスポンスでフィールド値を壊さない
- 🔵 信頼性レベル: TASK-0072.md テストケース2・EDGE-004・NFR-201 より

### TC-0072-E05: Unknown 時 value 不変・error_llm_unknown を emit

- **テスト名**: `generateCustomFieldValue` が Unknown を返すと対象 value 不変で error_llm_unknown が emit される
  - **エラーケースの概要**: 上記以外の予期しないエラー（全 Failure 種別網羅の完成）
- **入力値**: `rewrite(...)` → `LlmRewriteResult.Failure.Unknown(R.string.error_llm_unknown)`
  - **実際の発生シナリオ**: 想定外の例外
- **期待される結果**: 対象 `value` 不変、`received == listOf(R.string.error_llm_unknown)`
- **テストの目的**: 想定外例外でもクラッシュせず汎用メッセージ通知
- 🔵 信頼性レベル: TASK-0072.md テストケース2・NFR-201 より

---

## 3. 境界値テストケース（最小値・最大値・空値等）

### TC-0072-B01: index が先頭(0)の LLM フィールドを更新できる

- **テスト名**: `generateCustomFieldValue(0)` で先頭フィールドの value が更新される
  - **境界値の意味**: `customFields` インデックスの下限（0）
  - **境界値での動作保証**: リスト先頭でも `updateCustomField` の `index in indices` 判定を通過し更新される
- **入力値**: `customFields = [ CustomFieldState("s", "既存", TEXT, LLM, "P") , CustomFieldState("a", "既存A", TEXT, FIXED) ]`、`generateCustomFieldValue(0)`、`rewrite → Success("結果0")`
  - **境界値選択の根拠**: 0 は最小の有効インデックス
- **期待される結果**: `customFields[0].value == "結果0"`、`customFields[1]` 不変
  - **境界での正確性**: 先頭インデックスでも正しく対象特定される
- **テストの目的**: 下限境界での局所更新の正確性
- 🟡 信頼性レベル: 完了条件・`updateCustomField` の `index in fields.indices` 実装より妥当な推測

### TC-0072-B02: index が末尾の LLM フィールドを更新できる

- **テスト名**: `generateCustomFieldValue(lastIndex)` で末尾フィールドの value が更新される
  - **境界値の意味**: `customFields` インデックスの上限（`size - 1`）
- **入力値**: `customFields = [ FIXED, FIXED, CustomFieldState("s", "既存", TEXT, LLM, "P") ]`、`generateCustomFieldValue(2)`、`rewrite → Success("結果2")`
  - **境界値選択の根拠**: `size - 1` は最大の有効インデックス
- **期待される結果**: `customFields[2].value == "結果2"`、`customFields[0]`・`customFields[1]` 不変
  - **一貫した動作**: 上限境界でも先頭・中間と同一の局所更新
- **テストの目的**: 上限境界での局所更新の正確性
- 🟡 信頼性レベル: 完了条件・`updateCustomField` 実装より妥当な推測

### TC-0072-B03: Success("") 空応答で value が空文字に更新され、errorEvents は発行されない

- **テスト名**: `generateCustomFieldValue` が Success("") を返すと対象 value が空文字で上書きされエラー扱いにならない
  - **境界値の意味**: `LlmRewriteResult.Success.text` の下限（空文字 `""`）
  - **境界値での動作保証**: 空文字成功と失敗種別を混同しない
- **入力値**: `rewrite → Success("")`、対象フィールド初期値 `value = "既存値"`
  - **境界値選択の根拠**: 成功だが空応答という下限境界（既存 TC-0063-B04 と同型）
- **期待される結果**:
  - `customFields[index].value == ""`
  - `received.isEmpty()`（errorEvents 未発行）
  - **境界での正確性**: Success は空文字でも成功として `value` に反映
- **テストの目的**: 空応答成功の扱いの明確化
  - **堅牢性の確認**: 空応答成功と Failure の切り分け
- 🟡 信頼性レベル: `LlmRewriteResult` の Success 空文字許容・既存 TC-0063-B04 より妥当な推測

### TC-0072-B04: sourceContent が空文字でもガードされず rewrite が実行される

- **テスト名**: sourceContent 空文字でも `generateCustomFieldValue` は早期 return せず rewrite を呼ぶ
  - **境界値の意味**: `sourceContent` の最小値（空文字 `""`）
  - **境界値での動作保証**: 空文字ガード禁止（EDGE-101）を踏襲
- **入力値**: `initialize(..., sourceContent = "")`、LLM フィールド `llmPrompt = "P"`、`rewrite → Success("結果")`、`generateCustomFieldValue(index)`
  - **実際の使用場面**: 本文なし共有でもカスタムフィールド生成を試みるケース
- **期待される結果**:
  - `coVerify { mockRewrite.rewrite(any(), "P", "") }`
  - `customFields[index].value == "結果"`
  - **一貫した動作**: 空文字でも `rewriteBody()`/`suggestTags()` と同様にガードしない
- **テストの目的**: 空文字ガード禁止の踏襲確認
- 🟡 信頼性レベル: EDGE-101・既存 TC-0063-B02（`rewriteBody` 空文字非ガード）より妥当な推測

---

## 4. 参考: UI 統合テスト（EditScreen、本タスクの単体テスト範囲外・別途）

> ViewModel 単体テスト（本ドキュメント TC-0072-N/E/B）が TDD Red/Green の主対象。以下は EditScreen Compose の表示検証で、Compose UI Test（Robolectric/`createComposeRule`）として別途実装する参考項目。

- **UI-01**: `valueSource == LLM` のカスタムフィールドのみ「生成」ボタン（`field_generate_button`）が表示され、FIXED/HTML_META/URL/EMPTY では非表示 🔵（TASK-0072.md 完了条件・UI/UX要件）
- **UI-02**: 「生成」ボタン押下で `viewModel.generateCustomFieldValue(index)` が呼ばれ、完了後にフィールド値が更新される 🟡（UI/UX要件）
- **UI-03**: 生成中はそのフィールドのボタンがローディング表示（`CircularProgressIndicator`）になり、他フィールドの入力は継続可能 🟡（UI/UX要件、`generatingFieldIndex` 実装時）

---

## 5. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: 既存プロジェクトの実装言語（Android/Compose）。ViewModel・Coroutines・sealed class を用いた既存 LLM 機能と統一
  - **テストに適した機能**: バッククォート日本語テストメソッド名、data class の構造的等価比較（`assertEquals` でフィールド不変検証が容易）
- **テストフレームワーク**: JUnit 4.13.2 + MockK 1.13.12 + kotlinx-coroutines-test 1.9.0 + Robolectric
  - **フレームワーク選択の理由**: 参考テスト `EditScreenViewModelRewriteBodyTest.kt` と完全に同一構成。MockK の `coEvery`/`coVerify` で suspend `rewrite` をスタブ・検証、`StandardTestDispatcher`+`advanceUntilIdle()` で `viewModelScope.launch` を制御、`R.string.*` 参照のため `RobolectricTestRunner`（`@Config(sdk=[34])`）
  - **テスト実行環境**: `mise exec -- ./gradlew test --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelGenerateCustomFieldValueTest"`
- 🔵 信頼性レベル: note.md §1/§5・既存テストクラス構成より

---

## 6. テストケース実装時の日本語コメント指針

各テスト実装時、既存 `EditScreenViewModelRewriteBodyTest.kt` の様式に合わせて以下を必ず含める。

### テストケース開始時のコメント

```kotlin
// 【テスト目的】: このテストで確認する動作
// 【テスト内容】: 具体的にどの処理をテストするか
// 【期待される動作】: 正常動作時の結果
// 🔵🟡🔴 信頼性レベル: 参照元
```

### Given（準備フェーズ）のコメント

```kotlin
// 【テストデータ準備】: customFields に FIXED と LLM を混在させる理由
// 【初期条件設定】: initialize() で sourceContent / customFields を設定
// 【前提条件確認】: rewrite スタブ（coEvery）を設定済み
```

### When（実行フェーズ）のコメント

```kotlin
// 【実際の処理実行】: viewModel.generateCustomFieldValue(index) を呼び出す
// 【処理内容】: prompt取得 → getSettings().first() → rewrite() → 成功/失敗分岐
// 【実行タイミング】: advanceUntilIdle() で viewModelScope.launch 完了を待機
```

### Then（検証フェーズ）のコメント

```kotlin
// 【結果検証】: 対象 value 更新 / 他フィールド不変 / errorEvents 発行 を検証
// 【期待値確認】: 期待結果とその理由
// 【品質保証】: 局所更新・状態非破壊がシステム品質にどう貢献するか
```

### 各 expect/assert・verify ステートメントのコメント

```kotlin
// 【検証項目】: 対象インデックスの value 更新
// 🔵 信頼性レベル: 完了条件より
assertEquals("生成結果", viewModel.formState.value.customFields[1].value) // 【確認内容】: 対象 value が生成結果に更新される
assertEquals(before0, viewModel.formState.value.customFields[0])          // 【確認内容】: 非対象フィールドが全プロパティ不変
coVerify { mockRewrite.rewrite(any(), "要約プロンプト", "元コンテンツ") }    // 【確認内容】: 入力が sourceContent かつ対象 llmPrompt
```

### セットアップ・クリーンアップのコメント

```kotlin
@Before
fun setUp() {
    // 【テスト前準備】: Dispatchers.setMain(testDispatcher) で viewModelScope を制御
    // 【環境初期化】: mockSettings.getSettings() を有効な LlmSettings でスタブ
}

@After
fun tearDown() {
    // 【テスト後処理】: Dispatchers.resetMain() で dispatcher 汚染を防止
    // 【状態復元】: 後続テストへの影響排除
}
```

---

## 7. 要件定義との対応関係

- **参照した機能概要**: 要件定義書 §1（`generateCustomFieldValue` は LLM フィールドの `llmPrompt` と `sourceContent` を入力に値を生成、EditScreen は LLM のみ「生成」ボタン表示）
- **参照した入力・出力仕様**: 要件定義書 §2（入力: `index`/`llmPrompt`/`sourceContent`/`settings`、出力: 成功時 `updateCustomField(index, text)`／失敗時 `errorEvents` 発行、オプションで `generatingFieldIndex`）
- **参照した制約条件**: 要件定義書 §3（`.copy()` 不変更新・`viewModelScope.launch` 非同期・`sourceContent` 固定・失敗時状態非変更・文字列リソース化）
- **参照した使用例**: 要件定義書 §4（正常系＝生成ボタン押下で該当 value 更新、入力ソース独立性、失敗時 value 不変＋Toast、非LLMフィールドはボタン非表示、複数フィールドの独立性）
- **参照した EARS 要件**: REQ-104, REQ-303, REQ-304, REQ-406, NFR-201
- **参照した受け入れ基準（TASK-0072 単体テスト要件）**: TC1→TC-0072-N01、TC2→TC-0072-E01〜E05、TC3→TC-0072-N02
- **参照した設計文書**: architecture.md「EditScreenViewModel/EditScreen への変更」、dataflow.md「機能3」、既存実装 `EditScreenViewModel.kt`（`updateCustomField`/`runLlmRequest`/`rewriteBody`/`suggestTags`）

---

## 8. テストケース網羅サマリー

| 分類 | ケースID | 概要 | 信頼性 |
|------|---------|------|--------|
| 正常系 | TC-0072-N01 | 対象 value のみ更新・他フィールド不変 | 🔵 |
| 正常系 | TC-0072-N02 | 入力に sourceContent を使い body を使わない | 🔵 |
| 正常系 | TC-0072-N03 | 対象フィールドの llmPrompt を prompt に使用 | 🔵 |
| 正常系 | TC-0072-N04 | フィールド単位ローディング遷移（オプション） | 🟡 |
| 異常系 | TC-0072-E01 | NetworkError: value 不変・error_llm_network | 🔵 |
| 異常系 | TC-0072-E02 | AuthError: value 不変・error_llm_auth | 🔵 |
| 異常系 | TC-0072-E03 | Timeout: value 不変・error_llm_timeout | 🔵 |
| 異常系 | TC-0072-E04 | EmptyOrInvalidResponse: value 不変・error_llm_empty_response | 🔵 |
| 異常系 | TC-0072-E05 | Unknown: value 不変・error_llm_unknown | 🔵 |
| 境界値 | TC-0072-B01 | index=0（先頭）で更新 | 🟡 |
| 境界値 | TC-0072-B02 | index=末尾で更新 | 🟡 |
| 境界値 | TC-0072-B03 | Success("")で空文字更新・errorEvents 無し | 🟡 |
| 境界値 | TC-0072-B04 | sourceContent 空文字でもガードせず rewrite | 🟡 |
| 参考UI | UI-01〜03 | EditScreen 表示検証（別途 Compose Test） | 🔵🟡 |

### 信頼性レベル分布（ViewModel 単体テスト 13件）

- 🔵 青信号: 8件（62%）
- 🟡 黄信号: 5件（38%）
- 🔴 赤信号: 0件（0%）

---

## 9. 品質判定

| 判定項目 | 状態 |
|---------|------|
| テストケース分類 | 正常系(4)・異常系(5)・境界値(4) を網羅 |
| 期待値定義 | 各ケースの入力・期待値・検証手段（assertEquals/coVerify）が具体的 |
| 技術選択 | Kotlin + JUnit4 + MockK + coroutines-test + Robolectric に確定（既存テストと同一） |
| 実装可能性 | 確実（`rewriteBody` の TC-0063 系と同型、既存ヘルパー再利用で実装可能） |
| 信頼性レベル | 🔵62% / 🟡38% / 🔴0%（🟡 はオプションUI・境界値の妥当な推測に限定） |

**総合判定**: ✅ 高品質（🔴 赤信号なし。正常系・異常系・境界値を網羅し、TASK-0072 単体テスト要件 TC1〜TC3 を完全にカバー）

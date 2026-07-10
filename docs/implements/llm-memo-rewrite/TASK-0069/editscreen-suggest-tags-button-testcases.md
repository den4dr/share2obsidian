# TASK-0069 TDDテストケース定義書: EditScreen「タグを提案」ボタンUI追加

**機能名**: editscreen-suggest-tags-button
**タスクID**: TASK-0069
**要件名**: llm-memo-rewrite
**フェーズ**: Phase 5 - タグ提案（Should Have）
**作成日**: 2026-07-08

---

## 0. 前提・テスト設計方針

### 0.1 信頼性レベル凡例

- 🔵 **青信号**: 要件定義書・設計文書・既存実装を参考にしてほぼ推測していない
- 🟡 **黄信号**: 要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: 元資料にない推測

### 0.2 テスト対象

- **実装ファイル**:
  - `app/src/main/res/values/strings.xml`（`button_suggest_tags`＝「タグを提案」を新規追加。**現状未追加を確認済み**）
  - `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`（タグ入力欄付近に「タグを提案」`Button` を追加）
- **テストファイル**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt`（既存に追記）
- **追加対象UI**:
  - タグ入力欄（`tagsText` の `OutlinedTextField`）付近に配置する「タグを提案」`Button`（testTag = `suggest_tags_button`）
  - `isSuggestingTags == true` 時のボタン内 `CircularProgressIndicator`（testTag = `suggest_tags_progress`）
- 🔵 *TASK-0069.md 実装詳細・note.md「3. 関連実装」・editscreen-suggest-tags-button-requirements.md §6 より*

### 0.3 本文リライトボタン（TASK-0065）との差異（重要）🔵

「タグを提案」ボタンは本文リライトボタンと同一のローディング表示パターンを踏襲するが、**活性化条件が異なる**点に注意する。

| 観点 | 本文リライト（TASK-0065） | タグ提案（TASK-0069） |
|------|--------------------------|----------------------|
| 活性式 | `rewriteBodyEnabled && !isRewritingBody` | `!isSuggestingTags` のみ |
| プロンプト有無ガード | あり（`rewriteBodyEnabled`） | **なし** |
| 元コンテンツ空時 | 活性は `rewriteBodyEnabled` に依存 | 空でも活性のまま（EDGE-101） |

つまりタグ提案ボタンの非活性条件は「処理中（`isSuggestingTags == true`）」のみであり、`rewriteBodyEnabled` のような活性ガードを持たない。元コンテンツ（`ProcessedContent`）が空文字でもボタンは非活性化しない。

🔵 *editscreen-suggest-tags-button-requirements.md §3「ボタン活性化制約（重要）」・EDGE-101・TASK-0069.md 実装詳細2 の `enabled = !formState.isSuggestingTags` より*

### 0.4 ViewModel 構築方式（技術的判断）🔵

TASK-0065 で確立済みの MockK パターンを踏襲する。既存 `EditScreenTest.kt` には `mockViewModel(...)` ヘルパー（`mockk<EditScreenViewModel>()` ベース）が存在する。本タスクではこれを拡張し、`isSuggestingTags` と `suggestTags()` スタブを扱えるようにする。

- **本タスク新規テスト（TC-11〜TC-14, BC-11〜BC-12）**: `mockk<EditScreenViewModel>()` を用い、`formState`（`MutableStateFlow<EditFormState>`）・`errorEvents`（`MutableSharedFlow<Int>`）・`suggestTags()` をスタブする。`suggestTags()` の呼び出し検証は `verify(exactly = 1) { viewModel.suggestTags() }` で行う。
- **既存 `mockViewModel()` の拡張**: `isSuggestingTags: Boolean = false` パラメータを追加し、`EditFormState` に反映する。`relaxed = true` モックのため `suggestTags()` は既定でスタブ可能だが、明示的に `every { vm.suggestTags() } just Runs` を付与して意図を明確化する。

🔵 *editscreen-rewrite-button-testcases.md §0.4・EditScreenTest.kt 既存 `mockViewModel()`・EditScreenViewModel.kt の `suggestTags()` より*

### 0.5 テストフィクスチャ（拡張案）🔵

```kotlin
// 【共通ヘルパー拡張】: isSuggestingTags を追加した EditScreenViewModel モックを生成する
private fun mockViewModel(
    rewriteBodyEnabled: Boolean = true,
    isRewritingBody: Boolean = false,
    isSuggestingTags: Boolean = false,   // ← TASK-0069 で追加
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
            isSuggestingTags = isSuggestingTags,   // ← TASK-0069 で追加
        )
    )
    every { vm.errorEvents } returns errorEvents
    every { vm.rewriteBody() } just Runs
    every { vm.suggestTags() } just Runs        // ← TASK-0069 で追加
    return vm
}
```

🔵 *EditScreenTest.kt 既存 `mockViewModel()`・EditFormState.kt（`isSuggestingTags` 実装済み）より*

---

## 1. 正常系テストケース（基本的な動作）

### TC-11: 通常状態で「タグを提案」ボタンが表示され活性である

- **テスト名**: `isSuggestingTags=false` のとき「タグを提案」ボタンが表示され活性である
  - **何をテストするか**: `isSuggestingTags == false` のとき、testTag `suggest_tags_button` のボタンが表示され、活性状態で、ラベル文字列「タグを提案」が表示されること
  - **期待される動作**: ボタンが `assertIsDisplayed()` かつ `assertIsEnabled()`、ボタン内に `stringResource(R.string.button_suggest_tags)`（＝「タグを提案」）が表示される
- **入力値**: `mockViewModel(isSuggestingTags = false)`
  - **入力データの意味**: 共有テキストを開いた通常の待機状態（タグ提案を起動できる初期状態）を代表する
- **期待される結果**:
  - `onNodeWithTag("suggest_tags_button").assertIsDisplayed()`
  - `onNodeWithTag("suggest_tags_button").assertIsEnabled()`
  - `onNodeWithText("タグを提案").assertIsDisplayed()`
  - **期待結果の理由**: REQ-103（タグ提案UI表示）・AC-1 の反映。ラベルは strings.xml（`button_suggest_tags`＝「タグを提案」, 本タスクで追加）から取得される（NFR-201）
- **テストの目的**: ボタンの存在・活性・ラベル表示という基本要件の確認
  - **確認ポイント**: testTag が正しく設定されているか、ハードコードでなく stringResource 経由でラベルが表示されるか
- 🔵 *REQ-103・AC-1・TASK-0069.md 実装詳細1,2・strings.xml `button_suggest_tags` より*

### TC-12: ボタン押下で viewModel.suggestTags() が1回呼ばれる

- **テスト名**: 活性状態でボタンを押下すると `suggestTags()` が1回呼ばれる
  - **何をテストするか**: 活性状態のボタンを `performClick()` した際に `viewModel.suggestTags()` が正確に1回呼び出されること
  - **期待される動作**: onClick ハンドラが `viewModel.suggestTags()` を起動する
- **入力値**: `mockViewModel(isSuggestingTags = false)`、`onNodeWithTag("suggest_tags_button").performClick()`
  - **入力データの意味**: ユーザーがタグ提案を起動する中核ユースケース（REQ-103・REQ-301）を代表する
- **期待される結果**:
  - `verify(exactly = 1) { viewModel.suggestTags() }`
  - **期待結果の理由**: 完了条件「ボタン押下で `viewModel.suggestTags()` が呼ばれる」・AC-2。UIはロジックを持たず `suggestTags()` の呼び出しのみを担う（MVVM制約）。呼び出しは1回のみ（多重起動しない）
- **テストの目的**: ボタン押下→ViewModel呼び出しの結線確認
  - **確認ポイント**: `onClick = { viewModel.suggestTags() }` が正しく配線されているか、1回だけ呼ばれるか
- 🔵 *TASK-0069.md 単体テスト要件 テストケース1・完了条件・AC-2 より*

---

## 2. 異常系・状態系テストケース

### TC-13: isSuggestingTags=true のときローディング表示かつ非活性

- **テスト名**: タグ提案処理中はローディングインジケータ表示かつボタン非活性
  - **エラーケースの概要**: LLM呼び出し中（処理中）の状態。二重起動を防ぎつつ処理中であることを可視化する
  - **エラー処理の重要性**: 処理中の多重押下による重複API呼び出しを防止し、UXとして進行状況を伝える（REQ-201, NFR-202）
- **入力値**: `mockViewModel(isSuggestingTags = true)`
  - **不正な理由**: 処理中は新たな起動を受け付けてはならない
  - **実際の発生シナリオ**: ボタン押下後、LLM応答待ちの数秒間（最大30秒タイムアウトまで、REQ-202）
- **期待される結果**:
  - `onNodeWithTag("suggest_tags_progress").assertIsDisplayed()`（ボタン内に `CircularProgressIndicator` 相当ノードが表示される）
  - `onNodeWithTag("suggest_tags_button").assertIsNotEnabled()`
  - 通常ラベル「タグを提案」は表示されない（`onNodeWithText("タグを提案").assertDoesNotExist()`）
  - **エラーメッセージの内容**: エラーではなく処理中インジケータの表示
  - **システムの安全性**: 非活性のため処理中の再押下で `suggestTags()` が再呼び出しされない
- **テストの目的**: `if (isSuggestingTags) CircularProgressIndicator else Text(...)` の分岐と非活性化の確認
  - **品質保証の観点**: 処理中フィードバック（NFR-202）と多重起動防止（REQ-201）を保証
- 🔵 *TASK-0069.md 単体テスト要件 テストケース2・AC-3・REQ-201, NFR-202・TASK-0065 パターンより*

### TC-14: errorEvents 発行時に購読が機能し画面がクラッシュしない（再利用機構の確認）

- **テスト名**: `errorEvents` に resId を emit しても画面がクラッシュせず購読が機能する
  - **エラーケースの概要**: タグ提案（`suggestTags()`）失敗時、ViewModel が `errorEvents` にエラーメッセージのstring resource IDを流す。UIはこれを既存 `LaunchedEffect`+`collectLatest` で購読しToast表示する
  - **エラー処理の重要性**: 失敗をユーザーへ日本語Toastで通知する（NFR-201）。TASK-0065で実装済みの `errorEvents` 機構が `suggestTags()` 失敗も処理することを保証する
- **入力値**: `errorEvents` として `MutableSharedFlow<Int>(extraBufferCapacity = 1)` を注入し、描画後に `errorEvents.tryEmit(R.string.error_llm_network)` を発行
  - **不正な理由**: タグ提案がネットワークエラー等で失敗したことを表す
  - **実際の発生シナリオ**: ネットワーク不通・認証エラー・タイムアウト・空応答
- **期待される結果**:
  - emit 後も EditScreen がクラッシュせず、ボタン等の既存ノードが表示され続ける（`onNodeWithTag("suggest_tags_button").assertExists()`）
  - **エラーメッセージの内容**: Toast文言は `error_llm_*`（日本語）から取得される。Toast自体の表示検証はInstrumented Testでは難易度が高いため、購読セットアップ・emit がクラッシュを起こさないレベルの確認とする
  - **システムの安全性**: `collectLatest` により重複購読・リークが起きない
- **テストの目的**: 既存 `errorEvents` 購読が `suggestTags()` 失敗経路でもUIを破壊しないことの確認（本タスクでの追加実装が不要であることの裏付け）
  - **品質保証の観点**: NFR-201（日本語エラー通知）の購読経路が `suggestTags()` にも適用されることを最低限保証
- 🟡 *editscreen-suggest-tags-button-requirements.md §4「エラーケース」・NFR-201・TASK-0069.md UI/UX要件「エラー表示」より。Toast文言の直接アサートは環境依存のため補助扱い*

---

## 3. 境界値テストケース

### BC-11: isSuggestingTags=false が活性の唯一の条件（活性ガードなし）

- **テスト名**: 非活性条件は `isSuggestingTags` のみ（`rewriteBodyEnabled` の影響を受けない）
  - **境界値の意味**: タグ提案ボタンの活性式 `enabled = !isSuggestingTags` は `isSuggestingTags` の単一Boolean にのみ依存する。本文リライトと異なり `rewriteBodyEnabled` ガードを持たないことを確定する
  - **境界値での動作保証**: `rewriteBodyEnabled = false`（プロンプト未設定相当）でも `isSuggestingTags = false` ならボタンは活性であること
- **入力値**: `mockViewModel(rewriteBodyEnabled = false, isSuggestingTags = false)`
  - **境界値選択の根拠**: 本文リライトボタンが非活性になる条件（`rewriteBodyEnabled = false`）でも、タグ提案ボタンは活性を保つことを検証し、活性ガードの混入（誤って `rewriteBodyEnabled` を参照する回帰）を検出する
  - **実際の使用場面**: 本文用LLMプロンプト未設定のテンプレートでも、タグ提案は常に利用可能である
- **期待される結果**:
  - `onNodeWithTag("suggest_tags_button").assertIsEnabled()`
  - **境界での正確性**: `rewriteBodyEnabled` の値に関わらず活性
  - **一貫した動作**: 非活性化は `isSuggestingTags == true`（TC-13）のときのみ
- **テストの目的**: 活性判定が `isSuggestingTags` のみに依存し、`rewriteBodyEnabled` 等の他フラグに影響されないことの確認
  - **堅牢性の確認**: 本文リライトのコピペ由来で `rewriteBodyEnabled` ガードが誤混入する回帰を検出する
- 🔵 *editscreen-suggest-tags-button-requirements.md §3「ボタン活性化制約」・TASK-0069.md 実装詳細2 `enabled = !formState.isSuggestingTags` より*

### BC-12: EDGE-101 元コンテンツ（本文）が空文字でもボタンは活性のまま

- **テスト名**: 元コンテンツが空でもタグ提案ボタンは非活性化しない（EDGE-101）
  - **境界値の意味**: 空文字という境界入力でも、活性判定は `isSuggestingTags` のみに依存し、元コンテンツの空/非空に影響されないこと
  - **境界値での動作保証**: 空 body/元コンテンツによってボタンが誤って非活性化されないこと（EDGE-101）
- **入力値**: `mockViewModel(isSuggestingTags = false, body = "")`
  - **境界値選択の根拠**: 空文字は最小の入力境界。EDGE-101「元コンテンツが空文字でもボタン非活性化しない」を検証する
  - **実際の使用場面**: 本文を全消去した状態や、共有元が空コンテンツを渡したケースでもタグ提案の起動を許容する
- **期待される結果**:
  - `onNodeWithTag("suggest_tags_button").assertIsEnabled()`
  - **境界での正確性**: body/元コンテンツの空/非空が活性判定に影響しない
  - **一貫した動作**: 活性は `!isSuggestingTags` にのみ依存する
- **テストの目的**: 活性判定が元コンテンツ内容に依存しないことの確認（誤ガード混入の防止）
  - **堅牢性の確認**: 空入力で不用意にボタンを無効化する回帰を検出（EDGE-101 の実装保証）
- 🔵 *EDGE-101・editscreen-suggest-tags-button-requirements.md §4「エッジケース」より*

---

## 4. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: プロジェクト全体が Kotlin + Jetpack Compose で実装されており、対象UIもComposeで記述される
  - **テストに適した機能**: バッククォート日本語テストメソッド名、data class の equals による状態比較、コルーチン対応
- **テストフレームワーク**: Compose UI Test（JUnit4 + `androidx.compose.ui.test`）+ MockK
  - **フレームワーク選択の理由**: Compose Composable のUI検証には `createAndroidComposeRule` が必要。ViewModel（Hilt注入コンストラクタ）とその状態/イベントの供給には MockK を用いる（既存 EditScreenTest.kt の `mockViewModel()` パターン準拠）
  - **テスト実行環境**: AndroidJUnit4（Instrumented Test / `connectedAndroidTest`）。デバイスまたはエミュレータが必須
- 🔵 *note.md「1. 技術スタック / 5. テスト関連情報」・既存 EditScreenTest.kt より*

---

## 5. テストケース実装時の注意・日本語コメント指針

### 5.1 実装時の注意事項

1. **strings.xml への追加が必須**: `button_suggest_tags`＝「タグを提案」は**現状 strings.xml に未追加**（grep で確認済み。requirements.md 特記事項1）。テストで `onNodeWithText("タグを提案")` を成立させるため、Green フェーズで strings.xml に追加する。🔵
2. **`mockViewModel()` の拡張**: 既存ヘルパーに `isSuggestingTags: Boolean = false` パラメータと `every { vm.suggestTags() } just Runs` を追加する（§0.5）。既存テスト（TC-01〜BC-03 等）は既定値のまま影響を受けない（後方互換）。🔵
3. **testTag 付与推奨**: rewriteBody ボタン（`rewrite_body_button` / `rewrite_body_progress`）と同様に、`suggest_tags_button` / `suggest_tags_progress` の testTag を実装側に付与し、`onNodeWithTag` で安定検出する。`CircularProgressIndicator` はテキストを持たないため testTag での検出が必須。🟡 *既存パターン踏襲による妥当な推測*
4. **活性ガードなし**: rewriteBody ボタンの `rewriteBodyEnabled && !isRewritingBody` と異なり、タグ提案ボタンは `enabled = !formState.isSuggestingTags` のみ。BC-11 でこの差異を保証する。🔵
5. **エラー処理は再利用**: `errorEvents` 購読は EditScreen に実装済み（`LaunchedEffect { viewModel.errorEvents.collectLatest { ... } }`）で `suggestTags()` 失敗も処理するため、本タスクで Toast 実装の追加は不要。TC-14 は既存機構がクラッシュしないことのみ確認する。🔵
6. **既存レイアウト非変更**: 既存 `body_field`・`rewrite_body_button`・`bottomBar`（送信/キャンセル）の testTag・配置は変更しない。既存テスト（TC-041, TC-01〜BC-03 等）の後方互換を維持する。🔵

### 5.2 日本語コメント指針（各テスト共通）

#### テストケース開始時

```kotlin
// 【テスト目的】: [このテストで確認する内容]
// 【テスト内容】: [具体的な操作・検証]
// 【期待される動作】: [正常時の結果]
// 🔵🟡🔴 信頼性レベル
```

#### Given / When / Then

```kotlin
// 【テストデータ準備】: mockViewModel(isSuggestingTags = ...) で対象状態のViewModelモックを用意する理由
// 【初期条件設定】: setContent で EditScreen を描画する
// 【実際の処理実行】: onNodeWithTag("suggest_tags_button").performClick() 等
// 【結果検証】: assertIsEnabled / assertIsNotEnabled / verify { suggestTags() } 等
```

#### verify/assert ステートメント

```kotlin
verify(exactly = 1) { viewModel.suggestTags() } // 【確認内容】: 押下でタグ提案が1回起動される 🔵
composeTestRule.onNodeWithTag("suggest_tags_button").assertIsNotEnabled() // 【確認内容】: 処理中は非活性である 🔵
composeTestRule.onNodeWithTag("suggest_tags_progress").assertIsDisplayed() // 【確認内容】: 処理中はローディング表示 🔵
```

---

## 6. テストケース一覧（サマリ）

| ID | 分類 | 内容 | 主な検証 | 対応要件/AC | 信頼性 |
|----|------|------|---------|------------|--------|
| TC-11 | 正常系 | 通常時ボタン表示・活性・ラベル | `assertIsDisplayed`/`assertIsEnabled`/「タグを提案」表示 | REQ-103 / AC-1 | 🔵 |
| TC-12 | 正常系 | 押下で `suggestTags()` 1回 | `verify(exactly=1){ suggestTags() }` | 完了条件 / AC-2 | 🔵 |
| TC-13 | 状態系 | suggesting=true でローディング+非活性 | インジケータ表示 & `assertIsNotEnabled` & ラベル非表示 | REQ-201, NFR-202 / AC-3 | 🔵 |
| TC-14 | 異常系 | errorEvents emit で購読機能・非クラッシュ | emit 後もノード存続 | NFR-201 | 🟡 |
| BC-11 | 境界値 | 活性は `isSuggestingTags` のみ依存（ガードなし） | `rewriteBodyEnabled=false` でも `assertIsEnabled` | REQ-103 / §3 制約 | 🔵 |
| BC-12 | 境界値 | 空元コンテンツでも活性（EDGE-101） | 空 body でも `assertIsEnabled` | EDGE-101 | 🔵 |

**信頼性分布**: 🔵 5件 / 🟡 1件 / 🔴 0件（全6件）

---

## 7. 要件定義との対応関係

- **参照した機能概要**: requirements.md §1（「タグを提案」ボタンUIと `suggestTags()` 呼び出し）
- **参照した入力・出力仕様**: requirements.md §2（ボタンタップ入力、`isSuggestingTags` State、`button_suggest_tags` ラベル、`suggestTags()` 副作用、通常/処理中のUI状態）
- **参照した制約条件**: requirements.md §3（UI文字列リソース、本文リライトと同一ローディングパターン、ボタン活性化制約＝`isSuggestingTags` のみ、EDGE-101）
- **参照した使用例**: requirements.md §4（正常系、EDGE-101、処理中の多重押下防止、LLM呼び出し失敗）
- **参照した受け入れ基準**: requirements.md §7（AC-1〜AC-4）
- **参照したEARS要件**: REQ-103, REQ-201, REQ-202, REQ-301, REQ-302, REQ-406, NFR-201, NFR-202, EDGE-101
- **参照した設計文書**: architecture.md「変更が必要な既存コンポーネント」表（EditFormState/EditScreen）、dataflow.md（タグ提案フロー）
- **参照した実装**: `EditScreen.kt`（既存レイアウト・rewriteBody ボタン）、`EditScreenViewModel.kt`（`suggestTags()`/`errorEvents`）、`EditFormState.kt`（`isSuggestingTags` 実装済み）、`strings.xml`（`button_suggest_tags` 未追加）、既存 `EditScreenTest.kt`（`mockViewModel()` ヘルパー）
- **参考実装**: editscreen-rewrite-button-testcases.md（TASK-0065 テストパターン）

---

## 8. 品質判定

| 評価軸 | 状態 |
|--------|------|
| テストケース分類 | 正常系(2)・状態/異常系(2)・境界値(2) を網羅。活性条件・EDGE-101・ローディングを網羅 |
| 期待値定義 | 各ケースで具体的アサート（`assertIsEnabled`/`assertIsNotEnabled`/`verify`/ノード表示）を明示 |
| 技術選択 | Kotlin + Compose UI Test（AndroidJUnit4）+ MockK に確定 |
| 実装可能性 | 前提の `suggestTags()`/`isSuggestingTags` は実装済み。`mockViewModel()` 拡張方法を明記済み |
| 信頼性レベル | 🔵 5 / 🟡 1 / 🔴 0（大半が確定） |

**総合判定**: ✅ 高品質

---

**作成日**: 2026-07-08 by tsumiki:tdd-testcases

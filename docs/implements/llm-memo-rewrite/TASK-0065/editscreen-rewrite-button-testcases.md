# TASK-0065 TDDテストケース定義書: EditScreen UI「メモを更改」ボタン・ローディング・エラーToast

**機能名**: editscreen-rewrite-button
**タスクID**: TASK-0065
**要件名**: llm-memo-rewrite
**フェーズ**: Phase 3 - 本文リライトUI（Must Have）
**作成日**: 2026-07-06

---

## 0. 前提・テスト設計方針

### 0.1 信頼性レベル凡例

- 🔵 **青信号**: 要件定義書・設計文書・既存実装を参考にしてほぼ推測していない
- 🟡 **黄信号**: 要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: 元資料にない推測

### 0.2 テスト対象

- **実装ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`（既存に追記）
- **テストファイル**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt`（既存に追記）
- **追加対象UI**:
  - `body_field` 付近に配置する「メモを更改」`Button`（testTag = `rewrite_body_button`）
  - `isRewritingBody=true` 時のボタン内 `CircularProgressIndicator`
  - `errorEvents: SharedFlow<Int>` を購読する `LaunchedEffect` + Toast表示
- 🔵 *TASK-0065.md・note.md「3. 関連実装」より*

### 0.3 ViewModel 構築方式（重要な技術的判断）🔵

TASK-0063 で `EditScreenViewModel` は Hilt化され、コンストラクタが
`@Inject constructor(llmRewriteRepository: LlmRewriteRepository, llmSettingsRepository: LlmSettingsRepository)`
に変更された。一方、**既存 `EditScreenTest.kt` は引数なし `EditScreenViewModel()` を使用しており、現状のままではコンパイルできない**（TASK-0063 時点でテスト側が未更新のため）。

したがって TASK-0065 のテスト実装では、以下の方針でViewModelを供給する（note.md「5. テスト関連情報」のMockKパターン準拠）:

- **本タスク新規テスト（TC-01〜TC-07, BC-01〜BC-03）**: `mockk<EditScreenViewModel>()` を用い、`formState`（`MutableStateFlow<EditFormState>`）・`errorEvents`（`MutableSharedFlow<Int>`）・`rewriteBody()`・`buildSendParams()` をスタブする。`rewriteBody()` の呼び出し検証は `verify { viewModel.rewriteBody() }` で行う。
- **既存テストの後方互換**: 引数なし構築が使えないため、共通ヘルパー `createViewModel()` を「モックリポジトリを注入した実 `EditScreenViewModel`」へ更新する必要がある（Red/Green フェーズで対応。詳細は §5「実装時の注意」）。

🔵 *note.md「ViewModel モック（MockK）」・EditScreenViewModel.kt コンストラクタ・EditScreenTest.kt 既存実装より*

### 0.4 テストフィクスチャ（共通）🔵

```kotlin
// 【共通ヘルパー】: 指定した状態を持つ EditScreenViewModel モックを生成する
private fun mockViewModel(
    rewriteBodyEnabled: Boolean = true,
    isRewritingBody: Boolean = false,
    body: String = "テスト本文",
    errorEvents: MutableSharedFlow<Int> = MutableSharedFlow(extraBufferCapacity = 1),
): EditScreenViewModel {
    val vm = mockk<EditScreenViewModel>(relaxed = true)
    every { vm.formState } returns MutableStateFlow(
        EditFormState(
            vault = "testVault",
            title = "テストタイトル",
            body = body,
            tagsText = "shared",
            folder = "70_clippings",
            isRewritingBody = isRewritingBody,
            rewriteBodyEnabled = rewriteBodyEnabled,
        )
    )
    every { vm.errorEvents } returns errorEvents
    every { vm.rewriteBody() } just Runs
    return vm
}
```

🔵 *note.md「ViewModel モック（MockK）」・EditFormState.kt 各フィールドより*

---

## 1. 正常系テストケース（基本的な動作）

### TC-01: 活性状態でボタンが「メモを更改」ラベル付きで表示される

- **テスト名**: 活性状態のとき「メモを更改」ボタンが表示され活性である
  - **何をテストするか**: `rewriteBodyEnabled=true` かつ `isRewritingBody=false` のとき、testTag `rewrite_body_button` のボタンが表示され、活性状態で、ラベル文字列が表示されること
  - **期待される動作**: ボタンが `assertIsDisplayed()` かつ `assertIsEnabled()`、ボタン内に `stringResource(R.string.button_rewrite_body)`（＝「メモを更改」）が表示される
- **入力値**: `mockViewModel(rewriteBodyEnabled = true, isRewritingBody = false)`
  - **入力データの意味**: プロンプト設定済みテンプレートで開いた通常の初期状態（UC-1相当）を代表する
- **期待される結果**:
  - `onNodeWithTag("rewrite_body_button").assertIsDisplayed()`
  - `onNodeWithTag("rewrite_body_button").assertIsEnabled()`
  - `onNodeWithText("メモを更改").assertIsDisplayed()`
  - **期待結果の理由**: REQ-001（ボタン表示）・REQ-102 の活性側の反映。ラベルは strings.xml（`button_rewrite_body`=「メモを更改」, TASK-0064追加分）から取得される
- **テストの目的**: ボタンの存在・活性・ラベル表示という基本要件の確認
  - **確認ポイント**: testTag が正しく設定されているか、ハードコードでなく stringResource 経由でラベルが表示されるか
- 🔵 *REQ-001・TASK-0065.md「実装詳細1」・strings.xml `button_rewrite_body` より*

### TC-02: ボタン押下で viewModel.rewriteBody() が1回呼ばれる（TASK-0065 TC-2）

- **テスト名**: 活性状態でボタンを押下すると rewriteBody() が1回呼ばれる
  - **何をテストするか**: 活性状態のボタンを `performClick()` した際に `viewModel.rewriteBody()` が正確に1回呼び出されること
  - **期待される動作**: onClick ハンドラが `viewModel.rewriteBody()` を起動する
- **入力値**: `mockViewModel(rewriteBodyEnabled = true, isRewritingBody = false)`、`onNodeWithTag("rewrite_body_button").performClick()`
  - **入力データの意味**: ユーザーがリライトを起動する中核ユースケース（UC-1）を代表する
- **期待される結果**:
  - `verify(exactly = 1) { viewModel.rewriteBody() }`
  - **期待結果の理由**: REQ-001。UIはロジックを持たず `rewriteBody()` の呼び出しのみを担う（MVVM制約）。呼び出しは1回のみ（多重起動しない）
- **テストの目的**: ボタン押下→ViewModel呼び出しの結線確認
  - **確認ポイント**: `onClick = { viewModel.rewriteBody() }` が正しく配線されているか、1回だけ呼ばれるか
- 🔵 *TASK-0065.md「単体テスト要件 テストケース2」・要件定義書 TC-2・REQ-001 より*

---

## 2. 異常系・状態系テストケース

### TC-03: rewriteBodyEnabled=false のときボタンが非活性（TASK-0065 TC-1）

- **テスト名**: プロンプト未設定（rewriteBodyEnabled=false）のときボタンが非活性
  - **エラーケースの概要**: テンプレートに本文用LLMプロンプトが設定されていない状態。ボタンは表示されるが押下不可
  - **エラー処理の重要性**: プロンプト未設定でリライトを起動しても意味がなく、無効な操作を防ぐ（REQ-102）
- **入力値**: `mockViewModel(rewriteBodyEnabled = false, isRewritingBody = false)`
  - **不正な理由**: リライトに必要な `bodyLlmPrompt` が空のため起動条件を満たさない
  - **実際の発生シナリオ**: 本文プロンプト未設定のテンプレートを選んで共有・編集した場合（UC-2）
- **期待される結果**:
  - `onNodeWithTag("rewrite_body_button").assertIsDisplayed()`
  - `onNodeWithTag("rewrite_body_button").assertIsNotEnabled()`
  - **エラーメッセージの内容**: Toast等のエラーは出さず、非活性（グレーアウト）で操作を静かに抑止する
  - **システムの安全性**: 押下できないため `rewriteBody()` が呼ばれず、無効なLLM呼び出しが発生しない
- **テストの目的**: 活性判定 `enabled = rewriteBodyEnabled && !isRewritingBody` の false 側の確認
  - **品質保証の観点**: REQ-102 のUI反映が正しく行われ、無効操作を未然に防ぐ
- 🔵 *TASK-0065.md「単体テスト要件 テストケース1」・要件定義書 TC-1・REQ-102 より*

### TC-04: isRewritingBody=true のときローディング表示かつ非活性（TASK-0065 TC-3）

- **テスト名**: リライト処理中はローディングインジケータ表示かつボタン非活性
  - **エラーケースの概要**: LLM呼び出し中（処理中）の状態。二重起動を防ぎつつ処理中であることを可視化する
  - **エラー処理の重要性**: 処理中の多重押下による重複API呼び出しを防止し、UXとして進行状況を伝える（REQ-201, NFR-202）
- **入力値**: `mockViewModel(rewriteBodyEnabled = true, isRewritingBody = true)`
  - **不正な理由**: 処理中は新たな起動を受け付けてはならない
  - **実際の発生シナリオ**: ボタン押下後、LLM応答待ちの数秒間（UC-3）
- **期待される結果**:
  - ボタン配下に `CircularProgressIndicator` 相当ノードが表示される（`onNodeWithTag("rewrite_body_button").onChildren()` にプログレス系ノードが存在、または `hasProgressBarRangeInfo` 相当のセマンティクスで検出）
  - `onNodeWithTag("rewrite_body_button").assertIsNotEnabled()`
  - 通常ラベル「メモを更改」は表示されない（`onNodeWithText("メモを更改").assertDoesNotExist()`）
  - **エラーメッセージの内容**: エラーではなく処理中インジケータの表示
  - **システムの安全性**: 非活性のため処理中の再押下で `rewriteBody()` が再呼び出しされない
- **テストの目的**: `if (isRewritingBody) CircularProgressIndicator else Text(...)` の分岐と非活性化の確認
  - **品質保証の観点**: 処理中フィードバック（NFR-202）と多重起動防止（REQ-201）を保証
- 🔵 *TASK-0065.md「単体テスト要件 テストケース3」・要件定義書 TC-3・REQ-201, NFR-202 より*
- 🟡 *CircularProgressIndicator の具体的なノード検出手段（onChildren / ProgressBarRangeInfo）は Compose Test API からの妥当な選択。testTag 付与での検出に切り替える可能性あり（Red/Green で確定）*

### TC-05: errorEvents 発行時に購読が機能する（Toast購読・TASK-0065 TC-4 任意）

- **テスト名**: errorEvents に resId を emit しても画面がクラッシュせず購読が機能する
  - **エラーケースの概要**: LLMリライト失敗時、ViewModel が `errorEvents` にエラーメッセージのstring resource IDを流す。UIはこれを `LaunchedEffect`+`collectLatest` で購読しToast表示する
  - **エラー処理の重要性**: 失敗をユーザーへ日本語Toastで通知する（NFR-201）。購読漏れやクラッシュがあると失敗が黙殺される
- **入力値**: `errorEvents` として `MutableSharedFlow<Int>(extraBufferCapacity = 1)` を注入し、描画後に `errorEvents.emit(R.string.error_llm_network)` を発行
  - **不正な理由**: リライトがネットワークエラー等で失敗したことを表す（EC-1相当）
  - **実際の発生シナリオ**: ネットワーク不通・認証エラー・タイムアウト・空応答（EDGE-001〜004）
- **期待される結果**:
  - emit 後も EditScreen がクラッシュせず、ボタン等の既存ノードが表示され続ける（`onNodeWithTag("rewrite_body_button").assertExists()`）
  - **エラーメッセージの内容**: Toast文言は `error_llm_*`（日本語, TASK-0064追加分）から取得される。Toast自体の表示検証はInstrumented Testでは難易度が高いため、購読セットアップ・emit がクラッシュを起こさないレベルの確認とする
  - **システムの安全性**: `collectLatest` により重複購読・リークが起きない
- **テストの目的**: `LaunchedEffect(Unit) { errorEvents.collectLatest { ... } }` 購読の存在確認（emit経路がUIを破壊しないこと）
  - **品質保証の観点**: NFR-201（日本語エラー通知）の購読経路が確立していることを最低限保証
- 🟡 *要件定義書 TC-4（任意, Toast検証は emit/購読確認レベルでOK）・NFR-201 より。Toast文言の直接アサートは環境依存のため補助扱い*

---

## 3. 境界値テストケース

### BC-01: rewriteBodyEnabled=true かつ isRewritingBody=false の境界（活性の唯一の成立条件）

- **テスト名**: 活性となる唯一の組合せ（enabled=true かつ rewriting=false）でのみ活性
  - **境界値の意味**: `enabled = rewriteBodyEnabled && !isRewritingBody` は両条件が揃ったときのみ true。活性/非活性の境界を確定する
  - **境界値での動作保証**: 2つのBoolean条件の論理積が正しく評価されること
- **入力値**: `mockViewModel(rewriteBodyEnabled = true, isRewritingBody = false)`
  - **境界値選択の根拠**: 活性が成立する唯一の組合せ。TC-03（enabled=false）/ TC-04（rewriting=true）と対で、真理値表の全境界を網羅する
  - **実際の使用場面**: プロンプト設定済みかつ処理していない待機状態（押下可能状態）
- **期待される結果**:
  - `onNodeWithTag("rewrite_body_button").assertIsEnabled()`
  - **境界での正確性**: この組合せでのみ活性、他の3組合せ（下記）では非活性
  - **一貫した動作**: TC-03（F,F）→非活性 / TC-04（T,T）→非活性 / BC-02（F,T）→非活性 と整合
- **テストの目的**: 活性条件の論理積が境界で正しく評価されることの確認
  - **堅牢性の確認**: 条件の取り違え（`||` 誤用や否定漏れ）を検出する
- 🔵 *TASK-0065.md 実装詳細1 `enabled = formState.rewriteBodyEnabled && !formState.isRewritingBody` より*

### BC-02: rewriteBodyEnabled=false かつ isRewritingBody=true の境界（両方非活性要因）

- **テスト名**: 未設定かつ処理中の組合せでも非活性
  - **境界値の意味**: 非活性化要因が2つ同時に成立するケース。論理積が false になることの確認
  - **境界値での動作保証**: いずれか一方でも非活性要因があれば非活性
- **入力値**: `mockViewModel(rewriteBodyEnabled = false, isRewritingBody = true)`
  - **境界値選択の根拠**: 真理値表 (F,T) の網羅。理論上は稀だが状態遷移の過渡で起こり得る
  - **実際の使用場面**: プロンプト未設定にもかかわらず何らかの処理中フラグが立った異常過渡状態
- **期待される結果**:
  - `onNodeWithTag("rewrite_body_button").assertIsNotEnabled()`
  - **境界での正確性**: 非活性要因が重複しても矛盾なく非活性
  - **一貫した動作**: 活性は BC-01 の (T,F) のみという不変条件を保つ
- **テストの目的**: 論理積の全境界網羅（真理値表4通りのうち残り1通り）
  - **堅牢性の確認**: 複数非活性要因の同時成立でも安定して非活性
- 🟡 *TASK-0065.md 実装詳細1 の活性式から導出した妥当な境界（明示のTC番号はなく真理値表網羅のための補完）*

### BC-03: 空 sourceContent でも活性状態は rewriteBodyEnabled のみに依存する

- **テスト名**: 本文が空でもプロンプト設定済みならボタンは活性のまま
  - **境界値の意味**: 空文字という境界入力でも、活性判定は `rewriteBodyEnabled`（＝プロンプト有無）のみに依存し、本文の空/非空に影響されないこと
  - **境界値での動作保証**: 空 body によってボタンが誤って非活性化されないこと（EDGE-101）
- **入力値**: `mockViewModel(rewriteBodyEnabled = true, isRewritingBody = false, body = "")`
  - **境界値選択の根拠**: 空文字は最小の入力境界。リライト対象が空でも起動可能という仕様（EDGE-101/BC-1）を検証
  - **実際の使用場面**: 本文を全消去した状態でもプロンプト設定済みならリライト実行を許容する
- **期待される結果**:
  - `onNodeWithTag("rewrite_body_button").assertIsEnabled()`
  - **境界での正確性**: body の空/非空が活性判定に影響しない
  - **一貫した動作**: 活性は `rewriteBodyEnabled && !isRewritingBody` にのみ依存する
- **テストの目的**: 活性判定が本文内容に依存しないことの確認（誤ガード混入の防止）
  - **堅牢性の確認**: 空入力で不用意にボタンを無効化する回帰を検出
- 🔵 *要件定義書 §4.3 BC-1・EDGE-101 より*

---

## 4. 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: プロジェクト全体がKotlin + Jetpack Compose で実装されており、対象UIもComposeで記述される
  - **テストに適した機能**: バッククォート日本語テストメソッド名、data class の equals による状態比較、コルーチン対応
- **テストフレームワーク**: Compose UI Test（JUnit4 + `androidx.compose.ui.test`）+ MockK
  - **フレームワーク選択の理由**: Compose Composable のUI検証には `createAndroidComposeRule` が必要。ViewModel（Hilt注入コンストラクタ）とその状態/イベントの供給には MockK を用いる（note.md 準拠）
  - **テスト実行環境**: AndroidJUnit4（Instrumented Test / `connectedAndroidTest`）。デバイスまたはエミュレータが必須
- 🔵 *note.md「1. 技術スタック / 5. テスト関連情報」・既存 EditScreenTest.kt より*

---

## 5. テストケース実装時の注意・日本語コメント指針

### 5.1 実装時の注意事項

1. **ViewModel構築の統一**: 既存 `createViewModel()`（引数なし `EditScreenViewModel()`）は現行コンストラクタ（Hilt注入）と非互換。Red/Green フェーズで以下いずれかへ更新する:
   - モックリポジトリ（`mockk<LlmRewriteRepository>()`, `mockk<LlmSettingsRepository>()`）を注入した実 `EditScreenViewModel(...)` を返すよう `createViewModel()` を修正（既存テストの後方互換維持に有効）
   - 本タスク新規テストは `mockk<EditScreenViewModel>()` ベース（§0.4）で `formState`/`errorEvents`/`rewriteBody()` をスタブ
   🔵 *EditScreenViewModel.kt コンストラクタ・EditScreenTest.kt 現状より*
2. **必要な依存追加**: `androidTest` に MockK（`io.mockk:mockk-android`）が未導入の場合は追加が必要。`kotlinx-coroutines-test`（`MutableSharedFlow` emit のための `runTest` 等）も利用。🟡 *note.md 依存記載より（実際の導入状況はGreen前に要確認）*
3. **CircularProgressIndicator の検出**: セマンティクス上テキストを持たないため、`onNodeWithText` では検出不可。ボタン子ノード探索（`onChildren`）または `ProgressBarRangeInfo`、もしくは実装側でインジケータに testTag（例: `rewrite_body_progress`）を付与して `onNodeWithTag` で検出する方針を推奨。🟡
4. **body_field/bottomBar 非変更**: 既存 `body_field` の testTag および `bottomBar`（送信/キャンセル）は変更しない。既存テスト（TC-041, TC-NFR-102-01 等）の後方互換を維持する。🔵 *要件定義書 §3 レイアウト互換性制約より*

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
// 【テストデータ準備】: mockViewModel(...) で対象状態のViewModelモックを用意する理由
// 【初期条件設定】: setContent で EditScreen を描画する
// 【実際の処理実行】: onNodeWithTag("rewrite_body_button").performClick() 等
// 【結果検証】: assertIsEnabled / assertIsNotEnabled / verify { rewriteBody() } 等
```

#### expect/verify ステートメント

```kotlin
verify(exactly = 1) { viewModel.rewriteBody() } // 【確認内容】: 押下でリライトが1回起動される 🔵
composeTestRule.onNodeWithTag("rewrite_body_button").assertIsNotEnabled() // 【確認内容】: 非活性である 🔵
```

---

## 6. テストケース一覧（サマリ）

| ID | 分類 | 内容 | 主な検証 | 対応要件/TASK-TC | 信頼性 |
|----|------|------|---------|-----------------|--------|
| TC-01 | 正常系 | 活性時ボタン表示・活性・ラベル | `assertIsDisplayed`/`assertIsEnabled`/「メモを更改」表示 | REQ-001 | 🔵 |
| TC-02 | 正常系 | 押下で `rewriteBody()` 1回 | `verify(exactly=1){ rewriteBody() }` | TC-2 / REQ-001 | 🔵 |
| TC-03 | 状態系 | enabled=false で非活性 | `assertIsNotEnabled` | TC-1 / REQ-102 | 🔵 |
| TC-04 | 状態系 | rewriting=true でローディング+非活性 | インジケータ表示 & `assertIsNotEnabled` & ラベル非表示 | TC-3 / REQ-201, NFR-202 | 🔵 |
| TC-05 | 異常系 | errorEvents emit で購読機能・非クラッシュ | emit 後もノード存続 | TC-4 / NFR-201 | 🟡 |
| BC-01 | 境界値 | (enabled=T, rewriting=F) のみ活性 | `assertIsEnabled` | REQ-102 | 🔵 |
| BC-02 | 境界値 | (enabled=F, rewriting=T) で非活性 | `assertIsNotEnabled` | REQ-102/201 | 🟡 |
| BC-03 | 境界値 | 空 body でも活性は enabled のみ依存 | `assertIsEnabled` | EDGE-101 | 🔵 |

**信頼性分布**: 🔵 6件 / 🟡 2件 / 🔴 0件（全8件）

---

## 7. 要件定義との対応関係

- **参照した機能概要**: 要件定義書 §1（「メモを更改」ボタンUIとローディング・失敗フィードバック）
- **参照した入力・出力仕様**: 要件定義書 §2.2（購読状態 `rewriteBodyEnabled`/`isRewritingBody`/`errorEvents`）・§2.3（ボタン押下→`rewriteBody()`、活性式、ボタン内表示、エラーToast）
- **参照した制約条件**: 要件定義書 §3（UIレイヤ責務・文字列リソース・レイアウト互換性・購読安全性・インジケータサイズ/配置）
- **参照した使用例**: 要件定義書 §4（UC-1〜3、EC-1〜4、BC-1）
- **参照したEARS要件**: REQ-001, REQ-102, REQ-201, REQ-202, NFR-201, NFR-202, EDGE-001〜004, EDGE-101
- **参照した設計文書**: architecture.md「変更が必要な既存コンポーネント（EditScreen.kt）」、design-interview.md Q3（SharedFlow→LaunchedEffect+collectLatest+Toast）、dataflow.md
- **参照した実装**: `EditScreen.kt`（既存レイアウト）、`EditScreenViewModel.kt`（`rewriteBody()`/`errorEvents`/コンストラクタ）、`EditFormState.kt`（`isRewritingBody`/`rewriteBodyEnabled`）、`strings.xml`（`button_rewrite_body`/`error_llm_*`）、既存 `EditScreenTest.kt`

---

## 8. 品質判定

| 評価軸 | 状態 |
|--------|------|
| テストケース分類 | 正常系(2)・異常/状態系(3)・境界値(3) を網羅。活性真理値表4通りも網羅 |
| 期待値定義 | 各ケースで具体的アサート（`assertIsEnabled`/`assertIsNotEnabled`/`verify`/ノード表示）を明示 |
| 技術選択 | Kotlin + Compose UI Test（AndroidJUnit4）+ MockK に確定 |
| 実装可能性 | 前提のViewModel/文字列は実装済み。ViewModel構築方式の注意点を明記済み |
| 信頼性レベル | 🔵 6 / 🟡 2 / 🔴 0（大半が確定） |

**総合判定**: ✅ 高品質

---

**作成日**: 2026-07-06 by tsumiki:tdd-testcases

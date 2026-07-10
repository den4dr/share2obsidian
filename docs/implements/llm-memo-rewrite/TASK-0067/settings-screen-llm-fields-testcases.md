# TDDテストケース定義書: SettingsScreen LLM設定入力欄追加

**機能名**: settings-screen-llm-fields
**タスクID**: TASK-0067
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06
**フェーズ**: Phase 4 - LLM設定UI

---

## 【信頼性レベル凡例】

- 🔵 **青信号**: EARS要件定義書・設計文書・既存実装を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書・既存実装から妥当な推測をしている
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測をしている

---

## 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: プロジェクト全体がKotlinで実装されており、Jetpack Compose UIもKotlin DSLで記述されているため。既存の `SettingsScreen.kt` / `SettingsScreenTest.kt` がKotlinで書かれている。
  - **テストに適した機能**: バッククォート関数名による日本語テスト名、data classによるFake実装の簡潔な記述、Flow/Coroutinesによる非同期状態のテストが容易。
- **テストフレームワーク**: Jetpack Compose UI Test + JUnit 4 + AndroidJUnit4
  - **フレームワーク選択の理由**: 対象がCompose UI（`SettingsScreen`）であり、`composeTestRule.onNodeWithTag()` / `performTextInput()` / `assertTextContains()` によるUIノード操作・検証が必要。既存 `SettingsScreenTest.kt` が同フレームワークを採用済み。
  - **テスト実行環境**: 実機またはエミュレータ上で `mise exec -- ./gradlew connectedAndroidTest`（`app/src/androidTest/`）で実行。
- 🔵 信頼性レベル: note.md「2. 開発ルール」「5. テスト関連情報」・既存 `SettingsScreenTest.kt` より、ほぼ推測なし

---

## テスト実装上の重要な前提（設計メモ）

> 🔵 信頼性レベル: 既存 `SettingsViewModel.kt`（`combine().stateIn()`）・`SettingsScreenTest.kt`（`FakeLlmSettingsRepository`）の実装より

- **update関数の呼び出し検証方法**: `SettingsViewModel.updateLlm*()` は `llmSettingsRepository.save*()` へ引数をそのまま委譲する。ViewModel自体に呼び出し記録機能はないため、**入力値で `update` が呼ばれたことの検証は、`save*()` に渡された引数を記録する Fake（RecordingFake）で行う**。
- **静的Flowによる制約**: 既存 `FakeLlmSettingsRepository.getSettings()` は `flowOf(settings)` を返す静的Flowであり、入力後に `uiState` は再emitされない。そのため `OutlinedTextField` は「制御されているが値が更新されない」状態となり、入力テキストは画面上に反映されない。→ TC-1〜TC-3 は**表示テキストではなく `save*()` の記録引数**で検証する。1回の `performTextInput(fullString)` で `onValueChange` は入力全体で発火する。
- **初期値表示テスト（TC-5）**: Fakeへ初期 `LlmSettings` を注入することで `uiState` に初期値が反映される。endpoint/model は `assertTextContains` で、apiKey はマスク（平文非表示）で検証する。
- **testTag**: `settings_llm_endpoint_field` / `settings_llm_apikey_field` / `settings_llm_model_field`。

---

## 1. 正常系テストケース（基本的な動作）

### TC-N-01: endpointUrl欄への入力で saveEndpointUrl が入力値で呼ばれる（TC-1）

- **テスト名**: endpointUrl欄への入力でupdateLlmEndpointUrl（saveEndpointUrl）が呼ばれる
  - **何をテストするか**: `settings_llm_endpoint_field` にテキストを入力したとき、`viewModel.updateLlmEndpointUrl()` を経由して `LlmSettingsRepository.saveEndpointUrl()` が入力値で呼ばれること。
  - **期待される動作**: `onValueChange` → `updateLlmEndpointUrl(input)` → `saveEndpointUrl(input)` の即時保存フローが機能する。
- **入力値**: `"https://api.example.com/v1"`（endpoint欄へ `performTextInput`）
  - **入力データの意味**: 実際のLLM APIエンドポイントURLを代表する妥当な文字列。
- **期待される結果**: RecordingFakeに記録された `savedEndpointUrl == "https://api.example.com/v1"`
  - **期待結果の理由**: REQ-004により、入力の都度対応する `update` 関数へ入力値がそのまま渡され保存される（バリデーション・整形なし）。
- **テストの目的**: endpoint欄とViewModel/Repositoryの配線が正しいことを確認する。
  - **確認ポイント**: 記録された値が入力値と完全一致すること（加工されていないこと）。
- 🔵 信頼性レベル: TASK-0067.md テストケース1・requirements.md TC-1・REQ-004より

### TC-N-02: apiKey欄への入力で saveApiKey が入力値で呼ばれる（TC-2）

- **テスト名**: apiKey欄への入力でupdateLlmApiKey（saveApiKey）が呼ばれる
  - **何をテストするか**: `settings_llm_apikey_field` にテキストを入力したとき、`saveApiKey()` が入力値で呼ばれること。
  - **期待される動作**: マスク表示中でも `onValueChange` は平文の入力値を受け取り、`updateLlmApiKey(input)` → `saveApiKey(input)` が呼ばれる。
- **入力値**: `"sk-test-1234567890"`（apiKey欄へ `performTextInput`）
  - **入力データの意味**: 一般的なAPIキー形式（`sk-` プレフィックス）を代表する文字列。
- **期待される結果**: RecordingFakeに記録された `savedApiKey == "sk-test-1234567890"`
  - **期待結果の理由**: マスクは表示層（`visualTransformation`）のみの変換であり、保持値・保存値は平文（requirements.md 2.3）。
- **テストの目的**: マスク表示があっても保存値は平文で正しく渡ることを確認する。
  - **確認ポイント**: マスクによって保存値が変質しないこと。
- 🔵 信頼性レベル: TASK-0067.md テストケース2・requirements.md TC-2・REQ-004より

### TC-N-03: model欄への入力で saveModel が入力値で呼ばれる（TC-3）

- **テスト名**: model欄への入力でupdateLlmModel（saveModel）が呼ばれる
  - **何をテストするか**: `settings_llm_model_field` にテキストを入力したとき、`saveModel()` が入力値で呼ばれること。
  - **期待される動作**: `onValueChange` → `updateLlmModel(input)` → `saveModel(input)`。
- **入力値**: `"gpt-4o"`（model欄へ `performTextInput`）
  - **入力データの意味**: 実在するLLMモデル名を代表する文字列。
- **期待される結果**: RecordingFakeに記録された `savedModel == "gpt-4o"`
  - **期待結果の理由**: REQ-004により入力値がそのまま保存される。
- **テストの目的**: model欄の配線が正しいことを確認する。
  - **確認ポイント**: 記録された値が入力値と一致すること。
- 🔵 信頼性レベル: TASK-0067.md テストケース3・requirements.md TC-3・REQ-004より

### TC-N-04: 画面起動時に uiState の初期値が各入力欄に反映される（TC-5）

- **テスト名**: 画面起動時にuiStateの初期値が各入力欄に反映される
  - **何をテストするか**: Fakeへ初期 `LlmSettings` を注入した状態で `SettingsScreen` を表示すると、endpoint欄とmodel欄に初期値が表示されること。
  - **期待される動作**: `uiState`（`llmEndpointUrl` / `llmModel`）が各 `OutlinedTextField` の `value` に反映され表示される。
- **入力値**: `LlmSettings(endpointUrl = "https://example.com", apiKey = "sk-xxxx", model = "gpt-4o")` をFakeに設定
  - **入力データの意味**: 既にLLM設定が保存済みで設定画面を開くユースケース（UC-2）を代表する。
- **期待される結果**:
  - `settings_llm_endpoint_field` が `"https://example.com"` を含んで表示
  - `settings_llm_model_field` が `"gpt-4o"` を含んで表示
  - （apiKeyの平文表示検証はTC-B-02で扱う）
  - **期待結果の理由**: 完了条件「画面起動時にuiStateの初期値が各入力欄に反映される」より。
- **テストの目的**: 単方向データフロー（uiState→表示）が機能することを確認する。
  - **確認ポイント**: endpoint/modelの平文値が正しく表示されること。
- 🔵 信頼性レベル: TASK-0067.md テストケース5・requirements.md TC-5/UC-2より

### TC-N-05: LLM設定3入力欄がすべて画面に表示される

- **テスト名**: LLM設定3入力欄がすべて表示される
  - **何をテストするか**: `SettingsScreen` にendpoint/apiKey/modelの3つのtestTagノードが存在し表示されること。
  - **期待される動作**: LLM設定セクションが追加され、3欄が描画される。
- **入力値**: デフォルト状態（`LlmSettings()`、全フィールド `""`）で `SettingsScreen` を表示
  - **入力データの意味**: 新規ユーザーが初めて設定画面を開くユースケース（UC-1前提）。
- **期待される結果**: `settings_llm_endpoint_field` / `settings_llm_apikey_field` / `settings_llm_model_field` がすべて `assertIsDisplayed()`
  - **期待結果の理由**: 完了条件「LLM設定用のOutlinedTextFieldが3つ追加されている」より。
- **テストの目的**: LLM設定セクションの存在（3欄の追加）を確認する。
  - **確認ポイント**: testTagが仕様どおり命名され、3欄すべてが可視であること。
- 🔵 信頼性レベル: TASK-0067.md 完了条件・requirements.md 3.4 testTag命名制約より

---

## 2. 異常系テストケース（エラーハンドリング・後方互換）

### TC-E-01: 入力欄を空文字にすると save が空文字で呼ばれる（EC-1）

- **テスト名**: apiKey欄を空文字入力するとsaveApiKeyが空文字で呼ばれる
  - **エラーケースの概要**: 入力済みの値をクリアして空文字にするケース。UI層はバリデーションを行わないため空文字もそのまま保存へ委譲する。
  - **エラー処理の重要性**: 空文字を弾かず素通しする（＝設定解除できる）ことが仕様であり、意図せぬ例外やクラッシュが起きないことを保証する必要がある。
- **入力値**: 初期値 `apiKey = "sk-existing"` の状態から、`performTextClearance()` で空文字化（または空文字を入力）
  - **不正な理由**: 「不正」ではなく境界的な入力。ただしAPIキーとしては無効値であり、実運用では設定解除操作にあたる。
  - **実際の発生シナリオ**: ユーザーがAPIキーを削除して再入力しようとする、または設定を無効化する場面。
- **期待される結果**: RecordingFakeに記録された `savedApiKey == ""`（空文字での保存委譲）
  - **エラーメッセージの内容**: 本タスクではエラーメッセージは発生しない（バリデーションなし）。
  - **システムの安全性**: 空文字入力でも例外が発生せず、`update` 関数が空文字で正常に呼ばれる。
- **テストの目的**: 空文字入力がバリデーションで弾かれず、そのまま保存委譲されることを確認する。
  - **品質保証の観点**: TASK-0066 TC-B-01「空文字保存」との整合を担保し、設定解除操作の安全性を保証する。
- 🟡 信頼性レベル: requirements.md 4.2 EC-1（TASK-0066 TC-B-01由来の妥当な推測）より

### TC-E-02: 既存のvault/folder/テンプレート管理テストが引き続き成功する（後方互換）

- **テスト名**: LLM設定欄追加後も既存SettingsScreenテストが成功する
  - **エラーケースの概要**: 新規UI追加によって既存機能（vault/folder入力・テンプレート管理ナビゲーション表示）が壊れる回帰（レグレッション）を検出する。
  - **エラー処理の重要性**: 既存機能の破壊は最も避けるべきリスクであり、後方互換性の担保が必須（requirements.md 3.2）。
- **入力値**: 既存テスト（`templateManagementItem_callsOnNavigateToTemplates` / `settingsScreen_showsTemplateManagementItem` / `settingsScreen_showsVaultAndFolderValues`）をそのまま実行
  - **不正な理由**: 入力自体は正当。既存の期待動作が維持されているかを確認する回帰テスト。
  - **実際の発生シナリオ**: LLM設定セクション追加のためColumn構造やDivider配置を変更した際に、既存要素の表示・操作が影響を受ける場面。
- **期待される結果**: 既存3テストがすべて成功する（「テンプレート管理」表示・タップ、vault/folder値表示）。
  - **エラーメッセージの内容**: 失敗時はどの既存アサーションが壊れたかを示す（既存テストのメッセージを利用）。
  - **システムの安全性**: LLM設定欄の追加が既存UIの構造・可視性を損なわない。
- **テストの目的**: 変更範囲がView層に限定され既存機能に影響しないことを確認する。
  - **品質保証の観点**: リグレッション防止。requirements.md 3.2「既存UIの後方互換」を担保する。
- 🔵 信頼性レベル: requirements.md 3.2 互換性要件・既存 `SettingsScreenTest.kt` より

---

## 3. 境界値テストケース（デフォルト値・マスク・最大長）

### TC-B-01: 全フィールドがデフォルト空文字でも入力欄が空欄で正常表示される（EC-2）

- **テスト名**: LLM設定が未設定（全フィールド空文字）でも入力欄が空欄で正常表示される
  - **境界値の意味**: 「未設定＝空文字」という初期状態の境界。null非許容でデフォルトが `""` のため、空状態が最小値ケースとなる。
  - **境界値での動作保証**: 何も保存されていない状態でクラッシュせず空欄が表示されること。
- **入力値**: `LlmSettings()`（endpointUrl/apiKey/model すべて `""`）
  - **境界値選択の根拠**: requirements.md 2.1のデフォルト値 `""`、UC-1（新規入力前）の初期状態。
  - **実際の使用場面**: アプリ導入直後、まだLLM設定を一度も入力していないユーザーが設定画面を開く場面。
- **期待される結果**: 3欄すべてが例外なく `assertIsDisplayed()`（空欄として表示）。プレースホルダ/ラベルのみ表示され値テキストは空。
  - **境界での正確性**: 空文字が `value=""` として正しく扱われ、`null` 参照等が起きない。
  - **一貫した動作**: 値あり（TC-N-04）と値なし（本ケース）で表示ロジックが一貫する。
- **テストの目的**: デフォルト空文字状態の堅牢性を確認する。
  - **堅牢性の確認**: 最小入力（空）でもUIが安定描画されること。
- 🟡 信頼性レベル: requirements.md 4.2 EC-2（null非許容・デフォルト空文字）より

### TC-B-02: apiKey欄が非空値でマスク表示され平文が表示されない（TC-4 / EC-3）

- **テスト名**: apiKey欄の表示内容がマスクされている（平文非表示）
  - **境界値の意味**: 「空 vs 非空」の境界で、非空の機微情報が平文で漏れない表示上の境界。
  - **境界値での動作保証**: 非空のapiKeyが必ずマスク文字へ変換され、平文が画面テキストとして露出しないこと。
- **入力値**: `LlmSettings(apiKey = "sk-secret-value")` をFakeに設定して `SettingsScreen` を表示
  - **境界値選択の根拠**: マスク検証には非空文字列が必須（空文字ではマスク有無を判別不能）。
  - **実際の使用場面**: 保存済みAPIキーを持つユーザーが設定画面を開き、第三者に平文を見られたくない場面（ショルダーサーフィン対策）。
- **期待される結果**: `settings_llm_apikey_field` のノードに平文 `"sk-secret-value"` が**含まれない**（`assertTextContains("sk-secret-value")` が成立しない＝マスク文字に変換されている）。
  - **境界での正確性**: `PasswordVisualTransformation()` が適用され、EditableText/表示テキストのセマンティクス上に平文が現れない。
  - **一貫した動作**: endpoint/model欄（マスクなし）は平文表示、apiKey欄のみマスクという差異が仕様どおり。
- **テストの目的**: apiKey欄のマスク表示（REQ-401補完）を確認する。
  - **堅牢性の確認**: 機微情報がUI層で露出しないこと。
- 🟡 信頼性レベル: TASK-0067.md テストケース4・requirements.md TC-4/EC-3（マスクは妥当な推測）より

### TC-B-03: 長い文字列を入力しても入力値がそのまま save に渡る（最大長境界）

- **テスト名**: endpointUrlに長い文字列を入力してもsaveEndpointUrlに完全な値が渡る
  - **境界値の意味**: 入力長の上限側境界。UI層に長さ制限はないため、長文でも切り詰め・加工なく渡ることを確認する。
  - **境界値での動作保証**: 長い入力でも `onValueChange` が全文字を受け取り、保存値が入力と一致すること。
- **入力値**: 長いURL文字列（例: `"https://api.example.com/v1/" + "a".repeat(200)`）
  - **境界値選択の根拠**: クエリパラメータ付き長大URLを想定した上限側の代表値。
  - **実際の使用場面**: プロキシ・パス・クエリを含む長いエンドポイントURLを設定する場面。
- **期待される結果**: RecordingFakeの `savedEndpointUrl` が入力文字列と完全一致（切り詰めなし）。
  - **境界での正確性**: 長文でも文字欠落・切り詰めが起きない。
  - **一貫した動作**: 短い入力（TC-N-01）と長い入力で保存挙動が一貫する。
- **テストの目的**: 入力長の境界でデータ欠損が起きないことを確認する。
  - **堅牢性の確認**: 長大入力に対する安定性。
- 🟡 信頼性レベル: requirements.md 2.2「入力値そのまま渡す（加工なし）」からの妥当な推測（具体長は例示）

---

## 4. テストケース実装時の日本語コメント指針

各テストケース実装時には、Given/When/Then 構造に沿って以下の日本語コメントを含める。

#### テストケース開始時のコメント

```kotlin
// 【テスト目的】: endpoint欄への入力で saveEndpointUrl が入力値で呼ばれることを確認する
// 【テスト内容】: settings_llm_endpoint_field に performTextInput し、RecordingFake の記録値を検証する
// 【期待される動作】: onValueChange → updateLlmEndpointUrl → saveEndpointUrl(input) が発火する
// 🔵 信頼性レベル: TASK-0067.md テストケース1・REQ-004より
```

#### Given（準備フェーズ）のコメント

```kotlin
// 【テストデータ準備】: save 呼び出しを記録する RecordingFakeLlmSettingsRepository を生成する
// 【初期条件設定】: SettingsViewModel に Fake を注入し、SettingsScreen を setContent で表示する
// 【前提条件確認】: uiState 初期値は空文字（LlmSettings() デフォルト）である
```

#### When（実行フェーズ）のコメント

```kotlin
// 【実際の処理実行】: onNodeWithTag("settings_llm_endpoint_field").performTextInput(input) を実行
// 【処理内容】: OutlinedTextField の onValueChange 経由で updateLlmEndpointUrl が呼ばれる
// 【実行タイミング】: 画面表示完了後、入力操作を1回行う
```

#### Then（検証フェーズ）のコメント

```kotlin
// 【結果検証】: RecordingFake に記録された savedEndpointUrl を確認する
// 【期待値確認】: 記録値が入力値と完全一致すること（加工されていないこと）
// 【品質保証】: 入力→保存の配線正当性を担保し、設定が確実に永続化されることを保証する
assertEquals("https://api.example.com/v1", fake.savedEndpointUrl) // 【確認内容】: 入力値がそのまま保存委譲されたことを確認
```

#### セットアップ・クリーンアップのコメント

```kotlin
// createAndroidComposeRule はテストごとに独立した Activity/Compose 環境を提供するため、
// 明示的な beforeEach/afterEach は不要（各 @Test が独立した composeTestRule を利用）。
// RecordingFake はテストメソッド内でローカルに生成し、テスト間の状態共有を避ける。
```

#### RecordingFake 実装イメージ（テスト用ユーティリティ）

```kotlin
// 【テスト対応】: update 関数の呼び出し引数を検証するための記録用 Fake
// 静的 flowOf のため uiState は再emitされない点に留意（表示ではなく記録引数で検証する）
private class RecordingFakeLlmSettingsRepository(
    private val settings: LlmSettings = LlmSettings(),
) : LlmSettingsRepository {
    var savedEndpointUrl: String? = null
    var savedApiKey: String? = null
    var savedModel: String? = null
    override fun getSettings(): Flow<LlmSettings> = flowOf(settings)
    override suspend fun saveEndpointUrl(url: String) { savedEndpointUrl = url }
    override suspend fun saveApiKey(apiKey: String) { savedApiKey = apiKey }
    override suspend fun saveModel(model: String) { savedModel = model }
}
```

---

## 5. 要件定義との対応関係

- **参照した機能概要**: requirements.md「1. 機能の概要」（SettingsScreenへLLM3入力欄追加、apiKeyマスク、即時保存）
- **参照した入力・出力仕様**: requirements.md「2. 入力・出力の仕様」（uiState 3フィールド読み取り、`onValueChange` → `updateLlm*`、マスクは表示層のみ）
- **参照した制約条件**: requirements.md「3. 制約条件」（testTag命名 3.4、マスク 3.1、後方互換 3.2、View層限定 3.3）
- **参照した使用例**: requirements.md「4. 想定される使用例」（UC-1新規入力、UC-2既存値表示、EC-1空文字、EC-2未設定、EC-3マスク）
- **参照した受け入れ基準**: requirements.md「6. 受け入れ基準」TC-1〜TC-5、TASK-0067.md「単体テスト要件」

### テストケース ↔ 受け入れ基準 対応表

| テストケース | 対応する受け入れ基準 | 対応REQ | 信頼性 |
|-------------|--------------------|---------|--------|
| TC-N-01 | TC-1（endpoint入力でupdate呼び出し） | REQ-004 | 🔵 |
| TC-N-02 | TC-2（apiKey入力でupdate呼び出し） | REQ-004 | 🔵 |
| TC-N-03 | TC-3（model入力でupdate呼び出し） | REQ-004 | 🔵 |
| TC-N-04 | TC-5（初期値反映） | REQ-004 | 🔵 |
| TC-N-05 | 完了条件（3欄追加） | REQ-004 | 🔵 |
| TC-E-01 | EC-1（空文字保存） | REQ-004 | 🟡 |
| TC-E-02 | 3.2（後方互換） | REQ-021/既存 | 🔵 |
| TC-B-01 | EC-2（未設定デフォルト表示） | REQ-004 | 🟡 |
| TC-B-02 | TC-4（apiKeyマスク） | REQ-401補完 | 🟡 |
| TC-B-03 | 2.2（入力値そのまま保存） | REQ-004 | 🟡 |

---

## 6. テストケース網羅性サマリー

| カテゴリ | 件数 | テストケースID |
|---------|------|---------------|
| 正常系 | 5 | TC-N-01〜TC-N-05 |
| 異常系 | 2 | TC-E-01, TC-E-02 |
| 境界値 | 3 | TC-B-01〜TC-B-03 |
| **合計** | **10** | - |

### 信頼性レベル分布

| 信頼性 | 件数 | 割合 |
|--------|------|------|
| 🔵 青信号 | 6 | 60% |
| 🟡 黄信号 | 4 | 40% |
| 🔴 赤信号 | 0 | 0% |

- 🟡黄信号（TC-E-01/TC-B-01/TC-B-02/TC-B-03）はいずれも「マスク表示」「空文字/デフォルト/最大長の境界挙動」というUI詳細に集中しており、既存パターン・一般的UIプラクティス・TASK-0066テストからの妥当な推測に留まる。🔴赤信号（根拠なし推測）はゼロ。

---

## 7. 実装対象テストファイル

- **変更**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/SettingsScreenTest.kt`
  - TC-N-01〜TC-B-03 の10テストメソッドを追加
  - `RecordingFakeLlmSettingsRepository`（save引数記録用Fake）を追加
  - `createViewModel()` に RecordingFake を注入するオーバーロード追加を検討

---

## 8. 品質判定

```
✅ 高品質:
- テストケース分類: 正常系5・異常系2・境界値3で網羅（合計10ケース）
- 期待値定義: 各ケースの入力値・期待結果・検証手段（記録引数/表示/マスク）が明確
- 技術選択: Kotlin + Jetpack Compose UI Test + JUnit4/AndroidJUnit4 で確定
- 実装可能性: 依存タスク（TASK-0058/0061/0066）完了済み・既存Fakeパターン流用可で確実
- 信頼性レベル: 🔵 60% / 🟡 40% / 🔴 0%（赤信号ゼロ）
```

**総合評価**: 高品質。TASK-0067.md指定の5テストケース（TC-1〜TC-5）を正常系/境界値に完全マッピングしたうえで、後方互換・空文字・デフォルト・最大長のエッジケースを追加し網羅性を確保。静的Flowによる検証上の制約（表示ではなく記録引数で検証）を設計メモとして明示し、実装可能性を担保した。

---

**作成**: tsumiki:tdd-testcases TASK-0067

# TASK-0071 TDDテストケース定義書: TemplateEditViewModel/TemplateEditScreen/FieldAddDialog へのLLM UI追加

**機能名**: llm-memo-rewrite（template-edit-llm-ui）
**タスクID**: TASK-0071
**要件名**: llm-memo-rewrite
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0071/template-edit-llm-ui-testcases.md`
**作成日**: 2026-07-09

---

## 【信頼性レベル凡例】

- 🔵 **青信号**: 要件定義書・設計文書・既存実装を参考にしてほぼ推測していない
- 🟡 **黄信号**: 要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: 元の資料にない推測

---

## 開発言語・フレームワーク

- **プログラミング言語**: Kotlin 2.2.10
  - **言語選択の理由**: 既存プロジェクトが全面的にKotlinで構成されており、対象の `TemplateEditViewModel`/`TemplateEditScreen` もKotlin実装のため。（note.md 技術スタック）
  - **テストに適した機能**: data class の `copy()` による不変状態検証、null 安全性、`match {}` ラムダによる引数マッチングが容易。
- **テストフレームワーク**:
  - **単体テスト**: JUnit 4.13.2 + MockK 1.13.12 + kotlinx-coroutines-test 1.9.0（UnconfinedTestDispatcher）
  - **統合テスト**: Jetpack Compose UI Test（androidx.compose.ui.test-junit4）+ createAndroidComposeRule
  - **フレームワーク選択の理由**: 既存 `TemplateEditViewModelTest.kt` / `TemplateEditScreenTest.kt` が同構成であり、統一するため。（note.md テスト、既存テストファイル）
  - **テスト実行環境**: 単体テストは `app/src/test`（JVM/Robolectric不要）、統合テストは `app/src/androidTest`（実機/エミュレータ）。実行は `mise exec -- ./gradlew test` および `mise exec -- ./gradlew connectedAndroidTest`。
- 🔵 この内容の信頼性レベル: 既存テスト構成・CLAUDE.md ビルドコマンドに基づく

---

## テスト対象と前提

- 🔵 `domain/model/Template.kt` に `bodyLlmPrompt: String = ""` は実装済み（TASK-0056、ソース確認済み）
- 🔵 `domain/model/TemplateField.kt` に `llmPrompt: String = ""` は実装済み（TASK-0056、ソース確認済み）
- 🔵 `domain/model/FieldValueSource.kt` に `LLM` は実装済み（`enum class FieldValueSource { FIXED, HTML_META, URL, EMPTY, LLM }`、ソース確認済み）
- 🔵 `TemplateEditUiState`/`TemplateFieldEditState`/`updateBodyLlmPrompt()`/load/save のマッピングは本タスクで追加（現状ソースに未実装、確認済み）
- 🔵 既存単体テスト命名規約: `{操作}_{期待結果}()`。非同期待ちは `Thread.sleep(200)`。（TemplateEditViewModelTest.kt）
- 🔵 既存統合テスト: `onNodeWithText("ラベル文字列")` でUI要素を操作（testTag はほぼ未使用）。（TemplateEditScreenTest.kt）

---

## 1. 正常系テストケース（基本的な動作）

### TC-N-01: updateBodyLlmPrompt() で uiState.bodyLlmPrompt が更新される（単体）

- **テスト名**: 本文用LLMプロンプト更新メソッドが状態を反映する
  - **何をテストするか**: `updateBodyLlmPrompt(prompt)` 呼び出しで `uiState.bodyLlmPrompt` が渡した値になること
  - **期待される動作**: `_uiState.update { it.copy(bodyLlmPrompt = prompt) }` により同期的に状態が更新される
- **入力値**: `viewModel.updateBodyLlmPrompt("要約してください")`
  - **入力データの意味**: 本文リライト用の代表的なプロンプト文字列。日本語を含む一般的な設定値を代表する
- **期待される結果**: `viewModel.uiState.value.bodyLlmPrompt == "要約してください"`
  - **期待結果の理由**: 既存 `updateBody()` と同じ不変更新パターンに従い、状態更新関数は副作用なく同期反映されるべきだから（要件2.2）
- **テストの目的**: 状態更新関数の正当性確認
  - **確認ポイント**: 他フィールド（name, body 等）を変更していないこと（必要なら追加検証）
- 🔵 信頼性レベル: 要件定義書 6.1-1・note.md テストケース1・既存 `updateBody_updatesUiState()` 踏襲

### TC-N-02: save() で bodyLlmPrompt と各フィールドの llmPrompt が Template/TemplateField に反映される（単体）

- **テスト名**: 保存時に本文用・フィールド用LLMプロンプトが永続化される
  - **何をテストするか**: `save()` が構築する `Template.bodyLlmPrompt` と `TemplateField.llmPrompt` に状態値が正しく載ること
  - **期待される動作**: `templateRepository.saveTemplate()` へ渡される `Template` が両プロンプト値を保持する
- **入力値**:
  - `updateName("テスト")`
  - `updateBodyLlmPrompt("本文用プロンプト")`
  - `addField(TemplateFieldEditState(key = "category", valueSource = FieldValueSource.LLM, llmPrompt = "フィールド用プロンプト"))`
  - `coEvery { repository.saveTemplate(capture(slot)) } returns 1L` の後 `save()` → `Thread.sleep(200)`
  - **入力データの意味**: 本文用とフィールド用の双方向マッピングを1回の保存で同時検証する最小構成
- **期待される結果**:
  - `slot.last().bodyLlmPrompt == "本文用プロンプト"`
  - `slot.last().fields[0].llmPrompt == "フィールド用プロンプト"`
  - `slot.last().fields[0].valueSource == FieldValueSource.LLM`
  - **期待結果の理由**: `save()` の `Template`/`TemplateField` 構築時に `state.bodyLlmPrompt` と `field.llmPrompt` を反映する実装が求められるため（要件2.2、完了条件）
- **テストの目的**: 保存方向マッピングの正当性確認
  - **確認ポイント**: capture slot で実際に渡された引数を検証すること（`match {}` でも可）。`saveTemplate` が1回呼ばれること
- 🔵 信頼性レベル: 要件定義書 6.1-2・note.md テストケース2・既存 `save_persistsBody()` の capture slot 踏襲

### TC-N-03: loadTemplate() で既存テンプレートの bodyLlmPrompt/llmPrompt が復元される（単体）

- **テスト名**: 既存テンプレート読込時に両LLMプロンプトが復元される
  - **何をテストするか**: リポジトリが返す `Template.bodyLlmPrompt` と `TemplateField.llmPrompt` が `uiState` に反映されること
  - **期待される動作**: `getTemplateById()` の結果から `uiState.bodyLlmPrompt` と `fields[i].llmPrompt` が設定される
- **入力値**:
  - リポジトリが返す `Template(id=1L, name="既存テンプレート", body="## 記事\n{{content}}", bodyLlmPrompt="保存済み本文プロンプト", isDefault=true, fields=listOf(TemplateField(id=1L, templateId=1L, key="category", valueSource=FieldValueSource.LLM, valueType=FieldValueType.STRING, llmPrompt="保存済みフィールドプロンプト")))`
  - `coEvery { repository.getTemplateById(1L) } returns template`
  - `createViewModel(templateId = 1L)` → `Thread.sleep(200)`
  - **入力データの意味**: 保存済みデータからの復元（往復）を検証する代表ケース
- **期待される結果**:
  - `viewModel.uiState.value.bodyLlmPrompt == "保存済み本文プロンプト"`
  - `viewModel.uiState.value.fields[0].llmPrompt == "保存済みフィールドプロンプト"`
  - `viewModel.uiState.value.fields[0].valueSource == FieldValueSource.LLM`
  - **期待結果の理由**: `loadTemplate()` のマッピングに両プロンプトの復元を追加する実装が求められるため（要件2.2、完了条件）
- **テストの目的**: 読込方向マッピングの正当性確認
  - **確認ポイント**: `init` ブロックで `templateId` から自動 `loadTemplate` が走る既存挙動を利用すること
- 🔵 信頼性レベル: 要件定義書 6.1-3・note.md テストケース3・既存 `loadTemplate_setsUiStateFromRepository()` 踏襲

### TC-N-04: FieldAddDialog で「LLM生成」を選択するとプロンプト入力欄が表示される（統合）

- **テスト名**: 値取得方法「LLM生成」選択でLLMプロンプト入力欄が表示される
  - **何をテストするか**: `FieldAddDialog` の値取得方法ラジオボタンに「LLM生成」が存在し、選択でプロンプト欄が現れること（受け入れ基準 TC-104-01）
  - **期待される動作**: `field_source_llm`("LLM生成") 選択で `field_llm_prompt_label`("LLMプロンプト") ラベルの `OutlinedTextField` がcompositionに追加される
- **入力値**:
  - 「フィールドを追加」クリック → 「LLM生成」クリック
  - **入力データの意味**: 既存 `fixedSource_showsDefaultValueField()`（"固定値" 選択で "デフォルト値" 表示）と同じ操作パターンのLLM版
- **期待される結果**: `composeTestRule.onNodeWithText("LLMプロンプト").assertExists()`
  - **期待結果の理由**: REQ-104「当該フィールド用のプロンプト入力欄を表示しなければならない」に対応（TC-104-01）
  - 備考: ダイアログのビューポート外に出る場合があるため既存踏襲で `assertExists()` を用いる
- **テストの目的**: 条件付きUI表示の確認
  - **確認ポイント**: 「LLM生成」ラジオが選択肢に追加されていること、選択前は入力欄が非表示であること（`assertDoesNotExist` を追加検証してもよい）
- 🔵 信頼性レベル: 要件定義書 6.2-1・TC-104-01・既存 `fixedSource_showsDefaultValueField()` 踏襲

### TC-N-05: 本文用LLMプロンプト入力が ViewModel に反映される（統合）

- **テスト名**: 本文用LLMプロンプト欄への入力が状態に反映される
  - **何をテストするか**: `template_body_llm_prompt_label`("本文用LLMプロンプト") 欄への入力が `viewModel.uiState.bodyLlmPrompt` に反映されること
  - **期待される動作**: `onValueChange` が `viewModel.updateBodyLlmPrompt()` を呼ぶ
- **入力値**: `onNodeWithText("本文用LLMプロンプト").performTextInput("要約して")`
  - **入力データの意味**: 本文プロンプト欄のUI配線を検証する代表入力（既存 `bodyInput_updatesViewModel()` のLLM版）
- **期待される結果**: `viewModel.uiState.value.bodyLlmPrompt == "要約して"`
  - **期待結果の理由**: UI入力→ViewModel状態のバインドが要件2.3で規定されているため
- **テストの目的**: 本文用LLMプロンプト欄のUI配線確認
  - **確認ポイント**: ラベル文字列が strings.xml の `template_body_llm_prompt_label` と一致すること
- 🔵 信頼性レベル: 要件定義書 2.3・既存 `bodyInput_updatesViewModel()` 踏襲

### TC-N-06: 保存→再読込で本文用・フィールド用LLMプロンプトが復元表示される（統合）

- **テスト名**: 往復（保存→再読込）で両LLMプロンプトが画面に復元される
  - **何をテストするか**: 本文用LLMプロンプトとLLM生成フィールドのプロンプトを入力・保存し、同一テンプレートIDで再表示した際に両値が表示されること
  - **期待される動作**: `save()` で永続化された値が `loadTemplate()` 経由でUIに戻る
- **入力値**: 本文用LLMプロンプト・LLM生成フィールドのプロンプトを入力→保存→同じ `templateId` で `TemplateEditScreen` を再表示（保存値を返すFakeリポジトリを使用）
  - **入力データの意味**: 要件の主要ユースケース（4.1 基本的な使用パターン）のE2E的検証
- **期待される結果**: 本文用LLMプロンプト欄・フィールドのプロンプト欄に保存した値がそれぞれ表示される（`assertExists()`/`assumeText`）
  - **期待結果の理由**: 要件6.2-2・完了条件「loadTemplate() で復元される」の統合レベル担保
- **テストの目的**: 保存・復元往復の統合確認
  - **確認ポイント**: Fake リポジトリが保存した `Template`（bodyLlmPrompt/llmPrompt 含む）を `getTemplateById` で返すこと
- 🔵 信頼性レベル: 要件定義書 6.2-2・note.md 統合テスト2

---

## 2. 異常系テストケース（エラーハンドリング）

### TC-E-01: loadTemplate() 対象が存在しない（getTemplateById が null）場合、既存挙動を踏襲しクラッシュしない（単体）

- **テスト名**: 存在しないテンプレートID読込時に例外を出さない
  - **エラーケースの概要**: `getTemplateById(id)` が `null` を返す場合、`loadTemplate` は `return@launch` で復元をスキップする
  - **エラー処理の重要性**: LLMプロンプト追加後も既存の null 安全挙動を退行させないため（要件4.4）
- **入力値**: `coEvery { repository.getTemplateById(99L) } returns null` → `createViewModel(templateId = 99L)` → `Thread.sleep(200)`
  - **不正な理由**: 削除済み等で存在しないIDが渡される想定
  - **実際の発生シナリオ**: ナビゲーション引数に無効IDが残存、レコード削除後の再表示
- **期待される結果**: 例外が発生せず、`uiState.bodyLlmPrompt == ""`（初期値のまま）、`uiState.fields` が空
  - **エラーメッセージの内容**: 本タスクでは新規エラーメッセージは定義しない（既存踏襲）
  - **システムの安全性**: 状態は初期値を保ち安全
- **テストの目的**: null 復元スキップの退行防止
  - **品質保証の観点**: LLMマッピング追加が既存の防御的分岐を壊していないこと
- 🔵 信頼性レベル: 要件定義書 4.4・既存 `loadTemplate()` の `?: return@launch` 実装

### TC-E-02: LLM以外の値取得方法で追加すると llmPrompt が空文字になる（単体/統合）

- **テスト名**: 非LLMソース選択時にLLMプロンプトが破棄される
  - **エラーケースの概要**: `FieldAddDialog` でLLMを一旦選びプロンプト入力後、別ソース（例: URL）へ切替えて追加した場合、保存される `llmPrompt` が空文字になる
  - **エラー処理の重要性**: 使われないプロンプトが永続化され後続LLM実行を誤誘発するのを防ぐ（要件4.3 EDGE）
- **入力値**: `onAdd` に渡る `TemplateFieldEditState` を `valueSource = FieldValueSource.URL`（LLM欄には入力履歴あり）で生成
  - **不正な理由**: `valueSource != LLM` のとき `llmPrompt` は意味を持たない
  - **実際の発生シナリオ**: ユーザーが選択を迷って切り替えるUI操作
- **期待される結果**: 追加されたフィールドの `llmPrompt == ""`（`if (valueSource == LLM) llmPromptInput else ""` の分岐）
  - **エラーメッセージの内容**: エラーではなく正規化。メッセージなし
  - **システムの安全性**: 不要データが混入しない
- **テストの目的**: 値取得方法とプロンプトの整合性保証
  - **品質保証の観点**: 保存データのクリーンさ（後続 TASK-0072/0073 の入力健全性）
- 🟡 信頼性レベル: 要件定義書 4.3 EDGE（TASK-0071実装詳細7の分岐からの妥当推測）

### TC-E-03: save() 中に isSaving フラグが立ち、完了後 isSaved=true になる（単体・退行確認）

- **テスト名**: LLMプロンプト追加後も保存フローの状態遷移が維持される
  - **エラーケースの概要**: 厳密な異常系ではないが、`save()` 改修により既存の `isSaving`/`isSaved` 遷移が壊れないことを保証する
  - **エラー処理の重要性**: 保存中UI制御（多重保存防止）を退行させない
- **入力値**: `updateName("テスト")` + `updateBodyLlmPrompt("p")` → `save()` → `Thread.sleep(200)`
  - **不正な理由**: 該当なし（退行検証目的）
  - **実際の発生シナリオ**: 通常保存操作
- **期待される結果**: `coVerify { repository.saveTemplate(any()) }`、`uiState.isSaved == true`
  - **エラーメッセージの内容**: なし
  - **システムの安全性**: 保存完了状態が確定する
- **テストの目的**: 既存 `save_callsRepositoryAndSetsisSaved()` の退行防止
  - **品質保証の観点**: 完了条件「既存の単体テスト・統合テストがすべて通る」の担保
- 🔵 信頼性レベル: 既存 `save_callsRepositoryAndSetsisSaved()`・完了条件

---

## 3. 境界値テストケース（最小値、最大値、null等）

### TC-B-01: bodyLlmPrompt 空文字（未設定）で保存しても例外なく空文字が保存される（単体）

- **テスト名**: 本文用LLMプロンプト未入力保存（空文字境界）
  - **境界値の意味**: 空文字は「未設定」を表す仕様上の重要な境界（REQ-102、Template.kt コメント）
  - **境界値での動作保証**: 未入力でも保存が成功し、`bodyLlmPrompt == ""` が保持される
- **入力値**: `updateName("テスト")` のみ（`updateBodyLlmPrompt` は呼ばない）→ `save()`
  - **境界値選択の根拠**: デフォルト空文字。既存テンプレート（LLM未対応時代のデータ）互換の下限
  - **実際の使用場面**: LLM機能を使わないテンプレート作成
- **期待される結果**: `slot.last().bodyLlmPrompt == ""`、例外なし、`isSaved == true`
  - **境界での正確性**: 空文字がそのまま保存される
  - **一貫した動作**: 空文字/非空文字で保存経路が分岐しない
- **テストの目的**: 空文字境界の後方互換保証
  - **堅牢性の確認**: 未設定でもフローが破綻しない
- 🔵 信頼性レベル: 要件定義書 3（後方互換制約）・4.3 EDGE・REQ-102

### TC-B-02: LLM生成フィールドで llmPrompt 空文字のまま保存・復元できる（単体）

- **テスト名**: LLM選択かつプロンプト空のフィールド保存（空文字境界）
  - **境界値の意味**: `valueSource == LLM` かつ `llmPrompt == ""` の組み合わせ境界。本タスクは保存・復元のみ保証（生成挙動は対象外）
  - **境界値での動作保証**: 空プロンプトのLLMフィールドも保存・復元で往復できる
- **入力値**: `addField(TemplateFieldEditState(key="k", valueSource=FieldValueSource.LLM, llmPrompt=""))` → `save()`
  - **境界値選択の根拠**: LLM選択直後（未入力）の下限状態
  - **実際の使用場面**: フィールド追加後にプロンプト入力を保留したケース
- **期待される結果**: `slot.last().fields[0].valueSource == FieldValueSource.LLM` かつ `fields[0].llmPrompt == ""`、例外なし
  - **境界での正確性**: 空プロンプトが破棄も改変もされず保持される
  - **一貫した動作**: プロンプト有無で保存経路が分岐しない
- **テストの目的**: LLMフィールドの空プロンプト境界の保存保証
  - **堅牢性の確認**: 生成ロジック未実装でも保存層が安定
  - 備考: 生成時挙動（🔴 EDGE 未確定）は本タスク対象外（TASK-0072/0073）
- 🟡 信頼性レベル: 要件定義書 4.3 EDGE（🔴部分は対象外明記、保存・復元は🔵）

### TC-B-03: 複数フィールド（LLM含む混在）で sortOrder とマッピングが崩れない（単体）

- **テスト名**: 複数フィールド混在時のllmPromptマッピング整合
  - **境界値の意味**: フィールド0件/1件を超える複数件境界。LLMと非LLMが混在する順序保持を検証
  - **境界値での動作保証**: `save()` の `mapIndexed` で各フィールドの `llmPrompt` が正しいインデックスに対応する
- **入力値**: `addField(URL, key="src")` → `addField(LLM, key="cat", llmPrompt="推測して")` の順で2件追加 → `save()`
  - **境界値選択の根拠**: 単一フィールドでは検出できないインデックスずれを検出
  - **実際の使用場面**: 複数のカスタムフィールドを持つ実運用テンプレート
- **期待される結果**: `fields[0].key=="src" && fields[0].llmPrompt==""`、`fields[1].key=="cat" && fields[1].llmPrompt=="推測して" && fields[1].valueSource==LLM`、`sortOrder` が index と一致
  - **境界での正確性**: 各フィールドが自身のプロンプトを保持
  - **一貫した動作**: 追加順が保存順に一致
- **テストの目的**: 複数要素マッピングの堅牢性
  - **堅牢性の確認**: LLMマッピング追加が既存 `mapIndexed(sortOrder=index)` を壊さない
- 🟡 信頼性レベル: 既存 `save()` の `mapIndexed` 実装＋完了条件からの妥当推測

---

## 4. テストケース実装時の日本語コメント指針（実装時テンプレート）

各テストに以下の日本語コメントを付与する。

### 単体テスト（例: TC-N-02）

```kotlin
@Test
fun save_reflectsBodyLlmPromptAndFieldLlmPrompt() {
    // 【テスト目的】: save() が本文用・フィールド用LLMプロンプトを Template/TemplateField に反映することを確認
    // 【テスト内容】: bodyLlmPrompt と LLMフィールドの llmPrompt を設定し保存、渡された引数を検証
    // 【期待される動作】: saveTemplate へ渡る Template が両プロンプト値を保持
    // 🔵 信頼性レベル: 要件6.1-2・note.mdテストケース2

    // 【テストデータ準備】: 本文用とフィールド用の双方向マッピングを1保存で同時検証する最小構成
    // 【初期条件設定】: 新規テンプレート状態の ViewModel
    val viewModel = createViewModel()
    viewModel.updateName("テスト")
    viewModel.updateBodyLlmPrompt("本文用プロンプト")
    viewModel.addField(
        TemplateFieldEditState(key = "category", valueSource = FieldValueSource.LLM, llmPrompt = "フィールド用プロンプト")
    )
    val slot = mutableListOf<Template>()
    // 【前提条件確認】: saveTemplate をモックし引数を capture
    coEvery { repository.saveTemplate(capture(slot)) } returns 1L

    // 【実際の処理実行】: save() を呼び出し viewModelScope.launch(IO) の完了を待機
    viewModel.save()
    Thread.sleep(200)

    // 【結果検証】: capture した Template の本文用・フィールド用プロンプトを確認
    // 【検証項目】: bodyLlmPrompt が状態値と一致
    // 🔵 信頼性レベル: 要件6.1-2
    assertEquals("本文用プロンプト", slot.last().bodyLlmPrompt) // 【確認内容】: 本文用プロンプトが Template に載る
    assertEquals("フィールド用プロンプト", slot.last().fields[0].llmPrompt) // 【確認内容】: フィールド用プロンプトが TemplateField に載る
    assertEquals(FieldValueSource.LLM, slot.last().fields[0].valueSource) // 【確認内容】: 値取得方法が LLM で保持される
}
```

### 統合テスト（例: TC-N-04）

```kotlin
@Test
fun llmSource_showsLlmPromptField() {
    // 【テスト目的】: 値取得方法「LLM生成」選択でプロンプト入力欄が表示される（TC-104-01）
    // 【テスト内容】: FieldAddDialog を開き「LLM生成」を選択、"LLMプロンプト" ラベル欄の存在を確認
    // 【期待される動作】: field_llm_prompt_label 欄が composition に追加される
    // 🔵 信頼性レベル: 要件6.2-1・TC-104-01

    composeTestRule.setContent {
        // 【環境初期化】: Fake リポジトリで新規モードの画面を構築
        TemplateEditScreen(templateId = null, viewModel = createViewModel(), onNavigateBack = {})
    }

    // 【実際の処理実行】: フィールド追加ダイアログを開き LLM生成 を選択
    composeTestRule.onNodeWithText("フィールドを追加").performClick()
    composeTestRule.onNodeWithText("LLM生成").performClick()

    // 【結果検証】: プロンプト入力欄が存在することを確認（ビューポート外可能性のため assertExists）
    // 【検証項目】: LLMプロンプト ラベル欄の表示
    composeTestRule.onNodeWithText("LLMプロンプト").assertExists() // 【確認内容】: LLM選択でプロンプト欄が現れる
}
```

### セットアップ・クリーンアップ（既存踏襲）

```kotlin
@Before
fun setUp() {
    // 【テスト前準備】: viewModelScope の Dispatcher をテスト用に差し替え
    // 【環境初期化】: UnconfinedTestDispatcher で即時実行
    Dispatchers.setMain(testDispatcher)
}

@After
fun tearDown() {
    // 【テスト後処理】: Main Dispatcher をリセット
    // 【状態復元】: 次テストへの影響を防止
    Dispatchers.resetMain()
}
```

---

## 5. 要件定義との対応関係

- **参照した機能概要**: 要件定義書 §1（本文用LLMプロンプト欄追加・FieldAddDialogへのLLM選択肢追加・往復マッピング）
- **参照した入力・出力仕様**: 要件定義書 §2.1〜2.3（`TemplateEditUiState.bodyLlmPrompt`、`TemplateFieldEditState.llmPrompt`、`updateBodyLlmPrompt()`、save/load マッピング、UIバインド）
- **参照した制約条件**: 要件定義書 §3（不変更新、strings.xml 3項目、後方互換=空文字未設定、スコープ境界=生成はTASK-0072/0073）
- **参照した使用例**: 要件定義書 §4（基本使用パターン、データフロー、エッジケース、エラーケース）
- **参照した受け入れ基準**: TC-104-01（LLM選択でプロンプト欄表示 = TC-N-04）
- **参照した既存実装**: `TemplateEditViewModel.kt`（save/loadの `mapIndexed`・`?: return@launch`）、`TemplateEditViewModelTest.kt`（capture slot・Thread.sleep(200)）、`TemplateEditScreenTest.kt`（onNodeWithText・FakeEditRepository・assertExists）

---

## 6. テストケース一覧サマリー

| 分類 | ID | テスト名 | レベル | 信頼性 |
|------|-----|---------|--------|--------|
| 正常系 | TC-N-01 | updateBodyLlmPrompt が状態更新 | 単体 | 🔵 |
| 正常系 | TC-N-02 | save で両プロンプト反映 | 単体 | 🔵 |
| 正常系 | TC-N-03 | loadTemplate で両プロンプト復元 | 単体 | 🔵 |
| 正常系 | TC-N-04 | LLM生成選択でプロンプト欄表示 | 統合 | 🔵 |
| 正常系 | TC-N-05 | 本文用プロンプト入力の反映 | 統合 | 🔵 |
| 正常系 | TC-N-06 | 保存→再読込で復元表示 | 統合 | 🔵 |
| 異常系 | TC-E-01 | 存在しないID読込で例外なし | 単体 | 🔵 |
| 異常系 | TC-E-02 | 非LLMソースで llmPrompt 空文字化 | 単体/統合 | 🟡 |
| 異常系 | TC-E-03 | save 状態遷移の退行防止 | 単体 | 🔵 |
| 境界値 | TC-B-01 | bodyLlmPrompt 空文字保存 | 単体 | 🔵 |
| 境界値 | TC-B-02 | LLMフィールド空プロンプト保存・復元 | 単体 | 🟡 |
| 境界値 | TC-B-03 | 複数フィールド混在マッピング整合 | 単体 | 🟡 |

### 信頼性分布

| レベル | 件数 | 割合 |
|--------|------|------|
| 🔵 青信号 | 8 | 67% |
| 🟡 黄信号 | 4 | 33% |
| 🔴 赤信号 | 0 | 0% |

- 🟡 は「非LLM選択時のプロンプト破棄」「空プロンプトLLMフィールド」「複数フィールドマッピング」の実装詳細からの妥当推測。
- 🔴 該当なし（LLM値生成挙動は本タスク対象外として境界テストから除外済み）。

---

## 7. 品質判定

```
✅ 高品質
- テストケース分類: 正常系6・異常系3・境界値3で網羅
- 期待値定義: 全ケースで具体的なアサーション値を明記
- 技術選択: Kotlin/JUnit4/MockK/Compose UI Test（既存構成と統一）で確定
- 実装可能性: ドメイン・永続化は実装済み、既存テストパターン踏襲で確実
- 信頼性レベル: 🔵優勢（67%）、🔴ゼロ
```

---

**参照元一覧**:
- `docs/tasks/llm-memo-rewrite/TASK-0071.md`
- `docs/implements/llm-memo-rewrite/TASK-0071/note.md`
- `docs/implements/llm-memo-rewrite/TASK-0071/template-edit-llm-ui-requirements.md`
- `app/src/main/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModel.kt`
- `app/src/test/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModelTest.kt`
- `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/template/TemplateEditScreenTest.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/domain/model/{Template,TemplateField,FieldValueSource}.kt`
</content>
</invoke>

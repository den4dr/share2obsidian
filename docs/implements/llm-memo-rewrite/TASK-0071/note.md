# TASK-0071 TDD開発コンテキスト

**タスクID**: TASK-0071  
**機能名**: llm-memo-rewrite（TemplateEditViewModel/TemplateEditScreen/FieldAddDialog へのLLM UI追加）  
**タスク概要**: `TemplateEditScreen`に本文用LLMプロンプト入力欄を追加し、`FieldAddDialog`の値取得方法ラジオボタンに「LLM」を追加、選択時にフィールド用プロンプト入力欄を表示する。テンプレート保存時に`bodyLlmPrompt`/`llmPrompt`が正しく保存・復元されるよう`TemplateEditViewModel`を修正する。

**推定工数**: 8時間  
**フェーズ**: Phase 6 - カスタムフィールドのLLM生成（Could Have）

---

## 1. 技術スタック

### Kotlin & Android標準
- **言語**: Kotlin 2.2.10
- **AGP**: 9.2.1
- **SDK**: minSdk 33 (Android 13), targetSdk/compileSdk 36
- **参照元**: gradle/libs.versions.toml

### UIフレームワーク
- **Jetpack Compose**: BOM 2024.09.00
- **Material3**: androidx.compose.material3（既存パターン踏襲）
- **参照元**: gradle/libs.versions.toml, 既存 TemplateEditScreen.kt

### アーキテクチャ・DI
- **MVVM + Repository**: ViewModel + MutableStateFlow
- **DI**: Hilt 2.59.2（@HiltViewModel, @Inject, HiltModule）
- **SavedStateHandle**: androidx.lifecycle.SavedStateHandle（ナビゲーション引数の保持）
- **参照元**: app/src/main/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModel.kt

### テスト
- **単体テスト**: JUnit 4.13.2
- **Mock**: MockK 1.13.12
- **Coroutines Test**: kotlinx-coroutines-test 1.9.0（UnconfinedTestDispatcher）
- **参照元**: app/src/test/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModelTest.kt

---

## 2. 開発ルール

### Kotlin コーディング規約
- **StateFlow パターン**: `private val _uiState = MutableStateFlow<State>()`, `val uiState: StateFlow<State> = _uiState.asStateFlow()` 形式
- **状態更新**: `_uiState.update { it.copy(...) }` で不可変更新
- **ViewModel Coroutine**: `viewModelScope.launch(Dispatchers.IO)` でバックグラウンド処理
- **参照元**: TemplateEditViewModel.kt の既存パターン

### UI/Compose パターン
- **テストタグ**: `testTag` で UI要素を識別（例: `template_body_field`）
- **OutlinedTextField**: 複数行テキスト入力は `minLines = 2` 程度を指定
- **ラベル**: `stringResource(R.string.xxx)` で文字列リソース参照
- **参照元**: 既存 TemplateEditScreen.kt の実装

### テストケース設計
- **テストメソッド名規約**: `updateXxx_doesYyy()`
- **Arrange-Act-Assert**: Given-When-Then（AAA）パターン
- **MockK**: `coEvery {}, coVerify {}` で非同期関数をテスト
- **Thread.sleep(200ms)**: viewModelScope.launch の非同期完了待ち
- **参照元**: TemplateEditViewModelTest.kt の既存テストケース

---

## 3. 関連実装

### 既存のTemplateEditViewModel パターン

```kotlin
// データクラス
data class TemplateEditUiState(
    val templateId: Long? = null,
    val name: String = "",
    val body: String = "",
    // 以下、TASK-0071で追加:
    // val bodyLlmPrompt: String = "",
    val isDefault: Boolean = false,
    val fields: List<TemplateFieldEditState> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
)

data class TemplateFieldEditState(
    val id: Long = 0,
    val key: String = "",
    val valueSource: FieldValueSource = FieldValueSource.EMPTY,
    val valueType: FieldValueType = FieldValueType.STRING,
    val defaultValue: String = "",
    val metaKey: HtmlMetaKey? = null,
    // 以下、TASK-0071で追加:
    // val llmPrompt: String = "",
    val sortOrder: Int = 0,
)

// ViewModel: StateFlow + update() パターン
@HiltViewModel
class TemplateEditViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TemplateEditUiState(templateId = ...))
    val uiState: StateFlow<TemplateEditUiState> = _uiState.asStateFlow()

    // 既存パターン: updateName(), updateBody(), loadTemplate(), save()
    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateBody(body: String) = _uiState.update { it.copy(body = body) }
    
    private fun loadTemplate(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val template = templateRepository.getTemplateById(id) ?: return@launch
            _uiState.update {
                TemplateEditUiState(
                    // ... テンプレートから復元
                )
            }
        }
    }
    
    fun save() {
        // ... state から Template を構築して save()
    }
}
```

### 既存テストケース（updateXxx パターン）

```kotlin
@Test
fun updateName_updatesUiState() {
    val viewModel = createViewModel()
    viewModel.updateName("Web記事")
    assertEquals("Web記事", viewModel.uiState.value.name)
}

@Test
fun updateBody_updatesUiState() {
    val viewModel = createViewModel()
    viewModel.updateBody("## メモ\n{{content}}")
    assertEquals("## メモ\n{{content}}", viewModel.uiState.value.body)
}
```

### 既存の save() テストケース

```kotlin
@Test
fun save_callsRepositoryAndSetsisSaved() {
    val viewModel = createViewModel()
    viewModel.updateName("テスト")
    coEvery { repository.saveTemplate(any()) } returns 1L

    viewModel.save()
    Thread.sleep(200)

    coVerify { repository.saveTemplate(any()) }
    assertTrue(viewModel.uiState.value.isSaved)
}
```

### 既存の loadTemplate() テストケース

```kotlin
@Test
fun loadTemplate_setsUiStateFromRepository() {
    val template = Template(
        id = 1L,
        name = "既存テンプレート",
        body = "## 記事\n{{content}}",
        isDefault = true,
        fields = emptyList(),
    )
    coEvery { repository.getTemplateById(1L) } returns template

    val viewModel = createViewModel(templateId = 1L)
    Thread.sleep(200)

    assertEquals("既存テンプレート", viewModel.uiState.value.name)
    assertEquals("## 記事\n{{content}}", viewModel.uiState.value.body)
    assertTrue(viewModel.uiState.value.isDefault)
}
```

**参照元**: app/src/test/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModelTest.kt

---

## 4. 設計文書

### 要件定義（REQ-101, REQ-104, REQ-405）

| 要件ID | 説明 | 信頼性 |
|--------|------|--------|
| REQ-101 | テンプレートに本文用LLMプロンプトが設定されている場合、システムはそのプロンプトをLLMリクエストに含めなければならない | 🔵 |
| REQ-102 | テンプレートに本文用LLMプロンプトが設定されていない場合、システムは「メモを更改」ボタンを表示するが非活性化（グレーアウト）しなければならない | 🔵 |
| REQ-104 | カスタムフィールドのLLM生成（Could Have）が有効な場合、システムはテンプレート編集画面で当該フィールド用のプロンプト入力欄を表示しなければならない | 🔵 |
| REQ-303 | システムはカスタムフィールドの値取得方法（`FieldValueSource`）に `LLM` を追加してもよい（Could Have） | 🔵 |
| REQ-304 | `FieldValueSource` が `LLM` に設定されたカスタムフィールドについて、システムはテンプレート適用時にLLMで値を生成してもよい | 🟡 |
| REQ-405 | 本機能追加に伴い、既存の `TemplateEditScreen` / `FieldAddDialog` のUIを変更してもよい | 🔵 |

**参照元**: docs/spec/llm-memo-rewrite/requirements.md

### ユーザストーリー（ストーリー 3.1）

**私は** テンプレートを設計するユーザー **として**  
**カスタムフィールドの値取得方法として「LLM生成」を選び、フィールドごとにプロンプトを設定したい**  
**そうすることで** 固定値やHTMLメタ情報だけでなく、LLMによる動的な値生成をテンプレートに組み込める

**詳細シナリオ**:
1. ユーザーがテンプレート編集画面でカスタムフィールドを追加する
2. 値取得方法（FieldValueSource）の選択肢から「LLM」を選ぶ
3. システムはそのフィールド用のプロンプト入力欄を表示する
4. ユーザーがプロンプトを入力し保存する

**参照元**: docs/spec/llm-memo-rewrite/user-stories.md

### 受け入れ基準

#### TC-104-01: FieldValueSourceに「LLM」を選択するとプロンプト入力欄が表示される 🔵
- **入力**: FieldAddDialogで値取得方法="LLM"を選択
- **期待結果**: プロンプト入力用のテキストフィールドが表示される

#### TC-104-02: LLM生成のカスタムフィールド値がテンプレート適用時に生成される 🟡
- **入力**: FieldValueSource="LLM", プロンプト="URLからカテゴリを推測して"
- **期待結果**: テンプレート適用時（またはEditScreen操作時）にLLMで値が生成されCustomFieldStateに反映される
- **備考**: 生成タイミングは設計フェーズで確定

**参照元**: docs/spec/llm-memo-rewrite/acceptance-criteria.md（REQ-104・REQ-303・REQ-304）

### アーキテクチャ設計

**変更が必要な既存コンポーネント**（抜粋):

| ファイル | 変更内容 | 対応要件 |
|---------|---------|---------|
| `domain/model/Template.kt` | `bodyLlmPrompt: String = ""` を追加 | REQ-101, REQ-102 |
| `domain/model/TemplateField.kt` | `llmPrompt: String = ""` を追加 | REQ-104, REQ-303 |
| `domain/model/FieldValueSource.kt` | `LLM` を追加 | REQ-303 |
| `ui/template/TemplateEditViewModel.kt` | `TemplateEditUiState` に `bodyLlmPrompt` 追加、`updateBodyLlmPrompt()` 追加。`TemplateFieldEditState` に `llmPrompt` 追加 | REQ-101, REQ-104 |
| `ui/template/TemplateEditScreen.kt` | 本文用LLMプロンプト入力欄を追加。`FieldAddDialog` の値取得方法ラジオボタンに「LLM」を追加し、選択時にプロンプト入力欄を表示 | REQ-104, REQ-405 |
| `res/values/strings.xml` | ボタンラベル・エラーメッセージ等の新規文字列追加（日本語） | NFR-201 |

**参照元**: docs/design/llm-memo-rewrite/architecture.md

---

## 5. テスト関連情報

### テストフレームワーク・設定
- **単体テスト**: JUnit 4（既存）
- **Mock**: MockK 1.13.12（既存）
- **Coroutines Test**: kotlinx-coroutines-test 1.9.0（既存）
- **テストディレクトリ**: `app/src/test/java/com/den4dr/share2Obsidian/`
- **統合テスト**: Compose UI Test（androidx.compose.ui.test-junit4）

**参照元**: gradle/libs.versions.toml, app/src/test/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModelTest.kt

### 既存テストの命名パターン
- ViewModel単体テスト: `{メソッド名}_{期待結果}`
  - 例: `updateName_updatesUiState()`, `save_callsRepositoryAndSetsisSaved()`
- テスト前処理: `setUp()` で `Dispatchers.setMain(testDispatcher)`
- テスト後処理: `tearDown()` で `Dispatchers.resetMain()`
- 非同期待ち: `Thread.sleep(200ms)` で viewModelScope.launch の完了を待機

### 既存テストのディレクトリ構成
```
app/src/test/java/com/den4dr/share2Obsidian/ui/template/
├── TemplateEditViewModelTest.kt
└── TemplateListViewModelTest.kt
```

### TASK-0071 で追加必要なテストケース

#### テストケース1: updateBodyLlmPrompt()呼び出し後、uiState.bodyLlmPromptが更新されること 🔵
```kotlin
@Test
fun updateBodyLlmPrompt_updatesUiState() {
    val viewModel = createViewModel()
    viewModel.updateBodyLlmPrompt("要約してください")
    assertEquals("要約してください", viewModel.uiState.value.bodyLlmPrompt)
}
```

#### テストケース2: save()でbodyLlmPromptがTemplate.bodyLlmPromptに、各フィールドのllmPromptがTemplateField.llmPromptに正しく反映されること 🔵
```kotlin
@Test
fun save_reflectsBodyLlmPromptAndFieldLlmPrompt() {
    val viewModel = createViewModel()
    viewModel.updateName("テスト")
    viewModel.updateBodyLlmPrompt("本文用プロンプト")
    val field = TemplateFieldEditState(
        key = "field1", 
        valueSource = FieldValueSource.LLM,
        llmPrompt = "フィールド用プロンプト"
    )
    viewModel.addField(field)
    coEvery { repository.saveTemplate(any()) } returns 1L

    viewModel.save()
    Thread.sleep(200)

    coVerify { repository.saveTemplate(match { template ->
        template.bodyLlmPrompt == "本文用プロンプト" &&
        template.fields[0].llmPrompt == "フィールド用プロンプト"
    }) }
}
```

#### テストケース3: loadTemplate()で既存テンプレートのbodyLlmPrompt/llmPromptがuiStateに正しく復元されること 🔵
```kotlin
@Test
fun loadTemplate_restoresBodayLlmPromptAndFieldLlmPrompt() {
    val template = Template(
        id = 1L,
        name = "既存テンプレート",
        body = "## 記事\n{{content}}",
        bodyLlmPrompt = "保存済み本文プロンプト",
        isDefault = true,
        fields = listOf(
            TemplateField(
                id = 1L,
                key = "category",
                valueSource = FieldValueSource.LLM,
                llmPrompt = "保存済みフィールドプロンプト",
                // ...
            )
        ),
    )
    coEvery { repository.getTemplateById(1L) } returns template

    val viewModel = createViewModel(templateId = 1L)
    Thread.sleep(200)

    assertEquals("保存済み本文プロンプト", viewModel.uiState.value.bodyLlmPrompt)
    assertEquals("保存済みフィールドプロンプト", viewModel.uiState.value.fields[0].llmPrompt)
}
```

**参照元**: docs/tasks/llm-memo-rewrite/TASK-0071.md（単体テスト要件セクション）

### 統合テスト（Compose UI Test）

#### 統合テスト1: FieldAddDialogで値取得方法「LLM」を選択するとプロンプト入力欄が表示されること 🔵
- テスト内容: `FieldAddDialog`を表示し、値取得方法ラジオボタンから「LLM」（`field_source_llm`）を選択
- 期待結果: LLMプロンプト入力欄（`field_llm_prompt_label`）が表示される

#### 統合テスト2: テンプレートを保存後、画面を再読込した際に本文用LLMプロンプトとフィールドのLLMプロンプトが正しく復元されて表示されること 🔵
- テスト内容: 本文用LLMプロンプトとLLM生成フィールドのプロンプトを入力してテンプレートを保存し、`TemplateEditScreen`を同じテンプレートIDで再表示
- 期待結果: 本文用LLMプロンプト入力欄・フィールドのプロンプト入力欄に保存した値がそれぞれ表示される

**参照元**: docs/tasks/llm-memo-rewrite/TASK-0071.md（統合テスト要件セクション）

---

## 6. 注意事項

### 技術的制約
- **StateFlow の不可変更新**: `.copy()` で新しいインスタンスを生成。直接フィールド変更は禁止
- **Coroutine DispatcherはIoで**: バックグラウンドDB/ファイル操作は `Dispatchers.IO`
- **テストでの非同期待機**: `Thread.sleep(200ms)` は簡易的（本来なら `advanceUntilIdle()` 推奨）

### コーディング規約（既存パターン踏襲）
- **ViewModel メソッド**: public メソッドは状態更新関数と副作用関数に分離
  - 状態更新: `updateXxx()` → `_uiState.update { ... }`
  - 副作用: `loadXxx()`, `save()` → `viewModelScope.launch()`
- **テスト名**: `{操作}_{期待結果}()` 形式
- **文字列リソース**: すべて `strings.xml` に登録（ハードコード禁止）

### String Resource に追加すべき項目
- `field_source_llm`: "LLM生成"（ラジオボタンラベル）
- `field_llm_prompt_label`: "LLMプロンプト"（ フィールドプロンプト入力欄ラベル）
- `template_body_llm_prompt_label`: "本文用LLMプロンプト"（本文プロンプト入力欄ラベル）

**参照元**: docs/tasks/llm-memo-rewrite/TASK-0071.md（実装詳細セクション）

### UI/UX要件
- 本文用LLMプロンプト入力欄は `template_body_field` と同様の複数行入力コンポーネント（`minLines = 2` 程度）
- `FieldAddDialog` でLLM選択時の FieldValueSource.LLM 入力欄は既存の固定値/HTMLメタキーと同様の配置

**参照元**: docs/tasks/llm-memo-rewrite/TASK-0071.md（UI/UX要件セクション）

---

## 7. 関連ファイル一覧

### ドメインモデル（修正対象）
- `app/src/main/java/com/den4dr/share2Obsidian/domain/model/Template.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/domain/model/TemplateField.kt`
- `app/src/main/java/com/den4dr/share2Obsidian/domain/model/FieldValueSource.kt`

### ViewModel・UI（主要な実装対象）
- `app/src/main/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModel.kt` ← 🔴 主要対象
- `app/src/main/java/com/den4dr/share2Obsidian/ui/template/TemplateEditScreen.kt` ← 🔴 主要対象

### テスト（実装対象）
- `app/src/test/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModelTest.kt` ← 🔴 主要対象
- 必要に応じて Compose UI Test 追加

### リソース（追加対象）
- `app/src/main/res/values/strings.xml` ← 🔴 3つの文字列リソース追加

### ビルド設定（確認対象）
- `gradle/libs.versions.toml`（既存依存関係は十分）
- `app/build.gradle.kts`

### 設計・要件書（参考）
- `docs/tasks/llm-memo-rewrite/TASK-0071.md` ← タスク定義
- `docs/spec/llm-memo-rewrite/requirements.md` ← 要件定義（REQ-101, REQ-104, REQ-405）
- `docs/spec/llm-memo-rewrite/user-stories.md` ← ユーザストーリー
- `docs/spec/llm-memo-rewrite/acceptance-criteria.md` ← 受け入れ基準
- `docs/design/llm-memo-rewrite/architecture.md` ← アーキテクチャ設計

---

## 8. 実装手順

### TDDプロセス
1. `/tsumiki:tdd-requirements TASK-0071` - 詳細要件定義
2. `/tsumiki:tdd-testcases TASK-0071` - テストケース作成
3. `/tsumiki:tdd-red TASK-0071` - テスト実装（失敗）
4. `/tsumiki:tdd-green TASK-0071` - 最小実装
5. `/tsumiki:tdd-refactor TASK-0071` - リファクタリング
6. `/tsumiki:tdd-verify-complete TASK-0071` - 品質確認

---

**最終更新**: 2026-07-09  
**作成者**: Claude Code (TDD tasknote agent)

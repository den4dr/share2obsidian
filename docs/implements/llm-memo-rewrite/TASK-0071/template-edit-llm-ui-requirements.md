# TASK-0071 TDD要件定義書: TemplateEditViewModel/TemplateEditScreen/FieldAddDialog へのLLM UI追加

**機能名**: llm-memo-rewrite（template-edit-llm-ui）
**タスクID**: TASK-0071
**要件名**: llm-memo-rewrite
**出力ファイル**: `docs/implements/llm-memo-rewrite/TASK-0071/template-edit-llm-ui-requirements.md`
**作成日**: 2026-07-09

---

## 【信頼性レベル凡例】

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: テンプレート編集画面（`TemplateEditScreen`）に「本文用LLMプロンプト」の複数行入力欄を追加し、カスタムフィールド追加ダイアログ（`FieldAddDialog`）の値取得方法ラジオボタンに「LLM」を追加する。「LLM」選択時にフィールド用プロンプト入力欄を表示し、テンプレート保存・復元時に `Template.bodyLlmPrompt` と各 `TemplateField.llmPrompt` が正しく往復するよう `TemplateEditViewModel` を修正する。（TASK-0071 タスク概要）
- 🔵 **解決する問題**: 固定値・HTMLメタ情報だけでなく、LLMによる本文リライトやフィールド値の動的生成をテンプレートに組み込めるようにする。プロンプトを永続化する入口が無いと後続のLLM実行（TASK-0072/0073）が機能しないため、その設定UIを提供する。（user-stories.md ストーリー3.1）
- 🔵 **想定ユーザー**: テンプレートを設計するユーザー（As a テンプレートを設計するユーザー）。（user-stories.md ストーリー3.1）
- 🔵 **システム内での位置づけ**: MVVM + Repository構成のUI層。`TemplateEditViewModel`（状態管理）と `TemplateEditScreen`/`FieldAddDialog`（Compose UI）を変更する。ドメインモデル（Template/TemplateField/FieldValueSource）と永続化（Room Migration v3）はTASK-0056/0057で実装済みであり、本タスクはその設定値をUIで入出力する層を担う。（architecture.md「変更が必要な既存コンポーネント」）

- **参照したEARS要件**: REQ-101, REQ-104, REQ-303, REQ-405
- **参照した設計文書**: architecture.md「変更が必要な既存コンポーネント」、interfaces.kt（TemplateEditUiState/TemplateFieldEditState）

### 前提の実装状況（本タスク着手時点で確認済み）

- 🔵 `domain/model/Template.kt` に `bodyLlmPrompt: String = ""` が実装済み（TASK-0056）
- 🔵 `domain/model/TemplateField.kt` に `llmPrompt: String = ""` が実装済み（TASK-0056）
- 🔵 `domain/model/FieldValueSource.kt` に `LLM` が追加済み（TASK-0056）
- 🔵 `TemplateEditViewModel`/`TemplateEditScreen` はまだ `bodyLlmPrompt`/`llmPrompt` を扱っておらず、本タスクで追加する（現状ソース確認済み）

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 2.1 ViewModel 状態（データクラス）

🔵 **入力データクラス拡張**（interfaces.kt / TASK-0071実装詳細）:

```kotlin
data class TemplateEditUiState(
    val templateId: Long? = null,
    val name: String = "",
    val body: String = "",
    val bodyLlmPrompt: String = "",   // ← 追加
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
    val llmPrompt: String = "",        // ← 追加
    val sortOrder: Int = 0,
)
```

### 2.2 ViewModel メソッド

| メソッド | 入力 | 出力/効果 | 信頼性 |
|---------|------|-----------|--------|
| `updateBodyLlmPrompt(prompt: String)` | `String`（プロンプト文字列。空文字許容） | `_uiState.update { it.copy(bodyLlmPrompt = prompt) }` で状態更新 | 🔵 完了条件・updateBody踏襲 |
| `save()`（改修） | なし（現在の `_uiState`） | `Template.bodyLlmPrompt = state.bodyLlmPrompt`、各 `TemplateField.llmPrompt = field.llmPrompt` を反映して `templateRepository.saveTemplate()` を呼ぶ | 🔵 完了条件 |
| `loadTemplate(id)`（改修） | `Long`（templateId） | `uiState.bodyLlmPrompt = template.bodyLlmPrompt`、各 `fields[i].llmPrompt = template.fields[i].llmPrompt` を復元 | 🔵 完了条件 |

### 2.3 UI（Compose）入出力

- 🔵 **本文用LLMプロンプト入力欄**: `OutlinedTextField`。`value = uiState.bodyLlmPrompt`、`onValueChange = { viewModel.updateBodyLlmPrompt(it) }`、ラベル `R.string.template_body_llm_prompt_label`、複数行（`minLines = 2` 程度）。（TASK-0071実装詳細6）
- 🔵 **FieldAddDialog の値取得方法リスト**: 既存の `FIXED/HTML_META/URL/EMPTY` に加え `FieldValueSource.LLM to stringResource(R.string.field_source_llm)` を追加。（TASK-0071実装詳細7、REQ-303）
- 🔵 **FieldAddDialog のLLMプロンプト入力欄**: `valueSource == FieldValueSource.LLM` のときのみ表示する `OutlinedTextField`（ラベル `R.string.field_llm_prompt_label`）。`onAdd` 時に `llmPrompt = if (valueSource == LLM) llmPromptInput else ""` を設定した `TemplateFieldEditState` を渡す。（TASK-0071実装詳細7）

### 2.4 入出力の関係性・データフロー

🔵 dataflow: `TemplateEditScreen(入力)` → `updateBodyLlmPrompt()/onAdd(field)` → `_uiState` → `save()` → `Template/TemplateField` → `TemplateRepository.saveTemplate()`（Room）。逆方向は `loadTemplate()` → `getTemplateById()` → `_uiState` → UI表示。（architecture.md/dataflow.md）

- **参照したEARS要件**: REQ-101, REQ-104, REQ-303
- **参照した設計文書**: interfaces.kt（TemplateEditUiState/TemplateFieldEditState）、TASK-0071.md 実装詳細1〜8

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **アーキテクチャ制約**: StateFlowの不可変更新（`.copy()` による更新のみ、直接フィールド変更禁止）。状態更新は `updateXxx()`、副作用（DB/save/load）は `viewModelScope.launch(Dispatchers.IO)` で実行する。（note.md 開発ルール、既存 TemplateEditViewModel パターン）
- 🔵 **文字列リソース制約（NFR-201）**: ユーザー向け文字列はハードコード禁止。日本語で `strings.xml` に登録する。追加項目:
  - `field_source_llm` = "LLM生成"
  - `field_llm_prompt_label` = "LLMプロンプト"
  - `template_body_llm_prompt_label` = "本文用LLMプロンプト"
  （architecture.md「res/values/strings.xml」、TASK-0071実装詳細8）
- 🔵 **互換性要件（REQ-405）**: 既存の `TemplateEditScreen`/`FieldAddDialog` のUI変更は許可されている。ただし既存の単体テスト・統合テストはすべて通ること（完了条件）。
- 🔵 **後方互換制約**: `bodyLlmPrompt`/`llmPrompt` はデフォルト空文字。未設定テンプレート（既存データ）でも例外なく動作すること。空文字は「未設定」を意味する（Template.kt/TemplateField.ktコメント、REQ-102）。
- 🟡 **スコープ制約**: 本タスクはプロンプトの入力・保存・復元UIまで。実際のLLM API呼び出し・値生成（REQ-304 TC-104-02）はTASK-0072/0073の担当であり本タスクには含めない。（FieldValueSource.ktコメント、acceptance-criteria.md備考「生成タイミングは設計フェーズで確定」）
- 🔵 **SDK/言語制約**: Kotlin 2.2.10 / Jetpack Compose BOM 2024.09.00 / Material3 / minSdk33。（note.md 技術スタック、gradle/libs.versions.toml）

- **参照したEARS要件**: REQ-405, REQ-102, NFR-201, REQ-304
- **参照した設計文書**: architecture.md「変更が必要な既存コンポーネント」、note.md 技術スタック/開発ルール

---

## 4. 想定される使用例（EARS Edgeケース・データフローベース）

### 4.1 基本的な使用パターン 🔵

1. ユーザーがテンプレート編集画面を開き、本文テンプレートの下の「本文用LLMプロンプト」欄にプロンプトを入力する。
2. 「フィールド追加」からダイアログを開き、値取得方法で「LLM生成」を選ぶ。
3. 表示されたプロンプト入力欄にフィールド用プロンプトを入力し追加する。
4. 保存する。→ `Template.bodyLlmPrompt` と `TemplateField.llmPrompt` が永続化される。
5. 同じテンプレートを再度開くと、両プロンプトが復元表示される。
（user-stories.md 詳細シナリオ、TC-104-01）

### 4.2 データフロー 🔵

- 入力フロー: UI → `updateBodyLlmPrompt()` / `FieldAddDialog.onAdd()` → `_uiState`
- 保存フロー: `save()` → `Template`/`TemplateField` 構築 → `saveTemplate()`
- 復元フロー: `loadTemplate()` → `getTemplateById()` → `_uiState` → UI
（dataflow.md、architecture.md）

### 4.3 エッジケース 🟡

- 🔵 **EDGE: プロンプト未入力で保存**: `bodyLlmPrompt`/`llmPrompt` が空文字のまま保存される（未設定扱い）。例外を出さない。（REQ-102・空文字=未設定）
- 🟡 **EDGE: LLM以外の値取得方法選択時**: `onAdd` で `llmPrompt = ""` が設定される（LLM入力欄の値は破棄）。（TASK-0071実装詳細7の分岐から妥当推測）
- 🟡 **EDGE: LLM選択→他ソースへ切替**: ダイアログ内でLLMを一旦選んでプロンプト入力後、別ソースに切り替えて追加した場合、保存される `llmPrompt` は空文字となる。（実装詳細7の `if (valueSource == LLM)` 条件からの妥当推測）
- 🔴 **EDGE(未確定): 空プロンプトのLLMフィールド生成挙動**: LLM選択かつプロンプト空のフィールドの生成時挙動は本タスク対象外（TASK-0072/0073）。本タスクでは保存・復元のみ保証。（設計文書に本タスクでの規定なし）

### 4.4 エラーケース 🟡

- 🟡 本タスクは入力・保存・復元が中心で、新規のエラー分岐は要件上定義されていない。既存 `save()`/`loadTemplate()` の例外挙動（`getTemplateById` が null の場合は復元スキップ）を踏襲する。（既存ソース確認、設計文書に追加エラー規定なし）

- **参照したEARS要件**: REQ-102, REQ-104, TC-104-01
- **参照した設計文書**: dataflow.md、user-stories.md ストーリー3.1

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: ストーリー3.1「カスタムフィールドの値取得方法として『LLM生成』を選びフィールドごとにプロンプトを設定したい」（user-stories.md）
- **参照した機能要件**:
  - REQ-101: 本文用LLMプロンプトをLLMリクエストに含める（本タスクは設定入力・保存部分）
  - REQ-102: 未設定時のボタン非活性（本タスクは空文字=未設定の保持）
  - REQ-104: カスタムフィールド用プロンプト入力欄を表示
  - REQ-303: `FieldValueSource` に `LLM` を追加（実装済み・UIで選択可能化）
  - REQ-304: LLMでの値生成（本タスク対象外・TASK-0072/0073）
  - REQ-405: 既存 `TemplateEditScreen`/`FieldAddDialog` のUI変更許可
- **参照した非機能要件**: NFR-201（文字列は日本語で strings.xml に登録）
- **参照したEdgeケース**: 空プロンプト保存、LLM以外選択時の `llmPrompt` 破棄
- **参照した受け入れ基準**:
  - TC-104-01: 値取得方法「LLM」選択でプロンプト入力欄が表示される（🔵 本タスク対象）
  - TC-104-02: LLM生成のフィールド値がテンプレート適用時に生成される（🟡 本タスク対象外）
- **参照した設計文書**:
  - **アーキテクチャ**: architecture.md「変更が必要な既存コンポーネント」（TemplateEditViewModel.kt / TemplateEditScreen.kt / strings.xml の行）
  - **データフロー**: dataflow.md（テンプレート編集・保存・復元フロー）
  - **型定義**: interfaces.kt（TemplateEditUiState / TemplateFieldEditState）
  - **データベース**: database-schema.kt（TemplateFieldEntity.llmPrompt / TemplateEntity.bodyLlmPrompt、TASK-0057実装済み）
  - **API仕様**: 本タスクでは外部API変更なし（LLM API呼び出しはTASK-0072/0073）

---

## 6. 受け入れテスト観点（本タスクで担保する範囲）

### 6.1 単体テスト（TemplateEditViewModelTest） 🔵

1. `updateBodyLlmPrompt("要約してください")` 後、`uiState.bodyLlmPrompt == "要約してください"`
2. `save()` で `Template.bodyLlmPrompt` と `TemplateField.llmPrompt` が正しく反映される
3. `loadTemplate(id)` で `bodyLlmPrompt`/`fields[i].llmPrompt` が復元される

### 6.2 統合テスト（Compose UI Test） 🔵

1. `FieldAddDialog` で「LLM」（`field_source_llm`）選択 → プロンプト入力欄（`field_llm_prompt_label`）が表示される（TC-104-01）
2. 本文用LLMプロンプト・フィールドLLMプロンプトを入力→保存→再読込で両値が復元表示される

（TASK-0071.md 単体/統合テスト要件セクション）

---

## 7. 実装・テスト対象ファイル一覧

| ファイル | 変更内容 | 信頼性 |
|---------|---------|--------|
| `app/src/main/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModel.kt` | UiStateに`bodyLlmPrompt`、FieldEditStateに`llmPrompt`、`updateBodyLlmPrompt()`、load/saveのマッピング追加 | 🔵 |
| `app/src/main/java/com/den4dr/share2Obsidian/ui/template/TemplateEditScreen.kt` | 本文用LLMプロンプト欄、FieldAddDialogのLLM選択肢とプロンプト欄追加 | 🟡（UI配置は妥当推測） |
| `app/src/main/res/values/strings.xml` | `field_source_llm`/`field_llm_prompt_label`/`template_body_llm_prompt_label` 追加 | 🔵 |
| `app/src/test/java/com/den4dr/share2Obsidian/ui/template/TemplateEditViewModelTest.kt` | 単体テスト3件追加 | 🔵 |
| Compose UI Test（既存TemplateEditScreenTest等） | 統合テスト2件追加 | 🔵 |

（実装済みで変更不要: `domain/model/Template.kt`, `TemplateField.kt`, `FieldValueSource.kt` ― TASK-0056）

---

## 8. 品質判定

```
✅ 高品質
- 要件の曖昧さ: なし（入出力・状態遷移・マッピングが具体的に定義済み）
- 入出力定義: 完全（データクラス・メソッドシグネチャ・UIバインドを明記）
- 制約条件: 明確（不可変更新・strings.xml・後方互換・スコープ境界）
- 実装可能性: 確実（ドメイン/永続化は実装済み、既存パターン踏襲で完結）
- 信頼性レベル: 🔵優勢
```

### 信頼性分布

| カテゴリ | 🔵 | 🟡 | 🔴 |
|---------|----|----|----|
| 機能概要 | 5 | 0 | 0 |
| 入出力仕様 | 6 | 0 | 0 |
| 制約条件 | 5 | 1 | 0 |
| 使用例/エッジ | 4 | 4 | 1 |
| **合計** | **20** | **5** | **1** |

- 🟡黄信号は主にUI具体配置・LLM選択切替時のフィールド破棄挙動（実装詳細からの妥当推測）
- 🔴赤信号は「空プロンプトLLMフィールドの生成挙動」1件のみ（本タスク対象外・TASK-0072/0073で確定）

---

**参照元一覧**:
- `docs/tasks/llm-memo-rewrite/TASK-0071.md`
- `docs/implements/llm-memo-rewrite/TASK-0071/note.md`
- `docs/spec/llm-memo-rewrite/requirements.md`（REQ-101/102/104/303/304/405, NFR-201）
- `docs/spec/llm-memo-rewrite/user-stories.md`（ストーリー3.1）
- `docs/spec/llm-memo-rewrite/acceptance-criteria.md`（TC-104-01/02）
- `docs/design/llm-memo-rewrite/architecture.md`, `interfaces.kt`, `dataflow.md`
- 現行ソース: `TemplateEditViewModel.kt`, `TemplateEditScreen.kt`, `Template.kt`, `TemplateField.kt`, `FieldValueSource.kt`

# TDD要件定義書: SettingsScreen LLM設定入力欄追加

**機能名**: settings-screen-llm-fields
**タスクID**: TASK-0067
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06
**フェーズ**: Phase 4 - LLM設定UI

---

## 【信頼性レベル凡例】

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測をしている
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測をしている

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: `SettingsScreen` に、LLM APIエンドポイントURL・APIキー・モデル名を入力する3つの入力欄を追加する。既存のvault/folder入力欄と同様、入力変更のたびに `SettingsViewModel` の対応する `update` 関数を呼び出して即時保存する（保存ボタンなし）。APIキー欄は機微情報のためマスク表示する。
- 🔵 **どのような問題を解決するか**: LLMによるメモ更改機能（後続タスク）を利用するために必要な、LLM API接続情報（エンドポイント・APIキー・モデル）をユーザーが設定画面から入力・保存できる手段を提供する。
- 🔵 **想定されるユーザー**: Obsidianへ共有する際にLLMでメモを整形したいアプリ利用者。設定画面でLLM API接続情報を登録する。
- 🔵 **システム内での位置づけ**: MVVMアーキテクチャのView層（Jetpack Compose UI）。`SettingsScreen`（View）が `SettingsViewModel`（TASK-0066完了）を通じて `LlmSettingsRepository`（TASK-0058完了、DataStore + EncryptedSharedPreferences）へ設定を保存する。本タスクはView層のUI追加のみを担当し、状態管理・保存ロジックは既存実装を利用する。
- **参照したEARS要件**: REQ-004（LLM API設定入力項目の提供）、REQ-401（APIキーの暗号化保存）
- **参照した設計文書**: `docs/design/llm-memo-rewrite/architecture.md`「LLM設定管理設計」「新規追加コンポーネント」「変更が必要な既存コンポーネント」セクション

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 2.1 入力（ユーザー操作 / 画面状態）

- 🔵 **画面状態入力（uiState）**: `SettingsViewModel.uiState: StateFlow<SettingsUiState>` から以下3フィールドを読み取り、各 `OutlinedTextField` の `value` に反映する。
  - `llmEndpointUrl: String`（デフォルト `""`）
  - `llmApiKey: String`（デフォルト `""`）
  - `llmModel: String`（デフォルト `""`）
- 🔵 **ユーザー入力**: 各入力欄へのテキスト入力（`onValueChange` で `String` を受け取る）。
  - endpointUrl欄への入力 → `viewModel.updateLlmEndpointUrl(value: String)`
  - apiKey欄への入力 → `viewModel.updateLlmApiKey(value: String)`
  - model欄への入力 → `viewModel.updateLlmModel(value: String)`

### 2.2 出力（ViewModel呼び出し / 画面表示）

- 🔵 **ViewModel呼び出し**: 各 `onValueChange` で対応する `update` 関数が入力値そのままで呼ばれる。バリデーション・整形は本タスク範囲外（ViewModel/Repository側の責務）。
- 🔵 **画面表示**: `uiState` の各値を対応する入力欄に表示する。
- 🟡 **apiKey欄のマスク表示**: `visualTransformation = PasswordVisualTransformation()` により、`llmApiKey` の値が画面上に平文表示されず、マスク文字（`•` 等）に変換されて表示される。

### 2.3 入出力の関係性

- 🔵 単方向データフロー（Compose の state hoisting）: `uiState`（value）→ 表示、ユーザー入力 → `onValueChange` → ViewModel → Repository保存 → Flow emit → `uiState` 更新 → recompose → 表示更新。
- 🔵 入力値は `update` 関数へそのまま渡され、UI層での加工は行わない（マスクは表示層 `visualTransformation` のみで、保持値・保存値は平文）。

### 2.4 データフロー

```
SettingsScreen 表示
  ↓ uiState から llmEndpointUrl / llmApiKey / llmModel を読み取り
OutlinedTextField(value = uiState.llm*)
  ↓ ユーザーがテキスト入力
onValueChange(value)
  ↓
viewModel.updateLlmEndpointUrl / updateLlmApiKey / updateLlmModel
  ↓ viewModelScope.launch(Dispatchers.IO)
llmSettingsRepository.saveEndpointUrl / saveApiKey / saveModel
  ↓ DataStore + EncryptedSharedPreferences 保存
getSettings() Flow が新値を emit
  ↓ combine() で uiState 更新
SettingsScreen が recompose し表示更新
```

- **参照したEARS要件**: REQ-004、REQ-401
- **参照した設計文書**: `docs/design/llm-memo-rewrite/architecture.md`「LLM設定管理設計」、`app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`（`SettingsUiState` / `updateLlm*` 関数の実装）

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

### 3.1 セキュリティ要件

- 🟡 **APIキーの画面マスク**: apiKey入力欄は `PasswordVisualTransformation()` を適用し、ショルダーサーフィン等での漏洩を防ぐ。（機微情報の一般的UIパターンとしての妥当な推測。EARS上はREQ-401がストレージ暗号化を規定しており、UI層マスクは補完的措置）
- 🔵 **保存時の暗号化はRepository責務**: APIキーの暗号化保存は `LlmSettingsRepository`（EncryptedSharedPreferences）が担当済み（TASK-0058）。UI層で追加の暗号化は行わない（REQ-401）。

### 3.2 互換性要件

- 🔵 **既存UIの後方互換**: 既存のvault/folder入力欄・テンプレート管理ListItem・既存 `SettingsScreenTest` の各テストは引き続き成功しなければならない。
- 🔵 **既存ViewModel APIの利用**: TASK-0066で追加済みの `updateLlmEndpointUrl` / `updateLlmApiKey` / `updateLlmModel` および `SettingsUiState` の3フィールドをそのまま利用する（ViewModel側の変更は不要）。

### 3.3 アーキテクチャ制約

- 🔵 **View層のみの変更**: 本タスクの変更は `SettingsScreen.kt`（および文字列リソース `strings.xml`）に限定する。ViewModel/Repository/DIは変更しない。
- 🔵 **既存Composeパターン踏襲**: 既存vault/folder欄と同一の構造（`OutlinedTextField` + `fillMaxWidth()` + `padding(horizontal = 16.dp, vertical = 4.dp)` + `testTag`）に揃える。
- 🟡 **セクション区切り**: vault/folder設定とLLM設定の間を `HorizontalDivider()` で視覚的に区切る（既存デザインパターンからの妥当な推測）。
- 🟡 **文字列リソース**: ラベルはハードコードせず `stringResource` を用いる。`settings_llm_endpoint_label` / `settings_llm_apikey_label` / `settings_llm_model_label` を `res/values/strings.xml` に追加する必要がある（既存 `settings_vault_label` 等のパターンより。現時点で当該文字列は未定義）。

### 3.4 testTag命名制約

- 🔵 testTagは既存の `settings_vault_field` / `settings_folder_field` と同じ命名規則に従い、以下を使用する。
  - `settings_llm_endpoint_field`
  - `settings_llm_apikey_field`
  - `settings_llm_model_field`

### 3.5 パフォーマンス要件

- 🟡 入力変更時の即時保存は `viewModelScope.launch(Dispatchers.IO)` で非同期実行済み（ViewModel側）。UI層は同期的にrecomposeで反映するのみで追加のパフォーマンス考慮は不要（妥当な推測）。

- **参照したEARS要件**: REQ-004、REQ-401、NFR-201（LLM処理失敗時のToast。ただしToast表示は後続タスク範囲でありUI入力欄自体には直接関与しない）
- **参照した設計文書**: `docs/design/llm-memo-rewrite/architecture.md`「LLM設定管理設計」「変更が必要な既存コンポーネント」、`app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsScreen.kt`（既存パターン）

---

## 4. 想定される使用例（EARSデータフロー・エッジケースベース）

### 4.1 基本的な使用パターン 🔵

- **UC-1（新規入力）**: ユーザーが設定画面を開き、LLM設定セクションの各欄にエンドポイントURL・APIキー・モデル名を入力する。入力の都度、対応する `update` 関数が呼ばれ即時保存される。
- **UC-2（既存値の表示）**: 既にLLM設定が保存されている状態で設定画面を開くと、各入力欄に保存済みの値が初期表示される（apiKeyはマスク表示）。

### 4.2 エッジケース 🟡

- **EC-1（空文字入力）**: 入力欄をクリアして空文字にした場合も、`update` 関数が空文字で呼ばれ保存される（バリデーションなし。ViewModel/Repositoryが空文字を許容する前提。TASK-0066テストのTC-B-01「空文字保存」より妥当）。
- **EC-2（未設定状態）**: 全フィールドがデフォルト `""` の状態でも、入力欄は空欄として正常表示される（null非許容・デフォルト空文字のため）。
- **EC-3（apiKeyのマスク表示）**: 非空のapiKeyがセットされている場合、平文ではなくマスク文字で表示される。

### 4.3 エラーケース 🟡

- 🟡 本タスク（UI入力欄追加）自体にはエラーハンドリングは含まれない。LLM API呼び出し失敗時のToast（NFR-201）は「メモを更改」実行時（後続タスク TASK-0068以降）の責務であり、設定入力欄には関与しない（妥当な推測）。

- **参照したEARS要件**: REQ-004、REQ-401、（NFR-201は後続タスク範囲）
- **参照した設計文書**: `docs/design/llm-memo-rewrite/dataflow.md`、`docs/tasks/llm-memo-rewrite/TASK-0067.md`（テスト要件TC-1〜TC-5）

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: LLM API設定の入力・保存（設定画面からのLLM接続情報登録）
- **参照した機能要件**:
  - REQ-004: SettingsScreenにLLM APIエンドポイントURL・APIキー・モデル名の入力項目を提供する 🔵
  - REQ-401: LLM APIキーを暗号化ストレージに保存する（UI層はマスク表示で補完） 🔵
- **参照した非機能要件**:
  - NFR-201: LLM処理失敗時のToast表示（本タスク直接対象外、後続タスクで対応） 🟡
- **参照したEdgeケース**: 空文字保存（TASK-0066 TC-B-01 由来）、未設定デフォルト表示 🟡
- **参照した受け入れ基準**（`docs/spec/llm-memo-rewrite/acceptance-criteria.md` TC-004系）:
  - LLM設定3項目の入力欄が表示される
  - 各欄の入力でViewModelの `update` 関数が呼ばれる
  - apiKey欄がマスク表示される
  - 画面起動時に初期値が反映される
- **参照した設計文書**:
  - **アーキテクチャ**: `docs/design/llm-memo-rewrite/architecture.md`「LLM設定管理設計」「新規追加コンポーネント」「変更が必要な既存コンポーネント」
  - **データフロー**: `docs/design/llm-memo-rewrite/dataflow.md`（設定保存フロー）
  - **型定義**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`（`SettingsUiState`、`updateLlm*`）、`docs/design/llm-memo-rewrite/interfaces.kt`
  - **データベース/ストレージ**: `docs/design/llm-memo-rewrite/database-schema.kt`（`LlmSettings`、DataStore + EncryptedSharedPreferences）
  - **既存実装参照**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsScreen.kt`（vault/folderパターン）、`app/src/androidTest/java/com/den4dr/share2Obsidian/ui/SettingsScreenTest.kt`（既存Compose UIテスト・Fake実装）

---

## 6. 受け入れ基準（テストケース対応）

| ID | 内容 | 対応REQ | 信頼性 |
|----|------|---------|--------|
| TC-1 | endpointUrl欄への入力で `updateLlmEndpointUrl()` が入力値で呼ばれる | REQ-004 | 🔵 |
| TC-2 | apiKey欄への入力で `updateLlmApiKey()` が入力値で呼ばれる | REQ-004 | 🔵 |
| TC-3 | model欄への入力で `updateLlmModel()` が入力値で呼ばれる | REQ-004 | 🔵 |
| TC-4 | apiKey欄の表示がマスクされている（平文非表示） | REQ-401補完 | 🟡 |
| TC-5 | 画面起動時に `uiState` の初期値が各入力欄に反映される | REQ-004 | 🔵 |

**完了条件**（TASK-0067より 🔵）:
- `SettingsScreen` にLLM設定用 `OutlinedTextField` が3つ追加されている
- apiKey欄が `PasswordVisualTransformation` 等でマスク表示されている
- 各欄の `onValueChange` で対応する `update` 関数が呼ばれる
- 画面起動時に `uiState` 初期値が各欄に反映される
- テスト（Compose UI Test）が成功する

---

## 7. 実装対象ファイル

- **変更**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsScreen.kt`（LLM設定セクション追加）
- **追加**: `app/src/main/res/values/strings.xml`（`settings_llm_endpoint_label` / `settings_llm_apikey_label` / `settings_llm_model_label`）
- **変更**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/SettingsScreenTest.kt`（TC-1〜TC-5追加）
- **利用（変更なし）**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`（TASK-0066完了）

---

## 8. 品質判定

```
✅ 高品質:
- 要件の曖昧さ: なし（既存vault/folderパターンに準拠し入出力が明確）
- 入出力定義: 完全（uiState 3フィールド / update 3関数 / testTag 3種を特定）
- 制約条件: 明確（testTag命名・マスク方式・セクション区切り・変更範囲を規定）
- 実装可能性: 確実（依存タスクTASK-0066/0058/0061すべて完了済み）
- 信頼性レベル: 🔵 が主体（コア要件は設計文書・既存実装に基づき推測ほぼなし）
```

### 信頼性レベル分布

| カテゴリ | 🔵 | 🟡 | 🔴 |
|---------|----|----|----|
| 機能概要 | 5 | 0 | 0 |
| 入出力 | 6 | 1 | 0 |
| 制約条件 | 5 | 4 | 0 |
| 使用例 | 2 | 4 | 0 |
| 受け入れ基準 | 4 | 1 | 0 |
| **合計** | **22** | **10** | **0** |

- 🔵 青信号: 22項目（約69%）
- 🟡 黄信号: 10項目（約31%）
- 🔴 赤信号: 0項目（0%）

**総合評価**: 高品質。赤信号（根拠なし推測）ゼロ。黄信号はいずれもマスク表示・セクション区切り・文字列リソースといったUI詳細に集中しており、既存パターンおよび一般的UIプラクティスからの妥当な推測に留まる。

---

**作成**: tsumiki:tdd-requirements TASK-0067

# TASK-0066 TDD要件定義書: SettingsViewModel LLM設定対応

**機能名**: SettingsViewModel LLM設定対応 (settings-viewmodel-llm)
**タスクID**: TASK-0066
**要件名**: llm-memo-rewrite
**フェーズ**: Phase 4 - LLM設定UI
**作成日**: 2026-07-06

---

## 信頼性レベル凡例

- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: 既存 `SettingsViewModel` に `LlmSettingsRepository` を追加注入し、`SettingsUiState` に LLM API 設定 3 項目（`llmEndpointUrl` / `llmApiKey` / `llmModel`）を追加する。`noteSettingsRepository.getSettings()` と `llmSettingsRepository.getSettings()` を `combine()` して単一の `uiState` を構築し、各値を即時保存する `updateLlmEndpointUrl()` / `updateLlmApiKey()` / `updateLlmModel()` を実装する。
- 🔵 **どのような問題を解決するか**: LLM によるメモ整形機能（llm-memo-rewrite）を利用するために、ユーザーが LLM API 接続情報（エンドポイントURL・APIキー・モデル名）を設定画面から入力・保存できるようにする。ViewModel 層が設定の読み書き窓口を提供することで、UI（SettingsScreen）が LLM 設定を表示・編集できる基盤を整える。
- 🔵 **想定されるユーザー**: 本アプリ（Share2Obsidian）を使い、共有テキストを LLM で整形して Obsidian に送りたいエンドユーザー。
- 🔵 **システム内での位置づけ**: MVVM の ViewModel 層。UI（SettingsScreen, TASK-0067）と Repository 層（`LlmSettingsRepository`, TASK-0058 実装済 / DI は TASK-0061 実装済）の橋渡しを担う。既存の vault/folder 設定（NoteSettingsRepository 経由）と同居する。
- **参照したEARS要件**: REQ-004（SettingsScreen へ LLM APIエンドポイントURL・APIキー・モデル名の入力項目を提供）、REQ-401（APIキー暗号化保存）
- **参照した設計文書**: `docs/design/llm-memo-rewrite/architecture.md`「新規追加コンポーネント」表・「変更が必要な既存コンポーネント」（SettingsViewModel への LlmSettingsRepository 注入）、`docs/design/llm-memo-rewrite/interfaces.kt`（LlmSettings / LlmSettingsRepository 定義）

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 2.1 対象クラス・シグネチャ

🔵 *interfaces.kt・note.md・既存 SettingsViewModel.kt より*

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val noteSettingsRepository: NoteSettingsRepository,
    private val llmSettingsRepository: LlmSettingsRepository,
) : ViewModel()
```

### 2.2 入力（メソッド引数）

🔵 *完了条件・既存 updateVault()/updateFolder() パターンより*

| メソッド | 引数 | 型 | 制約 |
|---------|------|-----|------|
| `updateLlmEndpointUrl` | `url` | `String` | 空文字許容（未設定を "" で表現） |
| `updateLlmApiKey` | `apiKey` | `String` | 空文字許容。機微情報（暗号化保存対象） |
| `updateLlmModel` | `model` | `String` | 空文字許容 |

- 🔵 各 update 関数は引数をそのまま対応する Repository の save 系関数に渡す（変換・バリデーションは本タスクでは行わない）。

### 2.3 入力（Repository からの Flow）

🔵 *LlmSettingsRepository.kt / LlmSettings.kt より*

- `noteSettingsRepository.getSettings(): Flow<NoteSettings>` — `NoteSettings(vault, folder)`
- `llmSettingsRepository.getSettings(): Flow<LlmSettings>` — `LlmSettings(endpointUrl, apiKey, model)`（各 String、デフォルト ""）

### 2.4 出力

🔵 *完了条件・REQ-004 より*

- `uiState: StateFlow<SettingsUiState>`
- `SettingsUiState` 拡張後の定義:

```kotlin
data class SettingsUiState(
    val vault: String = "",
    val folder: String = "",
    val llmEndpointUrl: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "",
)
```

- update 系関数の戻り値: なし（`Unit`）。副作用として Repository への保存を行う。

### 2.5 入出力の関係性・データフロー

🔵 *architecture.md「LLM設定管理設計」・note.md データフローより*

```
NoteSettingsRepository.getSettings() ─┐
                                       ├─ combine { note, llm -> SettingsUiState(...) }
LlmSettingsRepository.getSettings() ──┘        │
                                               └─ .stateIn(viewModelScope,
                                                     SharingStarted.WhileSubscribed(5_000),
                                                     SettingsUiState())  → uiState

update系関数 → viewModelScope.launch(Dispatchers.IO) → llmSettingsRepository.save*()
            → getSettings() Flow が再emit → combine 再実行 → uiState 更新 → UI自動反映
```

- **参照したEARS要件**: REQ-004
- **参照した設計文書**: `interfaces.kt`（LlmSettings / LlmSettingsRepository）、`architecture.md`「LLM設定管理設計」

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **アーキテクチャ制約**: `@HiltViewModel` + `@Inject constructor` による Hilt DI を維持する。`SettingsViewModel` は既に Hilt 化済みのため、コンストラクタに `llmSettingsRepository` を追加するのみ。（architecture.md「変更が必要な既存コンポーネント」）
- 🔵 **セキュリティ要件（REQ-401）**: APIキーは `LlmSettingsRepository`（TASK-0058）側で EncryptedSharedPreferences に暗号化保存済み。本 ViewModel は API キー値を平文で恒久保持せず、Flow 経由で取得・save 系関数へ受け渡すのみ。
- 🔵 **スレッド制約**: save 系処理（EncryptedSharedPreferences 同期 API を含む）はメインスレッドで実行しない。update 系関数は `viewModelScope.launch(Dispatchers.IO)` でバックグラウンド実行する。（既存 updateVault/updateFolder パターン踏襲）
- 🔵 **StateFlow 構築制約**: `combine(...)` は `Flow<T>` を返すため `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())` で `StateFlow` に変換する。5 秒の購読停止でリソース解放（リーク対策）。初期値は `SettingsUiState()`（全フィールド ""）。
- 🔵 **後方互換性（REQ-021）**: 既存 vault/folder 設定機能を破壊しない。`SettingsUiState` へのフィールド追加はデフォルト値付きで行い、既存テスト（`SettingsViewModelTest`）が引き続き通ること。
- 🔵 **技術スタック制約**: Kotlin 2.2.10 / minSdk 33 / Coroutines + Flow / テストは JUnit4 + kotlinx-coroutines-test + MockK。
- **参照したEARS要件**: REQ-004, REQ-401, REQ-021
- **参照した設計文書**: `architecture.md`「LLM設定管理設計」「新規追加コンポーネント」「変更が必要な既存コンポーネント」

---

## 4. 想定される使用例（Edgeケース・データフローベース）

### 4.1 基本的な使用パターン 🔵

- SettingsScreen 表示時: `uiState` を購読すると、DataStore/EncryptedSharedPreferences に保存済みの vault/folder/LLM 設定を反映した `SettingsUiState` が流れる。
- ユーザーが LLM エンドポイントURL入力欄を編集 → `updateLlmEndpointUrl(newUrl)` → 保存 → Flow 再emit → `uiState` 更新（即時保存・保存ボタンなし）。
- APIキー・モデル名も同様。

### 4.2 データフロー 🔵

- 上記 2.5 のフロー図の通り。両 Repository の値が `combine()` により 1 つの `SettingsUiState` に集約される。

### 4.3 エッジケース 🟡

- 🟡 **空文字入力**: 未設定・クリア操作を空文字 "" で表現する。update 系関数は "" をそのまま保存する（バリデーションなし。妥当な推測）。
- 🟡 **初期状態（未保存）**: Repository が `LlmSettings()`（全 ""）を返す場合、`uiState` の LLM 3 項目は "" となる。初期値 `SettingsUiState()` と整合。
- 🟡 **連続更新**: 同一項目を短時間に複数回更新した場合、各呼び出しが個別に `launch(Dispatchers.IO)` で保存される（最終値が Flow に反映される。既存パターン踏襲の推測）。

### 4.4 エラーケース 🔴

- 🔴 Repository の save 系が例外を投げた場合のハンドリングは本タスクの完了条件・タスク定義に明記がない。既存 updateVault/updateFolder 同様、本タスクでは例外ハンドリングを追加しない（設計文書に根拠なし）。

- **参照したEARS要件**: REQ-004, REQ-021
- **参照した設計文書**: `architecture.md`「LLM設定管理設計」、note.md データフロー図

---

## 5. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: LLM でメモを整形して Obsidian に送るための LLM API 接続設定入力（llm-memo-rewrite）
- **参照した機能要件**: REQ-004（LLM設定入力項目の提供）
- **参照した非機能要件**: REQ-401（APIキー暗号化保存）
- **関連する既存要件**: REQ-021（vault/folder のグローバル設定・即時保存、後方互換）
- **参照したEdgeケース**: 空文字入力・初期未保存状態（設計文書に明示はなく妥当な推測 🟡）
- **参照した受け入れ基準（TASK-0066.md 完了条件）**:
  - コンストラクタに `llmSettingsRepository: LlmSettingsRepository` を追加
  - `SettingsUiState` に `llmEndpointUrl` / `llmApiKey` / `llmModel`(各 `String = ""`) を追加
  - `uiState` を `combine()` で構築
  - `updateLlmEndpointUrl()` / `updateLlmApiKey()` / `updateLlmModel()` が対応 save 系を呼ぶ
  - `mise exec -- ./gradlew test` が成功
- **参照した設計文書**:
  - **アーキテクチャ**: `docs/design/llm-memo-rewrite/architecture.md`「新規追加コンポーネント」「変更が必要な既存コンポーネント」「LLM設定管理設計」
  - **型定義**: `docs/design/llm-memo-rewrite/interfaces.kt`（`LlmSettings`, `LlmSettingsRepository`）
  - **実装済コード**: `app/src/main/java/com/den4dr/share2Obsidian/data/llm/LlmSettings.kt`, `.../LlmSettingsRepository.kt`
  - **変更対象コード**: `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt`
  - **テスト対象**: `app/src/test/java/com/den4dr/share2Obsidian/ui/SettingsViewModelTest.kt`

---

## 6. テストケース対応（TASK-0066.md より）

🔵 *完了条件・既存テストパターンより*

| ID | 内容 | 種別 |
|----|------|------|
| TC-1 | `updateLlmEndpointUrl()` が `saveEndpointUrl()` を1回呼ぶ | 正常系 |
| TC-2 | `updateLlmApiKey()` が `saveApiKey()` を1回呼ぶ | 正常系 |
| TC-3 | `updateLlmModel()` が `saveModel()` を1回呼ぶ | 正常系 |
| TC-4 | `uiState` が note/llm 両 Repository の値を反映して構築される | 正常系 |

- 既存テスト（`uiState_reflectsRepositorySettings`, `updateVault_savesToRepository` 等）が引き続き通ること（後方互換）。ただし既存テストはコンストラクタ引数が 1 個のため、`llmSettingsRepository` 追加に伴い既存テストの `SettingsViewModel(repo)` 呼び出しも 2 引数化する修正が必要（🟡 妥当な推測 / red フェーズで扱う）。

---

## 7. 品質判定

```
✅ 高品質:
- 要件の曖昧さ: なし（完了条件が明確、実装コード骨子が note.md に提示済み）
- 入出力定義: 完全（型・シグネチャ・データフロー確定）
- 制約条件: 明確（DI/スレッド/セキュリティ/後方互換すべて明記）
- 実装可能性: 確実（依存タスク TASK-0058/0061 実装済を確認）
- 信頼性レベル: 🔵 が中心。エラーケース等一部 🟡🔴 だが本タスク範囲外を明示
```

### 信頼性レベル分布

| セクション | 🔵 | 🟡 | 🔴 |
|-----------|-----|-----|-----|
| 1. 機能概要 | 4 | 0 | 0 |
| 2. 入出力仕様 | 5 | 0 | 0 |
| 3. 制約条件 | 6 | 0 | 0 |
| 4. 使用例 | 2 | 3 | 1 |
| 合計 | 17 | 3 | 1 |

**総合判定**: 高品質（🔵 約81%）。🟡🔴 はエッジ/エラーケースで、いずれも「本タスク範囲外」または「妥当な推測」として明示済み。

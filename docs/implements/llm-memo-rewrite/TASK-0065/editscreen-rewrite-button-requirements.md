# TASK-0065 TDD要件定義書: EditScreen UI「メモを更改」ボタン・ローディング・エラーToast実装

**機能名**: editscreen-rewrite-button
**タスクID**: TASK-0065
**要件名**: llm-memo-rewrite
**フェーズ**: Phase 3 - 本文リライトUI（Must Have）
**作成日**: 2026-07-06

---

## 0. 前提

**【信頼性レベル凡例】**:
- 🔵 **青信号**: EARS要件定義書・設計文書を参考にしてほぼ推測していない
- 🟡 **黄信号**: EARS要件定義書・設計文書から妥当な推測
- 🔴 **赤信号**: EARS要件定義書・設計文書にない推測

本タスクは UI 実装専任である。前提となる ViewModel 側（`rewriteBody()`・`errorEvents`・`formState.isRewritingBody`・`formState.rewriteBodyEnabled`）は TASK-0063 で、文字列リソース（`button_rewrite_body`・`error_llm_*`）は TASK-0064 で実装済み。本タスクでは既存 `EditScreen.kt` にボタンとエラーToast購読を追加し、状態を UI に反映することのみを行う。🔵 *TASK-0065.md タスク概要・note.md「3. 関連実装」より*

---

## 1. 機能の概要（EARS要件定義書・設計文書ベース）

- 🔵 **何をする機能か**: `EditScreen` の本文編集エリア付近に「メモを更改」ボタンを追加し、ユーザーが押下すると `viewModel.rewriteBody()`（LLMによる本文リライト）を起動する。処理中はボタン内にローディングインジケータを表示してボタンを非活性化し、失敗時は ViewModel から流れるエラーイベントを購読して日本語Toastを表示する。🔵 *REQ-001, REQ-201, NFR-201 より*
- 🔵 **どのような問題を解決するか**: ユーザーが共有・編集中のメモ本文を、テンプレートに保存したプロンプトを用いて外部LLMで書き換えられるようにする。その起点となる操作UI（発動ボタン）と、処理中・失敗のフィードバックUIを提供する。🔵 *requirements.md 概要・REQ-001 より*
- 🔵 **想定されるユーザー**: Obsidianへメモを共有する際、EditScreen上でLLMによる本文リライトを行いたいユーザー。🔵 *user-stories.md / requirements.md 概要より*
- 🔵 **システム内での位置づけ**: 単一アクティビティ + Compose UI + MVVM + Hilt アーキテクチャの「View層（Compose UI）」に属する。`EditScreen` Composable が `EditScreenViewModel`（TASK-0063でHilt化済み）の `formState: StateFlow` を購読し、`errorEvents: SharedFlow` を `LaunchedEffect` で購読する。🔵 *architecture.md「変更が必要な既存コンポーネント」・design-interview.md Q2/Q3 より*
- **参照したEARS要件**: REQ-001, REQ-102, REQ-201, NFR-201, NFR-202
- **参照した設計文書**: architecture.md「変更が必要な既存コンポーネント（EditScreen.kt）」、design-interview.md Q3（SharedFlowエラー通知）

---

## 2. 入力・出力の仕様（EARS機能要件・型定義ベース）

### 2.1 対象コンポーネント

- **実装ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreen.kt`
- **Composable シグネチャ**（既存、変更なし）:
  ```kotlin
  @Composable
  fun EditScreen(
      viewModel: EditScreenViewModel,
      onSend: (SendParams) -> Unit,
      onCancel: () -> Unit,
      onNavigateToSettings: () -> Unit = {},
  )
  ```
  🔵 *EditScreen.kt 既存実装より（本タスクでシグネチャは変更しない）*

### 2.2 入力（ViewModel から購読する状態・イベント）

| 入力 | 型 | 供給元 | UIでの用途 |
|------|-----|--------|-----------|
| `formState.rewriteBodyEnabled` | `Boolean` | `EditScreenViewModel.formState: StateFlow<EditFormState>` | ボタン活性判定（false=非活性）🔵 REQ-102 |
| `formState.isRewritingBody` | `Boolean` | 同上 | ローディング表示・非活性判定 🔵 REQ-201 |
| `errorEvents` | `SharedFlow<Int>`（string resource ID を流す） | `EditScreenViewModel.errorEvents` | Toast表示するメッセージのリソースID 🔵 NFR-201 |

🔵 *EditFormState.kt（`isRewritingBody`, `rewriteBodyEnabled`）・EditScreenViewModel.kt（`errorEvents`）より*

### 2.3 出力（UIが行う作用）

| 出力 | 内容 |
|------|------|
| ボタン押下 → 副作用 | `viewModel.rewriteBody()` を呼び出す（引数なし・戻り値なし）🔵 REQ-001 |
| ボタン活性状態 | `enabled = formState.rewriteBodyEnabled && !formState.isRewritingBody` 🔵 |
| ボタン内表示 | `isRewritingBody=true` 時 `CircularProgressIndicator`、それ以外は `Text(stringResource(R.string.button_rewrite_body))` 🔵 |
| エラーToast | `errorEvents` 受信時に `Toast.makeText(context, context.getString(resId), Toast.LENGTH_LONG).show()` 🔵 NFR-201 |

### 2.4 入出力の関係性 / データフロー

```
formState.rewriteBodyEnabled / isRewritingBody
      ↓（購読）
「メモを更改」ボタン活性・表示制御
      ↓（押下）
viewModel.rewriteBody()  ← TASK-0063実装済み
      ↓
  ├ 成功: formState.body 上書き → 本文フィールド自動再構成
  ├ 失敗: errorEvents に resId emit → LaunchedEffect購読 → Toast表示
  └ 常に: isRewritingBody を true→false へ遷移 → インジケータ消滅
```
🔵 *dataflow.md・note.md「6. データフロー」より*

- **参照したEARS要件**: REQ-001, REQ-102, REQ-201, NFR-201
- **参照した設計文書**: interfaces.kt（EditFormState / errorEvents 仕様）、EditFormState.kt、EditScreenViewModel.kt

---

## 3. 制約条件（EARS非機能要件・アーキテクチャ設計ベース）

- 🔵 **UIレイヤ制約**: `EditScreen` はロジックを持たず、`viewModel.rewriteBody()` の呼び出しと状態購読のみを行う（MVVM）。LLM呼び出し・タイムアウト（NFR-001/REQ-202）・成功/失敗判定は ViewModel/Repository 側（TASK-0060/0063）の責務であり本タスク対象外。🔵 *architecture.md・note.md「6. 注意事項」より*
- 🔵 **文字列リソース制約**: ボタンラベル・エラーメッセージはすべて `stringResource()` / `context.getString()` で `strings.xml`（TASK-0064追加分: `button_rewrite_body`, `error_llm_network`, `error_llm_auth`, `error_llm_timeout`, `error_llm_empty_response`, `error_llm_unknown`）から取得する。ハードコード禁止（NFR-201, NFR-102）。🔵 *TASK-0064・NFR-201 より*
- 🔵 **エラー表示トーン制約**: 既存の `MainActivity.onSend` の `ActivityNotFoundException` 捕捉時Toast（`Toast.LENGTH_LONG`・日本語）と同等のトーンを踏襲する。🔵 *NFR-201・note.md より*
- 🔵 **レイアウト互換性制約**: 追加は既存のスクロール可能 `Column` 内・`body_field` 付近への挿入に限る。`body_field` の testTag は変更せず、`bottomBar`（送信/キャンセル）も変更しない（既存テスト後方互換）。🔵 *note.md「6. 注意事項 / 技術的制約」・EditScreen.kt より*
- 🔵 **購読の安全性制約**: `errorEvents` の購読は `LaunchedEffect(Unit)` + `collectLatest` により画面表示時1回のみセットアップし、リコンポーズでの重複購読・リークを避ける。`LocalContext.current` から取得した `context` を Toast に用いる。🔵 *design-interview.md Q3・note.md「LaunchedEffect による購読の安全性」より*
- 🔵 **アーキテクチャ制約**: `EditScreenViewModel` は Hilt化済み（TASK-0063）で `errorEvents: SharedFlow<Int>` を公開。EditScreen は同一ViewModelインスタンスを購読する。🔵 *design-interview.md Q2/Q3・EditScreenViewModel.kt より*
- 🟡 **CircularProgressIndicator サイズ**: 標準ボタン内に収めるため `size = 16.dp`, `strokeWidth = 2.dp` を用いる（既存ボタン高さ約48.dpとのバランス）。🟡 *note.md 記載の推奨値。明示的ヒアリングはなく妥当な推測*
- 🟡 **ボタン配置位置**: `body_field` の直前または直後（同Column内）に配置する。厳密な位置は明示指定がないため既存UIパターンに沿った妥当推測。🟡 *TASK-0065.md「body_field付近（例えば直前または直後）」より*

- **参照したEARS要件**: NFR-001, NFR-102, NFR-201, NFR-202, REQ-102, REQ-202
- **参照した設計文書**: architecture.md「非機能要件の実現方法」、design-interview.md Q2/Q3

---

## 4. 想定される使用例（Edgeケース・データフローベース）

### 4.1 基本的な使用パターン（通常系）

- 🔵 **UC-1 リライト起動**: プロンプト設定済みテンプレートで開いた EditScreen で、ユーザーが「メモを更改」ボタンを押下 → `viewModel.rewriteBody()` 起動 → 成功時に本文が上書きされる。🔵 *REQ-001, REQ-003 より*
- 🔵 **UC-2 プロンプト未設定**: `rewriteBodyEnabled=false`（テンプレートに本文プロンプト無し）の場合、ボタンは表示されるが非活性（グレーアウト）で押下不可。🔵 *REQ-102 より*
- 🔵 **UC-3 処理中表示**: `isRewritingBody=true` の間、ボタン内に `CircularProgressIndicator` が表示され、ボタンは非活性。処理完了で通常ラベルに戻る。🔵 *REQ-201, NFR-202 より*

### 4.2 エラーケース

- 🔵 **EC-1 ネットワーク不通**: LLM呼び出しがネットワークエラーで失敗 → `errorEvents` に `error_llm_network` の resId が emit → Toast表示。本文は変更されない（変更は ViewModel 責務）。🔵 *EDGE-001, NFR-201 より*
- 🔵 **EC-2 認証エラー**: APIキー不正等 → `error_llm_auth` の Toast。🔵 *EDGE-002 より*
- 🔵 **EC-3 タイムアウト**: 30秒タイムアウト → `error_llm_timeout` の Toast。🔵 *EDGE-003, REQ-202 より*
- 🟡 **EC-4 空応答**: LLMが空応答 → `error_llm_empty_response` の Toast。本文上書きなし。🟡 *EDGE-004（妥当推測）より*
- 🔵 いずれのエラー時も UI は `errorEvents` の resId を受け取り Toast表示するのみで、resId の選定は ViewModel 側の責務。🔵 *design-interview.md Q3・NFR-201 より*

### 4.3 境界値

- 🔵 **BC-1 空 sourceContent**: 元コンテンツが空文字でもボタンは非活性化されない（活性判定は `rewriteBodyEnabled`＝プロンプト有無のみに依存）。空でもリライトは実行され得る。🔵 *EDGE-101 より*

- **参照したEARS要件**: REQ-001, REQ-003, REQ-102, REQ-201, EDGE-001〜004, EDGE-101
- **参照した設計文書**: dataflow.md、EditScreenViewModel.kt `rewriteBody()`

---

## 5. テストケース概要（TASK-0065.md 定義・Compose UI Test）

| ID | 内容 | 判定 | 信頼性 |
|----|------|------|--------|
| TC-1 | `rewriteBodyEnabled=false` 時、`onNodeWithTag("rewrite_body_button")` が `assertIsNotEnabled()` | 非活性 | 🔵 REQ-102 |
| TC-2 | `rewriteBodyEnabled=true` かつ `isRewritingBody=false` 時、ボタン `performClick()` で `viewModel.rewriteBody()` が1回呼ばれる（MockK検証） | 呼び出し | 🔵 REQ-001 |
| TC-3 | `isRewritingBody=true` 時、ボタン配下に `CircularProgressIndicator` 相当ノードが表示され、かつ `assertIsNotEnabled()` | ローディング+非活性 | 🔵 REQ-201 |
| TC-4（任意） | `errorEvents` emit 時のToast購読。Toast検証は難易度が高いため emit/購読セットアップ確認レベルでOK | 補助 | 🟡 NFR-201 |

- **テストファイル**: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/EditScreenTest.kt`（既存に追加）
- **testTag**: `rewrite_body_button`
- **フレームワーク**: Compose UI Test（AndroidJUnit4, `createAndroidComposeRule<ComponentActivity>()`）+ MockK
- 🔵 *TASK-0065.md「単体テスト要件」・note.md「5. テスト関連情報」より*

---

## 6. EARS要件・設計文書との対応関係

- **参照したユーザストーリー**: 「LLMでメモ本文を書き換える」（user-stories.md / requirements.md 概要）
- **参照した機能要件**:
  - REQ-001（「メモを更改」ボタン表示）
  - REQ-102（プロンプト未設定時のボタン非活性化のUI反映）
  - REQ-201（LLM呼び出し中のローディング表示）
  - REQ-202（30秒タイムアウト＝ViewModel側、UIはローディング/エラー反映のみ）
- **参照した非機能要件**: NFR-201（日本語Toastエラー）、NFR-202（ローディング表示によるUX）、NFR-102（機微情報を出力しない）
- **参照したEdgeケース**: EDGE-001（ネットワーク不通）、EDGE-002（認証エラー）、EDGE-003（タイムアウト）、EDGE-004（空応答）、EDGE-101（空sourceContentでも非活性化しない）
- **参照した受け入れ基準**: TASK-0065.md TC-1〜TC-3（ボタン非活性 / rewriteBody()呼び出し / ローディング表示）
- **参照した設計文書**:
  - **アーキテクチャ**: architecture.md「変更が必要な既存コンポーネント（EditScreen.kt）」「非機能要件の実現方法」
  - **データフロー**: dataflow.md（EditScreen UIフロー）、note.md「6. データフロー」
  - **型定義**: `EditFormState.kt`（`isRewritingBody`, `rewriteBodyEnabled`）、`EditScreenViewModel.kt`（`errorEvents: SharedFlow<Int>`, `rewriteBody()`）
  - **設計ヒアリング**: design-interview.md Q2（Hilt化）、Q3（SharedFlowエラー通知→LaunchedEffect+collectLatest+Toast）
  - **文字列リソース**: `strings.xml`（`button_rewrite_body`, `error_llm_*`：TASK-0064追加分）

---

## 7. 品質判定

| 評価軸 | 状態 |
|--------|------|
| 要件の曖昧さ | ほぼなし（ボタン配置の厳密位置・インジケータサイズのみ🟡だが実装に支障なし） |
| 入出力定義 | 完全（購読状態・イベント・testTag・呼び出しメソッド確定） |
| 制約条件 | 明確（UIレイヤ責務・文字列リソース・後方互換・購読安全性） |
| 実装可能性 | 確実（前提のViewModel・文字列リソースは実装済み。既存EditScreen.ktへの追加のみ） |
| 信頼性レベル分布 | 🔵 多数（大半が確定）／🟡 少数（ボタン位置・インジケータサイズ）／🔴 なし |

**総合判定**: ✅ 高品質

---

**作成日**: 2026-07-06 by tsumiki:tdd-requirements

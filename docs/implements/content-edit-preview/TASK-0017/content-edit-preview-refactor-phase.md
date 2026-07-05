# TASK-0017: EditScreenViewModel 実装 - Refactorフェーズ記録

**タスクID**: TASK-0017
**機能名**: content-edit-preview
**作成日**: 2026-03-31
**フェーズ**: Refactor（コード品質改善）

---

## 1. リファクタリング方針

Greenフェーズで作成した実装コードの品質を向上させる。機能的な変更は行わず、以下の3点に集中した:

1. **クラス KDoc の精緻化**: 「Greenフェーズ」表記を削除し、責務・設計方針・依存関係を明確化
2. **メソッド KDoc の充実**: `@param` / `@return` / `@see` タグを整備し、各メソッドの制約・許容値を明示
3. **`updateXxx()` 系メソッドのコメント統一**: テスト対応番号の記述を削除し、仕様・制約の説明に刷新

---

## 2. セキュリティレビュー結果

| 観点 | 評価 | 詳細 |
|------|------|------|
| 入力検証 | ✅ 問題なし | `ifBlank { null }` で空・スペースのみのタイトルを安全に処理（EDGE-001）🔵 |
| 型安全性 | ✅ 問題なし | Kotlin 型システムで null 安全性確保（`ProcessedContent.title: String?`）🔵 |
| URI エンコーディング | ✅ 適切な委譲 | ViewModel の責務外。`NoteComposer.buildUri()` が担当（関心分離）🔵 |
| データ漏洩リスク | ✅ なし | ViewModel は Intent 起動を行わない。データ変換専門 🔵 |
| 重大な脆弱性 | ✅ なし | ローカル状態管理のみ。外部通信・永続化なし 🔵 |

---

## 3. パフォーマンスレビュー結果

| メソッド | 計算量 | 評価 | 詳細 |
|---------|--------|------|------|
| `initialize()` | O(n) | ✅ | `joinToString` はタグ数 n に比例（通常数件）🔵 |
| `updateTitle()` | O(1) | ✅ | `copy()` によるイミュータブル更新 🔵 |
| `updateBody()` | O(1) | ✅ | `copy()` によるイミュータブル更新 🔵 |
| `updateTagsText()` | O(1) | ✅ | `copy()` によるイミュータブル更新 🔵 |
| `updateFolder()` | O(1) | ✅ | `copy()` によるイミュータブル更新 🔵 |
| `buildSendParams()` | O(n) | ✅ | `parseTagsText` のみがタグ文字列長 n に依存 🔵 |
| `initialized` フラグガード | O(1) | ✅ | 画面回転時の重複初期化を O(1) で防止 🔵 |

テスト実行時間: 17テスト合計 2.079 秒（TC-003 の初回Robolectricウォームアップ 1.847s を含む）。
以降のテストはすべて 0.01〜0.03s で高速実行。

---

## 4. 改善内容詳細

### 4.1 クラス KDoc の改善

**Before（Greenフェーズ）:**
```kotlin
/**
 * 【機能概要】: 編集画面（EditScreen）のフォーム状態を管理する ViewModel
 * 【実装方針】: TDD Greenフェーズ — テストを通すための最小実装。
 *              StateFlow<EditFormState> で UI 状態を管理し、
 *              画面回転時の重複初期化を initialized フラグで防止する（EDGE-101）。
 * 【テスト対応】: TASK-0017 EditScreenViewModelTest の TC-001〜TC-013 を通すための実装
 * 🔵 信頼性レベル: REQ-003, REQ-101, REQ-103, EDGE-101・interfaces.kt EditScreenViewModelSpec より
 */
```

**After（Refactorフェーズ）:**
```kotlin
/**
 * 【機能概要】: 編集画面（EditScreen）のフォーム状態を管理する ViewModel
 * 【設計方針】: `StateFlow<EditFormState>` で UI 状態をイミュータブルに管理する。
 *              AndroidX ViewModel のライフサイクルにより、画面回転（Activity 再作成）時も
 *              ViewModel インスタンスが保持される。`initialized` フラグで重複初期化を防止する（EDGE-101）。
 * 【責務】:
 *   1. `initialize()` で ProcessedContent + NoteConfig からフォーム初期値を構築
 *   2. `updateXxx()` でユーザーのフォーム入力を StateFlow に反映
 *   3. `buildSendParams()` で送信時にフォーム状態を SendParams に変換（タグパース・タイトル null 変換）
 * 【依存関係】:
 *   - 依存先: EditFormState, SendParams, parseTagsText(), ProcessedContent, NoteConfig
 *   - 依存元: EditScreen Composable（TASK-0018）
 * 🔵 信頼性レベル: REQ-003, REQ-101, REQ-103, EDGE-101・interfaces.kt EditScreenViewModelSpec より
 */
```

改善点: 🔵
- 「TDD Greenフェーズ」という開発フェーズ表記を削除
- 責務を3点箇条書きで明示
- 依存関係（依存先・依存元）を明記

### 4.2 `initialize()` KDoc の改善

`@param` に詳細説明を追加し、初期値マッピングを KDoc 内に明記した。

**改善点: 🔵**
- `@param processed`: nullable の説明を追加
- `@param config`: 含まれるフィールドの説明を追加
- 初期値マッピングを KDoc 内に整理（title/body/tagsText/folder）

### 4.3 `updateXxx()` 系メソッドの KDoc 改善

**Before（Greenフェーズ）:**
```kotlin
/**
 * 【機能概要】: フォーム状態のタイトルフィールドを更新する
 * 【実装方針】: copy() でイミュータブルに新しい状態を作成し、StateFlow に設定する
 * 【テスト対応】: TC-004（タイトル更新）、TC-006（空タイトル）、TC-007（スペースのみタイトル）、TC-013（複数更新）
 * 🔵 信頼性レベル: REQ-003 より
 *
 * @param title 新しいタイトル文字列（空文字列・スペースのみも許容）
 */
```

**After（Refactorフェーズ）:**
```kotlin
/**
 * 【機能概要】: フォーム状態のタイトルフィールドを更新する
 * 【実装方針】: `copy()` でタイトルのみを変更したイミュータブルな新しい状態を生成し、StateFlow に設定する。
 *              他のフィールド（body, tagsText, folder）には影響しない。
 * 【空文字許容】: 空文字列・スペースのみも有効な入力として許容（`buildSendParams()` で null 変換）
 * 🔵 信頼性レベル: REQ-003 より
 *
 * @param title 新しいタイトル文字列（空文字列・スペースのみも許容）
 */
```

改善点: 🔵
- 「テスト対応: TC-XXX」という開発内部情報を削除
- 副作用なし（他フィールドへの影響なし）を明示
- 各メソッドの許容値・制約をコメントに追記

### 4.4 `buildSendParams()` KDoc の改善

**改善点: 🔵🟡**
- `@see parseTagsText` タグを追加（TASK-0016実装との関係を明示）
- `@return` に型情報を追記
- `【保守性】` コメントで `config` をメソッド引数とする設計意図を説明

---

## 5. 最終実装コード全文

**ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`（164行）

```kotlin
package com.den4dr.share2Obsidian.ui

import androidx.lifecycle.ViewModel
import com.den4dr.share2Obsidian.content.ProcessedContent
import com.den4dr.share2Obsidian.format.NoteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 【機能概要】: 編集画面（EditScreen）のフォーム状態を管理する ViewModel
 * 【設計方針】: `StateFlow<EditFormState>` で UI 状態をイミュータブルに管理する。
 *              AndroidX ViewModel のライフサイクルにより、画面回転（Activity 再作成）時も
 *              ViewModel インスタンスが保持される。`initialized` フラグで重複初期化を防止する（EDGE-101）。
 * 【責務】:
 *   1. `initialize()` で ProcessedContent + NoteConfig からフォーム初期値を構築
 *   2. `updateXxx()` でユーザーのフォーム入力を StateFlow に反映
 *   3. `buildSendParams()` で送信時にフォーム状態を SendParams に変換（タグパース・タイトル null 変換）
 * 【依存関係】:
 *   - 依存先: EditFormState, SendParams, parseTagsText(), ProcessedContent, NoteConfig
 *   - 依存元: EditScreen Composable（TASK-0018）
 * 🔵 信頼性レベル: REQ-003, REQ-101, REQ-103, EDGE-101・interfaces.kt EditScreenViewModelSpec より
 */
class EditScreenViewModel : ViewModel() {

    // 【状態定義】: フォーム状態を保持する MutableStateFlow。外部には asStateFlow() でイミュータブルに公開する 🔵
    // 【初期値】: initialize() 呼び出し前のデフォルト値。すべて空文字列で初期化する 🟡
    private val _formState = MutableStateFlow(
        EditFormState(title = "", body = "", tagsText = "", folder = "")
    )

    /**
     * 【プロパティ概要】: フォーム状態の公開 StateFlow（イミュータブル）
     * 🔵 信頼性レベル: REQ-003・EDGE-101 より
     */
    val formState: StateFlow<EditFormState> = _formState.asStateFlow()

    // 【重複初期化防止フラグ】: initialize() が2回以上呼ばれた場合に2回目以降を無視するためのフラグ 🔵
    // 【用途】: 画面回転時に Activity が再作成されても、ViewModel は生存し続けるため
    //           initialize() が再呼び出しされても既存の編集内容を上書きしない（EDGE-101）
    private var initialized = false

    /**
     * 【機能概要】: ProcessedContent と NoteConfig からフォーム初期値をセットする
     * 【実装方針】: `initialized` フラグで重複呼び出しを防止し、初回のみ `_formState` を更新する。
     *              2回目以降の呼び出し（画面回転時の Activity 再作成を想定）は何もせずに早期リターンする（EDGE-101）。
     * 【初期値マッピング】:
     *   - title   : `processed.title ?: ""`（null の場合は空文字）
     *   - body    : `processed.body`（そのまま使用）
     *   - tagsText: `config.defaultTags.joinToString(", ")`（List → カンマ+スペース区切り文字列）
     *   - folder  : `config.folder`（そのまま使用）
     * 🔵 信頼性レベル: REQ-001, REQ-003, REQ-405・acceptance-criteria.md TC-003-01〜04 より
     *
     * @param processed コンテンツ処理結果。`title` は nullable（共有元アプリがタイトルを提供しない場合は null）
     * @param config アプリ設定。`vault`・`folder`・`defaultTags` を含む（TASK-0015: NoteConfig）
     */
    fun initialize(processed: ProcessedContent, config: NoteConfig) {
        // 【重複実行防止】: 画面回転時に initialize() が再度呼ばれても無視する（EDGE-101）🔵
        if (initialized) return

        // 【初期化フラグ更新】: 次回以降の呼び出しを無視するためにフラグを立てる 🔵
        initialized = true

        // 【状態更新】: ProcessedContent と NoteConfig から EditFormState の初期値を構築して StateFlow に設定する 🔵
        _formState.value = EditFormState(
            // 【タイトル初期値】: ProcessedContent.title が null の場合は空文字列で初期化する（TC-003-02）🔵
            title = processed.title ?: "",
            // 【本文初期値】: ProcessedContent.body をそのまま使用する（EDGE-002 空文字許容）🔵
            body = processed.body,
            // 【タグ初期値】: NoteConfig.defaultTags をカンマ+スペース区切り文字列に変換する（REQ-103）🔵
            tagsText = config.defaultTags.joinToString(", "),
            // 【フォルダ初期値】: NoteConfig.folder をそのまま使用する（REQ-405）🔵
            folder = config.folder,
        )
    }

    // ... （updateXxx() 系・buildSendParams() は最終実装コードを参照）
}
```

---

## 6. テスト実行結果（リファクタリング後）

```
mise exec -- ./gradlew :app:testDebugUnitTest

BUILD SUCCESSFUL in 7s（全体テスト・回帰テスト含む） ✅
```

### テストケース別結果（17件全て成功）

| TC | テスト内容 | 結果 |
|----|----------|------|
| TC-001 | initialize() で初期値がセットされる | ✅ PASSED |
| TC-002 | initialize() は2回目以降無視される | ✅ PASSED |
| TC-003 | title が null の場合は空文字で初期化 | ✅ PASSED |
| TC-004 | updateTitle() でタイトルが変更される | ✅ PASSED |
| TC-004b | updateBody() で本文が変更される | ✅ PASSED |
| TC-004c | updateTagsText() でタグテキストが変更される | ✅ PASSED |
| TC-004d | updateFolder() でフォルダが変更される | ✅ PASSED |
| TC-005 | buildSendParams() でタグがパースされる | ✅ PASSED |
| TC-005b | buildSendParams() で config が正しく渡される | ✅ PASSED |
| TC-006 | buildSendParams() で空タイトルが null になる | ✅ PASSED |
| TC-007 | buildSendParams() でスペースのみタイトルが null になる | ✅ PASSED |
| TC-008 | 複数デフォルトタグが joinToString で変換される | ✅ PASSED |
| TC-009 | buildSendParams() で空本文がそのまま渡される | ✅ PASSED |
| TC-010 | buildSendParams() で空タグテキストが空リストになる | ✅ PASSED |
| TC-011 | デフォルトタグが空リストの場合の初期化 | ✅ PASSED |
| TC-012 | initialize() 前の formState デフォルト値 | ✅ PASSED |
| TC-013 | 連続した update メソッド呼び出し | ✅ PASSED |

---

## 7. 品質評価

```
✅ 高品質:
- テスト結果: 17テスト全て継続成功（回帰テスト含む）
- セキュリティ: 重大な脆弱性なし（ローカル状態管理のみ）
- パフォーマンス: 重大な性能課題なし（全メソッド O(1)〜O(n)）
- リファクタ品質: 目標（Greenフェーズ表記削除・KDoc充実）達成
- コード品質: 164行（500行制限内）、Trailing comma 統一済み
- ドキュメント: refactor-phase.md 作成完了、memo.md 更新済み
```

---

**作成者**: Claude Code (tsumiki:tdd-refactor)
**作成日**: 2026-03-31

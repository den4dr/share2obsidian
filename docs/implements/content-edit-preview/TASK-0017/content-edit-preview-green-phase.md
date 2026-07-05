# TASK-0017: EditScreenViewModel 実装 - Greenフェーズ記録

**タスクID**: TASK-0017
**機能名**: content-edit-preview
**作成日**: 2026-03-31
**フェーズ**: Green（テストを通す最小実装）

---

## 1. 実装方針

Redフェーズで作成したスタブ実装（`throw NotImplementedError`）を実際のロジックに差し替える。
要件定義・テストケースファイルとの差異確認を実施し、仕様通りの実装であることを確認した。

### 実装の核心

| 要素 | 実装 | 根拠 |
|------|------|------|
| 状態管理 | `MutableStateFlow<EditFormState>` + `asStateFlow()` | EDGE-101, REQ-003 |
| 重複初期化防止 | `initialized: Boolean = false` フラグ | EDGE-101 |
| タイトル null 変換 | `ifBlank { null }` | EDGE-001 |
| タグパース | `parseTagsText()` 再利用（TASK-0016実装済み） | REQ-103 |
| 状態更新 | `copy()` によるイミュータブル更新 | REQ-003 |

---

## 2. 実装コード全文

**ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`

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
 * 【実装方針】: TDD Greenフェーズ — テストを通すための最小実装。
 *              StateFlow<EditFormState> で UI 状態を管理し、
 *              画面回転時の重複初期化を initialized フラグで防止する（EDGE-101）。
 * 【テスト対応】: TASK-0017 EditScreenViewModelTest の TC-001〜TC-013 を通すための実装
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

    fun initialize(processed: ProcessedContent, config: NoteConfig) {
        if (initialized) return
        initialized = true
        _formState.value = EditFormState(
            title = processed.title ?: "",
            body = processed.body,
            tagsText = config.defaultTags.joinToString(", "),
            folder = config.folder,
        )
    }

    fun updateTitle(title: String) {
        _formState.value = _formState.value.copy(title = title)
    }

    fun updateBody(body: String) {
        _formState.value = _formState.value.copy(body = body)
    }

    fun updateTagsText(tagsText: String) {
        _formState.value = _formState.value.copy(tagsText = tagsText)
    }

    fun updateFolder(folder: String) {
        _formState.value = _formState.value.copy(folder = folder)
    }

    fun buildSendParams(config: NoteConfig): SendParams {
        val state = _formState.value
        return SendParams(
            title = state.title.ifBlank { null },
            body = state.body,
            tags = parseTagsText(state.tagsText),
            config = config,
        )
    }
}
```

---

## 3. テスト実行結果

```
mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest"

BUILD SUCCESSFUL in 4s
EditScreenViewModelTest: 17 tests completed, 0 failed ✅

mise exec -- ./gradlew :app:testDebugUnitTest

BUILD SUCCESSFUL in 5s（全体テスト・回帰テスト含む） ✅
```

### テストケース別結果

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

## 4. 課題・改善点（Refactorフェーズで対応）

- KDoc コメントの詳細化（パラメータ説明の充実）
- 処理ブロックコメントの整合性確認
- Trailing comma スタイルの統一確認

---

**作成者**: Claude Code (tsumiki:tdd-green)
**作成日**: 2026-03-31

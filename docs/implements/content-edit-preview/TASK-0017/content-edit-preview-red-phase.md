# TASK-0017: EditScreenViewModel 実装 - Redフェーズ記録

**タスクID**: TASK-0017
**機能名**: content-edit-preview
**作成日**: 2026-03-31
**フェーズ**: Red（失敗するテスト作成）

---

## 1. 作成したテストケース一覧

| TC | テスト名 | 信頼性 | 対応要件 |
|----|----------|--------|----------|
| TC-001 | initialize で ProcessedContent と NoteConfig から初期値が正しくセットされる | 🔵 | REQ-003 |
| TC-002 | initialize は2回目以降の呼び出しを無視して状態を保持する | 🔵 | EDGE-101 |
| TC-003 | title が null の ProcessedContent で初期化すると title フィールドが空文字になる | 🔵 | TC-003-02 |
| TC-004 | updateTitle でフォーム状態のタイトルが正しく更新される | 🔵 | REQ-003 |
| TC-004b | updateBody でフォーム状態の本文が正しく更新される | 🔵 | REQ-003 |
| TC-004c | updateTagsText でフォーム状態のタグテキストが正しく更新される | 🔵 | REQ-103 |
| TC-004d | updateFolder でフォーム状態のフォルダが正しく更新される | 🔵 | REQ-405 |
| TC-005 | buildSendParams でカンマ区切りタグテキストが List にパースされる | 🔵 | REQ-103 |
| TC-005b | buildSendParams で引数の NoteConfig がそのまま SendParams に設定される | 🔵 | REQ-405 |
| TC-006 | buildSendParams で空文字タイトルが null に変換される | 🔵 | EDGE-001 |
| TC-007 | buildSendParams でスペースのみのタイトルが null に変換される | 🟡 | EDGE-001 |
| TC-008 | 複数のデフォルトタグが joinToString でカンマ+スペース区切り文字列になる | 🔵 | REQ-103 |
| TC-009 | buildSendParams で空文字の本文がそのまま SendParams に設定される | 🔵 | EDGE-002 |
| TC-010 | buildSendParams で空文字のタグテキストが空リストに変換される | 🔵 | EDGE-003 |
| TC-011 | デフォルトタグが空リストの場合に tagsText が空文字で初期化される | 🟡 | EDGE-003 |
| TC-012 | initialize 呼び出し前の formState がデフォルト値を持つ | 🟡 | (StateFlow初期値) |
| TC-013 | 複数の update メソッドを連続して呼び出した場合にすべての変更が反映される | 🟡 | REQ-003 |

**合計**: 17件（🔵 青信号: 13件、🟡 黄信号: 4件、🔴 赤信号: 0件）

---

## 2. テストコードファイル

**テストファイルパス**: `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelTest.kt`

**スタブ実装ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`

---

## 3. テスト実行結果（Redフェーズ確認）

```
EditScreenViewModelTest: 17 tests completed, 16 failed

- TC-001: FAILED (NotImplementedError: initialize() 未実装)
- TC-002: FAILED (NotImplementedError: initialize() 未実装)
- TC-003: FAILED (NotImplementedError: initialize() 未実装)
- TC-004: FAILED (NotImplementedError: updateTitle() 未実装)
- TC-004b: FAILED (NotImplementedError: updateBody() 未実装)
- TC-004c: FAILED (NotImplementedError: updateTagsText() 未実装)
- TC-004d: FAILED (NotImplementedError: updateFolder() 未実装)
- TC-005: FAILED (NotImplementedError: updateTagsText() 未実装)
- TC-005b: FAILED (NotImplementedError: initialize() 未実装)
- TC-006: FAILED (NotImplementedError: updateTitle() 未実装)
- TC-007: FAILED (NotImplementedError: updateTitle() 未実装)
- TC-008: FAILED (NotImplementedError: initialize() 未実装)
- TC-009: FAILED (NotImplementedError: updateBody() 未実装)
- TC-010: FAILED (NotImplementedError: updateTagsText() 未実装)
- TC-011: FAILED (NotImplementedError: initialize() 未実装)
- TC-012: PASSED (StateFlow 初期値アクセスはスタブでも動作)
- TC-013: FAILED (NotImplementedError: updateTitle() 未実装)
```

**Redフェーズ確認**: ✅ 16件が正しく失敗

---

## 4. 期待される失敗内容

すべてのテストが `kotlin.NotImplementedError` で失敗する。

スタブ実装（`EditScreenViewModel.kt`）は各メソッドで `throw NotImplementedError(...)` を実行するため、
Greenフェーズで実際のロジックを実装するまでは常に失敗する。

---

## 5. Greenフェーズで実装すべき内容

### EditScreenViewModel の実装要件

1. **`initialize(processed, config)` メソッド**:
   - `initialized` フラグで重複実行を防ぐ
   - `_formState.value = EditFormState(title = processed.title ?: "", body = processed.body, tagsText = config.defaultTags.joinToString(", "), folder = config.folder)`

2. **`updateTitle(title)` メソッド**:
   - `_formState.value = _formState.value.copy(title = title)`

3. **`updateBody(body)` メソッド**:
   - `_formState.value = _formState.value.copy(body = body)`

4. **`updateTagsText(tagsText)` メソッド**:
   - `_formState.value = _formState.value.copy(tagsText = tagsText)`

5. **`updateFolder(folder)` メソッド**:
   - `_formState.value = _formState.value.copy(folder = folder)`

6. **`buildSendParams(config)` メソッド**:
   - `val state = _formState.value`
   - `title = state.title.ifBlank { null }`
   - `tags = parseTagsText(state.tagsText)`
   - `SendParams(title, body = state.body, tags, config)` を返す

### 実装コマンド

```bash
# テスト実行（Greenフェーズ確認）
mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest"
```

---

## 6. 信頼性レベルサマリー

| 信頼性 | 件数 | 割合 |
|--------|------|------|
| 🔵 青信号（元資料に基づく） | 13 | 76% |
| 🟡 黄信号（妥当な推測） | 4 | 24% |
| 🔴 赤信号（推測） | 0 | 0% |

**品質評価**: ✅ 高品質（青信号 76%、赤信号なし）

---

**作成者**: Claude Code (tsumiki:tdd-red)
**作成日**: 2026-03-31

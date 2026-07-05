# TASK-0016: content-edit-preview - TDD Redフェーズ記録

**タスクID**: TASK-0016
**機能名**: content-edit-preview
**要件名**: content-edit-preview
**作成日**: 2026-03-31
**フェーズ**: Red（失敗テスト作成済み）

---

## 1. 作成したテストケース一覧

### ParseTagsTextTest.kt（8件）

| テストID | テスト名 | 分類 | 信頼性 |
|---------|---------|------|--------|
| TC-016-004 | カンマ区切りの複数タグが正しくパースされる | 正常系 | 🔵 |
| TC-016-005 | 前後スペースを含むタグが正しくトリムされる | 正常系 | 🔵 |
| TC-016-006 | 単一タグがサイズ1のリストに変換される | 正常系 | 🔵 |
| TC-016-009 | 空文字列入力で空リストが返される | 異常系 | 🟡 |
| TC-016-010 | カンマのみの入力で空リストが返される | 異常系 | 🟡 |
| TC-016-011 | スペースとカンマだけの入力で空リストが返される | 異常系 | 🟡 |
| TC-016-014 | 末尾カンマがある入力で有効タグのみが返される | 境界値 | 🟡 |
| TC-016-015 | 先頭カンマがある入力で有効タグのみが返される | 境界値 | 🟡 |
| TC-016-016 | 連続するカンマがある入力で有効タグのみが返される | 境界値 | 🟡 |

### EditFormStateTest.kt（5件）

| テストID | テスト名 | 分類 | 信頼性 |
|---------|---------|------|--------|
| TC-016-001 | EditFormState の全フィールド指定でインスタンスが正しく生成される | 正常系 | 🔵 |
| TC-016-002 | ProcessedContent と NoteConfig から正しい初期値で EditFormState が生成される | 正常系 | 🔵 |
| TC-016-003 | ProcessedContent の title が null の場合に EditFormState の title が空文字列になる | 正常系 | 🔵 |
| TC-016-017 | 同一パラメータの EditFormState インスタンスが equals で等価と判定される | 境界値 | 🟡 |
| TC-016-018 | copy メソッドで特定フィールドのみが変更され他は元の値が保持される | 境界値 | 🟡 |

### SendParamsTest.kt（5件）

| テストID | テスト名 | 分類 | 信頼性 |
|---------|---------|------|--------|
| TC-016-007 | SendParams の全フィールド指定でインスタンスが正しく生成される | 正常系 | 🔵 |
| TC-016-008 | SendParams の title が null でインスタンスが正しく生成される | 正常系 | 🔵 |
| TC-016-012 | SendParams の body が空文字列でも正常にインスタンスが生成される | 異常系 | 🟡 |
| TC-016-013 | SendParams の tags が空リストでも正常にインスタンスが生成される | 異常系 | 🟡 |
| TC-016-019 | 同一パラメータの SendParams インスタンスが equals で等価と判定される | 境界値 | 🟡 |

**合計: 19テストケース**

---

## 2. テストファイルのパス

- `app/src/test/java/com/den4dr/share2Obsidian/ui/ParseTagsTextTest.kt`
- `app/src/test/java/com/den4dr/share2Obsidian/ui/EditFormStateTest.kt`
- `app/src/test/java/com/den4dr/share2Obsidian/ui/SendParamsTest.kt`

---

## 3. 期待される失敗内容（確認済み）

テスト実行コマンド:
```bash
mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.ParseTagsTextTest" --tests "com.den4dr.share2Obsidian.ui.EditFormStateTest" --tests "com.den4dr.share2Obsidian.ui.SendParamsTest"
```

実際の失敗（2026-03-31 確認済み）:
```
> Task :app:compileDebugUnitTestKotlin FAILED
e: Unresolved reference 'EditFormState'.  （6箇所）
e: Unresolved reference 'parseTagsText'.  （8箇所）
e: Unresolved reference 'SendParams'.     （6箇所）
```

**失敗理由**: 以下のクラス/関数が未実装のため、コンパイルエラーが発生
- `EditFormState` データクラス（`ui` パッケージ）
- `parseTagsText()` トップレベル関数（`EditFormState.kt` に同居）
- `SendParams` データクラス（`ui` パッケージ）

---

## 4. Greenフェーズで実装すべき内容

### 実装ファイル

| ファイル | 内容 |
|---------|------|
| `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt` | `EditFormState` データクラス + `parseTagsText()` 関数 |
| `app/src/main/java/com/den4dr/share2Obsidian/ui/SendParams.kt` | `SendParams` データクラス |

### EditFormState データクラス仕様

```kotlin
package com.den4dr.share2Obsidian.ui

data class EditFormState(
    val title: String,      // 初期値: ProcessedContent.title ?: ""
    val body: String,       // 初期値: ProcessedContent.body
    val tagsText: String,   // 初期値: config.defaultTags.joinToString(", ")
    val folder: String      // 初期値: config.folder
)
```

### parseTagsText 関数仕様

```kotlin
fun parseTagsText(tagsText: String): List<String> {
    return tagsText
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
```

### SendParams データクラス仕様

```kotlin
package com.den4dr.share2Obsidian.ui

import com.den4dr.share2Obsidian.format.NoteConfig

data class SendParams(
    val title: String?,         // nullable（タイトルなしは null）
    val body: String,
    val tags: List<String>,
    val config: NoteConfig
)
```

---

## 5. 品質評価

| 評価項目 | 結果 |
|---------|------|
| テスト実行 | ✅ コンパイルエラーで失敗（未実装クラスへの参照） |
| 期待値 | ✅ 明確で具体的 |
| アサーション | ✅ 適切（assertEquals, assertTrue, assertNull） |
| 実装方針 | ✅ 明確（要件定義・interfaces.kt に基づく） |
| 信頼性レベル | 🔵 6件 / 🟡 13件 / 🔴 0件 |

**判定**: ✅ 高品質（Redフェーズの要件を満たしている）

---

**作成者**: Claude Code
**最終更新**: 2026-03-31

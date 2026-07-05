# TASK-0015: content-edit-preview Red フェーズ記録

**タスクID**: TASK-0015
**機能名**: content-edit-preview
**要件名**: content-edit-preview
**作成日**: 2026-03-29
**フェーズ**: Red（失敗するテスト作成）

---

## 1. 作成したテストケース一覧

| テストID | テスト名 | 対象メソッド | 信頼性 |
|---------|---------|------------|--------|
| TC-001 | タイトル・本文・タグありの Frontmatter が正しく生成される | NoteComposer.buildFrontmatter() | 🔵 |
| TC-002 | タイトルが null の場合に title フィールドが省略される | NoteComposer.buildFrontmatter() | 🔵 |
| TC-003 | 複数タグがカンマ+スペース区切りで正しく出力される | NoteComposer.buildFrontmatter() | 🔵 |
| TC-004 | buildUri が正しい scheme・host・クエリパラメータを持つ URI を生成する | NoteComposer.buildUri() | 🔵 |
| TC-005 | fromAppConfig が AppConfig の値を正確に読み込む | NoteConfig.fromAppConfig() | 🔵 |
| TC-006 | title が null の場合に URI の title パラメータが空文字になる | NoteComposer.buildUri() | 🔵 |
| TC-007 | 本文が空文字列の場合でも正常に Frontmatter が生成される | NoteComposer.buildFrontmatter() | 🟡 |
| TC-008 | タイトル null・本文空文字列の場合でも正常に Frontmatter が生成される | NoteComposer.buildFrontmatter() | 🟡 |
| TC-009 | タイトルにダブルクォートを含む場合の Frontmatter 生成 | NoteComposer.buildFrontmatter() | 🔵 |
| TC-010 | タグリストが空の場合に tags空配列が出力される | NoteComposer.buildFrontmatter() | 🔵 |
| TC-011 | タグが1つだけの場合にカンマなしで出力される | NoteComposer.buildFrontmatter() | 🔵 |
| TC-012 | 同一パラメータの NoteConfig インスタンスが等価と判定される | NoteConfig (data class) | 🟡 |
| TC-013 | 長い本文（改行を含む複数行テキスト）が正しく処理される | NoteComposer.buildFrontmatter() | 🟡 |
| TC-014 | NoteConfig の vault と folder が URI に正しく反映される | NoteComposer.buildUri() | 🟡 |
| TC-015 | NoteComposer buildFrontmatter が FrontmatterBuilder build と同等の出力を生成する | NoteComposer.buildFrontmatter() | 🟡 |

**合計**: 15テストケース

**信頼性レベル分布**:
- 🔵 青信号: 9件 (60%)
- 🟡 黄信号: 6件 (40%)
- 🔴 赤信号: 0件 (0%)

---

## 2. テストファイルパス

```
app/src/test/java/com/den4dr/share2Obsidian/format/NoteComposerTest.kt
```

---

## 3. 期待される失敗内容

### 失敗の原因
`NoteConfig` と `NoteComposer` クラスが未実装のため、コンパイルエラーが発生する。

### 実際の失敗メッセージ（テスト実行時）
```
e: Unresolved reference 'NoteConfig'.
e: Unresolved reference 'NoteComposer'.

FAILURE: Build failed with an exception.
* What went wrong:
  Execution failed for task ':app:compileDebugUnitTestKotlin'.
  > Compilation error. See log for more details
```

### 失敗を確認したコマンド
```bash
mise exec -- ./gradlew :app:compileDebugUnitTestKotlin
```

---

## 4. テストコード（変更点）

### パッケージインポート修正
テストファイルのインポートを要件定義書のパッケージ制約に合わせて修正:
```kotlin
// 変更前
import com.den4dr.share2Obsidian.NoteConfig
import com.den4dr.share2Obsidian.NoteComposer

// 変更後
import com.den4dr.share2Obsidian.format.NoteConfig
import com.den4dr.share2Obsidian.format.NoteComposer
```

### テスト名の修正
Kotlin バッククォート文字列で `[]` が illegal characters エラーになるため修正:
```kotlin
// 変更前
fun `TC-010 タグリストが空の場合に tags[] が出力される`()

// 変更後
fun `TC-010 タグリストが空の場合に tags空配列が出力される`()
```

### emptyList() 型引数を明示
Kotlin コンパイラの型推論を助けるため明示的に型引数を追加:
```kotlin
// 変更前
NoteConfig(vault = "testVault", folder = "70_clippings", defaultTags = emptyList())

// 変更後
NoteConfig(vault = "testVault", folder = "70_clippings", defaultTags = emptyList<String>())
```

---

## 5. Green フェーズで実装すべき内容

### 5.1 NoteConfig データクラス
**ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/format/NoteConfig.kt`

```kotlin
package com.den4dr.share2Obsidian.format

import com.den4dr.share2Obsidian.AppConfig

data class NoteConfig(
    val vault: String,
    val folder: String,
    val defaultTags: List<String>
) {
    companion object {
        fun fromAppConfig(): NoteConfig = NoteConfig(
            vault = AppConfig.OBSIDIAN_VAULT,
            folder = AppConfig.OBSIDIAN_FOLDER,
            defaultTags = AppConfig.OBSIDIAN_TAGS
        )
    }
}
```

### 5.2 NoteComposer オブジェクト
**ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/format/NoteComposer.kt`

```kotlin
package com.den4dr.share2Obsidian.format

import android.net.Uri

object NoteComposer {
    fun buildFrontmatter(title: String?, body: String, tags: List<String>): String {
        val titleLine = title?.let { "title: \"$it\"\n" } ?: ""
        val tagsString = tags.joinToString(", ")
        return "---\n${titleLine}tags: [$tagsString]\n---\n\n$body"
    }

    fun buildUri(content: String, title: String?, config: NoteConfig): Uri {
        return Uri.parse("obsidian://new").buildUpon()
            .appendQueryParameter("content", content)
            .appendQueryParameter("title", title ?: "")
            .appendQueryParameter("vault", config.vault)
            .appendQueryParameter("folder", config.folder)
            .build()
    }
}
```

### 5.3 実装時の制約
- `NoteComposer` は `AppConfig` をインポートしてはならない（AppConfig 非依存設計）
- `FrontmatterBuilder.kt` と `ObsidianUriBuilder.kt` を変更してはならない
- パッケージ: `com.den4dr.share2Obsidian.format`

---

## 6. 品質評価

| 評価項目 | 結果 |
|---------|------|
| テスト実行 | コンパイルエラーで失敗することを確認済み ✅ |
| 期待値 | 明確で具体的 ✅ |
| アサーション | 適切 ✅ |
| 実装方針 | 明確 ✅ |
| 信頼性レベル | 🔵青信号が多い ✅ |

**品質判定**: ✅ 高品質

# TASK-0015: content-edit-preview Green フェーズ記録

**タスクID**: TASK-0015
**機能名**: content-edit-preview
**要件名**: content-edit-preview
**作成日**: 2026-03-29
**フェーズ**: Green（最小実装）

---

## 1. 実装ファイル

| ファイル | 行数 | 状態 |
|---------|------|------|
| `app/src/main/java/com/den4dr/share2Obsidian/format/NoteConfig.kt` | 約45行 | 新規作成 |
| `app/src/main/java/com/den4dr/share2Obsidian/format/NoteComposer.kt` | 約65行 | 新規作成 |

---

## 2. 実装コード

### 2.1 NoteConfig.kt

```kotlin
package com.den4dr.share2Obsidian.format

import com.den4dr.share2Obsidian.AppConfig

/**
 * 【機能概要】: Obsidian ノート送信の設定を保持するデータクラス
 * 【実装方針】: AppConfig に依存せず明示的パラメータで動作し、将来のユーザー設定化への拡張ポイントとなる
 * 【テスト対応】: TC-005（fromAppConfig の正常動作）、TC-012（data class 等価性）を通すための実装
 * 🔵 信頼性レベル: interfaces.kt NoteConfig 定義・REQ-405 より
 */
data class NoteConfig(
    val vault: String,
    val folder: String,
    val defaultTags: List<String>
) {
    companion object {
        /**
         * 【機能概要】: AppConfig の定数値から NoteConfig を生成するファクトリメソッド
         * 【実装方針】: AppConfig を参照できるのはこのメソッドのみ（NoteComposer は AppConfig 非依存）
         * 🔵 信頼性レベル: REQ-405・AppConfig.kt の実装値に基づく
         */
        fun fromAppConfig(): NoteConfig = NoteConfig(
            vault = AppConfig.OBSIDIAN_VAULT,
            folder = AppConfig.OBSIDIAN_FOLDER,
            defaultTags = AppConfig.OBSIDIAN_TAGS
        )
    }
}
```

### 2.2 NoteComposer.kt

```kotlin
package com.den4dr.share2Obsidian.format

import android.net.Uri

/**
 * 【機能概要】: 編集後の値（タイトル・本文・タグ・設定）から Frontmatter 文字列と Obsidian URI を生成するオブジェクト
 * 【実装方針】:
 *   - AppConfig を一切インポートしない（AppConfig 非依存設計・REQ-402）
 *   - 既存の FrontmatterBuilder / ObsidianUriBuilder は変更しない（REQ-402）
 * 🔵 信頼性レベル: interfaces.kt NoteComposer 定義・REQ-101, REQ-103, REQ-402, REQ-405 より
 */
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

---

## 3. 実装方針と判断理由

### 3.1 NoteConfig をデータクラスとして実装

- Kotlin `data class` を使用することで `equals()`・`hashCode()`・`copy()` が自動生成される
- TC-012（data class 等価性）がコードを書かずに通る
- `fromAppConfig()` のみが `AppConfig` を参照することで、AppConfig 非依存設計（REQ-402）を実現

### 3.2 NoteComposer を object として実装

- 状態を持たないユーティリティなので `object`（シングルトン）が適切
- `buildFrontmatter()` は `FrontmatterBuilder.build()` と同等のロジックを採用
  - `title?.let { "title: \"$it\"\n" } ?: ""` でタイトル null を条件分岐
  - `tags.joinToString(", ")` でカンマ+スペース区切り
- `buildUri()` は `ObsidianUriBuilder.build()` と同等だが、vault/folder を `AppConfig` でなく `NoteConfig` から取得

### 3.3 AppConfig 非依存設計の実現

- `NoteComposer.kt` に `import com.den4dr.share2Obsidian.AppConfig` を記載しない
- すべての設定値は `NoteConfig` 引数経由で受け取る
- `NoteConfig.fromAppConfig()` のみが `AppConfig` を参照する唯一の入口

---

## 4. テスト実行結果

```
テスト実行コマンド:
mise exec -- ./gradlew :app:testDebugUnitTest --tests "com.den4dr.share2Obsidian.format.NoteComposerTest"

結果: BUILD SUCCESSFUL
  tests="15" skipped="0" failures="0" errors="0"
```

### テストケース別結果

| テストID | テスト名 | 結果 |
|---------|---------|------|
| TC-001 | タイトル・本文・タグありの Frontmatter が正しく生成される | ✅ PASSED |
| TC-002 | タイトルが null の場合に title フィールドが省略される | ✅ PASSED |
| TC-003 | 複数タグがカンマ+スペース区切りで正しく出力される | ✅ PASSED |
| TC-004 | buildUri が正しい scheme・host・クエリパラメータを持つ URI を生成する | ✅ PASSED |
| TC-005 | fromAppConfig が AppConfig の値を正確に読み込む | ✅ PASSED |
| TC-006 | title が null の場合に URI の title パラメータが空文字になる | ✅ PASSED |
| TC-007 | 本文が空文字列の場合でも正常に Frontmatter が生成される | ✅ PASSED |
| TC-008 | タイトル null・本文空文字列の場合でも正常に Frontmatter が生成される | ✅ PASSED |
| TC-009 | タイトルにダブルクォートを含む場合の Frontmatter 生成 | ✅ PASSED |
| TC-010 | タグリストが空の場合に tags空配列が出力される | ✅ PASSED |
| TC-011 | タグが1つだけの場合にカンマなしで出力される | ✅ PASSED |
| TC-012 | 同一パラメータの NoteConfig インスタンスが等価と判定される | ✅ PASSED |
| TC-013 | 長い本文（改行を含む複数行テキスト）が正しく処理される | ✅ PASSED |
| TC-014 | NoteConfig の vault と folder が URI に正しく反映される | ✅ PASSED |
| TC-015 | NoteComposer buildFrontmatter が FrontmatterBuilder build と同等の出力を生成する | ✅ PASSED |

---

## 5. 品質評価

| 評価項目 | 結果 |
|---------|------|
| テスト結果 | 全15ケース成功 ✅ |
| 実装のシンプルさ | シンプル（既存 FrontmatterBuilder と同等のロジック） ✅ |
| ファイルサイズ | NoteConfig.kt 約45行、NoteComposer.kt 約65行（800行制限内） ✅ |
| モック使用 | 実装コードにモック・スタブなし ✅ |
| AppConfig 非依存 | NoteComposer は AppConfig をインポートしていない ✅ |
| 既存ビルダー変更なし | FrontmatterBuilder / ObsidianUriBuilder は変更していない ✅ |
| コンパイルエラー | なし ✅ |

**品質判定**: ✅ 高品質

---

## 6. 課題・改善点（Refactor フェーズで対応）

### リファクタリング候補

1. **日本語コメントの整理**: 実装は動作しているが、コメントの粒度・配置をより読みやすく整理できる
2. **NoteConfig のバリデーション**: 現状は vault が空文字でも許容されている。将来的には `require(vault.isNotBlank())` 等のバリデーションを追加する可能性がある
3. **buildUri の BASE_URI 定数化**: `ObsidianUriBuilder` と同様に `private val BASE_URI = Uri.parse("obsidian://new")` として切り出すとより読みやすくなる

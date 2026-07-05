# TASK-0015 タスクノート: NoteConfig + NoteComposer 実装

**タスクID**: TASK-0015
**タスク種別**: TDD（テスト駆動開発）
**推定工数**: 4時間
**フェーズ**: Phase 1 - 基盤実装
**信頼性レベル**: 🔵 高品質（青信号 88%）
**作成日**: 2026-03-29

---

## 1. 実装対象

### 1.1 NoteConfig データクラス

**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/format/NoteConfig.kt`
**パッケージ**: `com.den4dr.share2Obsidian.format`
**信頼性**: 🔵 設計文書 interfaces.kt・ユーザヒアリング（将来のユーザー設定化）より

**責務**:
- Obsidian ノート送信の設定（vault/folder/defaultTags）を保持
- AppConfig に依存せず、明示的パラメータのみで動作
- 将来のユーザー設定機能への拡張ポイント

**仕様**:
```kotlin
data class NoteConfig(
    val vault: String,              // Obsidian Vault 名
    val folder: String,             // 保存先フォルダパス
    val defaultTags: List<String>   // タグフィールドの初期値
) {
    companion object {
        fun fromAppConfig(): NoteConfig = NoteConfig(
            vault = AppConfig.OBSIDIAN_VAULT,      // "testVault"
            folder = AppConfig.OBSIDIAN_FOLDER,    // "70_clippings"
            defaultTags = AppConfig.OBSIDIAN_TAGS  // listOf("shared")
        )
    }
}
```

---

### 1.2 NoteComposer オブジェクト

**ファイルパス**: `app/src/main/java/com/den4dr/share2Obsidian/format/NoteComposer.kt`
**パッケージ**: `com.den4dr.share2Obsidian.format`
**信頼性**: 🔵 REQ-101, REQ-103, REQ-402・既存 FrontmatterBuilder / ObsidianUriBuilder 実装より

**責務**:
- 編集後の値から Frontmatter 文字列を生成
- 編集後の値から Obsidian URI を生成
- 既存の FrontmatterBuilder / ObsidianUriBuilder を変更しない（REQ-402）
- AppConfig をインポートしない（明示的パラメータのみ）

**仕様**:
```kotlin
object NoteComposer {

    /**
     * Frontmatter ヘッダー付きノート本文を生成する
     * @param title ノートタイトル（null の場合は title フィールドを省略）
     * @param body ノート本文
     * @param tags タグリスト（空の場合は tags: [] ）
     * @return "---\ntitle: ...\ntags: [...]\n---\n\nbody" 形式
     */
    fun buildFrontmatter(title: String?, body: String, tags: List<String>): String {
        val titleLine = title?.let { "title: \"$it\"\n" } ?: ""
        val tagsString = tags.joinToString(", ")
        return "---\n${titleLine}tags: [$tagsString]\n---\n\n$body"
    }

    /**
     * Obsidian URI を生成する
     * @param content buildFrontmatter() で生成した文字列
     * @param title ノートタイトル（URI の title パラメータ）
     * @param config vault・folder を含む設定
     * @return obsidian://new?content=...&title=...&vault=...&folder=...
     */
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

## 2. 技術スタック

| 項目 | バージョン |
|------|----------|
| Kotlin | 2.2.10 |
| Android Gradle Plugin | 9.1.0 |
| minSdk | 33 (Android 13) |
| targetSdk/compileSdk | 36 |
| Compose BOM | 2024.09.00 |
| Java 互換性 | 11 |

---

## 3. 単体テスト要件

**テストファイル**: `app/src/test/java/com/den4dr/share2Obsidian/format/NoteComposerTest.kt`

### テストケース 1: タイトルありの Frontmatter 生成 ✅

**信頼性**: 🔵 REQ-101・acceptance-criteria.md TC-101-01 より

| 項目 | 値 |
|------|-----|
| **Given** | title="テスト", body="本文テスト", tags=["shared", "web"] |
| **When** | NoteComposer.buildFrontmatter(title, body, tags) |
| **Then** | `"---\ntitle: \"テスト\"\ntags: [shared, web]\n---\n\n本文テスト"` |

**検証ポイント**:
- タイトルが Frontmatter に含まれること
- タグが正確に列挙されること
- 本文が正確に出力されること

---

### テストケース 2: タイトルなしの Frontmatter 生成 ✅

**信頼性**: 🔵 EDGE-001・FrontmatterBuilder の既存動作より

| 項目 | 値 |
|------|-----|
| **Given** | title=null, body="本文", tags=["shared"] |
| **When** | NoteComposer.buildFrontmatter(null, body, tags) |
| **Then** | `"---\ntags: [shared]\n---\n\n本文"` （title フィールドを省略） |

**検証ポイント**:
- title が null の場合、title フィールド行が生成されないこと
- タグは正常に生成されること

---

### テストケース 3: 空タグリストの Frontmatter 生成 ✅

**信頼性**: 🔵 EDGE-003・requirements.md より

| 項目 | 値 |
|------|-----|
| **Given** | title=null, body="本文", tags=[] |
| **When** | NoteComposer.buildFrontmatter(null, body, []) |
| **Then** | `"---\ntags: []\n---\n\n本文"` |

**検証ポイント**:
- 空タグリストの場合も正常に処理されること
- `tags: []` という形式になること

---

### テストケース 4: buildUri の構造検証 ✅

**信頼性**: 🔵 REQ-101・ObsidianUriBuilder 既存実装より

| 項目 | 値 |
|------|-----|
| **Given** | content="frontmatter", title="タイトル", config=NoteConfig(vault="v", folder="f", defaultTags=[]) |
| **When** | NoteComposer.buildUri(content, title, config) |
| **Then** | URI に "content", "title", "vault=v", "folder=f" が含まれ、scheme="obsidian", host="new" である |

**検証ポイント**:
- scheme が "obsidian" であること
- host が "new" であること
- クエリパラメータが正確に設定されること
- パラメータが URL エンコードされること

**注意**: Uri を扱うテストなので Robolectric が必要な場合は `androidTest` に移動を検討

---

### テストケース 5: NoteConfig.fromAppConfig() ✅

**信頼性**: 🔵 REQ-405・AppConfig の値より

| 項目 | 値 |
|------|-----|
| **Given** | AppConfig.OBSIDIAN_VAULT="testVault", OBSIDIAN_FOLDER="70_clippings", OBSIDIAN_TAGS=listOf("shared") |
| **When** | NoteConfig.fromAppConfig() |
| **Then** | config.vault=="testVault", config.folder=="70_clippings", config.defaultTags==listOf("shared") |

**検証ポイント**:
- fromAppConfig() が AppConfig の値を正確に読み込むこと
- 返却された NoteConfig のフィールドが一致すること

---

## 4. 統合テスト要件

### NoteComposer が FrontmatterBuilder と同等の出力を生成する 🟡

**信頼性**: 🟡 REQ-402・既存 FrontmatterBuilder 実装から妥当な推測

**テスト内容**:
- title="テスト", body="本文", tags=AppConfig.OBSIDIAN_TAGS の場合
- NoteComposer.buildFrontmatter() の出力が FrontmatterBuilder.build() の出力と同等である

**期待結果**: 同一文字列またはセマンティクス的に等価な Frontmatter

**参考**: FrontmatterBuilder.kt での既存実装
```kotlin
object FrontmatterBuilder {
    private val tagsString = AppConfig.OBSIDIAN_TAGS.joinToString(", ")

    fun build(title: String?, body: String): String {
        val titleLine = title?.let { "title: \"$it\"\n" } ?: ""
        return "---\n${titleLine}tags: [$tagsString]\n---\n\n$body"
    }
}
```

NoteComposer は tags を動的パラメータで受け取るため、同じロジックで動作する。

---

## 5. 既存実装との関係

### FrontmatterBuilder.kt （変更なし REQ-402）

**ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/format/FrontmatterBuilder.kt`

```kotlin
object FrontmatterBuilder {
    private val tagsString = AppConfig.OBSIDIAN_TAGS.joinToString(", ")

    fun build(title: String?, body: String): String {
        val titleLine = title?.let { "title: \"$it\"\n" } ?: ""
        return "---\n${titleLine}tags: [$tagsString]\n---\n\n$body"
    }
}
```

**関係性**:
- NoteComposer.buildFrontmatter() は同等の出力をするが、tags をパラメータで受け取る
- 既存の MainActivityFlow は FrontmatterBuilder を使用し続ける（後続タスク TASK-0020 で変更）

---

### ObsidianUriBuilder.kt （変更なし REQ-402）

**ファイル**: `app/src/main/java/com/den4dr/share2Obsidian/format/ObsidianUriBuilder.kt`

```kotlin
object ObsidianUriBuilder {
    private val BASE_URI = Uri.parse("obsidian://new")

    fun build(content: String, title: String?): Uri {
        return BASE_URI.buildUpon()
            .appendQueryParameter("content", content)
            .appendQueryParameter("title", title ?: "")
            .appendQueryParameter("vault", AppConfig.OBSIDIAN_VAULT)
            .appendQueryParameter("folder", AppConfig.OBSIDIAN_FOLDER)
            .build()
    }
}
```

**関係性**:
- NoteComposer.buildUri() は config パラメータから vault/folder を取得する
- 既存の MainActivityFlow は ObsidianUriBuilder を使用し続ける

---

## 6. 設計上の注意事項

### 6.1 AppConfig 非依存設計

**実装ルール** (設計文書 interfaces.kt より):

> NoteComposer は AppConfig をインポートしない（明示的パラメータのみ）

**理由**:
- EditScreen での編集値（タグ・フォルダなど）がユーザー入力で変更される
- 編集画面では AppConfig の固定値ではなく、動的な値を使用する必要がある
- 将来のユーザー設定化（MVP以降）への準備

**実装例**:
```kotlin
// ❌ 避けるべき（AppConfig 依存）
object NoteComposer {
    fun buildFrontmatter(title: String?, body: String): String {
        val tags = AppConfig.OBSIDIAN_TAGS  // <- AppConfig 依存
        // ...
    }
}

// ✅ 推奨（明示的パラメータ）
object NoteComposer {
    fun buildFrontmatter(title: String?, body: String, tags: List<String>): String {
        // tags はパラメータ
        // ...
    }
}
```

---

### 6.2 null 処理（EDGE-001）

**ルール**: title が空文字の場合は `null` に変換して Frontmatter の title フィールドを省略

**実装例**:
```kotlin
// EditFormState から buildSendParams() を呼び出す際
fun buildSendParams(): SendParams {
    val titleValue = formState.value.title.ifEmpty { null }  // 空文字 -> null
    return SendParams(
        title = titleValue,
        body = formState.value.body,
        tags = parseTagsText(formState.value.tagsText),
        config = noteConfig
    )
}

// NoteComposer.buildFrontmatter() で title=null の場合
// "title: ..." 行を省略
```

---

### 6.3 タグパース

**ルール**: `split(",").map { trim() }.filter { isNotEmpty() }`

**実装例**:
```kotlin
fun parseTagsText(tagsText: String): List<String> {
    return tagsText
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
```

**テストケース**:
- "shared, web, clipping" → ["shared", "web", "clipping"]
- "shared ,  web " → ["shared", "web"] （前後スペース除去）
- "" → [] （空の場合は空リスト）
- "," → [] （カンマのみの場合も空リスト）

---

### 6.4 Uri テストの実行環境

**注意**: Uri を扱うテストは Android フレームワークへの依存があるため、Robolectric が必要

**オプション**:
1. **Robolectric を使用**: `app/src/test/` で実行（単体テスト、高速）
   - 依存関係に `testImplementation "org.robolectric:robolectric:*"` を追加
   - テストメソッドに `@RunWith(RobolectricTestRunner::class)` を付与

2. **androidTest に移動**: `app/src/androidTest/` で実行（インストゥルメントテスト、遅い）
   - 実際の Android 環境で実行
   - エミュレータまたは実機が必要

**推奨**: Robolectric を使用して `app/src/test/` で実行（開発効率重視）

---

## 7. ビルド・テスト構成

### 7.1 ビルドコマンド

```bash
# デバッグビルド
./gradlew assembleDebug

# リリースビルド
./gradlew assembleRelease

# 全ビルド
./gradlew build

# クリーン
./gradlew clean

# リント
./gradlew lint

# ユニットテスト
./gradlew test

# インストゥルメントテスト
./gradlew connectedAndroidTest
```

### 7.2 テスト実行

```bash
# TASK-0015 関連テストのみ
./gradlew test --include-build-cache \
    -Dorg.gradle.workers.max=4 \
    -Dorg.gradle.parallel=true

# 全テスト
./gradlew test

# 特定テストクラス
./gradlew test --tests NoteComposerTest
```

---

## 8. 実装手順（TDD フロー）

1. **要件整理**: `/tsumiki:tdd-requirements TASK-0015`
2. **テストケース作成**: `/tsumiki:tdd-testcases`
3. **テスト実装（Red）**: `/tsumiki:tdd-red`
4. **最小実装（Green）**: `/tsumiki:tdd-green`
5. **リファクタリング（Refactor）**: `/tsumiki:tdd-refactor`
6. **品質確認**: `/tsumiki:tdd-verify-complete`

---

## 9. 完了条件チェックリスト

- [ ] `NoteConfig.kt` が `format/` パッケージに作成されている
- [ ] `NoteComposer.kt` が `format/` パッケージに作成されている
- [ ] `NoteComposerTest.kt` が `app/src/test/java/` に作成されている
- [ ] テストケース 1-5 がすべてパスしている
- [ ] 統合テスト（FrontmatterBuilder 比較）がパスしている
- [ ] `./gradlew test` がエラーなく通過する
- [ ] 既存の `FrontmatterBuilder.kt` / `ObsidianUriBuilder.kt` に変更がない
- [ ] lint チェック (`./gradlew lint`) がパスしている

---

## 10. 信頼性レベルサマリー

| カテゴリ | 🔵 青 | 🟡 黄 | 🔴 赤 | 合計 |
|---------|-------|-------|-------|------|
| 実装詳細 | 2 | 0 | 0 | 2 |
| 単体テスト | 5 | 0 | 0 | 5 |
| 統合テスト | 0 | 1 | 0 | 1 |
| **計** | **7** | **1** | **0** | **8** |

- **総項目数**: 8項目
- 🔵 **青信号**: 7項目 (88%) - 要件定義書・設計文書・既存実装から確実な定義
- 🟡 **黄信号**: 1項目 (12%) - 要件定義書・既存実装から妥当な推測
- 🔴 **赤信号**: 0項目 (0%)

**品質評価**: ✅ 高品質

---

## 11. 関連ドキュメント

| ドキュメント | パス | 役割 |
|-------------|------|------|
| 概要 | `docs/spec/content-edit-preview/note.md` | 機能全体コンテキスト |
| 要件定義 | `docs/spec/content-edit-preview/requirements.md` | 機能要件（REQ-101, REQ-103, REQ-402, REQ-405） |
| インターフェース定義 | `docs/design/content-edit-preview/interfaces.kt` | Kotlin データクラス・オブジェクト定義 |
| アーキテクチャ | `docs/design/content-edit-preview/architecture.md` | 全体アーキテクチャ |
| データフロー | `docs/design/content-edit-preview/dataflow.md` | コンポーネント間の通信フロー |
| タスク概要 | `docs/tasks/content-edit-preview/TASK-0015.md` | タスク定義書（本タスク） |

---

## 12. 後続タスク

- **TASK-0020**: MainActivity フロー変更
  - 既存の share content expansion フロー から EditScreen フロー への切り替え
  - FrontmatterBuilder / ObsidianUriBuilder から NoteComposer への移行

---

**ノート完成日**: 2026-03-29
**最終確認**: 全テストケース・実装仕様が確認済み

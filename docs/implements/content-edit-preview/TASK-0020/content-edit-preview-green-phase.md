# TASK-0020 Green フェーズ記録

**機能名**: content-edit-preview（展開内容の編集・プレビュー機能）  
**タスクID**: TASK-0020  
**フェーズ**: Green（最小実装でテストを通す）  
**作成日**: 2026-05-30

---

## 1. 実装概要

`MainActivity.kt` のフローを変更し、コンテンツ処理完了後の即時 Obsidian 起動を撤廃して、`EditScreen` を表示するように修正した。また `MainActivityTest.kt` の旧テスト（即時起動を期待）を変更後フローに合わせて修正した。

### 変更ファイル一覧

| ファイル | 変更内容 |
|---------|---------|
| `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt` | フロー変更（EditScreen 表示・コールバック処理） |
| `app/src/test/java/com/den4dr/share2Obsidian/MainActivityTest.kt` | 既存テストを変更後フロー期待値に修正 |
| `app/src/test/java/com/den4dr/share2Obsidian/MainActivityEditFlowTest.kt` | TC-0020-E01 の `ShadowApplication.checkActivities(true)` 追加 |

---

## 2. 実装コード

### MainActivity.kt（変更後の全文）

```kotlin
package com.den4dr.share2Obsidian

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.den4dr.share2Obsidian.content.ContentTypeDetector
import com.den4dr.share2Obsidian.content.FileContentProcessor
import com.den4dr.share2Obsidian.content.HtmlContentProcessor
import com.den4dr.share2Obsidian.content.ShareContent
import com.den4dr.share2Obsidian.content.TextContentProcessor
import com.den4dr.share2Obsidian.content.UrlContentProcessor
import com.den4dr.share2Obsidian.format.NoteComposer
import com.den4dr.share2Obsidian.format.NoteConfig
import com.den4dr.share2Obsidian.ui.EditScreen
import com.den4dr.share2Obsidian.ui.EditScreenViewModel
import com.den4dr.share2Obsidian.ui.LoadingScreen
import com.den4dr.share2Obsidian.util.WebViewExtractor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // viewModels() デリゲートで Activity スコープに束縛された ViewModel を取得する（EDGE-101）
    private val viewModel: EditScreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shareContent = ContentTypeDetector.detect(intent)
        if (shareContent == null) {
            finish()
            return
        }

        if (shareContent is ShareContent.Url) {
            setContent { LoadingScreen() }
        }

        val config = NoteConfig.fromAppConfig()

        lifecycleScope.launch {
            val processed = when (shareContent) {
                is ShareContent.Text -> TextContentProcessor().process(shareContent)
                is ShareContent.Url -> UrlContentProcessor(WebViewExtractor(this@MainActivity)).process(shareContent)
                is ShareContent.Html -> HtmlContentProcessor().process(shareContent)
                is ShareContent.File -> FileContentProcessor(this@MainActivity).process(shareContent)
            }

            viewModel.initialize(processed, config)

            setContent {
                EditScreen(
                    viewModel = viewModel,
                    config = config,
                    onSend = { sendParams ->
                        val content = NoteComposer.buildFrontmatter(
                            sendParams.title,
                            sendParams.body,
                            sendParams.tags,
                        )
                        val uri = NoteComposer.buildUri(content, sendParams.title, sendParams.config)
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.error_obsidian_not_installed),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        finish()
                    },
                    onCancel = {
                        finish()
                    },
                )
            }
        }
    }
}
```

### 削除したコード（FrontmatterBuilder/ObsidianUriBuilder 直接呼び出し）

```kotlin
// ❌ 削除（ファイル自体は残す: REQ-402）
val noteContent = FrontmatterBuilder.build(processed.title, processed.body)
val uri = ObsidianUriBuilder.build(noteContent, processed.title)
try {
    startActivity(Intent(Intent.ACTION_VIEW, uri))
} catch (e: ActivityNotFoundException) { ... }
finish()
```

---

## 3. 実装方針と判断理由

| 項目 | 判断内容 |
|------|---------|
| `viewModels()` デリゲート | `androidx.activity.viewModels` 既に依存関係に含まれていたため追加なし |
| FrontmatterBuilder/ObsidianUriBuilder の import | REQ-402 に従い削除。ファイル自体は残す |
| `config` の取得位置 | `lifecycleScope.launch` の外側（コルーチン開始前）で取得。EditScreen に渡すまでのライフサイクル整合性のため |
| TC-0020-E01 の修正 | `ShadowApplication.checkActivities(true)` で obsidian:// 解決失敗を再現。これにより `try-catch` が機能し ShadowToast がキャプチャ可能に |

---

## 4. テスト実行結果

### MainActivityEditFlowTest（TASK-0020 専用）

| TC-ID | テスト名 | 結果 |
|-------|---------|------|
| TC-0020-N01 | テキスト共有インテントで起動した直後は Obsidian を起動しない | ✅ PASS |
| TC-0020-N02 | SendParams から NoteComposer 経由で正しい obsidian URI が生成される | ✅ PASS |
| TC-0020-N03 | キャンセルコールバックは Obsidian を起動せず Activity を終了する | ✅ PASS |
| TC-0020-N04 | 共有対象外インテントでは EditScreen を表示せず即終了する | ✅ PASS |
| TC-0020-E01 | 送信時に Obsidian が未インストールの場合はトーストを表示して終了する | ✅ PASS |
| TC-0020-E02 | title null で Frontmatter の title 行が省略され URI の title が空文字になる | ✅ PASS |
| TC-0020-B01 | 本文が空文字でも空ノートの URI が正常に構築される | ✅ PASS |
| TC-0020-B02 | タグが空リストの場合に Frontmatter に tags: [] が出力される | ✅ PASS |
| TC-0020-B03 | 画面回転相当の二度目の初期化呼び出しで編集内容が上書きされない | ✅ PASS |
| TC-0020-B04 | バックボタン押下はキャンセルと同等に Obsidian を起動せず終了する | ✅ PASS |

**合計**: 10/10 ✅

### MainActivityTest（リグレッション確認）

| テスト名 | 結果 |
|---------|------|
| text plain intent shows EditScreen without launching obsidian | ✅ PASS |
| null content type does not start obsidian | ✅ PASS |

**合計**: 2/2 ✅

### 全テストスイート合計

**109 tests completed, 0 failures, 0 errors** ✅

---

## 5. 品質評価

| 項目 | 評価 | 詳細 |
|------|------|------|
| テスト結果 | ✅ 高品質 | 全 109 ケース成功（MainActivityEditFlowTest 10/10、MainActivityTest 2/2） |
| 実装品質 | ✅ 高品質 | シンプルかつ動作する。ファイル 95行（800行以下） |
| コンパイル | ✅ 成功 | assembleDebug BUILD SUCCESSFUL |
| モック使用 | ✅ 適切 | 実装コードにモック・スタブなし |
| リファクタリング候補 | — | `onSend` コールバックのロジックを別メソッドに抽出できる可能性あり（Refactor フェーズで対応） |

**総合判定**: ✅ 高品質

---

## 6. 課題・改善点（Refactor フェーズ対応）

1. **onSend コールバックの肥大化**: `startActivity` と `Toast` の処理が `setContent` の lambda 内に入っており、関数分割を検討できる
2. **`config` 変数の宣言位置**: `lifecycleScope.launch` 内で宣言してもよかったが現状でも問題なし
3. **FrontmatterBuilder/ObsidianUriBuilder の import 削除**: 変更で不要になった import が削除済みでクリーン

---

## 7. 次のステップ

次のお勧めステップ: `/tsumiki:tdd-refactor content-edit-preview 0020` で Refactor フェーズ（品質改善）を開始します。

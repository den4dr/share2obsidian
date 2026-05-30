# TASK-0020 Refactor フェーズ記録

**機能名**: content-edit-preview（MainActivity フロー変更）  
**タスクID**: TASK-0020  
**フェーズ**: Refactor（コード品質改善）  
**作成日**: 2026-05-30

---

## 1. リファクタリング概要

Green フェーズで追加された過剰なコメント（WHAT コメント・ドキュメントコメント）を削除し、WHY が非自明な箇所のみにコメントを絞ることで、コードの可読性を向上させた。

### 変更ファイル

| ファイル | 変更内容 |
|---------|---------|
| `app/src/main/java/com/den4dr/share2Obsidian/MainActivity.kt` | 過剰コメント削除・クラスドキュメントコメント削除 |

---

## 2. 改善内容

### 2-1. クラスレベルドキュメントコメントの削除 🔵

**変更前**:
```kotlin
/**
 * 【機能概要】: テキスト/URL/HTML/ファイル共有インテントを受け取り、EditScreen を表示してユーザーに
 *              コンテンツを確認・編集させた後、Obsidian へ送信するメインアクティビティ
 * 【実装方針】: 変更後フロー（TASK-0020）に従い、コンテンツ処理完了後に即時 Obsidian 起動を撤廃して
 *              EditScreen を表示する。送信/キャンセルはコールバック経由で処理する。
 * 【テスト対応】: TC-0020-N01（即時起動撤廃）, TC-0020-N03（キャンセル), TC-0020-E01（未インストール）に対応
 * 🔵 信頼性レベル: TASK-0020.md 変更後フロー・note.md 実装例に基づく
 */
class MainActivity : ComponentActivity() {
```

**変更後**:
```kotlin
class MainActivity : ComponentActivity() {
```

**理由**: CLAUDE.md ルール「多行コメント/ドキュメントコメントは不要」。クラス名とコード構造から機能は自明。

### 2-2. ViewModel 宣言コメントの簡略化 🔵

**変更前**:
```kotlin
// 【ViewModel 取得】: viewModels() デリゲートで Activity スコープに束縛された ViewModel を取得する 🔵
// 【画面回転対応】: Activity が再作成されてもこの ViewModel インスタンスは保持される（EDGE-101）
// 【初期化保護】: EditScreenViewModel の initialized フラグにより、2回目の initialize() 呼び出しは無視される
private val viewModel: EditScreenViewModel by viewModels()
```

**変更後**:
```kotlin
// 画面回転時も ViewModel インスタンスを保持するため viewModels() デリゲートを使用する（EDGE-101）
private val viewModel: EditScreenViewModel by viewModels()
```

**理由**: `viewModels()` がなぜ使われているかの WHY（画面回転対応）は非自明なので1行のみ残す。WHAT コメント（「ViewModel 取得」「初期化保護」）は削除。

### 2-3. ローディング画面表示コメントの簡略化 🔵

**変更前**:
```kotlin
// 【ローディング画面表示】: URL の場合は WebView 本文抽出中にローディング画面を表示する 🔵
// 【URL 処理】: REQ-301・フロー2 に従い、抽出完了後に EditScreen に切り替える
if (shareContent is ShareContent.Url) {
    setContent { LoadingScreen() }
}
```

**変更後**:
```kotlin
// URL は WebView 本文抽出に時間がかかるため、処理中はローディング画面を先に表示する
if (shareContent is ShareContent.Url) {
    setContent { LoadingScreen() }
}
```

**理由**: なぜこの条件分岐があるかの WHY（WebView 抽出に時間がかかる）は非自明なので残す。ラベル形式の WHAT コメントは削除。

### 2-4. onSend/onCancel ブロック内の過剰コメント削除 🔵

**変更前**: `onSend` 内に「送信処理」「NoteComposer 経由」「Obsidian 起動」「REQ-401 エラー処理」「未インストール通知」「アクティビティ終了」など多数のラベルコメントが存在していた。

**変更後**: コメントなし。

**理由**: `NoteComposer.buildFrontmatter()`、`startActivity()`、`ActivityNotFoundException` キャッチなど、コード自体が処理内容を表している。WHAT コメントは全て削除。

### 2-5. early return のコメント削除 🔵

**変更前**:
```kotlin
if (shareContent == null) {
    // 【早期終了】: 共有対象外の Intent（MIME type なし等）は処理せず即 finish() する
    finish()
    return
}
```

**変更後**:
```kotlin
if (shareContent == null) {
    finish()
    return
}
```

**理由**: `null` チェック後に `finish()` して `return` するのは自明。WHAT コメントは不要。

---

## 3. セキュリティレビュー

| 項目 | 評価 | 詳細 |
|------|------|------|
| 入力値検証 | ✅ 問題なし | `ContentTypeDetector.detect()` で Intent 検証済み。null の場合は早期終了 |
| URI 構築 | ✅ 問題なし | `NoteComposer.buildUri()` で URI エンコードされる |
| ActivityNotFoundException | ✅ 適切な処理 | 例外をキャッチしてトーストを表示。アプリがクラッシュしない |
| 外部インテント | ✅ 問題なし | `ACTION_VIEW` のみ使用。任意コマンド実行の余地なし |

重大なセキュリティ脆弱性は発見されなかった。

---

## 4. パフォーマンスレビュー

| 項目 | 評価 | 詳細 |
|------|------|------|
| コルーチン | ✅ 適切 | `lifecycleScope.launch` で適切に非同期処理 |
| ViewModel 初期化 | ✅ 適切 | `initialized` フラグで重複処理を防止（EDGE-101） |
| ローディング画面 | ✅ 適切 | URL 処理中のみ表示。他のコンテンツタイプには不要 |
| URI 構築 | ✅ 問題なし | 軽量な文字列処理のみ |

重大なパフォーマンス課題は発見されなかった。

---

## 5. テスト実行結果

### リファクタリング後テスト

```
BUILD SUCCESSFUL in 6s
28 actionable tasks: 4 executed, 24 up-to-date
```

全テストスイート通過を確認。

| テストスイート | 結果 |
|-------------|------|
| MainActivityEditFlowTest (10件) | ✅ 全件 PASS |
| MainActivityTest (2件) | ✅ 全件 PASS |
| 全テストスイート | ✅ BUILD SUCCESSFUL |

---

## 6. リファクタリング後のコード全文

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

    // 画面回転時も ViewModel インスタンスを保持するため viewModels() デリゲートを使用する（EDGE-101）
    private val viewModel: EditScreenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shareContent = ContentTypeDetector.detect(intent)
        if (shareContent == null) {
            finish()
            return
        }

        // URL は WebView 本文抽出に時間がかかるため、処理中はローディング画面を先に表示する
        if (shareContent is ShareContent.Url) {
            setContent { LoadingScreen() }
        }

        val config = NoteConfig.fromAppConfig()

        lifecycleScope.launch {
            val processed = when (shareContent) {
                is ShareContent.Text -> TextContentProcessor().process(shareContent)
                is ShareContent.Url -> UrlContentProcessor(
                    WebViewExtractor(this@MainActivity)
                ).process(shareContent)
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

---

## 7. 品質評価

| 項目 | 評価 | 詳細 |
|------|------|------|
| テスト結果 | ✅ 高品質 | 全テスト継続成功 |
| セキュリティ | ✅ 高品質 | 重大な脆弱性なし |
| パフォーマンス | ✅ 高品質 | 重大な性能課題なし |
| リファクタ品質 | ✅ 目標達成 | WHAT コメント削除・WHY コメントのみ残存 |
| コード品質 | ✅ 高品質 | 87行（500行制限内）・クリーンなコード |
| インポート | ✅ 整理済み | 未使用 import なし |

**総合判定**: ✅ 高品質

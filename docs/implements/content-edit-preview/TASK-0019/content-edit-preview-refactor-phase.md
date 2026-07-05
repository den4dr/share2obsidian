# TASK-0019: EditScreen Composable - Refactor フェーズ記録

**機能名**: content-edit-preview
**タスクID**: TASK-0019
**フェーズ**: Refactor（品質改善）
**作成日**: 2026-05-30

---

## リファクタリング概要

Green フェーズで実装した `EditScreen.kt` に対して、CLAUDE.md のコーディングルールに基づいてコメント品質を改善した。
テストが通る状態を維持しながら、WHAT コメントを除去して WHY が非自明な場合のみコメントを残す方針に統一した。

---

## 改善ポイント

### 1. コメントの整理（CLAUDE.md 準拠） 🔵

**Before（Green フェーズ）**:
- 「【状態購読】: StateFlow を Compose State に変換して...」など、コードを読めば分かる WHAT コメントが多数
- インラインコメントが各行に付いており、可読性を下げていた
- KDoc が詳細過ぎて本体ロジックが見えにくかった

**After（Refactor フェーズ）**:
- WHY が非自明な箇所のみコメントを残した
  - `BackHandler`: EDGE-102 の要件番号でなぜバックボタンをキャンセルと同等にするかを示す
  - KDoc: `Scaffold.bottomBar` による固定表示（NFR-102）とバックボタン対応（EDGE-102）の設計方針のみ記述
- WHAT コメント（「【ボタン配置】」「【ボタンテキスト】」等）はすべて削除

**信頼性**: 🔵 CLAUDE.md「コメントは WHY が非自明な場合のみ」ルールより

### 2. KDoc の簡潔化 🔵

**Before**: `@param` に詳細な説明と要件番号・StateFlow の仕組みまで記述
**After**: 各引数の役割を1行で表現し、設計上の重要な制約（NFR-102, EDGE-102）は本文に残した

**信頼性**: 🔵 CLAUDE.md「多行コメント/ドキュメントコメントは不要」ルールより

### 3. ファイルサイズ削減 🔵

- Before: 158行
- After: 101行（約36%削減）
- 削減理由: 冗長なコメントの除去

---

## セキュリティレビュー

| 観点 | 評価 | 内容 |
|------|------|------|
| 入力検証 | 問題なし | 入力値の検証は EditScreenViewModel（TASK-0017）が担当。EditScreen は UI 表示に専念 |
| URI エンコーディング | 問題なし | NoteComposer.buildUri() で処理済み（EditScreen は buildSendParams を渡すのみ） |
| 権限 | 問題なし | 追加権限不要 |

**総評**: 重大な脆弱性なし ✅

---

## パフォーマンスレビュー

| 観点 | 評価 | 内容 |
|------|------|------|
| Recomposition | 問題なし | `collectAsState()` による StateFlow 購読は Compose 標準パターン |
| レイアウト計測 | 問題なし | `Scaffold` + `Column` + `verticalScroll` は軽量な Compose 標準レイアウト |
| 不要な remember{} | 問題なし | 状態は ViewModel に委譲しており、Composable 側で追加の remember{} は不要 |

**総評**: 重大な性能課題なし ✅

---

## 最終実装コード

```kotlin
package com.den4dr.share2Obsidian.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.den4dr.share2Obsidian.R
import com.den4dr.share2Obsidian.format.NoteConfig

/**
 * Obsidian への送信前にコンテンツ（タイトル・本文・タグ・フォルダ）を確認・編集するフォーム画面。
 *
 * ボタンは [Scaffold.bottomBar] に固定してフィールドのスクロールとは独立させる（NFR-102）。
 * Android バックボタンはキャンセルと同等に扱う（EDGE-102）。
 *
 * @param viewModel フォーム状態と送信パラメータ構築を担う ViewModel
 * @param config 送信先 vault・folder・defaultTags を含む設定
 * @param onSend 送信ボタン押下時に [SendParams] を渡して呼ばれるコールバック
 * @param onCancel キャンセルボタン・バックボタン押下時に呼ばれるコールバック
 */
@Composable
fun EditScreen(
    viewModel: EditScreenViewModel,
    config: NoteConfig,
    onSend: (SendParams) -> Unit,
    onCancel: () -> Unit,
) {
    val formState by viewModel.formState.collectAsState()

    // バックボタンをキャンセルと同等に扱う（EDGE-102）
    BackHandler { onCancel() }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.button_cancel))
                }
                Button(
                    onClick = { onSend(viewModel.buildSendParams(config)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.button_send))
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = formState.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text(stringResource(R.string.label_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = formState.body,
                onValueChange = { viewModel.updateBody(it) },
                label = { Text(stringResource(R.string.label_body)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
            )
            OutlinedTextField(
                value = formState.tagsText,
                onValueChange = { viewModel.updateTagsText(it) },
                label = { Text(stringResource(R.string.label_tags)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = formState.folder,
                onValueChange = { viewModel.updateFolder(it) },
                label = { Text(stringResource(R.string.label_folder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}
```

---

## テスト実行結果

| コマンド | 結果 |
|---------|------|
| `mise exec -- ./gradlew assembleDebug` | BUILD SUCCESSFUL |
| `mise exec -- ./gradlew test` | BUILD SUCCESSFUL（全件パス） |

---

## 品質判定

```
✅ 高品質:
- テスト結果: assembleDebug + test ともに BUILD SUCCESSFUL
- セキュリティ: 重大な脆弱性なし
- パフォーマンス: 重大な性能課題なし
- コメント品質: CLAUDE.md ルール準拠（WHAT コメント除去済み）
- ファイルサイズ: 101行（500行制限以内）
- 信頼性レベル: 🔵 中心（CLAUDE.md ルール・要件定義ベース）
```

---

**作成者**: Claude Code (tsumiki:tdd-refactor)
**最終更新**: 2026-05-30

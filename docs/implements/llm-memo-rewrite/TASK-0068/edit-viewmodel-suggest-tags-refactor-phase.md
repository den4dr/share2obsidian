# TDD Refactorフェーズ - TASK-0068: EditScreenViewModel suggestTags()実装

- **機能名**: edit-viewmodel-suggest-tags（EditScreenViewModel タグ提案）
- **タスクID**: TASK-0068
- **要件名**: llm-memo-rewrite
- **実施日**: 2026-07-08

## 1. リファクタ前の確認

### テスト実行結果（リファクタ前）

`EditScreenViewModelSuggestTagsTest`（7ケース）を含む `ui` パッケージ配下の全テストが成功していることを確認済み（Greenフェーズで確認済みの状態を再確認）。

### テスト実行時間のチェック

`EditScreenViewModelSuggestTagsTest` を単体クラスとして分離実行した場合、`TC-0068-B02` が3.6〜4.2秒かかることを検出した。調査のため以下を実施：

1. 単体クラス実行を2回試行 → 毎回 B02（実行順で先頭に来たケース）のみが3秒台、他は0.1秒未満
2. `ui` パッケージ全体（9クラス、全テストクラスまとめて）で実行 → `EditScreenViewModelSuggestTagsTest` は7ケース合計 **0.167秒**
3. 他クラス（`EditScreenViewModelRewriteBodyTest` 等）でも「そのプロセス内で最初に実行されるテストクラス」が2〜3秒かかる傾向を確認

**結論**: 3秒台の遅延は Robolectric/JVM の初回クラスロードコストであり、`suggestTags()` 実装自体の性能問題ではない。実装上の対応は不要と判断した。

### コード・テスト除外チェック

- `.gitignore` によるコード除外なし
- `@Ignore` / `describe.skip` 等によるテスト無効化なし（grep で該当箇所なし）
- ビルド設定（`app/build.gradle.kts`）にテスト除外パターンなし

### 開発時生成ファイルのクリーンアップ

`debug-*`, `test-*`, `temp-*`, `*.tmp`, `*.bak`, `*.orig`, `*~`, `.DS_Store` 等のパターンで検索したが、`build/`・`.gradle/` 配下以外に該当ファイルなし。クリーンアップ対象なし。

## 2. セキュリティレビュー

- `suggestTags()` は既存 `rewriteBody()`（TASK-0063 実装済み）と同一の認可・入力経路を使用しており、新規の外部入力受付やSQL/コマンド実行を追加しない
- APIキー等の機微情報は `LlmSettingsRepository` 経由で取得するのみで、ログ・例外メッセージへは出力しない（`_errorEvents.emit(result.messageResId)` は string resource ID のみを渡す、NFR-102準拠）
- `sourceContent` は空文字を含め検証なしでそのまま送信するが、これは仕様（EDGE-101）であり、ユーザー自身が共有したテキストのみが対象のため外部攻撃面の拡大はない
- **結論**: 重大な脆弱性なし。新規の対応不要。

## 3. パフォーマンスレビュー

- `suggestTags()` の計算量: `tagsText` のマージは文字列結合 O(n)（n はタグ文字列長）。ユーザー入力量を考えると無視できるコスト
- ネットワークI/O（LLM API呼び出し）は既存 `LlmRewriteRepository.rewrite()` に委譲済みで、30秒タイムアウト（NFR-001）は TASK-0060 で対応済み
- テスト実行時間の3秒台の遅延はJVM初回ロードのオーバーヘッドであり、実装のボトルネックではない（§1参照）
- **結論**: 重大な性能課題なし。新規の対応不要。

## 4. 改善計画と実施内容

| # | 改善内容 | 信頼性 | 実施 |
|---|---------|--------|------|
| 1 | `rewriteBody()` と `suggestTags()` の重複ロジック（ローディング開始→設定取得→LLM呼び出し→成功/失敗分岐→ローディング終了）を `runLlmRequest()` 共通ヘルパーへ抽出（DRY原則） | 🔵 両メソッドの既存ロジックをそのまま機械的に抽出。新規推測なし | ✅ 実施 |
| 2 | `TAG_SUGGESTION_PROMPT`（Kotlin定数）と `llm_tag_suggestion_prompt`（string resource）の二重管理解消 | 🟡 `Context` 注入が必要な設計変更であり、機能的変更を伴うためRefactorフェーズの対象外と判断 | ❌ 見送り（将来タスクで対応） |
| 3 | `Success("")` 時の tagsText 末尾スペース挙動の見直し | 🟡 TC-0068-B03 で明示的に期待される仕様（`"private, "`）であり、変更するとテストが失敗する機能的変更になるため対象外 | ❌ 見送り（仕様として妥当） |

### 改善1の詳細: `runLlmRequest()` への抽出

**Before**（Greenフェーズ）: `rewriteBody()` と `suggestTags()` がそれぞれ「ローディングtrue→設定取得→rewrite()呼び出し→成功/失敗分岐→ローディングfalse」を個別に実装しており、枠組み部分が完全に重複していた。

**After**（Refactorフェーズ）: 共通の流れを `private suspend fun runLlmRequest(prompt, setLoading, onSuccess)` に抽出し、`rewriteBody()` / `suggestTags()` はそれぞれ「プロンプト」「ローディングフラグの反映方法」「成功時の状態更新方法」のみを渡すだけになった。

```kotlin
private suspend fun runLlmRequest(
    prompt: String,
    setLoading: (Boolean) -> Unit,
    onSuccess: (String) -> Unit,
) {
    setLoading(true)
    val settings = llmSettingsRepository.getSettings().first()
    when (val result = llmRewriteRepository.rewrite(settings, prompt, sourceContent)) {
        is LlmRewriteResult.Success -> onSuccess(result.text)
        is LlmRewriteResult.Failure -> _errorEvents.emit(result.messageResId)
    }
    setLoading(false)
}

fun rewriteBody() {
    viewModelScope.launch {
        runLlmRequest(
            prompt = bodyLlmPrompt,
            setLoading = { loading -> _formState.update { it.copy(isRewritingBody = loading) } },
            onSuccess = { text -> _formState.update { it.copy(body = text) } },
        )
    }
}

fun suggestTags() {
    viewModelScope.launch {
        runLlmRequest(
            prompt = TAG_SUGGESTION_PROMPT,
            setLoading = { loading -> _formState.update { it.copy(isSuggestingTags = loading) } },
            onSuccess = { text ->
                _formState.update { state ->
                    val current = state.tagsText
                    val merged = if (current.isBlank()) text else "$current, $text"
                    state.copy(tagsText = merged)
                }
            },
        )
    }
}
```

振る舞いは Before/After で完全に同一（呼び出し順序・状態更新内容とも変更なし）。純粋なコード構造の改善であり、機能追加・変更はしていない。

## 5. リファクタ後のテスト実行結果

```
mise exec -- ./gradlew testDebugUnitTest \
  --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelSuggestTagsTest" \
  --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelRewriteBodyTest" \
  --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelTest" \
  --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelInitializeTest"

BUILD SUCCESSFUL
```

| テストクラス | 件数 | 結果 |
|-------------|------|------|
| EditScreenViewModelSuggestTagsTest | 7 | 全成功 |
| EditScreenViewModelRewriteBodyTest | 14 | 全成功（回帰なし） |
| EditScreenViewModelTest | 19 | 全成功（回帰なし） |
| EditScreenViewModelInitializeTest | 8 | 全成功（回帰なし） |

さらにプロジェクト全体 `mise exec -- ./gradlew test` および `mise exec -- ./gradlew lint` を実行し、いずれも `BUILD SUCCESSFUL` を確認した。

`lint` では `R.string.llm_tag_suggestion_prompt` の未使用警告（`UnusedResources`）が検出されたが、これは Green フェーズ時点で識別済みの既知事項（Context注入方式未確定のため Kotlin 定数側でのみ実際に使用）であり、既存の他11件の未使用リソース警告と同様のレベル。ビルド失敗要因ではない。

## 6. 品質判定

- ✅ **テスト結果**: 全て継続成功（7+14+19+8件、プロジェクト全体テストもBUILD SUCCESSFUL）
- ✅ **セキュリティ**: 重大な脆弱性なし
- ✅ **パフォーマンス**: 重大な性能課題なし（テスト遅延はJVM起因と特定済み）
- ✅ **リファクタ品質**: `runLlmRequest()` 抽出によりDRY原則を適用、目標達成
- ✅ **コード品質**: lint成功、日本語コメント（機能概要・改善内容・信頼性レベル）を強化
- ✅ **ファイルサイズ**: `EditScreenViewModel.kt` 約357行（500行制限内、分割不要）

**総合判定**: 高品質

## 7. 未対応の改善候補（将来タスクへの申し送り）

1. `TAG_SUGGESTION_PROMPT`（Kotlin定数）と `llm_tag_suggestion_prompt`（string resource）の二重管理: `@ApplicationContext` 注入方式が確定した際に一本化を検討
2. `Success("")` 時の tagsText 末尾スペース挙動: 現行テスト仕様どおりだが、UI表示上のトリム要否をTASK-0069（UI実装）で検討

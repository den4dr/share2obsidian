# TDD Greenフェーズ - TASK-0068: EditScreenViewModel suggestTags()実装

- **機能名**: edit-viewmodel-suggest-tags（EditScreenViewModel タグ提案）
- **タスクID**: TASK-0068
- **要件名**: llm-memo-rewrite
- **実施日**: 2026-07-08

## 1. 実装方針

Redフェーズで作成した7テストケース（必須5・補完2）を通す最小実装を、既存の `rewriteBody()`（TASK-0063）と同一パターンで追加した。

### 仕様との差異確認

要件定義書（`edit-viewmodel-suggest-tags-requirements.md` §3）で🟡としてフラグされていた「固定プロンプト解決のための Context 依存」について、Redフェーズのテストコードを確認したところ `EditScreenViewModel(mockRewrite, mockSettings)` と2引数コンストラクタのみが呼ばれており、`Context` は一切注入されていないことを確認した。

このため、`TAG_SUGGESTION_PROMPT` は `Context.getString()` を使わず、Kotlin側の `private const val`（companion object）として直接保持する方式を採用した。これは note.md §6「テストでは固定文字列で代替可能」の想定通りであり、テストのコンストラクタ呼び出しと矛盾しない。ユーザーへの確認は不要と判断した（テストコードという一次情報で解決可能なため）。

`strings.xml` にも同一文言の `llm_tag_suggestion_prompt` を追加した（TASK-0068.md の実装対象ファイル一覧に明記されているため）。ただし現時点ではViewModelから参照されておらず、将来Context注入方式が確定した際、または UI 表示用途で利用される想定。

## 2. 実装コード

### app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt（差分）

```kotlin
data class EditFormState(
    // ...(既存フィールド)
    val rewriteBodyEnabled: Boolean = false,
    // 【フィールド定義】: suggestTags() 実行中のローディング状態（REQ-201）。isRewritingBody と同様、UI はこれを購読してスピナー表示・ボタン非活性化を制御する 🔵
    // 【テスト対応】: TC-0068-B01
    val isSuggestingTags: Boolean = false,
)
```

### app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt（差分）

```kotlin
/**
 * 【機能概要】: LLM API を呼び出して sourceContent からタグ候補を生成し、既存の tagsText に追加する（置換ではない）
 * 【実装方針】: `viewModelScope.launch` で非同期実行する。`rewriteBody()` と同一のローディング・エラー処理パターンを
 *              踏襲するが、入力プロンプトはテンプレート単位の `bodyLlmPrompt` ではなく、アプリ内固定の
 *              `TAG_SUGGESTION_PROMPT` を使用する（REQ-406 補足設計）。`sourceContent` が空文字であっても
 *              ガード（早期return）せず、そのまま LLM 呼び出しに渡す（EDGE-101）。
 * 【テスト対応】: TC-0068-N01, N02, N03, E01, B01, B02, B03
 * 🔵 信頼性レベル: TASK-0068.md 実装詳細2・4・5・6・REQ-201, REQ-301, REQ-302, REQ-406, EDGE-101 より
 */
fun suggestTags() {
    viewModelScope.launch {
        // 【ローディング開始】: LLM 呼び出し開始を UI に伝えるため isSuggestingTags を true にする（REQ-201）🔵
        _formState.update { it.copy(isSuggestingTags = true) }

        // 【設定取得】: LLM API 接続設定（endpointUrl/apiKey/model）を取得する 🔵
        val settings = llmSettingsRepository.getSettings().first()

        // 【LLM呼び出し】: プロンプトはアプリ内固定の TAG_SUGGESTION_PROMPT、入力は sourceContent を使用する 🔵
        // 【空文字ガード禁止】: sourceContent が空文字でも早期returnせずそのまま渡す（EDGE-101）🔵
        when (val result = llmRewriteRepository.rewrite(settings, TAG_SUGGESTION_PROMPT, sourceContent)) {
            is LlmRewriteResult.Success -> {
                // 【成功時マージ】: 既存 tagsText が空白なら生成結果のみ、そうでなければカンマ+スペース区切りで追加連結する（REQ-302）🔵
                val current = _formState.value.tagsText
                val merged = if (current.isBlank()) result.text else "$current, ${result.text}"
                _formState.update { it.copy(tagsText = merged) }
            }
            is LlmRewriteResult.Failure ->
                // 【失敗時】: tagsText は変更せず、errorEvents に対応する messageResId を発行する（NFR-201）🔵
                _errorEvents.emit(result.messageResId)
        }

        // 【ローディング終了】: 成功・失敗いずれの場合も isSuggestingTags を false に戻す（REQ-201）🔵
        _formState.update { it.copy(isSuggestingTags = false) }
    }
}

// ...(buildSendParams() 等の後)

companion object {
    // 【定数定義】: suggestTags() で使用するアプリ内固定プロンプト。テンプレート単位の bodyLlmPrompt とは異なり、
    //             テンプレートに依存しない統一文字列を使用する（REQ-406 補足設計）。
    // 【Context非依存】: 現行 EditScreenViewModel コンストラクタは Context を保持しないため、
    //             strings.xml の llm_tag_suggestion_prompt と同一内容の定数として直接保持する
    //             （note.md §6 技術的制約「アプリ内固定プロンプト」/ requirements.md §3 Context依存の懸念より）🟡
    private const val TAG_SUGGESTION_PROMPT: String =
        "以下の文章から関連するタグを3〜5個、カンマ区切りで提案してください"
}
```

### app/src/main/res/values/strings.xml（差分）

```xml
<!-- LLMタグ提案機能: アプリ内固定プロンプト（TASK-0068）。EditScreenViewModel.TAG_SUGGESTION_PROMPT と同一内容 -->
<string name="llm_tag_suggestion_prompt">以下の文章から関連するタグを3〜5個、カンマ区切りで提案してください</string>
```

## 3. テスト実行結果

```
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelSuggestTagsTest"

BUILD SUCCESSFUL
```

`TEST-com.den4dr.share2Obsidian.ui.EditScreenViewModelSuggestTagsTest.xml`:
```
tests="7" skipped="0" failures="0" errors="0"
```

全7ケース成功:
- TC-0068-N01: 成功時に既存 tagsText へ追加連結 - PASS
- TC-0068-N02: 既存 tagsText 空文字なら生成結果のみ設定 - PASS
- TC-0068-N03（補完）: sourceContent が入力ソース（body/tagsText 非依存）- PASS
- TC-0068-E01: 失敗時 tagsText 不変・errorEvents 発行 - PASS
- TC-0068-B01: isSuggestingTags の true→false 状態遷移 - PASS
- TC-0068-B02: sourceContent 空文字でもガードされず1回実行 - PASS
- TC-0068-B03（補完）: 成功結果が空文字の場合のマージ挙動 - PASS

既存の `ui` パッケージ配下の全テスト（`EditScreenViewModelRewriteBodyTest` 等）を含めて再実行し、回帰がないことを確認済み（BUILD SUCCESSFUL）。

## 4. 課題・改善点（Refactorフェーズで対応）

1. **`rewriteBody()` との重複コード**: ローディング開始・設定取得・ローディング終了の枠組みが `rewriteBody()` と `suggestTags()` でほぼ同一。共通化（private suspend関数への抽出等）を検討する。
2. **`TAG_SUGGESTION_PROMPT` の二重管理**: `strings.xml` の `llm_tag_suggestion_prompt` と Kotlin側の `private const val` が同一文言を別々に保持しており、片方の変更漏れでズレるリスクがある。Context注入方式の確定（`@ApplicationContext` 注入等）により一本化を検討する。
3. **B03の末尾スペース挙動**: `Success("")` の場合 `"private, "` のように末尾に不要なスペースが残る。仕様上は許容範囲（テストが期待）だが、UI表示上のトリム処理が必要か検討の余地あり。

## 5. ファイルサイズ・モック使用確認

- `EditScreenViewModel.kt`: 約332行（800行制限内、分割不要）
- `EditFormState.kt`: 約68行
- 実装コード内にモック・スタブなし（モックはテストコード内のみ）

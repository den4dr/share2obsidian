# TDD Refactorフェーズ記録: edit-custom-field-llm-generation

- **タスク**: TASK-0072 EditScreenViewModel `generateCustomFieldValue()`・EditScreen UI「生成」ボタン追加
- **実施日**: 2026-07-10

## 1. リファクタリング対象の特定

Greenフェーズからの申し送り事項:

1. `generateCustomFieldValue()` と `runLlmRequest()` のローディング型の違い（`Boolean` vs `Int?`）によるコード重複（設定取得・成功/失敗分岐の構造が類似）
2. `EditScreen.kt` の生成ボタンと `LoadingButton` の統一感

## 2. 実施した改善

### 2-1. `generateCustomFieldValue()` の `runLlmRequest()` 再利用化

Greenフェーズでは「`generatingFieldIndex: Int?` は `setLoading: (Boolean) -> Unit` シグネチャと合わない」として専用実装としていたが、`index` を捕捉したクロージャで Boolean → Int? 変換を吸収できるため、共通ヘルパーを再利用する形に統一した。

**Before**（専用実装: 設定取得・when分岐・ローディング終了が `runLlmRequest()` と重複）:

```kotlin
fun generateCustomFieldValue(index: Int) {
    viewModelScope.launch {
        val prompt = formState.value.customFields[index].llmPrompt
        _formState.update { it.copy(generatingFieldIndex = index) }
        val settings = llmSettingsRepository.getSettings().first()
        when (val result = llmRewriteRepository.rewrite(settings, prompt, sourceContent)) {
            is LlmRewriteResult.Success -> updateCustomField(index, result.text)
            is LlmRewriteResult.Failure -> _errorEvents.emit(result.messageResId)
        }
        _formState.update { it.copy(generatingFieldIndex = null) }
    }
}
```

**After**（`runLlmRequest()` 再利用、重複約20行削減）:

```kotlin
fun generateCustomFieldValue(index: Int) {
    viewModelScope.launch {
        runLlmRequest(
            prompt = formState.value.customFields[index].llmPrompt,
            setLoading = { loading ->
                _formState.update { it.copy(generatingFieldIndex = if (loading) index else null) }
            },
            onSuccess = { text -> updateCustomField(index, text) },
        )
    }
}
```

これにより LLM 呼び出し3系統（`rewriteBody()` / `suggestTags()` / `generateCustomFieldValue()`）すべてが「ローディング開始 → 設定取得 → LLM呼び出し → 成功/失敗分岐 → ローディング終了」を `runLlmRequest()` に集約する構造になった。`runLlmRequest()` の KDoc も3呼び出し元対応に更新した。

### 2-2. EditScreen の生成ボタン共通化（見送り）

`LoadingButton` はテキストボタン（`Button` + ラベル）だが、カスタムフィールドの生成ボタンは行内 `IconButton` であり、UI構造・サイズ・配置が異なる。無理に共通化するとかえって条件分岐が増えて可読性が下がるため、見送りと判断した（YAGNI）。

## 3. セキュリティレビュー

- 内部リファクタのみで外部入出力・APIキーの取り扱いに変更なし。
- LLM への入力は引き続き `sourceContent` のみ（REQ-002/REQ-406 維持）。空文字ガードなし（EDGE-101 維持）。

## 4. パフォーマンスレビュー

- 動作は等価。追加コストはクロージャ1個分のアロケーションのみで無視できる。
- フィールド単位ローディングの状態遷移（index 設定 → null 復帰）は TC-0072-N04 で検証済み。

## 5. 最終テスト結果

```
mise exec -- ./gradlew testDebugUnitTest
```

- **全ユニットテストスイート: 284件成功・失敗0・エラー0**
- `EditScreenViewModelGenerateCustomFieldValueTest`: 13/13 成功（TC-0072-N01〜N04, E01〜E05, B01〜B04）

## 6. 品質判定

| 項目 | 結果 |
|------|------|
| テスト | ✅ 全284件成功（回帰なし） |
| 重複コード | ✅ LLM呼び出し3系統を `runLlmRequest()` に集約 |
| ファイルサイズ | ✅ EditScreenViewModel.kt 約400行（800行制限内） |
| 実装コードへのモック混入 | ✅ なし |
| セキュリティ | ✅ 変更なし（REQ-406 維持） |

**総合判定**: ✅ 高品質 — TASK-0072 の TDD サイクル完了

# TDD Greenフェーズ記録: EditScreenViewModel Hilt化・rewriteBody()実装

**機能名**: llm-memo-rewrite
**タスクID**: TASK-0063
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. 実装方針

TASK-0063.md「実装詳細」1〜7・note.mdの実装チェックリストに従い、以下を実装した。

1. `EditScreenViewModel` を `@HiltViewModel @Inject constructor(llmRewriteRepository, llmSettingsRepository) : ViewModel()` に変更
2. `errorEvents`（`MutableSharedFlow<Int>` / `SharedFlow<Int>`）を追加
3. `EditFormState` に `isRewritingBody: Boolean = false` / `rewriteBodyEnabled: Boolean = false` を追加
4. `initialize()` で `rewriteBodyEnabled = bodyLlmPrompt.isNotBlank()` を算出（既存 `initialized` ガードは維持）
5. `rewriteBody()` を実装（`sourceContent` を入力に使用、成功時は body 上書き、失敗時は errorEvents に emit）

### 仕様との差異確認

要件定義書・テストケース定義書・Red フェーズ記録と現在の実装を照合した結果、差異は検出されなかった（Red フェーズで確認済みの4点の未実装のみで、ロジック仕様自体に齟齬はなし）。

---

## 2. 変更したファイル

### `app/src/main/java/com/den4dr/share2Obsidian/ui/EditFormState.kt`

`EditFormState` に2フィールドを追加した。

```kotlin
data class EditFormState(
    val vault: String = "",
    val title: String,
    val body: String,
    val tagsText: String,
    val folder: String,
    val customFields: List<com.den4dr.share2Obsidian.domain.model.CustomFieldState> = emptyList(),
    // 【フィールド定義】: rewriteBody() 実行中のローディング状態（REQ-201）🔵
    val isRewritingBody: Boolean = false,
    // 【フィールド定義】:「メモを更改」ボタンの活性判定（REQ-102）🔵
    val rewriteBodyEnabled: Boolean = false,
)
```

### `app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt`

- クラス定義を `@HiltViewModel` 化し、`@Inject constructor(llmRewriteRepository, llmSettingsRepository)` へ変更
- `errorEvents`（`SharedFlow<Int>`）プロパティを追加
  - `MutableSharedFlow<Int>(extraBufferCapacity = 1)` とした（後述「課題・改善点」参照）
- `initialize()` の `_formState.value = EditFormState(...)` に `rewriteBodyEnabled = bodyLlmPrompt.isNotBlank()` を追加
- `rewriteBody()` を新規実装（TASK-0063.md 実装詳細5のコードそのまま）

```kotlin
@HiltViewModel
class EditScreenViewModel @Inject constructor(
    private val llmRewriteRepository: LlmRewriteRepository,
    private val llmSettingsRepository: LlmSettingsRepository,
) : ViewModel() {

    private val _errorEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val errorEvents: SharedFlow<Int> = _errorEvents.asSharedFlow()

    fun rewriteBody() {
        viewModelScope.launch {
            _formState.update { it.copy(isRewritingBody = true) }
            val settings = llmSettingsRepository.getSettings().first()
            when (val result = llmRewriteRepository.rewrite(settings, bodyLlmPrompt, sourceContent)) {
                is LlmRewriteResult.Success ->
                    _formState.update { it.copy(body = result.text) }
                is LlmRewriteResult.Failure ->
                    _errorEvents.emit(result.messageResId)
            }
            _formState.update { it.copy(isRewritingBody = false) }
        }
    }
}
```

`MainActivity` は既に `@AndroidEntryPoint` であり `private val viewModel: EditScreenViewModel by viewModels()` はそのまま Hilt 経由で動作することを確認した（コード変更不要）。

---

## 3. 既存テストへの影響と修正

Hilt化によりコンストラクタが `EditScreenViewModel()` から `EditScreenViewModel(llmRewriteRepository, llmSettingsRepository)` に変わったため、既存テストで直接インスタンス化していた箇所がすべてコンパイルエラーになった。以下のファイルを、MockK でスタブした2リポジトリをコンストラクタに渡す形に修正した（ロジック自体の変更はなし）。

- `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelTest.kt`（19箇所）
- `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelInitializeTest.kt`（8箇所）
- `app/src/test/java/com/den4dr/share2Obsidian/MainActivityEditFlowTest.kt`（1箇所）

---

## 4. テスト実行結果

### 新規テスト（TASK-0063）

```
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelRewriteBodyTest"
```

14件全て成功（TC-0063-N01〜N05, E01〜E05, B01〜B04）。

### 全体テストスイート

```
mise exec -- ./gradlew testDebugUnitTest
```

全35テストクラス・240テストケースすべて成功（既存の `EditScreenViewModelTest`・`EditScreenViewModelInitializeTest`・`MainActivityEditFlowTest` を含む）。

---

## 5. 調査した問題と対応（テストコードの修正）

Red フェーズのテストコード（`EditScreenViewModelRewriteBodyTest.kt`）は実装完了後もコンパイルは通ったが、`errorEvents` を検証する5件（TC-0063-E01〜E05）が実行時に失敗した。Task ツールで実際にデバッグ出力を仕込んで原因を特定した。

**症状**: `viewModel.errorEvents` を `backgroundScope.launch { collect { ... } }` で購読しても、`rewriteBody()` の `_errorEvents.emit(...)` が発行した値を一切受信できない（`received` が常に空リスト）。

**原因**: 本プロジェクトの `kotlinx-coroutines-test`（1.9.0）環境において、`TestScope.backgroundScope` で起動したコルーチンが、`advanceUntilIdle()` 呼び出し後も再開（レジューム）されないケースを確認した（ViewModel やロジックとは無関係な最小再現コードでも同一現象を確認済み）。実装コード自体の不備ではなく、Red フェーズで作成されたテストコード側の待ち合わせ方法に起因する問題と判断した。

**対応**（テストコードの修正、実装コードは変更不要）:
1. `backgroundScope.launch { ... }` を通常の `launch { ... }`（テストの `runTest` スコープの子コルーチンとして起動）に変更し、`Job` を `errorEventsJob` として保持
2. 各テストの検証後に `errorEventsJob.cancel()` を呼び出し、`collect` の無限ループを明示的に終了させる（通常の `launch` はテスト終了までに完了/キャンセルされないと `UncompletedCoroutinesError` になるため）
3. `Dispatchers.setMain(...)` と `runTest(...)` が同一の `TestCoroutineScheduler` を共有するよう、`StandardTestDispatcher()` インスタンスをテストクラスのフィールドとして保持し、`setMain()` と `runTest()` の両方に明示的に渡すよう変更（`viewModelScope.launch` と `launch` が確実に同一キューで順序通り処理されるようにするための追加の堅牢化）

あわせて、production コード側の `_errorEvents` の定義も `MutableSharedFlow<Int>(extraBufferCapacity = 1)`（バッファ1）に変更した。emit 時点で収集側が「値を待って完全にサスペンドしている」タイミングに一致しないと配信できないゼロバッファのランデブー方式は、実運用でも UI 側の購読開始が1フレーム遅れるだけでエラー通知を取りこぼすリスクがあるため、バッファを1に設定することでその種のタイミング依存を解消した。これは "一回限りのイベント" というerrorEventsの意味論を変えるものではない（🟡 元資料に明記のない実装判断のため黄信号）。

---

## 6. 品質判定

```
✅ 高品質:
- テスト結果: 新規14件 + 既存全テスト（計240件）すべて成功
- 実装品質: シンプル。TASK-0063.md 実装詳細のコードをほぼそのまま反映
- リファクタ箇所:
  - EditScreenViewModel.kt が294行に増加（800行制限内だが、Refactorフェーズで
    LLM呼び出し部分のコメント整理や責務分離を検討余地あり）
  - 既存テストファイルへのコンストラクタ引数追加が機械的な変更のため、
    共通ヘルパー（fun createViewModel()）へのリファクタを検討余地あり
- 機能的問題: なし
- コンパイルエラー: なし
- ファイルサイズ: 294行（800行以下）
- モック使用: 実装コード（EditScreenViewModel.kt, EditFormState.kt）にモック・スタブなし
```

---

## 7. 課題・改善点（Refactorフェーズで対応）

- `_errorEvents` のバッファ設計（`extraBufferCapacity = 1`）についてコメントで意図を説明済みだが、
  設計文書（interfaces.kt / architecture.md）には明記がないため、Refactor フェーズもしくは
  設計文書側への反映を検討する
- 既存テストファイル（Test/InitializeTest/MainActivityEditFlowTest）へのモック注入が
  ファイルごとに重複しているため、共通化の余地がある
- `EditScreenViewModelRewriteBodyTest.kt` の各エラー系テストで `errorEventsJob.cancel()` を
  手動で呼び出すパターンが5箇所重複しているため、`@After` での一括キャンセルや
  ヘルパー関数化を検討する

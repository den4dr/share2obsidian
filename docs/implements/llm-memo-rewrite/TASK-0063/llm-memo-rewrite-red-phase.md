# TDD Redフェーズ記録: EditScreenViewModel Hilt化・rewriteBody()実装

**機能名**: llm-memo-rewrite
**タスクID**: TASK-0063
**要件名**: llm-memo-rewrite
**作成日**: 2026-07-06

---

## 1. 実装したテストケース一覧

テストケース定義書（`llm-memo-rewrite-testcases.md`）の全14件（TC-0063-N01〜N05・E01〜E05・B01〜B04）を実装した（テストケース追加目標数10以上を満たす）。

| No. | 種別 | テスト名 | 対応要件 | 信頼性 |
|-----|------|---------|---------|--------|
| TC-0063-N01 | 正常系 | rewriteBody 成功時 formState.body が LLM 応答テキストに更新される | REQ-003 | 🔵 |
| TC-0063-N02 | 正常系 | initialize で bodyLlmPrompt 非空の場合 rewriteBodyEnabled が true になる | REQ-102 | 🔵 |
| TC-0063-N03 | 正常系 | rewriteBody 実行中は isRewritingBody が true になり完了後 false に戻る | REQ-201 | 🔵 |
| TC-0063-N04 | 正常系 | rewriteBody は sourceContent を入力とし formState.body を入力にしない | REQ-002, REQ-406 | 🔵 |
| TC-0063-N05 | 正常系 | コンストラクタ注入で EditScreenViewModel を生成でき既存 formState 初期化が回帰しない | Hilt互換 | 🟡 |
| TC-0063-E01 | 異常系 | NetworkError 時 body 不変・error_llm_network が emit される | EDGE-001, NFR-201 | 🔵 |
| TC-0063-E02 | 異常系 | AuthError 時 body 不変・error_llm_auth が emit される | EDGE-002, NFR-201 | 🔵 |
| TC-0063-E03 | 異常系 | Timeout 時 body 不変・error_llm_timeout が emit される | EDGE-003, REQ-202, NFR-001 | 🔵 |
| TC-0063-E04 | 異常系 | EmptyOrInvalidResponse 時 body 不変・error_llm_empty_response が emit される | EDGE-004, NFR-201 | 🔵 |
| TC-0063-E05 | 異常系 | Unknown 時 body 不変・error_llm_unknown が emit される | NFR-201 | 🔵 |
| TC-0063-B01 | 境界値 | initialize で bodyLlmPrompt 空文字の場合 rewriteBodyEnabled が false | REQ-102 | 🔵 |
| TC-0063-B02 | 境界値 | sourceContent 空文字でも rewriteBody がガードされず rewrite が実行される | EDGE-101 | 🔵 |
| TC-0063-B03 | 境界値 | 画面回転想定の再 initialize で sourceContent/bodyLlmPrompt/formState が上書きされない | EDGE-101 | 🔵 |
| TC-0063-B04 | 境界値 | rewriteBody 成功結果が空文字なら body が空文字で上書きされる | REQ-003 | 🟡 |

**信頼性分布**: 🔵 12件 / 🟡 2件 / 🔴 0件（テストケース定義書の分布を踏襲）

---

## 2. 作成したテストファイル

### `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelRewriteBodyTest.kt`（新規）

- 対象: TC-0063-N01〜N05・E01〜E05・B01〜B04（14件）
- 実行環境: `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk=[34])`（`R.string.error_llm_*` リソース参照のため）
- DI方式: Hilt を起動せず、MockK でスタブした `LlmRewriteRepository` / `LlmSettingsRepository` を `EditScreenViewModel(mockRewrite, mockSettings)` としてコンストラクタに直接渡す（`SettingsViewModelTest` の既存パターンを踏襲）
- 非同期制御: `kotlinx-coroutines-test` の `runTest` + `StandardTestDispatcher`（`Dispatchers.setMain`/`resetMain`）+ `advanceUntilIdle()`
- `errorEvents`（`SharedFlow<Int>`, replay=0）の検証は `backgroundScope.launch { collect { ... } }` で emit 前に先行購読する
- `isRewritingBody` の on→off 遷移は `CompletableDeferred<LlmRewriteResult>` で `rewrite()` の完了タイミングを外部制御して検証する

---

## 3. テストコード全文

全文は以下のファイルを参照:
- `app/src/test/java/com/den4dr/share2Obsidian/ui/EditScreenViewModelRewriteBodyTest.kt`

---

## 4. 実行結果と期待される失敗

```bash
mise exec -- ./gradlew testDebugUnitTest --tests "com.den4dr.share2Obsidian.ui.EditScreenViewModelRewriteBodyTest"
```

**結果**: `compileDebugUnitTestKotlin` タスクでコンパイルエラー（約50件）。

```
e: .../EditScreenViewModelRewriteBodyTest.kt:109:45 Too many arguments for 'constructor(): EditScreenViewModel'.
e: .../EditScreenViewModelRewriteBodyTest.kt:120:19 Unresolved reference 'rewriteBody'.
e: .../EditScreenViewModelRewriteBodyTest.kt:156:39 Unresolved reference 'rewriteBodyEnabled'.
e: .../EditScreenViewModelRewriteBodyTest.kt:187:39 Unresolved reference 'isRewritingBody'.
e: .../EditScreenViewModelRewriteBodyTest.kt:299:44 Unresolved reference 'errorEvents'.
... (以下、各 @Test メソッド内で EditScreenViewModel(mockRewrite, mockSettings) 呼び出し・
     rewriteBody()/errorEvents/formState.isRewritingBody/formState.rewriteBodyEnabled 参照箇所で
     計約50件、原因は下記4点のいずれか)
```

**原因**（すべて production コード `EditScreenViewModel` / `EditFormState` の未実装に起因。テストコードの記述ミスではない）:
1. `EditScreenViewModel` が引数なしコンストラクタ `ViewModel()` のままで、`@Inject constructor(llmRewriteRepository, llmSettingsRepository)` が存在しない
   → `Too many arguments for 'constructor(): EditScreenViewModel'`
2. `EditScreenViewModel` に `rewriteBody()` メソッドが存在しない
   → `Unresolved reference 'rewriteBody'`
3. `EditScreenViewModel` に `errorEvents`（`SharedFlow<Int>`）プロパティが存在しない
   → `Unresolved reference 'errorEvents'`
4. `EditFormState` に `isRewritingBody` / `rewriteBodyEnabled` フィールドが存在しない
   → `Unresolved reference 'isRewritingBody'` / `Unresolved reference 'rewriteBodyEnabled'`

既存クラス（`ProcessedContent`・`NoteConfig`・`LlmRewriteRepository`・`LlmSettingsRepository`・`LlmRewriteResult`・`LlmSettings`・`R.string.error_llm_*`）を使う箇所はすべて正常にコンパイル可能であることを確認済み（TASK-0060・TASK-0062 Red フェーズと同方針）。

---

## 5. Greenフェーズで実装すべき内容

TASK-0063.md「実装詳細」1〜7 の通り、`app/src/main/java/com/den4dr/share2Obsidian/ui/EditScreenViewModel.kt` と `EditFormState.kt` に以下を実装する:

1. `EditScreenViewModel` を `@HiltViewModel` 化し、`@Inject constructor(llmRewriteRepository: LlmRewriteRepository, llmSettingsRepository: LlmSettingsRepository) : ViewModel()` に変更
2. `errorEvents`: `private val _errorEvents = MutableSharedFlow<Int>()` / `val errorEvents: SharedFlow<Int> = _errorEvents.asSharedFlow()` を追加
3. `EditFormState` に `isRewritingBody: Boolean = false` / `rewriteBodyEnabled: Boolean = false` を追加
4. `initialize()` で `rewriteBodyEnabled = bodyLlmPrompt.isNotBlank()` を算出して `_formState.value` に設定（既存 `initialized` ガードは維持）
5. `rewriteBody()` を実装:
   ```kotlin
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
   ```
6. `sourceContent`（既に private set プロパティとして存在）を入力に使い、`formState.body` は使わない
7. `sourceContent` の空文字ガードは行わない（EDGE-101）

実装後、`EditScreenViewModelRewriteBodyTest` を再実行して14件全て成功することを確認し、続けて既存テストスイート（`EditScreenViewModelTest`, `EditScreenViewModelInitializeTest` 等）が後方互換で合格することを確認する。

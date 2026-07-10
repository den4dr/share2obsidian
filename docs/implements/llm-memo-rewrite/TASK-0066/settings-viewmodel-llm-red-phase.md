# TASK-0066 TDD Redフェーズ記録: SettingsViewModel LLM設定対応

**機能名**: SettingsViewModel LLM設定対応 (settings-viewmodel-llm)
**タスクID**: TASK-0066
**要件名**: llm-memo-rewrite
**フェーズ**: Phase 4 - LLM設定UI
**作成日**: 2026-07-06

---

## 1. 作成したテストケース一覧

テストファイル: `app/src/test/java/com/den4dr/share2Obsidian/ui/SettingsViewModelTest.kt`（既存ファイルに追記・一部既存テストを2引数コンストラクタへ更新）

| No | テストメソッド | 対応TC | 種別 | 信頼性 |
|----|---------------|--------|------|--------|
| 1 | `uiState_reflectsRepositorySettings` | TC-E-02（既存テスト移行） | 回帰防止 | 🟡 |
| 2 | `updateVault_savesToRepository` | TC-E-02（既存テスト移行） | 回帰防止 | 🟡 |
| 3 | `updateLlmEndpointUrl_savesToRepository` | TC-N-01 | 正常系 | 🔵 |
| 4 | `updateLlmApiKey_savesToRepository` | TC-N-02 | 正常系 | 🔵 |
| 5 | `updateLlmModel_savesToRepository` | TC-N-03 | 正常系 | 🔵 |
| 6 | `uiState_reflectsBothNoteAndLlmRepositorySettings` | TC-N-04 | 正常系 | 🔵 |
| 7 | `uiState_reflectsNoteSettingsWhenLlmSettingsAreDefault` | TC-N-05 | 正常系（後方互換） | 🟡 |
| 8 | `updateLlmApiKey_savesEmptyStringAsIs` | TC-B-01 | 境界値 | 🟡 |
| 9 | `uiState_isAllEmptyWhenBothRepositoriesReturnDefaults` | TC-B-02 | 境界値 | 🟡 |
| 10 | `updateLlmModel_calledTwice_savesTwice` | TC-B-03 | 境界値 | 🟡 |

- **TC-E-01**（save系例外ハンドリング）は要件定義書§4.4・テストケース定義書で「本タスク範囲外・実装しない」と明示されているため、意図的に未実装（🔴）。
- テストケース追加目標数（10以上）は「利用可能なテストケースが10未満の場合は全て追加」の条件を満たし、テストケース定義書に列挙された実装可能な全9ケース（TC-N-01〜05, TC-B-01〜03, TC-E-02）＋既存回帰2件を実装（重複を除き実質10メソッド）。

---

## 2. テストコード全文

`app/src/test/java/com/den4dr/share2Obsidian/ui/SettingsViewModelTest.kt` に実装済み。主な変更点:

1. 既存2テスト（`uiState_reflectsRepositorySettings`, `updateVault_savesToRepository`）を、`SettingsViewModel(repo)`（1引数）から `SettingsViewModel(repo, llmRepo)`（2引数）呼び出しへ変更し、`llmRepo` を `mockk<LlmSettingsRepository>()` でスタブ化。
2. 新規8テストを追加。いずれも `NoteSettingsRepository` と `LlmSettingsRepository` の両方をモック化し、`SettingsViewModel` の未実装API（2引数コンストラクタ・`updateLlmEndpointUrl`/`updateLlmApiKey`/`updateLlmModel`・`SettingsUiState` のLLM3フィールド）を呼び出す。

各テストの構成:
- `@Before`/`@After` で `UnconfinedTestDispatcher` を `Dispatchers.setMain`/`resetMain`
- `runTest` + `advanceUntilIdle()` でコルーチン完了を待機
- `coVerify(exactly = N, timeout = 2000)` で呼び出し回数・引数を検証
- `assertEquals` で `SettingsUiState` の構造的等価性を検証
- 各テストに日本語コメント（テスト目的・テスト内容・期待される動作・信頼性レベル・各expectの確認内容）を付与

---

## 3. テスト実行結果と期待される失敗

**実行コマンド**:
```bash
mise exec -- ./gradlew testDebugUnitTest --tests "*SettingsViewModelTest*"
```

**結果**: `:app:compileDebugUnitTestKotlin` タスクでコンパイルエラーが発生し、`BUILD FAILED`（想定通りの失敗）。

**エラー内容（抜粋）**:
```
e: SettingsViewModelTest.kt:62:49 Too many arguments for 'constructor(noteSettingsRepository: NoteSettingsRepository): SettingsViewModel'.
e: SettingsViewModelTest.kt:108:19 Unresolved reference 'updateLlmEndpointUrl'.
e: SettingsViewModelTest.kt:138:19 Unresolved reference 'updateLlmApiKey'.
e: SettingsViewModelTest.kt:168:19 Unresolved reference 'updateLlmModel'.
e: SettingsViewModelTest.kt:212:17 No parameter with name 'llmEndpointUrl' found.
e: SettingsViewModelTest.kt:213:17 No parameter with name 'llmApiKey' found.
e: SettingsViewModelTest.kt:214:17 No parameter with name 'llmModel' found.
（以降、同様のエラーが全新規テストで発生）
```

**失敗の理由**: `SettingsViewModel` が現時点で1引数コンストラクタ（`noteSettingsRepository` のみ）であり、`llmSettingsRepository` を受け取らない。また `updateLlmEndpointUrl`/`updateLlmApiKey`/`updateLlmModel` メソッドが存在せず、`SettingsUiState` に `llmEndpointUrl`/`llmApiKey`/`llmModel` フィールドが存在しない。すべてGreenフェーズで実装すべき未実装機能であるため、コンパイル時点で失敗する（TDD Redフェーズとして妥当な失敗）。

---

## 4. Greenフェーズで実装すべき内容

`app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsViewModel.kt` を以下のように変更する:

1. **`SettingsUiState` の拡張**:
   ```kotlin
   data class SettingsUiState(
       val vault: String = "",
       val folder: String = "",
       val llmEndpointUrl: String = "",
       val llmApiKey: String = "",
       val llmModel: String = "",
   )
   ```

2. **コンストラクタへの `LlmSettingsRepository` 追加注入**:
   ```kotlin
   @HiltViewModel
   class SettingsViewModel @Inject constructor(
       private val noteSettingsRepository: NoteSettingsRepository,
       private val llmSettingsRepository: LlmSettingsRepository,
   ) : ViewModel() {
   ```

3. **`uiState` の `combine()` 化**（`map` から `combine` へ変更）:
   ```kotlin
   val uiState: StateFlow<SettingsUiState> = combine(
       noteSettingsRepository.getSettings(),
       llmSettingsRepository.getSettings(),
   ) { note, llm ->
       SettingsUiState(
           vault = note.vault,
           folder = note.folder,
           llmEndpointUrl = llm.endpointUrl,
           llmApiKey = llm.apiKey,
           llmModel = llm.model,
       )
   }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())
   ```

4. **update系関数の追加**:
   ```kotlin
   fun updateLlmEndpointUrl(url: String) {
       viewModelScope.launch(Dispatchers.IO) { llmSettingsRepository.saveEndpointUrl(url) }
   }

   fun updateLlmApiKey(apiKey: String) {
       viewModelScope.launch(Dispatchers.IO) { llmSettingsRepository.saveApiKey(apiKey) }
   }

   fun updateLlmModel(model: String) {
       viewModelScope.launch(Dispatchers.IO) { llmSettingsRepository.saveModel(model) }
   }
   ```

5. Hilt DI（`LlmModule`, TASK-0061）はすでに `LlmSettingsRepository` を提供済みのため、追加設定は不要。

実装後、`mise exec -- ./gradlew testDebugUnitTest --tests "*SettingsViewModelTest*"` を実行し、全10テストの成功を確認する。

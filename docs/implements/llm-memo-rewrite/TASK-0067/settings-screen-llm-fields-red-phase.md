# TASK-0067 TDD Redフェーズ記録: SettingsScreen LLM設定入力欄追加

**機能名**: settings-screen-llm-fields
**タスクID**: TASK-0067
**要件名**: llm-memo-rewrite
**フェーズ**: Phase 4 - LLM設定UI
**作成日**: 2026-07-06

---

## 1. 作成したテストケース一覧

テストファイル: `app/src/androidTest/java/com/den4dr/share2Obsidian/ui/SettingsScreenTest.kt`（既存ファイルに10テストメソッド＋`RecordingFakeLlmSettingsRepository`を追記、`createViewModel()`に`llmRepository`差し替え用の第2デフォルト引数を追加）

| No | テストメソッド | 対応TC | 種別 | 信頼性 |
|----|---------------|--------|------|--------|
| 1 | `llmEndpointField_inputTriggersSaveEndpointUrlWithInputValue` | TC-N-01 | 正常系 | 🔵 |
| 2 | `llmApiKeyField_inputTriggersSaveApiKeyWithInputValue` | TC-N-02 | 正常系 | 🔵 |
| 3 | `llmModelField_inputTriggersSaveModelWithInputValue` | TC-N-03 | 正常系 | 🔵 |
| 4 | `llmFields_showInitialUiStateValuesOnLaunch` | TC-N-04 | 正常系 | 🔵 |
| 5 | `llmSection_allThreeFieldsAreDisplayed` | TC-N-05 | 正常系 | 🔵 |
| 6 | `llmApiKeyField_clearingInputTriggersSaveApiKeyWithEmptyString` | TC-E-01 | 異常系/境界 | 🟡 |
| 7 | `existingVaultFolderAndTemplateItem_stillDisplayedAlongsideLlmFields` | TC-E-02 | 異常系（後方互換） | 🔵 |
| 8 | `llmFields_displayCorrectlyWhenAllFieldsAreDefaultEmpty` | TC-B-01 | 境界値 | 🟡 |
| 9 | `llmApiKeyField_masksNonEmptyValueAndDoesNotShowPlaintext` | TC-B-02 | 境界値 | 🟡 |
| 10 | `llmEndpointField_longInputIsSavedWithoutTruncation` | TC-B-03 | 境界値 | 🟡 |

テストケース定義書（`settings-screen-llm-fields-testcases.md`）に列挙された10ケース（TC-N-01〜05, TC-E-01〜02, TC-B-01〜03）を全件実装した（テストケース追加目標数10以上を満たす）。

---

## 2. テストコード全文

`app/src/androidTest/java/com/den4dr/share2Obsidian/ui/SettingsScreenTest.kt` に実装済み。主な変更点:

1. `createViewModel()` に `llmRepository: LlmSettingsRepository = FakeLlmSettingsRepository()` を追加し、テストごとに異なるFake（表示用の`FakeLlmSettingsRepository`、記録用の`RecordingFakeLlmSettingsRepository`）を注入可能にした。
2. 新規10テストを追加。いずれも `SettingsScreen` の `settings_llm_endpoint_field` / `settings_llm_apikey_field` / `settings_llm_model_field` testTagノードを操作・検証する。
3. `save*()` への引数記録用に `RecordingFakeLlmSettingsRepository`（`LlmSettingsRepository` 実装）を追加した。

各テストの構成:
- `composeTestRule.setContent { SettingsScreen(...) }` で画面表示
- `onNodeWithTag(...).performTextInput(...)` / `performTextClearance()` でユーザー操作
- `composeTestRule.waitUntil(timeoutMillis = 2000) { fake.saved* != null }` でコルーチン経由の非同期保存完了を待機
- `assertEquals` / `assertIsDisplayed` / `assertTextContains` / `assert(!hasText(...))` で検証
- 各テストに日本語コメント（テスト目的・テスト内容・期待される動作・信頼性レベル・各expectの確認内容）を付与

抜粋（TC-N-01）:

```kotlin
@Test
fun llmEndpointField_inputTriggersSaveEndpointUrlWithInputValue() {
    // 【テスト目的】: endpoint欄への入力で saveEndpointUrl が入力値で呼ばれることを確認する
    // 🔵 信頼性レベル: TASK-0067.md テストケース1・testcases.md TC-N-01・REQ-004より
    val fake = RecordingFakeLlmSettingsRepository()
    composeTestRule.setContent {
        SettingsScreen(
            onNavigateBack = {},
            onNavigateToTemplates = {},
            viewModel = createViewModel(llmRepository = fake),
        )
    }

    composeTestRule.onNodeWithTag("settings_llm_endpoint_field").performTextInput("https://api.example.com/v1")

    composeTestRule.waitUntil(timeoutMillis = 2000) { fake.savedEndpointUrl != null }
    assertEquals("https://api.example.com/v1", fake.savedEndpointUrl)
}
```

`RecordingFakeLlmSettingsRepository`:

```kotlin
private class RecordingFakeLlmSettingsRepository(
    private val settings: LlmSettings = LlmSettings(),
) : LlmSettingsRepository {
    var savedEndpointUrl: String? = null
    var savedApiKey: String? = null
    var savedModel: String? = null
    override fun getSettings(): Flow<LlmSettings> = flowOf(settings)
    override suspend fun saveEndpointUrl(url: String) { savedEndpointUrl = url }
    override suspend fun saveApiKey(apiKey: String) { savedApiKey = apiKey }
    override suspend fun saveModel(model: String) { savedModel = model }
}
```

---

## 3. テスト実行結果と期待される失敗

**実行コマンド**:
```bash
mise exec -- ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.den4dr.share2Obsidian.ui.SettingsScreenTest
```

**環境状況**: 本開発環境にはAVD（`Medium_Phone_API_36.1`）が導入されており、エミュレータの起動・`adb`接続には成功した。しかし `connectedAndroidTest` タスク実行時、本タスクの変更とは無関係に、`:app:mergeDebugAndroidTestJavaResource` タスクが以下のエラーで失敗する（プロジェクト全体・既存テストクラス実行時にも再現する環境依存の問題）。

```
> Task :app:mergeDebugAndroidTestJavaResource FAILED
> A failure occurred while executing com.android.build.gradle.internal.tasks.MergeJavaResWorkAction
   > 6 files found with path 'META-INF/LICENSE.md' from inputs:
      - org.junit.jupiter:junit-jupiter-params:5.8.2/...
      - org.junit.jupiter:junit-jupiter-engine:5.8.2/...
      - org.junit.jupiter:junit-jupiter-api:5.8.2/...
      - org.junit.platform:junit-platform-engine:1.8.2/...
      - org.junit.platform:junit-platform-commons:1.8.2/...
      - org.junit.jupiter:junit-jupiter:5.8.2/...
```

**再現確認**: 既存の `TemplateEditScreenTest`（本タスク未変更のテストクラス）を対象に同コマンドを実行しても同一のMETA-INF重複エラーで失敗することを確認した。したがって本エラーは `androidTestImplementation` 依存関係グラフ（robolectric/mockk-android等がjunit-jupiterを推移的に持ち込むことによるパッケージング競合）に起因する、本タスクとは無関係な既存環境課題であり、`SettingsScreen.kt`（View層限定という本タスクのスコープ、requirements.md 3.3）を超える `app/build.gradle.kts` の変更が必要となるため、本Redフェーズでは修正しない。

**暫定確認方針**（TASK-0058/TASK-0060のRedフェーズ前例に倣う）:
- `mise exec -- ./gradlew compileDebugAndroidTestKotlin` は **BUILD SUCCESSFUL**（新規10テストのコンパイルエラーなし。呼び出し先の `SettingsViewModel.updateLlm*()` 等は既存実装のため未解決参照エラーは発生しない）。
- 一方、`SettingsScreen.kt` には `settings_llm_endpoint_field` / `settings_llm_apikey_field` / `settings_llm_model_field` のOutlinedTextFieldが未実装（現状はvault/folder欄→HorizontalDivider→テンプレート管理ListItemのみ）であるため、実機/エミュレータで実行した場合は全10テストが `onNodeWithTag(...)` で対象ノードが見つからず `AssertionError: Failed to perform text input./Failed: assertIsDisplayed` 等で **失敗することが構造的に確定している**（Composeツリーに当該testTagのノードが存在しないため）。
- `connectedAndroidTest` の実機実行確認は、パッケージング競合（環境課題）の解消後に別途行う。

**失敗の理由**: `SettingsScreen`（`app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsScreen.kt`）にLLM設定用の3つの`OutlinedTextField`（`settings_llm_endpoint_field` 等）が実装されていないため、テストが操作・検証対象とするノードが存在しない。これはGreenフェーズで実装すべき未実装機能であり、TDD Redフェーズとして妥当な失敗である。

---

## 4. Greenフェーズで実装すべき内容

`app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsScreen.kt` を以下のように変更する:

1. **LLM設定セクションの追加**（vault/folder欄後、テンプレート管理ListItem前に `HorizontalDivider()` で区切る）:
   ```kotlin
   HorizontalDivider() // vault/folderとLLM設定の区切り

   OutlinedTextField(
       value = uiState.llmEndpointUrl,
       onValueChange = viewModel::updateLlmEndpointUrl,
       label = { Text(stringResource(R.string.settings_llm_endpoint_label)) },
       modifier = Modifier
           .fillMaxWidth()
           .padding(horizontal = 16.dp, vertical = 4.dp)
           .testTag("settings_llm_endpoint_field"),
   )
   OutlinedTextField(
       value = uiState.llmApiKey,
       onValueChange = viewModel::updateLlmApiKey,
       label = { Text(stringResource(R.string.settings_llm_apikey_label)) },
       visualTransformation = PasswordVisualTransformation(),
       modifier = Modifier
           .fillMaxWidth()
           .padding(horizontal = 16.dp, vertical = 4.dp)
           .testTag("settings_llm_apikey_field"),
   )
   OutlinedTextField(
       value = uiState.llmModel,
       onValueChange = viewModel::updateLlmModel,
       label = { Text(stringResource(R.string.settings_llm_model_label)) },
       modifier = Modifier
           .fillMaxWidth()
           .padding(horizontal = 16.dp, vertical = 4.dp)
           .testTag("settings_llm_model_field"),
   )

   HorizontalDivider() // LLM設定とテンプレート管理の区切り（既存divider位置を踏襲）
   ```

2. **文字列リソースの追加**（`app/src/main/res/values/strings.xml`）:
   ```xml
   <string name="settings_llm_endpoint_label">LLM エンドポイント</string>
   <string name="settings_llm_apikey_label">APIキー</string>
   <string name="settings_llm_model_label">モデル名</string>
   ```

3. **import追加**（`SettingsScreen.kt`）:
   - `androidx.compose.ui.text.input.PasswordVisualTransformation`（または `androidx.compose.foundation.text.PasswordVisualTransformation`。Material3 `OutlinedTextField` が要求するシグネチャに合わせて選択）

4. `SettingsViewModel` / `LlmSettingsRepository` / `SettingsUiState` は変更不要（TASK-0066/0058で実装済み）。

Green フェーズ完了条件: 上記実装後、`mise exec -- ./gradlew compileDebugAndroidTestKotlin` が成功し、実機/エミュレータ環境が利用可能になった時点で10テストすべてがパスすること。

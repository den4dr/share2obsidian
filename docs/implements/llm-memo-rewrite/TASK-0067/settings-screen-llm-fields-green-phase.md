# TASK-0067 TDD Greenフェーズ記録: SettingsScreen LLM設定入力欄追加

**機能名**: settings-screen-llm-fields
**タスクID**: TASK-0067
**要件名**: llm-memo-rewrite
**フェーズ**: Phase 4 - LLM設定UI
**作成日**: 2026-07-06

---

## 1. 実装方針

Redフェーズ記録（`settings-screen-llm-fields-red-phase.md`）の「4. Greenフェーズで実装すべき内容」に記載された計画をそのまま採用した。仕様（requirements.md / testcases.md）と現在の実装（`SettingsScreen.kt` / `SettingsViewModel.kt`）を照合した結果、差異は検出されなかった。

- `SettingsUiState` / `updateLlmEndpointUrl` / `updateLlmApiKey` / `updateLlmModel`（TASK-0066完了済み）をそのまま利用し、View層（`SettingsScreen.kt`）のみを変更（requirements.md 3.3）
- 既存vault/folder欄と同一のパターン（`OutlinedTextField` + `fillMaxWidth()` + `padding(horizontal = 16.dp, vertical = 4.dp)` + `testTag`）を踏襲
- apiKey欄のみ `visualTransformation = PasswordVisualTransformation()` を付与
- vault/folderセクションとLLM設定セクションの間、LLM設定セクションとテンプレート管理`ListItem`の間にそれぞれ `HorizontalDivider()` を配置

## 2. 実装コード

### `app/src/main/java/com/den4dr/share2Obsidian/ui/SettingsScreen.kt`（全文）

```kotlin
package com.den4dr.share2Obsidian.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.den4dr.share2Obsidian.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTemplates: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    BackHandler { onNavigateBack() }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.label_settings),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // vault/folder のグローバルデフォルト設定（入力変更時に即時保存、REQ-021）
            OutlinedTextField(
                value = uiState.vault,
                onValueChange = { viewModel.updateVault(it) },
                label = { Text(stringResource(R.string.settings_vault_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("settings_vault_field"),
            )
            OutlinedTextField(
                value = uiState.folder,
                onValueChange = { viewModel.updateFolder(it) },
                label = { Text(stringResource(R.string.settings_folder_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("settings_folder_field"),
            )
            // 【区切り】: vault/folder設定とLLM設定を視覚的に区切る 🟡
            HorizontalDivider()

            // 【LLM設定セクション】: LLM API接続情報（endpointUrl/apiKey/model）の入力欄（REQ-004）
            // 【実装方針】: 既存vault/folder欄と同一のOutlinedTextField構造・padding・testTag命名パターンを踏襲する
            // 【テスト対応】: TC-N-01〜TC-N-05, TC-E-01〜TC-E-02, TC-B-01〜TC-B-03 を通すための実装
            // 🔵 信頼性レベル: TASK-0067.md・requirements.md 3.3/3.4・red-phase.mdより
            OutlinedTextField(
                value = uiState.llmEndpointUrl,
                onValueChange = viewModel::updateLlmEndpointUrl,
                label = { Text(stringResource(R.string.settings_llm_endpoint_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("settings_llm_endpoint_field"),
            )
            // 【apiKeyマスク表示】: 機微情報のためPasswordVisualTransformationで平文非表示にする（REQ-401補完）
            // 保持値・保存値は平文のまま。マスクは表示層のみの変換 🟡
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

            // 【区切り】: LLM設定とテンプレート管理メニューを視覚的に区切る（既存divider位置を踏襲）
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.template_list_title)) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable { onNavigateToTemplates() },
            )
        }
    }
}
```

### `app/src/main/res/values/strings.xml`（差分）

```xml
<string name="settings_vault_label">デフォルト Vault</string>
<string name="settings_folder_label">デフォルトフォルダ</string>

<!-- 設定画面: LLM API設定（TASK-0067） -->
<string name="settings_llm_endpoint_label">LLM エンドポイント</string>
<string name="settings_llm_apikey_label">APIキー</string>
<string name="settings_llm_model_label">モデル名</string>
```

`SettingsViewModel.kt` / `LlmSettingsRepository` は変更なし（TASK-0066/0058で実装済みのものをそのまま利用）。

---

## 3. テスト実行結果

### コンパイル確認

```bash
mise exec -- ./gradlew compileDebugAndroidTestKotlin compileDebugKotlin
```

結果: **BUILD SUCCESSFUL**（`compileDebugKotlin` / `compileDebugAndroidTestKotlin` ともに成功。新規実装・既存10テストともにコンパイルエラーなし）

### 実機/エミュレータでの実行

エミュレータ（`emulator-5554`）が起動・接続済みであることを確認した上で、以下を実行した。

```bash
mise exec -- ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.den4dr.share2Obsidian.ui.SettingsScreenTest
```

結果: **BUILD FAILED**（`:app:mergeDebugAndroidTestJavaResource` タスクが `META-INF/LICENSE.md` の重複エラーで失敗）。

```
> 6 files found with path 'META-INF/LICENSE.md' from inputs:
   - org.junit.jupiter:junit-jupiter-params:5.8.2/...
   - org.junit.jupiter:junit-jupiter-engine:5.8.2/...
   - org.junit.jupiter:junit-jupiter-api:5.8.2/...
   - org.junit.platform:junit-platform-engine:1.8.2/...
   - org.junit.platform:junit-platform-commons:1.8.2/...
   - org.junit.jupiter:junit-jupiter:5.8.2/...
```

**本タスクと無関係であることの再確認**: 本Greenフェーズで、未変更の既存テストクラス `TemplateEditScreenTest` を対象に同じコマンドを実行し、**同一のエラーで同様に失敗すること**を確認した。したがって本エラーは `androidTestImplementation` 依存関係グラフ（mockk-android等がjunit-jupiterを推移的に持ち込むことによるパッケージング競合）に起因する既存の環境課題であり、`SettingsScreen.kt`/`strings.xml`（View層限定という本タスクのスコープ、requirements.md 3.3）を超える `app/build.gradle.kts` の変更が必要となるため、本Greenフェーズでは修正しない（Red フェーズの判断を踏襲）。

**構造的な確認**（実機実行の代替として、実装とテストコードを突き合わせた確認）:

| テストケース | 実装での対応状況 |
|---|---|
| TC-N-01〜03（endpoint/apiKey/model入力→save呼び出し） | 各 `OutlinedTextField` の `onValueChange` が `viewModel::updateLlmEndpointUrl` 等を直接参照。ViewModel側は `launchOnIo { llmSettingsRepository.save*(...) }` で入力値をそのまま委譲（変更なし、TASK-0066実装済み）ため、テストの `RecordingFakeLlmSettingsRepository` に入力値がそのまま記録される |
| TC-N-04（初期値表示） | `value = uiState.llmEndpointUrl` / `uiState.llmModel` を`OutlinedTextField`にバインド。`combine()`によりFakeの`LlmSettings`初期値が`uiState`に反映される（vault/folderの既存テスト`settingsScreen_showsVaultAndFolderValues`と同一機構） |
| TC-N-05（3欄表示） | 3つの`OutlinedTextField`に`testTag("settings_llm_endpoint_field")`等をそれぞれ付与済み |
| TC-E-01（空文字保存） | `onValueChange`にバリデーションを挟んでいないため、空文字もそのまま`updateLlmApiKey("")`に渡る |
| TC-E-02（後方互換） | vault/folder欄・`HorizontalDivider`・テンプレート管理`ListItem`の構造・testTagは変更していない |
| TC-B-01（デフォルト空文字表示） | `value=""`でも`OutlinedTextField`は例外なく描画される（Compose標準動作） |
| TC-B-02（apiKeyマスク） | `visualTransformation = PasswordVisualTransformation()`をapiKey欄にのみ付与 |
| TC-B-03（長文字列） | `onValueChange`はCompose標準の`TextFieldValue`変更ハンドラであり、長さ制限を課していないため入力全体がそのまま渡る |

---

## 4. 品質判定

```
⚠️ 要改善（環境課題により実機確認未了、コードは仕様どおり）:
- テスト結果: compileDebugAndroidTestKotlin は成功。connectedAndroidTest 実機実行は
  本タスクと無関係な既存環境課題（META-INF重複、未変更の既存テストクラスでも再現確認済み）で
  ブロックされており、Taskツールによる完全な成功確認はできていない。
- 実装品質: シンプル（既存vault/folderパターンをそのまま踏襲した宣言的UIコードのみ）
- リファクタ箇所: コメント量がやや多く整理の余地あり。テストタグ命名・padding値等の重複は
  既存パターンと統一されておりリファクタ不要
- 機能的問題: なし（要件・テストケースとの差異なし）
- コンパイルエラー: なし
- ファイルサイズ: SettingsScreen.kt 136行（800行制限に対し余裕あり）
- モック使用: 実装コード（SettingsScreen.kt）にモック・スタブなし
```

Refactorフェーズでは、環境課題（META-INF重複）の解消可否について改めて検討するか、`app/build.gradle.kts` 変更が必要な場合はスコープ外として別タスク化を提案する。

---

**作成**: tsumiki:tdd-green TASK-0067

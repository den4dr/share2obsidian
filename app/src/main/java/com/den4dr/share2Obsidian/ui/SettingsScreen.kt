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

/**
 * 【機能概要】: アプリ設定画面。vault/folderのデフォルト値、LLM API接続情報（TASK-0067）、
 *              テンプレート管理への導線を1画面に表示する。
 * 【改善内容】: LLM設定セクションを `LlmSettingsSection` として分離し、単一責任原則に沿って
 *              画面全体のレイアウト（トップバー・vault/folder・ナビゲーション）と
 *              LLM設定の入力欄群の責務を切り分けた。
 * 【設計方針】: 各入力欄の `Modifier`（fillMaxWidth + padding + testTag）を
 *              `Modifier.settingsFieldModifier()` に共通化し、重複コードを除去した（DRY原則）。
 * 【保守性】: セクション追加時は `LlmSettingsSection` のような専用Composableを増やす形に揃えることで、
 *            `SettingsScreen` 本体の見通しを保ちやすくする。
 * 🔵 信頼性レベル: 既存vault/folder実装・TASK-0067要件定義（requirements.md 3.3/3.4）より、
 *                動作・構造は変更せず可読性のみを改善（機能的な変更なし）
 */
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
            // 【vault/folder設定】: グローバルデフォルト値。入力変更のたびに即時保存する（REQ-021）
            OutlinedTextField(
                value = uiState.vault,
                onValueChange = { viewModel.updateVault(it) },
                label = { Text(stringResource(R.string.settings_vault_label)) },
                singleLine = true,
                modifier = Modifier.settingsFieldModifier("settings_vault_field"),
            )
            OutlinedTextField(
                value = uiState.folder,
                onValueChange = { viewModel.updateFolder(it) },
                label = { Text(stringResource(R.string.settings_folder_label)) },
                singleLine = true,
                modifier = Modifier.settingsFieldModifier("settings_folder_field"),
            )
            // 【区切り】: vault/folder設定とLLM設定を視覚的に区切る 🟡
            HorizontalDivider()

            LlmSettingsSection(
                endpointUrl = uiState.llmEndpointUrl,
                apiKey = uiState.llmApiKey,
                model = uiState.llmModel,
                onEndpointUrlChange = viewModel::updateLlmEndpointUrl,
                onApiKeyChange = viewModel::updateLlmApiKey,
                onModelChange = viewModel::updateLlmModel,
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

/**
 * 【ヘルパー関数】: LLM API接続情報（endpointUrl/apiKey/model）の入力欄3種をまとめて描画する（REQ-004）。
 * 【単一責任】: LLM設定セクションの表示・入力配線のみを担当し、画面全体のレイアウトからは独立している。
 * 【再利用性】: `SettingsScreen` からのみ呼び出す想定だが、値と `onValueChange` を引数化しているため
 *              プレビューや将来的な画面分割時にも流用できる。
 * 🔵 信頼性レベル: TASK-0067.md・requirements.md 3.3/3.4・green-phase.mdの実装内容をそのまま移設（機能的な変更なし）
 *
 * @param endpointUrl 現在のLLMエンドポイントURL（`uiState.llmEndpointUrl`）
 * @param apiKey 現在のLLM APIキー（`uiState.llmApiKey`）。画面上は `PasswordVisualTransformation` でマスクされる
 * @param model 現在のLLMモデル名（`uiState.llmModel`）
 * @param onEndpointUrlChange endpointUrl欄の入力変更時に呼ばれるコールバック（`viewModel::updateLlmEndpointUrl`）
 * @param onApiKeyChange apiKey欄の入力変更時に呼ばれるコールバック（`viewModel::updateLlmApiKey`）
 * @param onModelChange model欄の入力変更時に呼ばれるコールバック（`viewModel::updateLlmModel`）
 */
@Composable
private fun LlmSettingsSection(
    endpointUrl: String,
    apiKey: String,
    model: String,
    onEndpointUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
) {
    // 【実装詳細】: 既存vault/folder欄と同一のOutlinedTextField構造・padding・testTag命名パターンを踏襲する
    // 【テスト対応】: TC-N-01〜TC-N-05, TC-E-01〜TC-E-02, TC-B-01〜TC-B-03 を通すための実装
    OutlinedTextField(
        value = endpointUrl,
        onValueChange = onEndpointUrlChange,
        label = { Text(stringResource(R.string.settings_llm_endpoint_label)) },
        modifier = Modifier.settingsFieldModifier("settings_llm_endpoint_field"),
    )
    // 【apiKeyマスク表示】: 機微情報のためPasswordVisualTransformationで平文非表示にする（REQ-401補完）
    // 【保守性】: 保持値・保存値は平文のまま。マスクは表示層のみの変換であり、保存経路には影響しない 🟡
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text(stringResource(R.string.settings_llm_apikey_label)) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.settingsFieldModifier("settings_llm_apikey_field"),
    )
    OutlinedTextField(
        value = model,
        onValueChange = onModelChange,
        label = { Text(stringResource(R.string.settings_llm_model_label)) },
        modifier = Modifier.settingsFieldModifier("settings_llm_model_field"),
    )
}

/**
 * 【ヘルパー関数】: 設定画面の入力欄（vault/folder/LLM設定）に共通する `Modifier` を組み立てる。
 * 【再利用性】: `fillMaxWidth` + 標準padding + `testTag` の組み合わせを1箇所に集約し、
 *              新しい設定項目を追加する際も同じ見た目・挙動を保証する。
 * 【単一責任】: レイアウト装飾（サイズ・余白・テスト識別子）のみを担当し、入力値やコールバックには関与しない。
 * 🔵 信頼性レベル: 既存vault/folder欄・green-phase.mdの `padding(horizontal = 16.dp, vertical = 4.dp)` を
 *                そのまま集約したものであり、見た目・挙動の変更はない
 *
 * @param testTag Compose UI Testでノードを特定するためのタグ（例: `settings_llm_endpoint_field`）
 * @return `fillMaxWidth` + 標準padding + `testTag` を適用した `Modifier`
 */
private fun Modifier.settingsFieldModifier(testTag: String): Modifier =
    this
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .testTag(testTag)

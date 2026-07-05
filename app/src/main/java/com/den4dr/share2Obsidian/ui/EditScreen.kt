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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.den4dr.share2Obsidian.R
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme

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
 * @param onNavigateToSettings 設定アイコンタップ時に呼ばれるコールバック
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    viewModel: EditScreenViewModel,
    onSend: (SendParams) -> Unit,
    onCancel: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
) {
    val formState by viewModel.formState.collectAsState()

    // バックボタンをキャンセルと同等に扱う（EDGE-102）
    BackHandler { onCancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.label_settings),
                        )
                    }
                },
            )
        },
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
                    onClick = { onSend(viewModel.buildSendParams()) },
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
            // ① vault（保存先 Vault）— REQ-041 表示順の最上部、REQ-043
            OutlinedTextField(
                value = formState.vault,
                onValueChange = { viewModel.updateVault(it) },
                label = { Text(stringResource(R.string.label_vault)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_field"),
                singleLine = true,
            )
            // ② folder（保存先フォルダ）
            OutlinedTextField(
                value = formState.folder,
                onValueChange = { viewModel.updateFolder(it) },
                label = { Text(stringResource(R.string.label_folder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("folder_field"),
                singleLine = true,
            )

            // ③ title（ファイル名）— frontmatter とは別セクション（REQ-042）
            HorizontalDivider()
            Text(
                text = stringResource(R.string.edit_section_filename),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = formState.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text(stringResource(R.string.label_title)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("title_field"),
                singleLine = true,
            )

            // ④ frontmatter（タグ + カスタムフィールド）セクション
            HorizontalDivider()
            Text(
                text = stringResource(R.string.edit_section_frontmatter),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = formState.tagsText,
                onValueChange = { viewModel.updateTagsText(it) },
                label = { Text(stringResource(R.string.label_tags)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            formState.customFields.forEachIndexed { index, field ->
                OutlinedTextField(
                    value = field.value,
                    onValueChange = { viewModel.updateCustomField(index, it) },
                    label = { Text(field.key) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // ⑤ body（本文）— 最下部（REQ-041）
            HorizontalDivider()
            OutlinedTextField(
                value = formState.body,
                onValueChange = { viewModel.updateBody(it) },
                label = { Text(stringResource(R.string.label_body)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("body_field"),
                minLines = 5,
            )
        }
    }
}

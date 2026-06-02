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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.den4dr.share2Obsidian.R
import com.den4dr.share2Obsidian.format.NoteConfig

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
    config: NoteConfig,
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
                    onClick = { onSend(viewModel.buildSendParams(config)) },
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
            OutlinedTextField(
                value = formState.folder,
                onValueChange = { viewModel.updateFolder(it) },
                label = { Text(stringResource(R.string.label_folder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = formState.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text(stringResource(R.string.label_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = formState.tagsText,
                onValueChange = { viewModel.updateTagsText(it) },
                label = { Text(stringResource(R.string.label_tags)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = formState.body,
                onValueChange = { viewModel.updateBody(it) },
                label = { Text(stringResource(R.string.label_body)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
            )
        }
    }
}

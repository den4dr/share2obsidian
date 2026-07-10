package com.den4dr.share2Obsidian.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.den4dr.share2Obsidian.R
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import kotlinx.coroutines.flow.collectLatest

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
    val context = LocalContext.current

    // バックボタンをキャンセルと同等に扱う（EDGE-102）
    BackHandler { onCancel() }

    // rewriteBody() 失敗時のエラー通知（NFR-201）。string resource ID を受け取り日本語Toastで表示する。
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collectLatest { messageResId ->
            Toast.makeText(context, context.getString(messageResId), Toast.LENGTH_LONG).show()
        }
    }

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
            // 【機能概要】: 「タグを提案」ボタン。押下で viewModel.suggestTags() を呼び出し、
            // 元コンテンツ（ProcessedContent）を入力に LLM がタグ候補を生成し既存タグへ追加する
            // 【改善内容】: 「メモを更改」ボタンと共通していたローディング表示ロジックを
            // LoadingButton に抽出し重複を解消（Refactorフェーズ）
            // 【設計方針】: 活性条件は isSuggestingTags のみとし、rewriteBodyEnabled 相当のガードは
            // 意図的に渡さない（EDGE-101: 元コンテンツが空文字でも非活性化しない）
            // 【テスト対応】: TC-11, TC-12, TC-13, BC-11, BC-12（editscreen-suggest-tags-button-testcases.md）
            // 🔵 信頼性レベル: REQ-103, REQ-201, NFR-202, EDGE-101 より
            LoadingButton(
                onClick = { viewModel.suggestTags() },
                enabled = !formState.isSuggestingTags,
                isLoading = formState.isSuggestingTags,
                label = stringResource(R.string.button_suggest_tags),
                buttonTestTag = "suggest_tags_button",
                progressTestTag = "suggest_tags_progress",
            )
            // 【機能概要】: カスタムフィールドの表示・編集欄。valueSource == LLM のフィールドのみ、
            // 末尾に「生成」ボタン（IconButton）を表示し、押下で viewModel.generateCustomFieldValue(index)
            // を呼び出して sourceContent と当該フィールドの llmPrompt を入力に値を生成する（REQ-104）
            // 【設計方針】: 生成中はそのフィールドのボタンのみローディング表示（CircularProgressIndicator）とし、
            // 他フィールドの入力・生成は継続可能にする（他ボタンの enabled には影響させない）
            // 【テスト対応】: TASK-0072.md 完了条件（forEachIndexed ループ内で LLM の場合のみ生成ボタン表示）
            // 🔵 信頼性レベル: TASK-0072.md 実装詳細2・UI/UX要件より
            formState.customFields.forEachIndexed { index, field ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = { viewModel.updateCustomField(index, it) },
                        label = { Text(field.key) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    // 【表示条件】: valueSource == LLM の場合のみ「生成」ボタンを表示する（完了条件）🔵
                    if (field.valueSource == FieldValueSource.LLM) {
                        val isGenerating = formState.generatingFieldIndex == index
                        IconButton(
                            onClick = { viewModel.generateCustomFieldValue(index) },
                            // 【活性条件】: 当該フィールドが生成中でない場合のみ活性化する（二重押下防止）🟡
                            enabled = !isGenerating,
                            modifier = Modifier.testTag("field_generate_button_$index"),
                        ) {
                            if (isGenerating) {
                                // 【生成中表示】: このフィールドのみローディング表示を行う（他フィールドの入力は継続可能）🟡
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .testTag("field_generate_progress_$index"),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.field_generate_button),
                                )
                            }
                        }
                    }
                }
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
            // 「メモを更改」ボタン: bodyLlmPrompt 設定済み（REQ-102）かつ処理中でないときのみ活性
            // 【改善内容】: 「タグを提案」ボタンと共通していたローディング表示ロジックを
            // LoadingButton に抽出し重複を解消（Refactorフェーズ）
            LoadingButton(
                onClick = { viewModel.rewriteBody() },
                enabled = formState.rewriteBodyEnabled && !formState.isRewritingBody,
                isLoading = formState.isRewritingBody,
                label = stringResource(R.string.button_rewrite_body),
                buttonTestTag = "rewrite_body_button",
                progressTestTag = "rewrite_body_progress",
            )
        }
    }
}

/**
 * 【ヘルパー関数】: LLM 呼び出しを起動するボタンの共通UIパターン（通常時ラベル表示／処理中ローディング表示）を提供する
 * 【再利用性】: 「メモを更改」ボタン（rewrite_body_button）と「タグを提案」ボタン（suggest_tags_button）で
 *              同一のローディング表示・非活性化ロジックが必要だったため、両者から共通利用できるよう抽出した
 * 【単一責任】: ボタンの見た目（活性/非活性・ラベル/ローディング切り替え）のみを担当し、
 *              活性条件の計算やクリック時の処理内容は呼び出し元（EditScreen）が決定する
 * 【設計方針】: 活性条件（enabled）と処理中フラグ（isLoading）を別引数として受け取ることで、
 *              「メモを更改」ボタンの `rewriteBodyEnabled && !isRewritingBody` のような
 *              追加ガード条件を持つケースと、「タグを提案」ボタンの `!isSuggestingTags` のみの
 *              ケースの両方に対応できる（EDGE-101: 活性ガードの有無はボタンごとに異なる）
 * 🔵 信頼性レベル: TASK-0065（メモを更改ボタン）・TASK-0069（タグを提案ボタン）の既存実装より
 *
 * @param onClick ボタン押下時に呼び出すコールバック
 * @param enabled ボタンの活性状態（呼び出し元で算出済みの最終的な活性条件）
 * @param isLoading 処理中かどうか。true の場合はラベルの代わりに CircularProgressIndicator を表示する
 * @param label 通常時（isLoading == false）に表示するボタンラベル文字列
 * @param buttonTestTag Compose UI テストでボタンを検出するための testTag
 * @param progressTestTag Compose UI テストでローディングインジケータを検出するための testTag
 */
@Composable
private fun LoadingButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    label: String,
    buttonTestTag: String,
    progressTestTag: String,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(buttonTestTag),
    ) {
        // 【処理内容】: isLoading 中はテキストの代わりに小型の進捗インジケータを表示し、
        // ユーザーに処理中であることを即座にフィードバックする（NFR-202）
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(16.dp)
                    .testTag(progressTestTag),
                strokeWidth = 2.dp,
            )
        } else {
            Text(label)
        }
    }
}

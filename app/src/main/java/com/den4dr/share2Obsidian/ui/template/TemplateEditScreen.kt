package com.den4dr.share2Obsidian.ui.template

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.den4dr.share2Obsidian.R
import com.den4dr.share2Obsidian.domain.model.FieldValueSource
import com.den4dr.share2Obsidian.domain.model.FieldValueType
import com.den4dr.share2Obsidian.domain.model.HtmlMetaKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditScreen(
    templateId: Long?,
    viewModel: TemplateEditViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    BackHandler { onNavigateBack() }

    val uiState by viewModel.uiState.collectAsState()
    var showFieldDialog by remember { mutableStateOf(false) }

    LaunchedEffect(templateId) {
        if (templateId != null) viewModel.loadTemplateById(templateId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    val title = if (templateId == null) {
        stringResource(R.string.template_edit_title_new)
    } else {
        stringResource(R.string.template_edit_title_edit)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = title,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text(stringResource(R.string.template_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))

            // 本文テンプレート入力（{{content}} プレースホルダーを含む任意テキスト・複数行）
            OutlinedTextField(
                value = uiState.body,
                onValueChange = { viewModel.updateBody(it) },
                label = { Text(stringResource(R.string.template_body_label)) },
                supportingText = { Text(stringResource(R.string.template_body_supporting)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("template_body_field"),
                minLines = 4,
            )
            Spacer(Modifier.height(8.dp))

            // 【本文用LLMプロンプト入力欄】: template_body_field と同様の複数行入力欄。空文字は「未設定」を表す
            // 【テスト対応】: TC-N-05（bodyLlmPromptInput_updatesViewModel）、TC-N-06（往復復元）を通すための実装
            // 🔵 信頼性レベル: 要件定義書 2.3・note.md UI/UX要件
            OutlinedTextField(
                value = uiState.bodyLlmPrompt,
                onValueChange = { viewModel.updateBodyLlmPrompt(it) },
                label = { Text(stringResource(R.string.template_body_llm_prompt_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("template_body_llm_prompt_field"),
                minLines = 2,
            )
            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.template_default_label),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = uiState.isDefault,
                    onCheckedChange = { viewModel.updateIsDefault(it) },
                )
            }
            Spacer(Modifier.height(24.dp))

            // ── カスタムフィールドセクション ──────────────────────────
            Text(
                text = stringResource(R.string.template_fields_header),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))

            uiState.fields.forEachIndexed { index, field ->
                FieldListItem(
                    field = field,
                    onDelete = { viewModel.removeField(index) },
                )
                HorizontalDivider()
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showFieldDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.template_add_field_button))
            }
            Spacer(Modifier.height(16.dp))

            // ── 保存ボタン ────────────────────────────────────────────
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.template_save_button))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showFieldDialog) {
        FieldAddDialog(
            onDismiss = { showFieldDialog = false },
            onAdd = { field ->
                viewModel.addField(field)
                showFieldDialog = false
            },
        )
    }
}

@Composable
private fun FieldListItem(
    field: TemplateFieldEditState,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = field.key, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = field.valueSource.name,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.template_delete_button),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldAddDialog(
    onDismiss: () -> Unit,
    onAdd: (TemplateFieldEditState) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var valueSource by remember { mutableStateOf(FieldValueSource.EMPTY) }
    var valueType by remember { mutableStateOf(FieldValueType.STRING) }
    var defaultValue by remember { mutableStateOf("") }
    var metaKey by remember { mutableStateOf(HtmlMetaKey.OG_TITLE) }
    var metaKeyExpanded by remember { mutableStateOf(false) }
    // 【LLMプロンプト入力状態】: valueSource == LLM のときのみ表示・使用する入力欄の一時状態
    // 🔵 信頼性レベル: 要件定義書 2.3・TASK-0071実装詳細7
    var llmPromptInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_add_field_button)) },
        text = {
            Column {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.field_key_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))

                Text(stringResource(R.string.field_value_source_label))
                // 【改善内容】: valueSource選択肢リストをremember化し、key入力等によるダイアログの再コンポーズのたびに
                // リストオブジェクトとラベル解決が再実行されるのを防ぐ
                // 【設計方針】: stringResource()はComposable専用関数のためremember{}の外側で解決し、
                // 結果のPairリストのみをremember対象にする（remember内でComposable呼び出しは不可のため）
                // 【パフォーマンス】: 5件程度の小さなリストではあるが、テキスト入力のたびに走る処理から
                // 不要な再割り当てを排除する意図で分離した
                // 🔵 信頼性レベル: 既存ロジック・並び順は変更せず、生成タイミングのみ最適化（要件への影響なし）
                val fieldSourceFixedLabel = stringResource(R.string.field_source_fixed)
                val fieldSourceHtmlMetaLabel = stringResource(R.string.field_source_html_meta)
                val fieldSourceUrlLabel = stringResource(R.string.field_source_url)
                val fieldSourceEmptyLabel = stringResource(R.string.field_source_empty)
                // 【LLM生成の値取得方法選択肢】: REQ-303に基づき追加 🔵
                val fieldSourceLlmLabel = stringResource(R.string.field_source_llm)
                val fieldSourceOptions = remember(
                    fieldSourceFixedLabel,
                    fieldSourceHtmlMetaLabel,
                    fieldSourceUrlLabel,
                    fieldSourceEmptyLabel,
                    fieldSourceLlmLabel,
                ) {
                    listOf(
                        FieldValueSource.FIXED to fieldSourceFixedLabel,
                        FieldValueSource.HTML_META to fieldSourceHtmlMetaLabel,
                        FieldValueSource.URL to fieldSourceUrlLabel,
                        FieldValueSource.EMPTY to fieldSourceEmptyLabel,
                        FieldValueSource.LLM to fieldSourceLlmLabel,
                    )
                }
                fieldSourceOptions.forEach { (source, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = valueSource == source,
                                onClick = { valueSource = source },
                                role = Role.RadioButton,
                            ),
                    ) {
                        RadioButton(
                            selected = valueSource == source,
                            onClick = null,
                        )
                        Text(label)
                    }
                }

                if (valueSource == FieldValueSource.FIXED) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = defaultValue,
                        onValueChange = { defaultValue = it },
                        label = { Text(stringResource(R.string.field_default_value_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                if (valueSource == FieldValueSource.LLM) {
                    // 【LLMプロンプト入力欄】: valueSource == LLM のときのみ表示する（受け入れ基準 TC-104-01）
                    // 【テスト対応】: TC-N-04（llmSource_showsLlmPromptField）を通すための実装
                    // 🔵 信頼性レベル: 要件定義書 2.3・TASK-0071実装詳細7
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = llmPromptInput,
                        onValueChange = { llmPromptInput = it },
                        label = { Text(stringResource(R.string.field_llm_prompt_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_llm_prompt_field"),
                        minLines = 2,
                    )
                }

                if (valueSource == FieldValueSource.HTML_META) {
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = metaKeyExpanded,
                        onExpandedChange = { metaKeyExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = metaKeyLabel(metaKey),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.field_html_meta_key_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(metaKeyExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = metaKeyExpanded,
                            onDismissRequest = { metaKeyExpanded = false },
                        ) {
                            HtmlMetaKey.entries.forEach { mk ->
                                DropdownMenuItem(
                                    text = { Text(metaKeyLabel(mk)) },
                                    onClick = {
                                        metaKey = mk
                                        metaKeyExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.field_value_type_label))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = valueType == FieldValueType.STRING,
                        onClick = { valueType = FieldValueType.STRING },
                    )
                    Text(stringResource(R.string.field_type_string))
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    RadioButton(
                        selected = valueType == FieldValueType.LIST,
                        onClick = { valueType = FieldValueType.LIST },
                    )
                    Text(stringResource(R.string.field_type_list))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (key.isNotBlank()) {
                        onAdd(
                            TemplateFieldEditState(
                                key = key.trim(),
                                valueSource = valueSource,
                                valueType = valueType,
                                defaultValue = if (valueSource == FieldValueSource.FIXED) defaultValue else "",
                                metaKey = if (valueSource == FieldValueSource.HTML_META) metaKey else null,
                                // 【非LLM選択時のプロンプト破棄】: valueSource != LLM の場合は空文字にする（TC-E-02）
                                // 🟡 信頼性レベル: 要件定義書 4.3 EDGE（実装詳細7の分岐からの妥当推測）
                                llmPrompt = if (valueSource == FieldValueSource.LLM) llmPromptInput else "",
                            )
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.field_add_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        },
    )
}

@Composable
private fun metaKeyLabel(key: HtmlMetaKey): String = when (key) {
    HtmlMetaKey.OG_TITLE -> stringResource(R.string.html_meta_og_title)
    HtmlMetaKey.OG_DESCRIPTION -> stringResource(R.string.html_meta_og_description)
    HtmlMetaKey.URL -> stringResource(R.string.html_meta_url)
    HtmlMetaKey.PUBLISHED_DATE -> stringResource(R.string.html_meta_published_date)
    HtmlMetaKey.MODIFIED_DATE -> stringResource(R.string.html_meta_modified_date)
    HtmlMetaKey.AUTHOR -> stringResource(R.string.html_meta_author)
}

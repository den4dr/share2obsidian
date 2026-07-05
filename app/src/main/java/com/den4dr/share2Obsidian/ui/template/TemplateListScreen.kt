package com.den4dr.share2Obsidian.ui.template

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.den4dr.share2Obsidian.R
import com.den4dr.share2Obsidian.domain.model.Template

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(
    viewModel: TemplateListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (id: Long?) -> Unit,
) {
    BackHandler { onNavigateBack() }

    val uiState by viewModel.uiState.collectAsState()
    var templateToDelete by remember { mutableStateOf<Template?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.template_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.template_list_title),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToEdit(null) }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.template_add_button),
                )
            }
        },
    ) { paddingValues ->
        if (uiState.templates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.template_list_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                items(uiState.templates, key = { it.id }) { template ->
                    TemplateListItem(
                        template = template,
                        onEdit = { onNavigateToEdit(template.id) },
                        onDeleteClick = { templateToDelete = template },
                    )
                }
            }
        }
    }

    templateToDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text(stringResource(R.string.template_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.template_delete_confirm_message, template.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTemplate(template)
                        templateToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.template_delete_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) {
                    Text(stringResource(R.string.button_cancel))
                }
            },
        )
    }
}

@Composable
private fun TemplateListItem(
    template: Template,
    onEdit: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(template.name)
                if (template.isDefault) {
                    Spacer(Modifier.width(8.dp))
                    Badge {
                        Text(stringResource(R.string.template_default_badge))
                    }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.template_delete_button),
                )
            }
        },
        modifier = Modifier.clickable { onEdit() },
    )
}

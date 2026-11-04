package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkflowsScreen(
    onOpenTemplate: (String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val viewModel: WorkflowsViewModel = viewModel(
        factory = WorkflowsViewModel.factory(app.container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.errorRes?.let { stringResource(it) } ?: state.errorDetail

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    LaunchedEffect(state.importedTemplateId) {
        state.importedTemplateId?.let { id ->
            viewModel.consumeImportedTemplate()
            onOpenTemplate(id)
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.templates.isEmpty() -> {
                EmptyWorkflows(
                    onImport = { filePicker.launch(JSON_MIME_TYPES) },
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.templates, key = { it.id }) { template ->
                        WorkflowCard(
                            template = template,
                            onClick = { onOpenTemplate(template.id) },
                        )
                    }
                }
            }
        }

        if (state.importing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }

        if (!state.importing && state.templates.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { filePicker.launch(JSON_MIME_TYPES) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.workflow_import)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }
}

@Composable
private fun EmptyWorkflows(onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(80.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.List,
                contentDescription = null,
                modifier = Modifier.padding(22.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.workflow_empty_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.workflow_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onImport) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.workflow_import))
        }
    }
}

@Composable
private fun WorkflowCard(
    template: WorkflowTemplate,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = template.sourceFileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledValue(
                    label = stringResource(R.string.workflow_nodes),
                    value = template.nodeCount.toString(),
                )
                LabeledValue(
                    label = stringResource(R.string.workflow_reachable),
                    value = template.reachableNodeCount.toString(),
                )
                LabeledValue(
                    label = stringResource(R.string.workflow_selected),
                    value = template.selectedCount.toString(),
                )
            }
            if (template.missingNodeTypes.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.workflow_missing_nodes,
                            template.missingNodeTypes.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                text = formatWorkflowTime(template.importedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private val JSON_MIME_TYPES = arrayOf(
    "application/json",
    "text/json",
    "text/plain",
)

private fun formatWorkflowTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

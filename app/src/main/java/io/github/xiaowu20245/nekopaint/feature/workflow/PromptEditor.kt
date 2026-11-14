package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xiaowu20245.nekopaint.NekoPaintApplication
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.PromptTagGroup
import io.github.xiaowu20245.nekopaint.core.workflow.PromptTagCatalog
import io.github.xiaowu20245.nekopaint.core.workflow.PromptTagExpander
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoTheme
import kotlinx.coroutines.launch

/**
 * 简单模式下的简略提示词卡：两行预览 + 标签组/词条统计，点击进入智能编辑器。
 */
private data class PromptSummaryStats(
    val referencedGroups: List<PromptTagGroup>,
    val partCount: Int,
    val tokenCount: Int,
)

@Composable
internal fun PromptSummaryCard(
    candidate: ParameterCandidate,
    groups: List<PromptTagGroup>,
    isPrimary: Boolean = false,
    canSetPrimary: Boolean = true,
    onTogglePrimary: () -> Unit = {},
    onOpen: () -> Unit,
) {
    val promptStats = remember(candidate.value, groups) {
        val expandedValue = PromptTagExpander.expand(candidate.value, groups)
        PromptSummaryStats(
            referencedGroups = PromptTagExpander.referencedGroups(candidate.value, groups),
            partCount = PromptTagExpander.promptPartCount(expandedValue),
            tokenCount = PromptTagExpander.estimateTokenCount(expandedValue),
        )
    }
    val isNegative = candidate.label.contains("负")
    val referencedGroups = promptStats.referencedGroups
    val partCount = promptStats.partCount
    val tokenCount = promptStats.tokenCount

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = candidate.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isPrimary) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.workflow_primary_prompt),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (canSetPrimary) {
                    IconButton(
                        onClick = onTogglePrimary,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = stringResource(
                                if (isPrimary) {
                                    R.string.workflow_unset_primary_prompt
                                } else {
                                    R.string.workflow_set_primary_prompt
                                },
                            ),
                            tint = if (isPrimary) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = "#${candidate.nodeId}",
                    style = NekoMonoSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .clickable(onClick = onOpen),
                ) {
                    Text(
                        text = stringResource(R.string.prompt_editor_smart_edit),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = candidate.value.ifBlank {
                    stringResource(R.string.prompt_editor_empty)
                },
                style = NekoMonoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isNegative) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (referencedGroups.isNotEmpty() || !isNegative) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (referencedGroups.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.prompt_editor_group_count,
                                    referencedGroups.size,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.prompt_editor_item_count, partCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = pluralStringResource(
                            R.plurals.prompt_editor_token_count,
                            tokenCount,
                            tokenCount,
                        ),
                        style = NekoMonoSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 弹窗式提示词编辑器：本地联想、标签组展开、标签组管理。
 *
 * AI 翻译/润色入口保留在设计中，但当前未接入 AI 服务，按钮以禁用态展示。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PromptEditorDialog(
    candidate: ParameterCandidate,
    groups: List<PromptTagGroup>,
    onValueChange: (String) -> Unit,
    onSaveTagGroup: (String, List<String>) -> Unit,
    onDeleteTagGroup: (String) -> Unit,
    onOpenAiSettings: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NekoPaintApplication
    val assistantViewModel: PromptAssistantViewModel = viewModel(
        factory = PromptAssistantViewModel.factory(app.container),
    )
    val assistantConfig by assistantViewModel.config.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var textFieldValue by remember(candidate.id) {
        mutableStateOf(
            TextFieldValue(
                text = candidate.value,
                selection = TextRange(candidate.value.length),
            ),
        )
    }
    val value = textFieldValue.text
    var suggestionsVisible by remember { mutableStateOf(false) }
    var managerOpen by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupValues by remember { mutableStateOf("") }
    var assistInput by remember { mutableStateOf("") }
    var assistResult by remember { mutableStateOf<String?>(null) }
    var assistFallback by remember { mutableStateOf(false) }
    var assistLoading by remember { mutableStateOf(false) }
    var assistError by remember { mutableStateOf<String?>(null) }
    var assistIsPolish by remember { mutableStateOf(false) }
    val emptyInputMessage = stringResource(R.string.prompt_editor_ai_empty_input)
    val loadingMessage = stringResource(R.string.prompt_editor_ai_loading)

    val parts = remember(value) {
        value.split(',').map { it.trim() }.filter { it.isNotBlank() }
    }
    val query = remember(value) {
        value.substringAfterLast(',', "").trim()
    }
    val groupQuery = query.removePrefix("#").trim()
    val tagSuggestions = remember(query) {
        if (query.startsWith("#")) {
            emptyList()
        } else {
            PromptTagCatalog.search(query, limit = 6)
        }
    }
    val groupSuggestions = remember(groupQuery, groups) {
        if (groupQuery.isBlank()) {
            emptyList()
        } else {
            groups.filter { it.name.contains(groupQuery, ignoreCase = true) }.take(4)
        }
    }
    val tokenCount = PromptTagExpander.estimateTokenCount(
        PromptTagExpander.expand(value, groups),
    )

    fun applyValue(next: String) {
        val normalized = normalizePromptCommas(next)
        textFieldValue = TextFieldValue(
            text = normalized,
            selection = TextRange(normalized.length),
        )
        onValueChange(normalized)
    }

    fun appendSuggestion(insert: String) {
        val lastComma = value.lastIndexOf(',')
        val prefix = if (lastComma >= 0) {
            value.substring(0, lastComma + 1) + " "
        } else {
            ""
        }
        applyValue(prefix + insert + ", ")
        suggestionsVisible = false
    }

    fun appendWhole(insert: String) {
        val base = value.trim().trimEnd(',')
        applyValue(if (base.isBlank()) insert else "$base, $insert")
        suggestionsVisible = false
    }

    fun handleAssistResult(result: PromptAssistResult, polish: Boolean) {
        when (result) {
            is PromptAssistResult.Success -> {
                assistResult = result.text
                assistIsPolish = polish
                assistFallback = false
                assistError = null
            }
            PromptAssistResult.NotConfigured -> {
                assistResult = null
                assistFallback = true
                assistIsPolish = false
            }
            is PromptAssistResult.Failed -> {
                assistResult = null
                assistFallback = false
                assistIsPolish = false
                assistError = result.message
            }
        }
    }

    fun requestTranslate() {
        val input = assistInput.trim()
        if (input.isBlank()) {
            assistError = emptyInputMessage
            return
        }
        assistError = null
        val local = PromptTagCatalog.translateChinese(input)
        if (local != null) {
            assistResult = local
            assistFallback = false
            assistIsPolish = false
            return
        }
        if (!assistantConfig.isConfigured) {
            assistResult = null
            assistFallback = true
            assistIsPolish = false
            return
        }
        scope.launch {
            assistLoading = true
            val result = assistantViewModel.translate(input)
            assistLoading = false
            handleAssistResult(result, polish = false)
        }
    }

    fun requestPolish() {
        val input = assistInput.trim().ifBlank { value.trim() }
        if (input.isBlank()) {
            assistError = emptyInputMessage
            return
        }
        assistError = null
        if (!assistantConfig.isConfigured) {
            assistResult = null
            assistFallback = true
            assistIsPolish = false
            return
        }
        scope.launch {
            assistLoading = true
            val result = assistantViewModel.polish(input)
            assistLoading = false
            handleAssistResult(result, polish = true)
        }
    }

    fun finish() {
        val cleaned = normalizePromptCommas(value).trim().trimEnd(',').trim()
        if (cleaned != candidate.value) {
            onValueChange(cleaned)
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = { finish() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .heightIn(max = 760.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = candidate.label,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Text(
                                text = "#${candidate.nodeId} · ${candidate.classType}",
                                style = NekoMonoSmall.copy(fontSize = 10.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { managerOpen = !managerOpen },
                    ) {
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(
                                    if (managerOpen) {
                                        R.string.prompt_editor_back_to_edit
                                    } else {
                                        R.string.prompt_editor_groups
                                    },
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { finish() },
                    ) {
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.prompt_editor_done),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }

                if (!managerOpen) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            BasicTextField(
                                value = textFieldValue,
                                onValueChange = { newValue ->
                                    val normalized = normalizePromptTextFieldValue(newValue)
                                    textFieldValue = normalized
                                    suggestionsVisible = true
                                    onValueChange(normalized.text)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .heightIn(min = 52.dp),
                                textStyle = NekoMonoSmall.copy(
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                maxLines = 6,
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.TopStart) {
                                        if (value.isBlank()) {
                                            Text(
                                                text = stringResource(R.string.prompt_editor_empty),
                                                style = NekoMonoSmall.copy(fontSize = 12.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ) {
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.prompt_editor_token_count,
                                            tokenCount,
                                            tokenCount,
                                        ),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        modifier = Modifier.padding(
                                            horizontal = 6.dp,
                                            vertical = 2.dp,
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    if (suggestionsVisible && query.isNotBlank() &&
                        (groupSuggestions.isNotEmpty() || tagSuggestions.isNotEmpty())
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            ),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.prompt_editor_suggestion_title,
                                            query,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .clickable { suggestionsVisible = false },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "×",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                var suggestionIndex = 0
                                groupSuggestions.forEach { group ->
                                    if (suggestionIndex > 0) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                        )
                                    }
                                    SuggestionRow(
                                        primary = "#${group.name}",
                                        secondary = group.tags.joinToString(", "),
                                        highlighted = suggestionIndex == 0,
                                        onClick = { appendSuggestion("#${group.name}") },
                                    )
                                    suggestionIndex++
                                }
                                tagSuggestions.forEach { suggestion ->
                                    if (suggestionIndex > 0) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                        )
                                    }
                                    SuggestionRow(
                                        primary = suggestion.tag,
                                        secondary = suggestion.zh,
                                        highlighted = suggestionIndex == 0,
                                        onClick = { appendSuggestion(suggestion.tag) },
                                    )
                                    suggestionIndex++
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (assistInput.isBlank()) {
                                            Text(
                                                text = stringResource(
                                                    R.string.prompt_editor_translate_placeholder,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        BasicTextField(
                                            value = assistInput,
                                            onValueChange = {
                                                assistInput = it
                                                assistResult = null
                                                assistFallback = false
                                                assistError = null
                                                assistIsPolish = false
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                            ),
                                        )
                                    }
                                }
                                CompactActionButton(
                                    label = stringResource(R.string.prompt_editor_translate),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    enabled = !assistLoading,
                                    onClick = { requestTranslate() },
                                )
                                CompactActionButton(
                                    label = stringResource(R.string.prompt_editor_polish),
                                    icon = NekoIcons.Sparkles,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    enabled = !assistLoading,
                                    onClick = { requestPolish() },
                                )
                            }
                            if (assistLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        text = loadingMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            assistResult?.let { result ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = if (assistIsPolish) {
                                                stringResource(
                                                    R.string.prompt_editor_ai_result,
                                                    result,
                                                )
                                            } else {
                                                stringResource(
                                                    R.string.prompt_editor_translate_result,
                                                    result,
                                                )
                                            },
                                            style = NekoMonoSmall.copy(fontSize = 11.sp),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (assistIsPolish) {
                                            CompactActionButton(
                                                label = stringResource(
                                                    R.string.prompt_editor_replace_all,
                                                ),
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                                height = 30.dp,
                                                onClick = {
                                                    applyValue(result)
                                                    assistResult = null
                                                    assistIsPolish = false
                                                },
                                            )
                                            CompactActionButton(
                                                label = stringResource(
                                                    R.string.prompt_editor_append,
                                                ),
                                                icon = Icons.Outlined.Add,
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                height = 30.dp,
                                                onClick = {
                                                    appendWhole(result)
                                                    assistResult = null
                                                    assistIsPolish = false
                                                },
                                            )
                                        } else {
                                            CompactActionButton(
                                                label = stringResource(R.string.prompt_editor_add),
                                                icon = Icons.Outlined.Add,
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                                height = 30.dp,
                                                onClick = {
                                                    appendSuggestion(result)
                                                    assistResult = null
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            assistError?.let { message ->
                                Text(
                                    text = stringResource(
                                        R.string.prompt_editor_ai_error,
                                        message,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (assistFallback) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = NekoTheme.colors.warningContainer,
                                    contentColor = NekoTheme.colors.onWarningContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.prompt_editor_ai_fallback),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        CompactActionButton(
                                            label = stringResource(
                                                R.string.prompt_editor_ai_config,
                                            ),
                                            containerColor = NekoTheme.colors.warningContainer,
                                            contentColor = NekoTheme.colors.onWarningContainer,
                                            height = 28.dp,
                                            onClick = {
                                                finish()
                                                onOpenAiSettings()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.prompt_editor_selected_title),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = stringResource(R.string.prompt_editor_expand_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                parts.forEachIndexed { index, part ->
                                    val group = groups.firstOrNull {
                                        part == "#${it.name}"
                                    }
                                    PromptPartChip(
                                        part = part,
                                        group = group,
                                        onRemove = {
                                            val next = parts.toMutableList().apply {
                                                removeAt(index)
                                            }
                                            applyValue(next.joinToString(", "))
                                            suggestionsVisible = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = stringResource(R.string.prompt_editor_create_group),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            OutlinedTextField(
                                value = newGroupName,
                                onValueChange = { newGroupName = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = {
                                    Text(stringResource(R.string.prompt_editor_group_name_hint))
                                },
                            )
                            OutlinedTextField(
                                value = newGroupValues,
                                onValueChange = { newGroupValues = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = {
                                    Text(stringResource(R.string.prompt_editor_group_values_hint))
                                },
                            )
                            Button(
                                onClick = {
                                    onSaveTagGroup(
                                        newGroupName,
                                        newGroupValues.split(',').map { it.trim() }
                                            .filter { it.isNotBlank() },
                                    )
                                    newGroupName = ""
                                    newGroupValues = ""
                                },
                                enabled = newGroupName.isNotBlank() &&
                                    newGroupValues.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.prompt_editor_group_save))
                            }
                        }
                    }

                    if (groups.isEmpty()) {
                        Text(
                            text = stringResource(R.string.prompt_editor_group_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        groups.forEach { group ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "#${group.name}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = group.tags.joinToString(", ").ifBlank {
                                                stringResource(R.string.prompt_editor_group_tags_empty)
                                            },
                                            style = NekoMonoSmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(
                                        onClick = { onDeleteTagGroup(group.id) },
                                        contentPadding = PaddingValues(
                                            horizontal = 6.dp,
                                            vertical = 2.dp,
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(
                                                R.string.prompt_editor_group_delete,
                                            ),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    primary: String,
    secondary: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlighted) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = primary,
            style = NekoMonoSmall,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = secondary,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.primary
            },
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.8f),
        )
    }
}

@Composable
private fun CompactActionButton(
    label: String,
    icon: ImageVector? = null,
    containerColor: Color,
    contentColor: Color,
    height: Dp = 42.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        contentColor = contentColor.copy(alpha = if (enabled) 1f else 0.5f),
        modifier = Modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .height(height)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.let { imageVector ->
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun PromptPartChip(
    part: String,
    group: PromptTagGroup?,
    onRemove: () -> Unit,
) {
    val isGroup = group != null
    val zh = if (isGroup) null else PromptTagCatalog.zhFor(part)
    val matched = zh != null
    val backgroundColor = when {
        isGroup -> MaterialTheme.colorScheme.primary
        matched -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val contentColor = when {
        isGroup -> MaterialTheme.colorScheme.onPrimary
        matched -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = part,
                    style = NekoMonoSmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onRemove)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "×",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.75f),
                    )
                }
            }
            Text(
                text = if (isGroup) {
                    stringResource(R.string.prompt_editor_tag_group_count, group.tags.size)
                } else {
                    zh ?: stringResource(R.string.prompt_editor_custom_tag)
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = when {
                    isGroup -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    matched -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

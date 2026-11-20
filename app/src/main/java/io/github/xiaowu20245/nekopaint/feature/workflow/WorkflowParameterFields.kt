package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoTheme
import kotlin.random.Random

@Composable
internal fun ParameterField(
    candidate: ParameterCandidate,
    error: FieldError?,
    onValueChange: (String) -> Unit,
    uploading: Boolean,
    onPickImage: () -> Unit,
    isMaskSlot: Boolean,
    onDrawMask: () -> Unit,
) {
    val errorText = fieldErrorText(error)
    when (candidate.type) {
        ParameterType.BOOLEAN -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        if (candidate.value.equals("true", ignoreCase = true)) {
                            R.string.workflow_on
                        } else {
                            R.string.workflow_off
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = candidate.value.equals("true", ignoreCase = true),
                    onCheckedChange = { onValueChange(if (it) "true" else "false") },
                )
            }
        }

        ParameterType.ENUM -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val forceTextInput = candidate.constraints.optionsSource == "UNSUPPORTED" ||
                    candidate.constraints.optionsSource == "ERROR"
                if (candidate.constraints.options.isEmpty() || forceTextInput) {
                    OutlinedTextField(
                        value = candidate.value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = errorText != null,
                        supportingText = errorText?.let { { Text(it) } },
                    )
                } else {
                    EnumField(
                        value = candidate.value,
                        options = candidate.constraints.options,
                        errorText = errorText,
                        onValueChange = onValueChange,
                    )
                }
                candidate.constraints.optionsSource?.let { source ->
                    val unsupported = source == "UNSUPPORTED"
                    val failed = source == "ERROR"
                    Text(
                        text = stringResource(
                            when (source) {
                                "NETWORK" -> R.string.workflow_model_source_network
                                "CACHE" -> R.string.workflow_model_source_cache
                                "UNSUPPORTED" -> R.string.workflow_model_source_unsupported
                                else -> R.string.workflow_model_source_error
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            unsupported || failed -> NekoTheme.colors.warning
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        ParameterType.INT,
        ParameterType.FLOAT,
        -> {
            val presets = numericPresets(candidate)
            val isSeed = candidate.inputName in WorkflowTemplate.SEED_INPUT_NAMES
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = candidate.value,
                        onValueChange = onValueChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = NekoMonoSmall.copy(fontSize = 13.sp),
                        isError = errorText != null,
                        supportingText = errorText?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (candidate.type == ParameterType.INT) {
                                KeyboardType.Number
                            } else {
                                KeyboardType.Decimal
                            },
                        ),
                        trailingIcon = if (presets.isNotEmpty()) {
                            {
                                NumericPresetDropdown(
                                    presets = presets,
                                    onSelect = onValueChange,
                                )
                            }
                        } else {
                            null
                        },
                    )
                    if (isSeed) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onValueChange(Random.nextLong(1L, 999_999_999_999_999L).toString())
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = NekoIcons.Dice,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = stringResource(R.string.workflow_random),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        ParameterType.IMAGE -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = candidate.value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = NekoMonoSmall.copy(fontSize = 12.sp),
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onPickImage,
                        enabled = !uploading,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(6.dp))
                        }
                        Text(
                            text = stringResource(
                                if (uploading) {
                                    R.string.workflow_image_uploading
                                } else {
                                    R.string.workflow_image_pick_short
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (isMaskSlot) {
                        Button(
                            onClick = onDrawMask,
                            enabled = !uploading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = NekoIcons.BrushMask,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.workflow_mask_draw_short),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }

        ParameterType.MULTILINE_TEXT,
        ParameterType.TEXT,
        ParameterType.UNKNOWN,
        -> OutlinedTextField(
            value = candidate.value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = if (candidate.type == ParameterType.MULTILINE_TEXT) 3 else 1,
            maxLines = if (candidate.type == ParameterType.MULTILINE_TEXT) 8 else 3,
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumField(
    value: String,
    options: List<String>,
    errorText: String?,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun fieldErrorText(error: FieldError?): String? = when (error) {
    null -> null
    is FieldError.Local -> stringResource(error.messageRes)
    is FieldError.Server -> error.message
}

@Composable
internal fun groupLabel(group: ParameterGroup): String = when (group) {
    ParameterGroup.PROMPT -> stringResource(R.string.workflow_group_prompt)
    ParameterGroup.IMAGE -> stringResource(R.string.workflow_group_image)
    ParameterGroup.SAMPLING -> stringResource(R.string.workflow_group_sampling)
    ParameterGroup.MODEL -> stringResource(R.string.workflow_group_model)
    ParameterGroup.ADVANCED -> stringResource(R.string.workflow_group_advanced)
    ParameterGroup.OTHER -> stringResource(R.string.workflow_group_other)
}

internal fun numericPresets(candidate: ParameterCandidate): List<String> = when {
    candidate.inputName == "steps" -> listOf("10", "20", "30", "40")
    candidate.inputName == "cfg" -> listOf("1", "3", "5", "7", "8", "10")
    candidate.inputName == "denoise" -> listOf("0.2", "0.4", "0.6", "0.8", "1.0")
    candidate.inputName == "batch_size" -> listOf("1", "2", "4")
    candidate.inputName == "scale_by" -> listOf("1.0", "1.25", "1.5", "2.0")
    candidate.label.contains("宽度") -> listOf("512", "768", "1024", "1280", "1536", "1920")
    candidate.label.contains("高度") -> listOf("512", "768", "1024", "1280", "1536", "1920")
    else -> emptyList()
}

@Composable
internal fun NumericPresetDropdown(
    presets: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = stringResource(R.string.workflow_numeric_presets),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = preset,
                            style = NekoMonoSmall,
                        )
                    },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

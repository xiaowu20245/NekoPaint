package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoMonoSmall
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoRadius

/**
 * 简单/高级模式共用的参数卡片外壳：统一卡片头、值区、只读与错误状态。
 *
 * 具体值编辑器由 [content] 提供，只读参数直接显示 [readOnlyValue]。
 */
@Composable
internal fun ParameterControlShell(
    label: String,
    nodeLabel: String,
    errorText: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    readOnlyValue: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(NekoRadius.md),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (errorText != null) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            leading?.invoke()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = if (compact) {
                            MaterialTheme.typography.labelSmall
                        } else {
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        color = if (compact) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                    if (compact) {
                        Text(
                            text = nodeLabel,
                            style = NekoMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.75f,
                            ),
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Text(
                                text = nodeLabel,
                                style = NekoMonoSmall.copy(
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (readOnlyValue != null) {
                    Text(
                        text = readOnlyValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    content()
                }
            }
        }
    }
}

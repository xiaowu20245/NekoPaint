package io.github.xiaowu20245.nekopaint.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoPaintTheme
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoTheme
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoIconSize
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoSpacing

enum class NekoStatusTone {
    SUCCESS,
    WARNING,
    DANGER,
    INFO,
    NEUTRAL,
}

/**
 * 统一状态徽章：颜色 + 图标双重编码，避免只靠红绿区分状态。
 */
@Composable
fun NekoStatusBadge(
    text: String,
    tone: NekoStatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = defaultIcon(tone),
) {
    val colors = NekoTheme.colors
    val containerColor = when (tone) {
        NekoStatusTone.SUCCESS -> colors.successContainer
        NekoStatusTone.WARNING -> colors.warningContainer
        NekoStatusTone.DANGER -> colors.dangerContainer
        NekoStatusTone.INFO -> colors.infoContainer
        NekoStatusTone.NEUTRAL -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (tone) {
        NekoStatusTone.SUCCESS -> colors.onSuccessContainer
        NekoStatusTone.WARNING -> colors.onWarningContainer
        NekoStatusTone.DANGER -> colors.onDangerContainer
        NekoStatusTone.INFO -> colors.onInfoContainer
        NekoStatusTone.NEUTRAL -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = modifier.height(24.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NekoSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NekoSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(NekoIconSize.xs),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun defaultIcon(tone: NekoStatusTone): ImageVector = when (tone) {
    NekoStatusTone.SUCCESS -> Icons.Outlined.CheckCircle
    NekoStatusTone.WARNING -> Icons.Outlined.Warning
    NekoStatusTone.DANGER -> Icons.Outlined.Close
    NekoStatusTone.INFO -> Icons.Outlined.Info
    NekoStatusTone.NEUTRAL -> Icons.Outlined.Info
}

@Preview(showBackground = true)
@Composable
private fun NekoStatusBadgePreview() {
    NekoPaintTheme(dynamicColor = false) {
        Row(
            modifier = Modifier.padding(NekoSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(NekoSpacing.sm),
        ) {
            NekoStatusBadge("成功", NekoStatusTone.SUCCESS)
            NekoStatusBadge("重连中", NekoStatusTone.WARNING)
            NekoStatusBadge("失败", NekoStatusTone.DANGER)
            NekoStatusBadge("当前", NekoStatusTone.NEUTRAL)
        }
    }
}

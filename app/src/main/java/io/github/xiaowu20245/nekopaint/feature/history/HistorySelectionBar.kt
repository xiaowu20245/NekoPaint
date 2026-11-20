package io.github.xiaowu20245.nekopaint.feature.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.graphics.Color
import io.github.xiaowu20245.nekopaint.core.ui.NekoTopBarIconAction
import io.github.xiaowu20245.nekopaint.core.ui.icon.NekoIcons

/**
 * 历史多选模式下的顶栏操作集合。UI 只负责把结果交给 [NekoTopBarIconAction]。
 */
internal fun historySelectionActions(
    allSelected: Boolean,
    selectedIds: Set<String>,
    exporting: Boolean,
    deleting: Boolean,
    selectAllDescription: String,
    exportDescription: String,
    deleteDescription: String,
    deleteTint: Color,
    doneLabel: String,
    doneDescription: String,
    onToggleAll: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
): List<NekoTopBarIconAction> = buildList {
    add(
        NekoTopBarIconAction(
            icon = if (allSelected) NekoIcons.Deselect else NekoIcons.SelectAll,
            contentDescription = selectAllDescription,
            onClick = onToggleAll,
        ),
    )
    add(
        NekoTopBarIconAction(
            icon = NekoIcons.Download,
            contentDescription = exportDescription,
            enabled = selectedIds.isNotEmpty() && !exporting,
            onClick = onExport,
        ),
    )
    add(
        NekoTopBarIconAction(
            icon = Icons.Outlined.Delete,
            contentDescription = deleteDescription,
            tint = deleteTint,
            enabled = selectedIds.isNotEmpty() && !deleting,
            onClick = onDelete,
        ),
    )
    add(
        NekoTopBarIconAction(
            label = doneLabel,
            contentDescription = doneDescription,
            onClick = onDone,
        ),
    )
}

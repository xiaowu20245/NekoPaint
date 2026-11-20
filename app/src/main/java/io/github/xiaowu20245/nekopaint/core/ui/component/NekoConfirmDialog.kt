package io.github.xiaowu20245.nekopaint.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.xiaowu20245.nekopaint.R
import androidx.compose.ui.res.stringResource

/**
 * 统一确认弹窗。破坏性操作确认按钮使用 error 语义色，取消在左、确认在右。
 *
 * 可选 [secondaryLabel]/[onSecondary] 用于“放弃修改”等第三种选择；
 * 第三个动作使用 error 语义色，因为它同样会丢弃用户输入。
 */
@Composable
fun NekoConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        dismissButton = {
            Row {
                if (secondaryLabel != null && onSecondary != null) {
                    TextButton(
                        onClick = onSecondary,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(secondaryLabel)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
    )
}

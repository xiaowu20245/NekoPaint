package io.github.xiaowu20245.nekopaint.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.xiaowu20245.nekopaint.R
import io.github.xiaowu20245.nekopaint.core.network.ComfyConnectionState

enum class NekoConnectionStatus {
    UNCONFIGURED,
    OFFLINE,
    CONNECTING,
    ONLINE,
    RECONNECTING,
    ERROR,
}

enum class NekoTaskStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    INTERRUPTED,
    UNKNOWN,
    SUBMITTING,
}

enum class NekoTaskSource {
    LOCAL,
    OTHER,
    UNKNOWN,
}

enum class NekoServerStatus {
    CHECKING,
    ONLINE,
    INVALID_CONFIG,
    OFFLINE,
    NOT_CHECKED,
}

/**
 * 状态文案/颜色的唯一映射入口。
 *
 * 连接枚举保持 5 态；“错误”是 UI 状态，由最近一次刷新是否失败决定，
 * 不进入 [ComfyConnectionState]，避免出现永远无法到达的枚举值。
 */
object NekoStatusMapper {

    fun connection(
        state: ComfyConnectionState,
        hasError: Boolean,
    ): NekoConnectionStatus = when {
        state == ComfyConnectionState.Unconfigured -> NekoConnectionStatus.UNCONFIGURED
        hasError && state != ComfyConnectionState.Online -> NekoConnectionStatus.ERROR
        state == ComfyConnectionState.Online -> NekoConnectionStatus.ONLINE
        state == ComfyConnectionState.Connecting -> NekoConnectionStatus.CONNECTING
        state == ComfyConnectionState.Reconnecting -> NekoConnectionStatus.RECONNECTING
        else -> NekoConnectionStatus.OFFLINE
    }

    fun task(status: String?): NekoTaskStatus = when (status?.lowercase()) {
        "queued" -> NekoTaskStatus.QUEUED
        "running" -> NekoTaskStatus.RUNNING
        "success" -> NekoTaskStatus.SUCCESS
        "failed" -> NekoTaskStatus.FAILED
        "interrupted" -> NekoTaskStatus.INTERRUPTED
        "submitting" -> NekoTaskStatus.SUBMITTING
        else -> NekoTaskStatus.UNKNOWN
    }

    fun source(isOwn: Boolean?): NekoTaskSource = when (isOwn) {
        true -> NekoTaskSource.LOCAL
        false -> NekoTaskSource.OTHER
        null -> NekoTaskSource.UNKNOWN
    }

    fun server(
        isTesting: Boolean,
        isOnline: Boolean,
        hasInvalidConfig: Boolean,
        hasError: Boolean,
    ): NekoServerStatus = when {
        isTesting -> NekoServerStatus.CHECKING
        isOnline -> NekoServerStatus.ONLINE
        hasInvalidConfig -> NekoServerStatus.INVALID_CONFIG
        hasError -> NekoServerStatus.OFFLINE
        else -> NekoServerStatus.NOT_CHECKED
    }
}

@Composable
fun NekoConnectionStatus.label(): String = stringResource(
    when (this) {
        NekoConnectionStatus.UNCONFIGURED -> R.string.connection_unconfigured
        NekoConnectionStatus.OFFLINE -> R.string.connection_offline
        NekoConnectionStatus.CONNECTING -> R.string.connection_connecting
        NekoConnectionStatus.ONLINE -> R.string.connection_online
        NekoConnectionStatus.RECONNECTING -> R.string.connection_reconnecting
        NekoConnectionStatus.ERROR -> R.string.connection_error
    },
)

fun NekoConnectionStatus.tone(): NekoStatusTone = when (this) {
    NekoConnectionStatus.UNCONFIGURED -> NekoStatusTone.WARNING
    NekoConnectionStatus.OFFLINE -> NekoStatusTone.DANGER
    NekoConnectionStatus.CONNECTING -> NekoStatusTone.INFO
    NekoConnectionStatus.ONLINE -> NekoStatusTone.SUCCESS
    NekoConnectionStatus.RECONNECTING -> NekoStatusTone.WARNING
    NekoConnectionStatus.ERROR -> NekoStatusTone.DANGER
}

fun NekoConnectionStatus.icon(): ImageVector = when (this) {
    NekoConnectionStatus.UNCONFIGURED -> Icons.Outlined.Warning
    NekoConnectionStatus.OFFLINE -> Icons.Outlined.Close
    NekoConnectionStatus.CONNECTING -> Icons.Outlined.Info
    NekoConnectionStatus.ONLINE -> Icons.Outlined.CheckCircle
    NekoConnectionStatus.RECONNECTING -> Icons.Outlined.Warning
    NekoConnectionStatus.ERROR -> Icons.Outlined.Close
}

@Composable
fun NekoTaskStatus.label(): String = stringResource(
    when (this) {
        NekoTaskStatus.QUEUED -> R.string.task_status_queued
        NekoTaskStatus.RUNNING -> R.string.task_status_running
        NekoTaskStatus.SUCCESS -> R.string.history_status_success
        NekoTaskStatus.FAILED -> R.string.history_status_failed
        NekoTaskStatus.INTERRUPTED -> R.string.history_status_interrupted
        NekoTaskStatus.UNKNOWN -> R.string.history_status_unknown
        NekoTaskStatus.SUBMITTING -> R.string.task_status_submitting
    },
)

fun NekoTaskStatus.tone(): NekoStatusTone = when (this) {
    NekoTaskStatus.QUEUED -> NekoStatusTone.NEUTRAL
    NekoTaskStatus.RUNNING -> NekoStatusTone.INFO
    NekoTaskStatus.SUCCESS -> NekoStatusTone.SUCCESS
    NekoTaskStatus.FAILED -> NekoStatusTone.DANGER
    NekoTaskStatus.INTERRUPTED -> NekoStatusTone.WARNING
    NekoTaskStatus.UNKNOWN -> NekoStatusTone.NEUTRAL
    NekoTaskStatus.SUBMITTING -> NekoStatusTone.INFO
}

fun NekoTaskStatus.icon(): ImageVector = when (this) {
    NekoTaskStatus.QUEUED -> Icons.Outlined.Info
    NekoTaskStatus.RUNNING -> Icons.Outlined.Info
    NekoTaskStatus.SUCCESS -> Icons.Outlined.CheckCircle
    NekoTaskStatus.FAILED -> Icons.Outlined.Close
    NekoTaskStatus.INTERRUPTED -> Icons.Outlined.Warning
    NekoTaskStatus.UNKNOWN -> Icons.Outlined.Info
    NekoTaskStatus.SUBMITTING -> Icons.Outlined.Info
}

@Composable
fun NekoServerStatus.label(): String = stringResource(
    when (this) {
        NekoServerStatus.CHECKING -> R.string.server_checking
        NekoServerStatus.ONLINE -> R.string.server_online
        NekoServerStatus.INVALID_CONFIG -> R.string.server_config_invalid_status
        NekoServerStatus.OFFLINE -> R.string.server_offline
        NekoServerStatus.NOT_CHECKED -> R.string.server_not_checked
    },
)

fun NekoServerStatus.tone(): NekoStatusTone = when (this) {
    NekoServerStatus.CHECKING -> NekoStatusTone.WARNING
    NekoServerStatus.ONLINE -> NekoStatusTone.SUCCESS
    NekoServerStatus.INVALID_CONFIG,
    NekoServerStatus.OFFLINE,
    -> NekoStatusTone.DANGER
    NekoServerStatus.NOT_CHECKED -> NekoStatusTone.WARNING
}

fun NekoServerStatus.icon(): ImageVector = when (this) {
    NekoServerStatus.CHECKING -> Icons.Outlined.Refresh
    NekoServerStatus.ONLINE -> Icons.Outlined.CheckCircle
    NekoServerStatus.INVALID_CONFIG -> Icons.Outlined.Warning
    NekoServerStatus.OFFLINE -> Icons.Outlined.Close
    NekoServerStatus.NOT_CHECKED -> Icons.Outlined.Info
}

@Composable
fun NekoTaskSource.label(): String = stringResource(
    when (this) {
        NekoTaskSource.LOCAL -> R.string.queue_task_own
        NekoTaskSource.OTHER -> R.string.queue_task_other
        NekoTaskSource.UNKNOWN -> R.string.task_source_unknown
    },
)

fun NekoTaskSource.tone(): NekoStatusTone = when (this) {
    NekoTaskSource.LOCAL -> NekoStatusTone.INFO
    NekoTaskSource.OTHER -> NekoStatusTone.NEUTRAL
    NekoTaskSource.UNKNOWN -> NekoStatusTone.NEUTRAL
}

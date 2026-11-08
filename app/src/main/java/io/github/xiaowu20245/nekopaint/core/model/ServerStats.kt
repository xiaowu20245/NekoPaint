package io.github.xiaowu20245.nekopaint.core.model

data class ServerStats(
    val comfyUiVersion: String?,
    val os: String?,
    val pythonVersion: String?,
    val pytorchVersion: String?,
    val ramTotal: Long?,
    val ramFree: Long?,
    val devices: List<GpuInfo>,
    val argv: List<String> = emptyList(),
)

data class GpuInfo(
    val name: String?,
    val vramTotal: Long,
    val vramFree: Long,
) {
    val vramUsed: Long
        get() = (vramTotal - vramFree).coerceAtLeast(0L)

    val vramUsedFraction: Float
        get() = if (vramTotal <= 0L) 0f else (vramUsed.toDouble() / vramTotal).toFloat().coerceIn(0f, 1f)
}

data class ConnectionSnapshot(
    val stats: ServerStats,
    val latencyMs: Long,
    val checkedAt: Long,
    val runningCount: Int = 0,
    val pendingCount: Int = 0,
)

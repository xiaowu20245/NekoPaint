package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.network.ManagerQueueStatusDto

/**
 * ComfyUI-Manager 安装队列判定。
 *
 * 基线忙碌时不得提交任何 install 请求；startQueue 后必须观察到
 * “新鲜 active 且 total >= queued” 才能认为本次安装已启动，否则不自动重启。
 */
object NodeInstallQueuePolicy {

    enum class Baseline {
        READY,
        BUSY,
        UNAVAILABLE,
    }

    fun baseline(status: ManagerQueueStatusDto?): Baseline = when {
        status == null -> Baseline.UNAVAILABLE
        isActive(status) -> Baseline.BUSY
        else -> Baseline.READY
    }

    fun isActive(status: ManagerQueueStatusDto): Boolean =
        status.isProcessing || status.inProgressCount > 0 || status.totalCount > 0

    fun isIdle(status: ManagerQueueStatusDto): Boolean = !isActive(status)

    /** 队列已启动且覆盖本次提交数量，可作为“本次安装正在执行”的证据。 */
    fun activeCoversQueued(
        status: ManagerQueueStatusDto,
        queuedCount: Int,
    ): Boolean = isActive(status) && status.totalCount >= queuedCount
}

package io.github.xiaowu20245.nekopaint.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class NodeInstallOutcomeTest {

    @Test
    fun `queue start failure asks for manual install`() {
        val summary = NodeInstallOutcome(
            queuedCount = 2,
            queueStartFailed = true,
        ).summary()

        assertTrue(summary.contains("安装队列启动失败"))
        assertTrue(summary.contains("手动"))
    }

    @Test
    fun `queue timeout does not claim success`() {
        val summary = NodeInstallOutcome(
            queuedCount = 1,
            queueTimedOut = true,
        ).summary()

        assertTrue(summary.contains("仍在主机后台进行"))
    }

    @Test
    fun `reports remaining missing nodes after restart`() {
        val summary = NodeInstallOutcome(
            queuedCount = 1,
            restartRequested = true,
            serverReconnected = true,
            remainingMissingNodeTypes = listOf("NodeA", "NodeB"),
        ).summary()

        assertTrue(summary.contains("仍有 2 个节点缺失"))
        assertTrue(summary.contains("NodeA"))
    }

    @Test
    fun `reports complete after restart`() {
        val summary = NodeInstallOutcome(
            queuedCount = 1,
            restartRequested = true,
            serverReconnected = true,
        ).summary()

        assertTrue(summary.contains("缺失节点已补齐"))
    }

    @Test
    fun `reports reboot rejected`() {
        val summary = NodeInstallOutcome(
            queuedCount = 1,
            restartRequested = true,
            restartRejected = true,
        ).summary()

        assertTrue(summary.contains("拒绝了重启请求"))
    }

    @Test
    fun `reports restart needed when restart disabled`() {
        val summary = NodeInstallOutcome(
            queuedCount = 1,
            restartRequested = false,
        ).summary()

        assertTrue(summary.contains("重启 ComfyUI 后生效"))
    }
}

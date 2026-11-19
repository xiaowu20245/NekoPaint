package io.github.xiaowu20245.nekopaint.feature.chat

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTaskActionSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun oldProposalWithoutSourceDefaultsToUnknown() {
        val original = ChatActionProposal(
            id = "proposal-1",
            action = ChatTaskAction.RETRY,
            promptId = "task-1",
            title = "任务 A",
            status = "failed",
            source = ChatTaskSource.LOCAL,
            batchTargets = listOf(
                ChatBatchTarget(
                    promptId = "task-2",
                    title = "任务 B",
                    status = "queued",
                    source = ChatTaskSource.OTHER,
                ),
            ),
        )
        val encoded = json.encodeToString(ChatActionProposal.serializer(), original)
        // 模拟旧版本 JSON：没有 source 字段。
        val oldJson = encoded.replace(Regex(",\"source\":\"[A-Z_]+\""), "")

        val decoded = json.decodeFromString(ChatActionProposal.serializer(), oldJson)

        assertEquals(ChatTaskSource.UNKNOWN, decoded.source)
        assertEquals(ChatTaskSource.UNKNOWN, decoded.batchTargets.single().source)
    }
}

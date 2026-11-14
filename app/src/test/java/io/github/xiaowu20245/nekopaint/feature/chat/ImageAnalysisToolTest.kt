package io.github.xiaowu20245.nekopaint.feature.chat

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageAnalysisToolTest {

    private val attachment = ChatImageAttachment(
        id = "img-1",
        dataUrl = "data:image/jpeg;base64,aaa",
    )

    @Test
    fun `analyzes latest image and reuses cached result`() = kotlinx.coroutines.test.runTest {
        val analyzer = FakeVisionAnalyzer()
        val tool = ImageAnalysisTool(analyzer)
        val args = buildJsonObject { put("mode", "tags") }

        val first = tool.analyze(args, listOf(attachment))
        val second = tool.analyze(args, listOf(attachment))

        assertTrue(first.contains("视觉模型分析"))
        assertTrue(second.contains("本会话缓存"))
        assertEquals(1, analyzer.calls)
    }

    @Test
    fun `selects requested image id`() = kotlinx.coroutines.test.runTest {
        val analyzer = FakeVisionAnalyzer()
        val tool = ImageAnalysisTool(analyzer)
        val other = ChatImageAttachment("img-2", "data:image/jpeg;base64,bbb")

        tool.analyze(
            args = buildJsonObject {
                put("mode", "describe")
                put("imageId", "img-2")
            },
            attachments = listOf(attachment, other),
        )

        assertEquals(other.dataUrl, analyzer.lastRequest?.imageDataUrl)
    }

    @Test
    fun `missing image returns error`() = kotlinx.coroutines.test.runTest {
        val analyzer = FakeVisionAnalyzer()
        val tool = ImageAnalysisTool(analyzer)

        val result = tool.analyze(
            args = buildJsonObject { put("mode", "tags") },
            attachments = emptyList(),
        )

        assertTrue(result.startsWith("工具执行失败："))
        assertEquals(0, analyzer.calls)
    }

    @Test
    fun `question mode requires question`() = kotlinx.coroutines.test.runTest {
        val analyzer = FakeVisionAnalyzer()
        val tool = ImageAnalysisTool(analyzer)

        val result = tool.analyze(
            args = buildJsonObject { put("mode", "question") },
            attachments = listOf(attachment),
        )

        assertTrue(result.contains("question 不能为空"))
        assertEquals(0, analyzer.calls)
    }

    private class FakeVisionAnalyzer : VisionAnalyzer {
        var calls = 0
        var lastRequest: ChatVisionRequest? = null

        override suspend fun analyze(request: ChatVisionRequest): Result<String> {
            calls++
            lastRequest = request
            return Result.success("```tags\n1girl, solo\n```")
        }
    }
}

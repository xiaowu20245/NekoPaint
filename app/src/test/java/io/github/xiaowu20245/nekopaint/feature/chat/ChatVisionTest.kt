package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertTrue
import org.junit.Test

class ChatVisionTest {

    @Test
    fun `image availability lists ids and points to analyze tool`() {
        val prompt = imageAvailabilityPrompt(
            listOf(
                ChatImageAttachment("img-1", "data:image/jpeg;base64,aaa"),
                ChatImageAttachment("img-2", "data:image/jpeg;base64,bbb"),
            ),
        )

        assertTrue(prompt.contains("imageId=img-1"))
        assertTrue(prompt.contains("imageId=img-2（最新）"))
        assertTrue(prompt.contains("analyze_image"))
    }

    @Test
    fun `tags mode asks for tags code block`() {
        val prompt = visionUserPrompt(
            ChatVisionRequest(
                imageDataUrl = "data:image/jpeg;base64,aaa",
                mode = "tags",
            ),
        )

        assertTrue(prompt.contains("```tags"))
    }

    @Test
    fun `question mode includes question`() {
        val prompt = visionUserPrompt(
            ChatVisionRequest(
                imageDataUrl = "data:image/jpeg;base64,aaa",
                mode = "question",
                question = "她头发是什么颜色？",
            ),
        )

        assertTrue(prompt.contains("她头发是什么颜色？"))
    }
}

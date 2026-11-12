package io.github.xiaowu20245.nekopaint.feature.history

import io.github.xiaowu20245.nekopaint.core.model.workflow.QueueItem
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMetadataExtractorTest {

    @Test
    fun extractsParametersFromTaskRecord() {
        val task = TaskRecord(
            promptId = "test-prompt-123",
            templateId = "flux-dev",
            templateName = "Flux 快速生图",
            clientId = "test-client",
            submittedAt = 1000L,
            values = mapOf(
                "3:seed" to "42819024",
                "3:steps" to "20",
                "3:cfg" to "7.0",
                "3:sampler_name" to "euler",
                "3:scheduler" to "normal",
                "4:ckpt_name" to "sd_xl_base_1.0.safetensors",
                "5:width" to "1024",
                "5:height" to "1024",
                "6:text" to "masterpiece, 1girl, cat ears, neon city",
                "7:text" to "worst quality, low quality",
            ),
        )

        val meta = HistoryMetadataExtractor.extract(task)

        assertEquals("42819024", meta.seed)
        assertEquals("20", meta.steps)
        assertEquals("7.0", meta.cfg)
        assertEquals("euler", meta.sampler)
        assertEquals("normal", meta.scheduler)
        assertEquals("euler / normal", meta.samplerText)
        assertEquals("sd_xl_base_1.0.safetensors", meta.model)
        assertEquals("1024x1024", meta.sizeText)
        assertEquals("masterpiece, 1girl, cat ears, neon city", meta.prompt)
        assertEquals("worst quality, low quality", meta.negativePrompt)
    }

    @Test
    fun extractsPromptFromQueueItemWhenTaskValuesEmpty() {
        val task = TaskRecord(
            promptId = "test-empty",
            templateId = "test",
            templateName = "Test",
            clientId = "c1",
            submittedAt = 1000L,
            values = emptyMap(),
        )

        val nodeInputs = JsonObject(mapOf("text" to JsonPrimitive("anime landscape, sunset")))
        val nodeObj = JsonObject(
            mapOf(
                "class_type" to JsonPrimitive("CLIPTextEncode"),
                "inputs" to nodeInputs,
            ),
        )
        val promptObj = JsonObject(mapOf("3" to nodeObj))

        val queueItem = QueueItem(
            number = 1,
            promptId = "test-empty",
            clientId = "c1",
            prompt = promptObj,
            nodeTitles = emptyMap(),
        )

        val prompt = HistoryMetadataExtractor.extractPrompt(task, queueItem)
        assertEquals("anime landscape, sunset", prompt)
    }

    @Test
    fun handlesNullAndEmptyTaskGracefully() {
        val meta = HistoryMetadataExtractor.extract(null)
        assertNull(meta.prompt)
        assertNull(meta.seed)
        assertNull(meta.sizeText)
        assertNull(meta.samplerText)
    }
}

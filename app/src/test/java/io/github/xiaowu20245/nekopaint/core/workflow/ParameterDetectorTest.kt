package io.github.xiaowu20245.nekopaint.core.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterDetectorTest {

    @Test
    fun resolvesPromptAndSizeFromUpstream() {
        val graph = (WorkflowParser.parse(WorkflowParserTest.SAMPLE_API) as WorkflowParseResult.Success).graph
        val detection = ParameterDetector.detect(graph, catalog = null)

        val positive = detection.candidates.firstOrNull { it.label == "正面提示词" }
        assertEquals("3", positive?.nodeId)
        assertEquals("text", positive?.inputName)
        assertEquals("a cat", positive?.defaultValue)

        val negative = detection.candidates.firstOrNull { it.label == "负面提示词" }
        assertEquals("5", negative?.nodeId)

        val width = detection.candidates.firstOrNull { it.label == "宽度" }
        assertEquals("6", width?.nodeId)
        assertEquals("value", width?.inputName)
        assertEquals("512", width?.defaultValue)

        val height = detection.candidates.firstOrNull { it.label == "高度" }
        assertEquals("7", height?.nodeId)
    }

    @Test
    fun exposesSamplerAndModelParameters() {
        val graph = (WorkflowParser.parse(WorkflowParserTest.SAMPLE_API) as WorkflowParseResult.Success).graph
        val detection = ParameterDetector.detect(graph, catalog = null)

        assertTrue(detection.candidates.any { it.label == "步数" && it.nodeId == "4" })
        assertTrue(detection.candidates.any { it.label == "CFG" && it.nodeId == "4" })
        assertTrue(detection.candidates.any { it.label == "模型" && it.nodeId == "1" })
        assertTrue(detection.candidates.any { it.label == "输出前缀" && it.nodeId == "9" })
    }

    @Test
    fun nonReadOnlyPromptFallbackIsMarkedReadOnly() {
        val raw = """
            {
              "1": {"class_type": "CLIPTextEncode", "inputs": {"text": ["2", 0], "clip": ["3", 0]}, "_meta": {"title": "正面"}},
              "2": {"class_type": "MysteryText", "inputs": {"value": "hello"}},
              "3": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "model"}},
              "4": {"class_type": "SaveImage", "inputs": {"filename_prefix": "x", "images": ["1", 0]}}
            }
        """.trimIndent()
        val graph = (WorkflowParser.parse(raw) as WorkflowParseResult.Success).graph
        val detection = ParameterDetector.detect(graph, catalog = null)

        val prompt = detection.candidates.firstOrNull { it.label.contains("正面提示词") }
        assertEquals(true, prompt?.readOnly)
    }
}

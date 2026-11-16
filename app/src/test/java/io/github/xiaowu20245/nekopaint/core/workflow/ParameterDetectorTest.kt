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
    fun skipsStructuredObjectAndArrayInputs() {
        val raw = """
            {
              "1": {"class_type": "CustomNode", "inputs": {
                "text": "hello", "count": 3, "enabled": true,
                "widget": {"nested": 1}, "items": [1, 2, 3],
                "model": ["2", 0]
              }},
              "2": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "model"}},
              "3": {"class_type": "SaveImage", "inputs": {"filename_prefix": "x", "images": ["1", 0]}}
            }
        """.trimIndent()
        val graph = (WorkflowParser.parse(raw) as WorkflowParseResult.Success).graph

        val candidates = ParameterDetector.detect(graph, catalog = null).candidates

        assertEquals(
            setOf("text", "count", "enabled"),
            candidates.filter { it.nodeId == "1" }.map { it.inputName }.toSet(),
        )
        assertTrue(candidates.none { it.inputName == "widget" || it.inputName == "items" })
    }

    @Test
    fun knownInputsAlsoRequireScalarValues() {
        val raw = """
            {
              "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": {"bad": true}}},
              "2": {"class_type": "SaveImage", "inputs": {"filename_prefix": "x", "images": ["1", 0]}}
            }
        """.trimIndent()
        val graph = (WorkflowParser.parse(raw) as WorkflowParseResult.Success).graph

        val candidates = ParameterDetector.detect(graph, catalog = null).candidates

        assertTrue(candidates.none { it.id == "1:ckpt_name" })
    }

    @Test
    fun upstreamSizeAndPromptRequireScalarSources() {
        val raw = """
            {
              "1": {"class_type": "CustomConfig", "inputs": {"value": {"nested": 1}}},
              "2": {"class_type": "EmptyLatentImage", "inputs": {"width": ["1", 0], "height": 512, "batch_size": 1}},
              "3": {"class_type": "CLIPTextEncode", "inputs": {"text": ["1", 0], "clip": ["4", 0]}},
              "4": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "model"}},
              "5": {"class_type": "SaveImage", "inputs": {"filename_prefix": "x", "images": ["2", 0]}},
              "6": {"class_type": "SaveImage", "inputs": {"filename_prefix": "y", "images": ["3", 0]}}
            }
        """.trimIndent()
        val graph = (WorkflowParser.parse(raw) as WorkflowParseResult.Success).graph

        val candidates = ParameterDetector.detect(graph, catalog = null).candidates

        assertTrue(candidates.none { it.inputName == "width" && it.nodeId == "1" })
        assertTrue(candidates.any { it.readOnly && it.id == "3:text" })
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

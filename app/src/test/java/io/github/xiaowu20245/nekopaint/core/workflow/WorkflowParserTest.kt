package io.github.xiaowu20245.nekopaint.core.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowParserTest {

    @Test
    fun parsesApiGraphAndComputesReachableNodes() {
        val result = WorkflowParser.parse(SAMPLE_API)

        assertTrue(result is WorkflowParseResult.Success)
        val graph = (result as WorkflowParseResult.Success).graph
        assertEquals(10, graph.nodes.size)
        assertEquals(setOf("9"), graph.outputNodeIds)
        assertEquals(setOf("1", "2", "3", "4", "5", "6", "7", "8", "9"), graph.reachableNodeIds)
    }

    @Test
    fun ignoresDisconnectedNodes() {
        val result = WorkflowParser.parse(SAMPLE_API)
        val graph = (result as WorkflowParseResult.Success).graph

        assertEquals(false, graph.reachableNodeIds.contains("99"))
    }

    @Test
    fun detectsUiFormat() {
        val result = WorkflowParser.parse("""{"nodes":[],"links":[]}""")

        assertEquals(WorkflowParseResult.UiFormat, result)
    }

    companion object {
        val SAMPLE_API = """
            {
              "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "model.safetensors"}},
              "2": {"class_type": "CLIPTextEncode", "inputs": {"text": ["3", 0], "clip": ["1", 1]}, "_meta": {"title": "正面"}},
              "3": {"class_type": "CR Text", "inputs": {"text": "a cat"}},
              "4": {"class_type": "KSampler", "inputs": {
                "seed": 1, "steps": 20, "cfg": 7.5, "sampler_name": "euler", "scheduler": "normal",
                "denoise": 1.0, "model": ["1", 0], "positive": ["2", 0], "negative": ["5", 0],
                "latent_image": ["8", 0]
              }},
              "5": {"class_type": "CLIPTextEncode", "inputs": {"text": "bad", "clip": ["1", 1]}, "_meta": {"title": "负面"}},
              "6": {"class_type": "INTConstant", "inputs": {"value": 512}},
              "7": {"class_type": "INTConstant", "inputs": {"value": 768}},
              "8": {"class_type": "EmptyLatentImage", "inputs": {"width": ["6", 0], "height": ["7", 0], "batch_size": 1}},
              "9": {"class_type": "SaveImage", "inputs": {"filename_prefix": "test", "images": ["4", 0]}},
              "99": {"class_type": "Note", "inputs": {"text": "unused"}}
            }
        """.trimIndent()
    }
}

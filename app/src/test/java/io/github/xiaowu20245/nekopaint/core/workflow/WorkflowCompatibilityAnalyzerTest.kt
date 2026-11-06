package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowIssueSeverity
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowMode
import io.github.xiaowu20245.nekopaint.core.network.NodeInfoDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowCompatibilityAnalyzerTest {

    private fun graphOf(raw: String) =
        (WorkflowParser.parse(raw) as WorkflowParseResult.Success).graph

    @Test
    fun detectsTextToImage() {
        val graph = graphOf(
            """
            {
              "1": {"class_type": "EmptyLatentImage", "inputs": {"width": 512, "height": 512, "batch_size": 1}},
              "2": {"class_type": "KSampler", "inputs": {"latent_image": ["1", 0]}},
              "3": {"class_type": "SaveImage", "inputs": {"images": ["2", 0]}}
            }
            """.trimIndent(),
        )

        val result = WorkflowCompatibilityAnalyzer.analyze(graph, catalog = null)

        assertEquals(WorkflowMode.TEXT_TO_IMAGE, result.mode)
        assertTrue(result.imageSlots.isEmpty())
        assertTrue(result.maskSlots.isEmpty())
        assertTrue(result.issues.any { it.message.contains("未连接服务器") })
    }

    @Test
    fun detectsConnectedLoadImage() {
        val graph = graphOf(
            """
            {
              "1": {"class_type": "LoadImage", "inputs": {"image": "input.png"}},
              "2": {"class_type": "SaveImage", "inputs": {"images": ["1", 0]}}
            }
            """.trimIndent(),
        )

        val result = WorkflowCompatibilityAnalyzer.analyze(graph, catalog = null)

        assertEquals(WorkflowMode.IMAGE_TO_IMAGE, result.mode)
        assertEquals("1", result.imageSlots.single().nodeId)
        assertEquals("input.png", result.imageSlots.single().currentValue)
    }

    @Test
    fun detectsImageAndMaskSlots() {
        val graph = graphOf(
            """
            {
              "1": {"class_type": "LoadImage", "inputs": {"image": "input.png"}},
              "2": {"class_type": "MaskConsumer", "inputs": {"image": ["1", 0], "mask": ["1", 1]}},
              "3": {"class_type": "SaveImage", "inputs": {"images": ["2", 0]}}
            }
            """.trimIndent(),
        )

        val result = WorkflowCompatibilityAnalyzer.analyze(graph, catalog = null)

        assertEquals(WorkflowMode.MASK_INPAINT, result.mode)
        assertEquals(1, result.imageSlots.size)
        assertEquals(1, result.maskSlots.size)
        assertEquals("1", result.maskSlots.single().nodeId)
    }

    @Test
    fun reportsMissingRequiredInput() {
        val catalog = NodeCatalog(
            mapOf(
                "DetailerForEach" to Json.decodeFromString<NodeInfoDto>(
                    """
                    {"input":{"required":{"image":["IMAGE"],"segs":["SEGS"]}}}
                    """.trimIndent(),
                ),
            ),
        )
        val graph = graphOf(
            """
            {
              "1": {"class_type": "DetailerForEach", "inputs": {"segs": ["2", 0]}},
              "2": {"class_type": "SaveImage", "inputs": {"images": ["1", 0]}}
            }
            """.trimIndent(),
        )

        val result = WorkflowCompatibilityAnalyzer.analyze(graph, catalog)

        assertTrue(
            result.issues.any {
                it.severity == WorkflowIssueSeverity.ERROR &&
                    it.message.contains("缺少必需输入 `image`")
            },
        )
    }

    @Test
    fun detectsDanbooruPromptFlowingIntoTextInput() {
        val graph = graphOf(
            """
            {
              "561": {"class_type": "DanbooruGalleryNode", "inputs": {"selection_data": "{\"selections\":[{\"prompt\":\"external\"}]}" }},
              "405": {"class_type": "PreviewAny", "inputs": {"source": ["561", 1]}},
              "96": {"class_type": "WeiLinPromptUIWithoutLora", "inputs": {"positive": "my prompt", "opt_text": ["405", 0]}},
              "3": {"class_type": "SaveImage", "inputs": {"images": ["96", 0]}}
            }
            """.trimIndent(),
        )

        val result = WorkflowCompatibilityAnalyzer.analyze(graph, catalog = null)

        assertTrue(
            result.issues.any {
                it.severity == WorkflowIssueSeverity.ERROR &&
                    it.message.contains("D站画廊包含外部提示词") &&
                    it.message.contains("96.opt_text")
            },
        )
    }

    @Test
    fun reportsOrphanLoadImageAndUiPlaceholder() {
        val graph = graphOf(
            """
            {
              "1": {"class_type": "LoadImage", "inputs": {"image": "orphan.png"}},
              "2": {"class_type": "PreviewBridge", "inputs": {"image": "$2-0"}},
              "3": {"class_type": "SaveImage", "inputs": {"images": ["2", 0]}}
            }
            """.trimIndent(),
        )

        val result = WorkflowCompatibilityAnalyzer.analyze(graph, catalog = null)

        assertTrue(
            result.issues.any {
                it.nodeId == "1" && it.message.contains("没有接入输出链路")
            },
        )
        assertTrue(
            result.issues.any {
                it.nodeId == "2" && it.severity == WorkflowIssueSeverity.ERROR &&
                    it.message.contains("UI 占位值")
            },
        )
    }
}

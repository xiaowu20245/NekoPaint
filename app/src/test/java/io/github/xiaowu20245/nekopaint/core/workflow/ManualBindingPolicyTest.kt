package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowFormat
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowGraph
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualBindingPolicyTest {

    private fun candidate(
        inputName: String = "text",
        label: String = "提示词",
        type: ParameterType = ParameterType.MULTILINE_TEXT,
        value: String = "a cat",
    ) = ParameterCandidate(
        id = "3:$inputName",
        nodeId = "3",
        inputName = inputName,
        label = label,
        type = type,
        group = ParameterGroup.PROMPT,
        value = value,
        selected = true,
        classType = "CLIPTextEncode",
    )

    @Test
    fun validBindingHasNoError() {
        assertNull(
            WorkflowManualBindingPolicy.validate(
                nodeExists = true,
                inputExists = true,
                existingIds = setOf("1:seed"),
                candidate = candidate(),
            ),
        )
    }

    @Test
    fun missingNodeInputDuplicateIdAndBlankLabelAreRejected() {
        assertEquals(
            ManualBindingError.NODE_MISSING,
            WorkflowManualBindingPolicy.validate(false, true, emptySet(), candidate()),
        )
        assertEquals(
            ManualBindingError.INPUT_MISSING,
            WorkflowManualBindingPolicy.validate(true, false, emptySet(), candidate()),
        )
        assertEquals(
            ManualBindingError.DUPLICATE_ID,
            WorkflowManualBindingPolicy.validate(true, true, setOf("3:text"), candidate()),
        )
        assertEquals(
            ManualBindingError.LABEL_BLANK,
            WorkflowManualBindingPolicy.validate(true, true, emptySet(), candidate(label = "  ")),
        )
    }

    @Test
    fun typeAndValueValidationRejectsMismatches() {
        assertEquals(
            ManualBindingError.VALUE_INVALID,
            WorkflowManualBindingPolicy.validate(
                true,
                true,
                emptySet(),
                candidate(type = ParameterType.INT, value = "abc"),
            ),
        )
        assertEquals(
            ManualBindingError.VALUE_INVALID,
            WorkflowManualBindingPolicy.validate(
                true,
                true,
                emptySet(),
                candidate(type = ParameterType.ENUM, value = "euler"),
                schema = InputSchema(
                    type = ParameterType.ENUM,
                    constraints = io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterConstraints(
                        options = listOf("dpmpp_2m"),
                    ),
                ),
            ),
        )
        assertNull(
            WorkflowManualBindingPolicy.validate(
                true,
                true,
                emptySet(),
                candidate(type = ParameterType.ENUM, value = "euler"),
                schema = InputSchema(
                    type = ParameterType.ENUM,
                    constraints = io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterConstraints(
                        options = listOf("euler", "dpmpp_2m"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun infersTypesFromActualApiValuesAndBuildsId() {
        assertEquals(
            ParameterType.INT,
            WorkflowManualBindingPolicy.inferType(JsonPrimitive(20), null),
        )
        assertEquals(
            ParameterType.FLOAT,
            WorkflowManualBindingPolicy.inferType(JsonPrimitive(7.5), null),
        )
        assertEquals(
            ParameterType.BOOLEAN,
            WorkflowManualBindingPolicy.inferType(JsonPrimitive(true), null),
        )
        assertEquals(
            ParameterType.MULTILINE_TEXT,
            WorkflowManualBindingPolicy.inferType(JsonPrimitive("a cat"), null),
        )
        assertEquals("3:text", WorkflowManualBindingPolicy.candidateId(" 3 ", " text "))
    }

    @Test
    fun nodeSummaryMarksMissingAndUnreachableNodes() {
        val graph = WorkflowGraph(
            nodes = mapOf(
                "1" to WorkflowNode("1", "KSampler", "采样器", emptyMap()),
                "2" to WorkflowNode("2", "MissingNode", "缺失节点", emptyMap()),
                "3" to WorkflowNode("3", "SaveImage", "保存", emptyMap()),
            ),
            outputNodeIds = setOf("1", "3"),
            reachableNodeIds = setOf("1", "3"),
            format = WorkflowFormat.API,
        )

        val summaries = WorkflowNodeSummaryPolicy.from(graph, listOf("MissingNode"))

        assertEquals(listOf("1", "2", "3"), summaries.map { it.id })
        assertEquals(true, summaries[1].missing)
        assertEquals(false, summaries[1].reachable)
        assertEquals(false, summaries[0].missing)
    }
}

package io.github.xiaowu20245.nekopaint.feature.chat

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterConstraints
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatParameterOverrideTest {

    @Test
    fun `validates and normalizes supported parameter types`() {
        val result = validateParameterOverrides(
            template = template(),
            requests = listOf(
                ChatSubmissionParameter("63:steps", "30"),
                ChatSubmissionParameter("63:cfg", "4.5"),
                ChatSubmissionParameter("63:sampler", "EULER"),
                ChatSubmissionParameter("63:enabled", "TRUE"),
            ),
        ).getOrThrow()

        assertEquals(4, result.size)
        assertEquals("30", result[0].newValue)
        assertEquals("4.5", result[1].newValue)
        assertEquals("euler", result[2].newValue)
        assertEquals("true", result[3].newValue)
    }

    @Test
    fun `rejects prompt readonly image and unknown parameters`() {
        val template = template()
        listOf("1:text", "63:readonly", "1:image", "missing").forEach { id ->
            assertTrue(
                "$id should be rejected",
                validateParameterOverrides(
                    template,
                    listOf(ChatSubmissionParameter(id, "1")),
                ).isFailure,
            )
        }
    }

    @Test
    fun `rejects out of range and invalid enum values`() {
        val template = template()
        assertTrue(
            validateParameterOverrides(
                template,
                listOf(ChatSubmissionParameter("63:steps", "101")),
            ).isFailure,
        )
        assertTrue(
            validateParameterOverrides(
                template,
                listOf(ChatSubmissionParameter("63:sampler", "unknown")),
            ).isFailure,
        )
    }

    @Test
    fun `apply overrides changes only the submission copy`() {
        val original = template()
        val overrides = validateParameterOverrides(
            original,
            listOf(ChatSubmissionParameter("63:steps", "30")),
        ).getOrThrow()

        val updated = applyParameterOverrides(original, original, overrides).getOrThrow()

        assertEquals("13", original.candidates.first { it.id == "63:steps" }.value)
        assertEquals("30", updated.candidates.first { it.id == "63:steps" }.value)
        assertTrue(updated.candidates.first { it.id == "63:steps" }.selected)
    }

    private fun template(): WorkflowTemplate = WorkflowTemplate(
        id = "workflow-1",
        name = "Anima T2I",
        sourceFileName = "anima.json",
        sourceHash = "hash",
        importedAt = 0L,
        nodeCount = 8,
        reachableNodeCount = 8,
        candidates = listOf(
            candidate(
                id = "1:text",
                label = "正面提示词",
                type = ParameterType.MULTILINE_TEXT,
                group = ParameterGroup.PROMPT,
                value = "1girl",
            ),
            candidate(
                id = "1:image",
                label = "输入图片",
                type = ParameterType.IMAGE,
                group = ParameterGroup.IMAGE,
                value = "image.png",
            ),
            candidate(
                id = "63:steps",
                label = "步数",
                type = ParameterType.INT,
                group = ParameterGroup.SAMPLING,
                value = "13",
                constraints = ParameterConstraints(min = 1.0, max = 100.0),
            ),
            candidate(
                id = "63:cfg",
                label = "CFG",
                type = ParameterType.FLOAT,
                group = ParameterGroup.SAMPLING,
                value = "1",
                constraints = ParameterConstraints(min = 0.0, max = 100.0),
            ),
            candidate(
                id = "63:sampler",
                label = "采样器",
                type = ParameterType.ENUM,
                group = ParameterGroup.SAMPLING,
                value = "euler",
                constraints = ParameterConstraints(options = listOf("euler", "karras")),
            ),
            candidate(
                id = "63:enabled",
                label = "启用",
                type = ParameterType.BOOLEAN,
                group = ParameterGroup.SAMPLING,
                value = "false",
            ),
            candidate(
                id = "63:readonly",
                label = "只读参数",
                type = ParameterType.INT,
                group = ParameterGroup.SAMPLING,
                value = "1",
                readOnly = true,
            ),
        ),
    )

    private fun candidate(
        id: String,
        label: String,
        type: ParameterType,
        group: ParameterGroup,
        value: String,
        constraints: ParameterConstraints = ParameterConstraints(),
        readOnly: Boolean = false,
    ): ParameterCandidate = ParameterCandidate(
        id = id,
        nodeId = id.substringBefore(':'),
        inputName = id.substringAfter(':'),
        label = label,
        type = type,
        group = group,
        value = value,
        constraints = constraints,
        selected = true,
        readOnly = readOnly,
    )
}

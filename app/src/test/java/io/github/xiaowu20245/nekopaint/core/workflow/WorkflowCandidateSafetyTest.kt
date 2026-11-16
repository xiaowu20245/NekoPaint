package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.PresetValueEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowCandidateSafetyTest {

    @Test
    fun acceptsValidScalarCandidates() {
        WorkflowCandidateSafety.validate(listOf(candidate("3:text"), candidate("4:steps")))
    }

    @Test
    fun rejectsDuplicateBlankAndInconsistentIds() {
        assertFailsFor(listOf(candidate("3:text"), candidate("3:text")))
        assertFailsFor(listOf(candidate("3:text").copy(id = "")))
        assertFailsFor(listOf(candidate("3:text").copy(id = "other:text")))
        assertFailsFor(listOf(candidate("3:text").copy(nodeId = "")))
        assertFailsFor(listOf(candidate("3:text").copy(inputName = "")))
    }

    @Test
    fun rejectsReadOnlySelectedAndInvalidScalarValues() {
        assertFailsFor(listOf(candidate("3:text").copy(readOnly = true, selected = true)))
        assertFailsFor(listOf(candidate("4:steps").copy(value = "many")))
        assertFailsFor(listOf(candidate("4:steps").copy(defaultValue = "1.5")))
        assertFailsFor(listOf(candidate("5:cfg").copy(value = "NaN")))
        assertFailsFor(listOf(candidate("6:enabled").copy(value = "yes")))
    }

    @Test
    fun rejectsReadOnlyFlipTypeAndUnknownReferenceChanges() {
        val stored = candidate("3:text").copy(readOnly = true, selected = false)
        assertFailsFor(listOf(stored.copy(readOnly = false)), reference = listOf(stored))
        assertFailsFor(
            listOf(candidate("4:steps").copy(type = ParameterType.FLOAT)),
            reference = listOf(candidate("4:steps")),
        )
        assertFailsFor(
            listOf(candidate("99:text")),
            reference = listOf(candidate("3:text")),
        )
    }

    @Test
    fun reusesOnlyCompatibleEditableValues() {
        val text = candidate("3:text")
        assertTrue(
            WorkflowCandidateSafety.canReuseValue(
                text.copy(value = "custom"),
                text,
            ),
        )
        assertFalse(
            WorkflowCandidateSafety.canReuseValue(
                text.copy(value = "custom", type = ParameterType.INT),
                text,
            ),
        )
        assertFalse(
            WorkflowCandidateSafety.canReuseValue(
                text.copy(value = "custom", readOnly = true),
                text,
            ),
        )
        assertFalse(
            WorkflowCandidateSafety.canReuseValue(
                candidate("4:steps").copy(value = "not-a-number"),
                candidate("4:steps"),
            ),
        )
    }

    @Test
    fun primaryPromptMustBeEditableText() {
        assertTrue(WorkflowCandidateSafety.isPrimaryPromptCandidate(candidate("3:text")))
        assertFalse(
            WorkflowCandidateSafety.isPrimaryPromptCandidate(
                candidate("3:text").copy(readOnly = true),
            ),
        )
        assertFalse(
            WorkflowCandidateSafety.isPrimaryPromptCandidate(
                candidate("3:text").copy(type = ParameterType.IMAGE),
            ),
        )
    }

    @Test
    fun presetSanitizerDropsStaleAndIncompatibleEntries() {
        val text = candidate("3:text")
        val valid = WorkflowPreset(
            id = "p1",
            name = "valid",
            entries = listOf(
                PresetValueEntry("3:text", "CLIPTextEncode", "text", text.label, "hello"),
            ),
        )
        val stale = WorkflowPreset(
            id = "p2",
            name = "stale",
            entries = listOf(
                PresetValueEntry("99:text", "Removed", "text", "gone", "x"),
            ),
        )
        val incompatible = WorkflowPreset(
            id = "p3",
            name = "incompatible",
            entries = listOf(
                PresetValueEntry("4:steps", "KSampler", "steps", "步数", "not-a-number"),
            ),
        )

        val sanitized = WorkflowCandidateSafety.sanitizePresets(
            listOf(valid, stale, incompatible),
            listOf(text, candidate("4:steps")),
        )

        assertEquals(listOf("p1"), sanitized.map { it.id })
        assertEquals(1, sanitized.single().entries.size)
    }

    private fun assertFailsFor(
        candidates: List<ParameterCandidate>,
        reference: List<ParameterCandidate> = emptyList(),
    ) {
        val failure = runCatching {
            WorkflowCandidateSafety.validate(candidates, reference)
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    private fun candidate(id: String): ParameterCandidate {
        val (nodeId, inputName) = id.split(":", limit = 2)
        return when (inputName) {
            "text" -> ParameterCandidate(
                id = id,
                nodeId = nodeId,
                inputName = inputName,
                label = "提示词",
                type = ParameterType.MULTILINE_TEXT,
                group = ParameterGroup.PROMPT,
                defaultValue = "a cat",
                value = "a cat",
                selected = true,
                classType = "CLIPTextEncode",
            )

            "steps" -> ParameterCandidate(
                id = id,
                nodeId = nodeId,
                inputName = inputName,
                label = "步数",
                type = ParameterType.INT,
                group = ParameterGroup.SAMPLING,
                defaultValue = "20",
                value = "20",
                selected = true,
                classType = "KSampler",
            )

            "cfg" -> ParameterCandidate(
                id = id,
                nodeId = nodeId,
                inputName = inputName,
                label = "CFG",
                type = ParameterType.FLOAT,
                group = ParameterGroup.SAMPLING,
                defaultValue = "7.0",
                value = "7.0",
                selected = true,
                classType = "KSampler",
            )

            "enabled" -> ParameterCandidate(
                id = id,
                nodeId = nodeId,
                inputName = inputName,
                label = "启用",
                type = ParameterType.BOOLEAN,
                group = ParameterGroup.ADVANCED,
                defaultValue = "true",
                value = "true",
                selected = true,
                classType = "Switch",
            )

            else -> error("unsupported candidate $id")
        }
    }
}

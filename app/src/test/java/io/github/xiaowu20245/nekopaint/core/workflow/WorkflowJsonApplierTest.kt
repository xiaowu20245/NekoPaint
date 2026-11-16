package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.BindingSource
import io.github.xiaowu20245.nekopaint.core.model.workflow.DetectionConfidence
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowJsonApplierTest {

    private val raw = """
        {
          "1": {"class_type": "KSampler", "inputs": {"seed": 1, "steps": 20}},
          "2": {"class_type": "INTConstant", "inputs": {"value": 512}},
          "3": {"class_type": "EmptyLatentImage", "inputs": {"width": ["2", 0]}}
        }
    """.trimIndent()

    private val structuredRaw = """
        {
          "1": {"class_type": "CustomNode", "inputs": {
            "text": "hello", "count": 3, "enabled": true,
            "widget": {"nested": 1}, "items": [1, 2, 3]
          }},
          "2": {"class_type": "SaveImage", "inputs": {"images": ["1", 0]}}
        }
    """.trimIndent()

    @Test
    fun appliesScalarValuesWithoutChangingSourceJson() {
        val result = WorkflowJsonApplier.apply(
            rawJson = raw,
            candidates = listOf(
                candidate("1", "seed", ParameterType.INT, "42"),
                candidate("2", "value", ParameterType.INT, "768"),
            ),
        )

        assertEquals(42L, result["1"]!!.jsonObject["inputs"]!!.jsonObject["seed"]!!.jsonPrimitive.content.toLong())
        assertEquals(768L, result["2"]!!.jsonObject["inputs"]!!.jsonObject["value"]!!.jsonPrimitive.content.toLong())
    }

    @Test(expected = WorkflowApplyException::class)
    fun rejectsLinkedInput() {
        WorkflowJsonApplier.apply(
            rawJson = raw,
            candidates = listOf(
                candidate("3", "width", ParameterType.INT, "1024"),
            ),
        )
    }

    @Test
    fun rejectsObjectAndArrayInputs() {
        assertFailsFor("widget")
        assertFailsFor("items")
    }

    @Test
    fun unknownStringKeepsStringType() {
        val result = WorkflowJsonApplier.apply(
            structuredRaw,
            listOf(candidate("1", "text", ParameterType.UNKNOWN, "changed")),
        )

        val value = input(result, "1", "text")
        assertTrue(value.isString)
        assertEquals("changed", value.content)
    }

    @Test
    fun unknownNumberKeepsNumericType() {
        val result = WorkflowJsonApplier.apply(
            structuredRaw,
            listOf(candidate("1", "count", ParameterType.UNKNOWN, "9")),
        )

        val value = input(result, "1", "count")
        assertFalse(value.isString)
        assertEquals("9", value.content)
    }

    @Test
    fun unknownBooleanKeepsBooleanType() {
        val result = WorkflowJsonApplier.apply(
            structuredRaw,
            listOf(candidate("1", "enabled", ParameterType.UNKNOWN, "false")),
        )

        val value = input(result, "1", "enabled")
        assertFalse(value.isString)
        assertEquals("false", value.content)
    }

    @Test
    fun unknownInvalidNumericOrBooleanThrows() {
        val numberFailure = runCatching {
            WorkflowJsonApplier.apply(
                structuredRaw,
                listOf(candidate("1", "count", ParameterType.UNKNOWN, "many")),
            )
        }.exceptionOrNull()
        assertTrue(numberFailure is WorkflowApplyException)

        val booleanFailure = runCatching {
            WorkflowJsonApplier.apply(
                structuredRaw,
                listOf(candidate("1", "enabled", ParameterType.UNKNOWN, "yes")),
            )
        }.exceptionOrNull()
        assertTrue(booleanFailure is WorkflowApplyException)
    }

    @Test
    fun rejectsMissingInputInsteadOfAddingIt() {
        val failure = runCatching {
            WorkflowJsonApplier.apply(
                raw,
                listOf(candidate("1", "missing", ParameterType.TEXT, "x")),
            )
        }.exceptionOrNull()

        assertTrue(failure is WorkflowApplyException)
    }

    @Test
    fun safeCandidatesFiltersStructuredAndLinkedInputs() {
        val safe = WorkflowJsonApplier.safeCandidates(
            rawJson = structuredRaw,
            candidates = listOf(
                candidate("1", "text", ParameterType.TEXT, "hello"),
                candidate("1", "widget", ParameterType.UNKNOWN, "{}"),
                candidate("1", "items", ParameterType.UNKNOWN, "[]"),
                candidate("2", "images", ParameterType.TEXT, "x"),
            ),
        )

        assertEquals(listOf("1:text"), safe.map { it.id })
    }

    @Test
    fun safeCandidatesReturnsEmptyForUnparseableJson() {
        assertTrue(
            WorkflowJsonApplier.safeCandidates(
                rawJson = "{broken",
                candidates = listOf(candidate("1", "text", ParameterType.TEXT, "x")),
            ).isEmpty(),
        )
    }

    private fun assertFailsFor(inputName: String) {
        val failure = runCatching {
            WorkflowJsonApplier.apply(
                structuredRaw,
                listOf(candidate("1", inputName, ParameterType.TEXT, "x")),
            )
        }.exceptionOrNull()
        assertTrue(failure is WorkflowApplyException)
    }

    private fun input(
        result: kotlinx.serialization.json.JsonObject,
        nodeId: String,
        inputName: String,
    ) = result[nodeId]!!.jsonObject["inputs"]!!.jsonObject[inputName]!!.jsonPrimitive

    private fun candidate(
        nodeId: String,
        inputName: String,
        type: ParameterType,
        value: String,
    ) = ParameterCandidate(
        id = "$nodeId:$inputName",
        nodeId = nodeId,
        inputName = inputName,
        label = inputName,
        type = type,
        group = ParameterGroup.ADVANCED,
        value = value,
        source = BindingSource.DIRECT,
        confidence = DetectionConfidence.HIGH,
        selected = true,
    )
}

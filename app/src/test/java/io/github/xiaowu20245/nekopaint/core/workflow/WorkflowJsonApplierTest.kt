package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.BindingSource
import io.github.xiaowu20245.nekopaint.core.model.workflow.DetectionConfidence
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowJsonApplierTest {

    private val raw = """
        {
          "1": {"class_type": "KSampler", "inputs": {"seed": 1, "steps": 20}},
          "2": {"class_type": "INTConstant", "inputs": {"value": 512}},
          "3": {"class_type": "EmptyLatentImage", "inputs": {"width": ["2", 0]}}
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

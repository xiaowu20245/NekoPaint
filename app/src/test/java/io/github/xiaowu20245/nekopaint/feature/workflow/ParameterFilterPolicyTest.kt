package io.github.xiaowu20245.nekopaint.feature.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import org.junit.Assert.assertEquals
import org.junit.Test

class ParameterFilterPolicyTest {

    @Test
    fun quickFiltersMatchSelectedReadOnlyAndErrors() {
        val selected = candidate(id = "1", selected = true)
        val readOnly = candidate(id = "2", readOnly = true)
        val broken = candidate(id = "3")
        val plain = candidate(id = "4")
        val candidates = listOf(selected, readOnly, broken, plain)
        val errors = mapOf(broken.id to FieldError.Server("invalid"))

        assertEquals(
            listOf(selected),
            ParameterFilterPolicy.apply(candidates, errors, ParameterQuickFilter.SELECTED),
        )
        assertEquals(
            listOf(readOnly),
            ParameterFilterPolicy.apply(candidates, errors, ParameterQuickFilter.READ_ONLY),
        )
        assertEquals(
            listOf(broken),
            ParameterFilterPolicy.apply(candidates, errors, ParameterQuickFilter.ERROR),
        )
    }

    @Test
    fun allFilterKeepsOriginalOrderAndCandidateCount() {
        val candidates = listOf(
            candidate(id = "1", selected = true),
            candidate(id = "2", readOnly = true),
            candidate(id = "3"),
        )

        assertEquals(
            candidates,
            ParameterFilterPolicy.apply(
                candidates,
                mapOf("3" to FieldError.Server("invalid")),
                ParameterQuickFilter.ALL,
            ),
        )
    }

    private fun candidate(
        id: String,
        selected: Boolean = false,
        readOnly: Boolean = false,
    ): ParameterCandidate = ParameterCandidate(
        id = id,
        nodeId = id,
        inputName = "input$id",
        label = "参数 $id",
        type = ParameterType.TEXT,
        group = ParameterGroup.OTHER,
        defaultValue = "",
        value = "",
        selected = selected,
        readOnly = readOnly,
        classType = "PreviewNode",
    )
}

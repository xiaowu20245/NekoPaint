package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowSubmitterTest {

    @Test
    fun detailPathUsesSeededSelection() = runTest {
        var selected: List<ParameterCandidate> = emptyList()
        val submitter = WorkflowSubmitter { _, candidates, _ ->
            selected = candidates
            WorkflowSubmitResult.Success("p1", 1)
        }

        submitter.submit(
            template = template(),
            seedStrategy = SeedStrategy.INCREMENT,
            context = SubmissionRunContext(runIndex = 0, batchIndex = 0, batchCount = 1),
        )

        val seed = selected.first { it.id == "1:seed" }
        assertTrue(seed.selected)
        assertEquals("10", seed.value)
    }

    @Test
    fun quickRunProducesDifferentSeedPerRun() = runTest {
        val seeds = mutableListOf<String>()
        val submitter = WorkflowSubmitter { _, candidates, _ ->
            seeds += candidates.first { it.id == "1:seed" }.value
            WorkflowSubmitResult.Success("p1", 1)
        }

        repeat(2) { index ->
            submitter.submit(
                template = template(),
                seedStrategy = SeedStrategy.INCREMENT,
                context = SubmissionRunContext(
                    runIndex = index,
                    batchIndex = index,
                    batchCount = 2,
                ),
            )
        }

        assertEquals(listOf("10", "11"), seeds)
    }

    @Test
    fun chatOverridesMergeWithoutDroppingSeed() = runTest {
        var selected: List<ParameterCandidate> = emptyList()
        val submitter = WorkflowSubmitter { _, candidates, _ ->
            selected = candidates
            WorkflowSubmitResult.Success("p1", 1)
        }
        val override = template().candidates.first { it.id == "2:text" }
            .copy(value = "new prompt", selected = true)

        submitter.submit(
            template = template(),
            overrides = listOf(override),
            seedStrategy = SeedStrategy.INCREMENT,
            context = SubmissionRunContext(),
        )

        assertEquals("new prompt", selected.first { it.id == "2:text" }.value)
        assertTrue(selected.first { it.id == "1:seed" }.selected)
    }

    @Test
    fun batchContextIsPassedToRepository() = runTest {
        var captured: SubmissionRunContext? = null
        val submitter = WorkflowSubmitter { _, _, context ->
            captured = context
            WorkflowSubmitResult.Success("p1", 1)
        }

        submitter.submit(
            template = template(),
            context = SubmissionRunContext(runIndex = 2, batchIndex = 2, batchCount = 5),
        )

        assertNotNull(captured)
        assertEquals(2, captured?.runIndex)
        assertEquals(2, captured?.batchIndex)
        assertEquals(5, captured?.batchCount)
    }

    @Test
    fun validationIsReturnedUnchanged() = runTest {
        val validation = WorkflowSubmitResult.Validation(
            io.github.xiaowu20245.nekopaint.core.common.NekoMessage(
                io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey.WORKFLOW_VALIDATION_FAILED,
                listOf("bad"),
            ),
            emptyList(),
        )
        val submitter = WorkflowSubmitter { _, _, _ -> validation }

        assertSame(validation, submitter.submit(template()))
    }

    @Test
    fun errorIsReturnedUnchanged() = runTest {
        val error = WorkflowSubmitResult.Error(
            io.github.xiaowu20245.nekopaint.core.common.NekoMessage(
                io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey.WORKFLOW_FAILED,
            ),
        )
        val submitter = WorkflowSubmitter { _, _, _ -> error }

        assertSame(error, submitter.submit(template()))
    }

    @Test
    fun unknownIsReturnedUnchanged() = runTest {
        val unknown = WorkflowSubmitResult.Unknown(
            "p1",
            io.github.xiaowu20245.nekopaint.core.common.NekoMessage(
                io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey.WORKFLOW_SUBMIT_UNKNOWN,
                listOf("timeout"),
            ),
        )
        val submitter = WorkflowSubmitter { _, _, _ -> unknown }

        assertSame(unknown, submitter.submit(template()))
    }

    private fun template() = WorkflowTemplate(
        id = "t1",
        name = "template",
        sourceFileName = "api.json",
        sourceHash = "hash",
        importedAt = 1L,
        nodeCount = 2,
        reachableNodeCount = 2,
        candidates = listOf(
            ParameterCandidate(
                id = "1:seed",
                nodeId = "1",
                inputName = "seed",
                label = "种子",
                type = ParameterType.INT,
                group = ParameterGroup.SAMPLING,
                value = "10",
                selected = false,
            ),
            ParameterCandidate(
                id = "2:text",
                nodeId = "2",
                inputName = "text",
                label = "提示词",
                type = ParameterType.TEXT,
                group = ParameterGroup.PROMPT,
                value = "old prompt",
                selected = true,
            ),
        ),
    )
}

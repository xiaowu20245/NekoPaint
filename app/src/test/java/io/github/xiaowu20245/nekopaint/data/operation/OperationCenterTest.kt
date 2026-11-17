package io.github.xiaowu20245.nekopaint.data.operation

import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationCenterTest {

    private val template = WorkflowTemplate(
        id = "t1",
        name = "template",
        sourceFileName = "api.json",
        sourceHash = "hash",
        importedAt = 1L,
        nodeCount = 1,
        reachableNodeCount = 1,
    )

    @Test
    fun partialBatchStopsOnValidationAndCountsSkipped() = runTest {
        var calls = 0
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submit = {
                calls += 1
                if (calls == 3) {
                    WorkflowSubmitResult.Validation("bad", emptyList())
                } else {
                    WorkflowSubmitResult.Success("p$calls", calls)
                }
            },
        )

        val id = center.start(template, 5, SeedStrategy.FIXED).getOrThrow()
        advanceUntilIdle()

        val state = center.states.value.getValue(id)
        assertEquals(OperationStatus.PARTIAL, state.status)
        assertEquals(5, state.total)
        assertEquals(3, state.processed)
        assertEquals(2, state.success)
        assertEquals(1, state.failed)
        assertEquals(2, state.skipped)
        assertTrue(state.message?.contains("bad") == true)
    }

    @Test
    fun zeroSuccessIsFailed() = runTest {
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submit = { WorkflowSubmitResult.Error("down") },
        )

        val id = center.start(template, 3, SeedStrategy.FIXED).getOrThrow()
        advanceUntilIdle()

        val state = center.states.value.getValue(id)
        assertEquals(OperationStatus.FAILED, state.status)
        assertEquals(0, state.success)
        assertEquals(1, state.failed)
        assertEquals(2, state.skipped)
    }

    @Test
    fun allSuccessCompletes() = runTest {
        var calls = 0
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submit = {
                calls += 1
                WorkflowSubmitResult.Success("p$calls", calls)
            },
        )

        val id = center.start(template, 3, SeedStrategy.FIXED).getOrThrow()
        advanceUntilIdle()

        val state = center.states.value.getValue(id)
        assertEquals(OperationStatus.SUCCESS, state.status)
        assertEquals(3, state.success)
        assertEquals(0, state.failed)
        assertEquals(0, state.skipped)
        assertEquals("p3", state.lastSuccess?.promptId)
    }

    @Test
    fun exceptionAfterSuccessIsPartialAndValidationIsStored() = runTest {
        var calls = 0
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submit = {
                calls += 1
                if (calls == 2) error("boom")
                WorkflowSubmitResult.Success("p$calls", calls)
            },
        )
        val id = center.start(template, 3, SeedStrategy.FIXED).getOrThrow()
        advanceUntilIdle()
        val state = center.states.value.getValue(id)
        assertEquals(OperationStatus.PARTIAL, state.status)
        assertEquals(1, state.success)
        assertEquals(1, state.failed)
        assertEquals(state.total, state.processed + state.skipped)
        assertTrue(state.success + state.failed <= state.total)

        var validationCalls = 0
        val validationCenter = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submit = {
                validationCalls += 1
                WorkflowSubmitResult.Validation("bad", emptyList())
            },
        )
        val validationId = validationCenter.start(template, 1, SeedStrategy.FIXED).getOrThrow()
        advanceUntilIdle()
        val validationState = validationCenter.states.value.getValue(validationId)
        assertEquals(OperationStatus.FAILED, validationState.status)
        assertEquals("bad", validationState.lastValidation?.summary)
    }


    @Test
    fun cancelIsNotOverwrittenByFinalState() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submit = {
                gate.await()
                WorkflowSubmitResult.Success("p", 1)
            },
        )
        val id = center.start(template, 2, SeedStrategy.FIXED).getOrThrow()
        runCurrent()

        center.cancel(id)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(OperationStatus.CANCELLED, center.states.value.getValue(id).status)
    }

    @Test
    fun duplicateTemplateOperationIsRejectedWhileRunning() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submit = {
                gate.await()
                WorkflowSubmitResult.Success("p", 1)
            },
        )

        assertTrue(center.start(template, 2, SeedStrategy.FIXED).isSuccess)
        runCurrent()
        assertEquals(OperationStatus.RUNNING, center.stateFor(template.id)?.status)
        val second = center.start(template, 2, SeedStrategy.FIXED)

        assertTrue(second.isFailure)
        gate.complete(Unit)
        advanceUntilIdle()
    }
}

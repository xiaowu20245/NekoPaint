package io.github.xiaowu20245.nekopaint.data.operation

import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.data.repository.SubmissionRunContext
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
            submitter = testSubmitter {
                calls += 1
                if (calls == 3) {
                    WorkflowSubmitResult.Validation(
                        NekoMessage(NekoMessageKey.WORKFLOW_SERVER_REJECTED, listOf("400")),
                        emptyList(),
                    )
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
        assertEquals(NekoMessageKey.WORKFLOW_SERVER_REJECTED, state.message?.key)
    }

    @Test
    fun zeroSuccessIsFailed() = runTest {
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submitter = testSubmitter {
                WorkflowSubmitResult.Error(
                    NekoMessage(NekoMessageKey.WORKFLOW_FAILED),
                )
            },
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
    fun unknownResultIsCountedAndReportedAsUnknown() = runTest {
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submitter = testSubmitter {
                WorkflowSubmitResult.Unknown(
                    "p1",
                    NekoMessage(NekoMessageKey.WORKFLOW_UNKNOWN_RESULT),
                )
            },
        )

        val id = center.start(template, 1, SeedStrategy.FIXED).getOrThrow()
        advanceUntilIdle()

        val state = center.states.value.getValue(id)
        assertEquals(OperationStatus.UNKNOWN, state.status)
        assertEquals(1, state.unknown)
        assertEquals(0, state.failed)
        assertEquals(0, state.skipped)
    }

    @Test
    fun successThenUnknownIsPartialWithUnknownCount() = runTest {
        var calls = 0
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submitter = testSubmitter {
                calls += 1
                if (calls == 1) {
                    WorkflowSubmitResult.Success("p1", 1)
                } else {
                    WorkflowSubmitResult.Unknown(
                        "p2",
                        NekoMessage(NekoMessageKey.WORKFLOW_UNKNOWN_RESULT),
                    )
                }
            },
        )

        val id = center.start(template, 2, SeedStrategy.FIXED).getOrThrow()
        advanceUntilIdle()

        val state = center.states.value.getValue(id)
        assertEquals(OperationStatus.PARTIAL, state.status)
        assertEquals(1, state.success)
        assertEquals(1, state.unknown)
        assertEquals(0, state.failed)
        assertEquals(state.total, state.processed + state.skipped)
    }

    @Test
    fun allSuccessCompletes() = runTest {
        var calls = 0
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submitter = testSubmitter {
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
            submitter = testSubmitter {
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
            submitter = testSubmitter {
                validationCalls += 1
                WorkflowSubmitResult.Validation(
                    NekoMessage(NekoMessageKey.WORKFLOW_SERVER_REJECTED, listOf("400")),
                    emptyList(),
                )
            },
        )
        val validationId = validationCenter.start(template, 1, SeedStrategy.FIXED).getOrThrow()
        advanceUntilIdle()
        val validationState = validationCenter.states.value.getValue(validationId)
        assertEquals(OperationStatus.FAILED, validationState.status)
        assertEquals(
            NekoMessageKey.WORKFLOW_SERVER_REJECTED,
            validationState.lastValidation?.summary?.key,
        )
    }


    @Test
    fun cancelIsNotOverwrittenByFinalState() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submitter = testSubmitter {
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
    fun cancelIsNotOverwrittenByNonCancellableFinalWrite() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submitter = testSubmitter {
                // 模拟真实网络/落盘在取消后仍完成，最终写入必须尊重 CANCELLED。
                withContext(NonCancellable) { gate.await() }
                WorkflowSubmitResult.Success("p", 1)
            },
        )
        val id = center.start(template, 1, SeedStrategy.FIXED).getOrThrow()
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
            submitter = testSubmitter {
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

    @Test
    fun batchPassesSeedAndContextToSingleSubmitter() = runTest {
        val seeds = mutableListOf<String>()
        val contexts = mutableListOf<SubmissionRunContext>()
        val seededTemplate = template.copy(
            candidates = listOf(
                ParameterCandidate(
                    id = "1:seed",
                    nodeId = "1",
                    inputName = "seed",
                    label = "种子",
                    type = ParameterType.INT,
                    group = ParameterGroup.SAMPLING,
                    value = "10",
                ),
            ),
        )
        val center = OperationCenter(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            submitter = WorkflowSubmitter { _, selected, context ->
                contexts += context
                seeds += selected.first { it.id == "1:seed" }.value
                WorkflowSubmitResult.Success("p${context.runIndex}", 1)
            },
        )

        center.start(seededTemplate, 2, SeedStrategy.INCREMENT).getOrThrow()
        advanceUntilIdle()

        assertEquals(listOf("10", "11"), seeds)
        assertEquals(listOf(0, 1), contexts.map { it.batchIndex })
        assertEquals(listOf(2, 2), contexts.map { it.batchCount })
    }

    private fun testSubmitter(block: suspend () -> WorkflowSubmitResult) =
        WorkflowSubmitter { _, _, _ -> block() }
}

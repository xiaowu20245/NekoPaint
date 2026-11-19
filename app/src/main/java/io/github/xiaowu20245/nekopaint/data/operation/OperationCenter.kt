package io.github.xiaowu20245.nekopaint.data.operation

import io.github.xiaowu20245.nekopaint.core.common.NekoException
import io.github.xiaowu20245.nekopaint.core.common.NekoMessage
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.data.repository.SubmissionRunContext
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class OperationStatus {
    RUNNING,
    PARTIAL,
    SUCCESS,
    FAILED,
    UNKNOWN,
    CANCELLED,
}

data class SubmissionOperationState(
    val operationId: String,
    val templateId: String,
    val status: OperationStatus,
    val total: Int,
    val processed: Int,
    val success: Int,
    val failed: Int,
    val skipped: Int,
    val unknown: Int = 0,
    val lastSuccess: WorkflowSubmitResult.Success? = null,
    val lastValidation: WorkflowSubmitResult.Validation? = null,
    val lastTemplate: WorkflowTemplate? = null,
    val executionValuesPersisted: Boolean? = null,
    val localRecordWarning: NekoMessage? = null,
    val message: NekoMessage? = null,
    val updatedAt: Long = 0L,
) {
    val isTerminal: Boolean
        get() = status != OperationStatus.RUNNING
}

/**
 * 工作流批量提交的后台操作中心。
 *
 * 操作使用独立的 application scope，页面销毁只取消观察，不取消已启动的提交循环。
 */
class OperationCenter(
    private val scope: CoroutineScope,
    private val submitter: WorkflowSubmitter,
    private val persistExecutionValues: suspend (WorkflowTemplate) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val _states = MutableStateFlow<Map<String, SubmissionOperationState>>(emptyMap())
    val states: StateFlow<Map<String, SubmissionOperationState>> = _states.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val startLock = Any()

    fun start(
        template: WorkflowTemplate,
        totalRuns: Int,
        seedStrategy: SeedStrategy,
    ): Result<String> {
        pruneExpired()
        val total = totalRuns.coerceIn(1, 16)
        val operationId = UUID.randomUUID().toString()
        val job: Job
        synchronized(startLock) {
            val active = _states.value.values.firstOrNull {
                it.templateId == template.id && it.status == OperationStatus.RUNNING
            }
            if (active != null) {
                return Result.failure(
                    NekoException(NekoMessage(NekoMessageKey.OPERATION_ALREADY_RUNNING)),
                )
            }
            val initial = SubmissionOperationState(
                operationId = operationId,
                templateId = template.id,
                status = OperationStatus.RUNNING,
                total = total,
                processed = 0,
                success = 0,
                failed = 0,
                skipped = 0,
                updatedAt = now(),
            )
            update(initial)
            job = scope.launch(start = CoroutineStart.LAZY) {
                runOperation(operationId, template, total, seedStrategy)
            }
            jobs[operationId] = job
        }
        job.start()
        return Result.success(operationId)
    }

    fun cancel(operationId: String) {
        val state = _states.value[operationId] ?: return
        if (state.isTerminal) return
        jobs[operationId]?.cancel()
        jobs.remove(operationId)
        _states.update { states ->
            val current = states[operationId] ?: return@update states
            if (current.status != OperationStatus.RUNNING) return@update states
            states + (
                operationId to current.copy(
                    status = OperationStatus.CANCELLED,
                    skipped = current.total - current.processed,
                    message = NekoMessage(NekoMessageKey.OPERATION_CANCELLED),
                    updatedAt = now(),
                )
                )
        }
    }

    fun stateFor(templateId: String): SubmissionOperationState? =
        _states.value.values
            .filter { it.templateId == templateId }
            .maxByOrNull { it.updatedAt }

    private suspend fun runOperation(
        operationId: String,
        template: WorkflowTemplate,
        total: Int,
        seedStrategy: SeedStrategy,
    ) {
        var processed = 0
        var success = 0
        var failed = 0
        var unknown = 0
        var lastSuccess: WorkflowSubmitResult.Success? = null
        var lastValidation: WorkflowSubmitResult.Validation? = null
        var lastTemplate: WorkflowTemplate? = null
        var executionValuesPersisted: Boolean? = null
        var localRecordWarning: NekoMessage? = null
        var message: NekoMessage? = null
        var runSucceeded = false
        try {
            for (index in 0 until total) {
                runSucceeded = false
                val prepared = submitter.prepare(
                    template = template,
                    seedStrategy = seedStrategy,
                    context = SubmissionRunContext(
                        runIndex = index,
                        batchIndex = index,
                        batchCount = total,
                    ),
                )
                when (val result = submitter.submit(prepared)) {
                    is WorkflowSubmitResult.Success -> {
                        processed += 1
                        success += 1
                        runSucceeded = true
                        lastSuccess = result
                        lastTemplate = prepared.template
                        if (result.localRecordWarning != null) {
                            localRecordWarning = result.localRecordWarning
                        }
                    }

                    WorkflowSubmitResult.NoServer -> {
                        processed += 1
                        failed += 1
                        message = NekoMessage(NekoMessageKey.WORKFLOW_NO_SERVER)
                        break
                    }

                    is WorkflowSubmitResult.Validation -> {
                        processed += 1
                        failed += 1
                        message = result.summary
                        lastValidation = result
                        break
                    }

                    is WorkflowSubmitResult.Error -> {
                        processed += 1
                        failed += 1
                        message = result.message
                        break
                    }

                    is WorkflowSubmitResult.Unknown -> {
                        processed += 1
                        unknown += 1
                        message = result.message
                        break
                    }
                }
                update(
                    currentState(operationId, template.id, total).copy(
                        processed = processed,
                        success = success,
                        failed = failed,
                        unknown = unknown,
                        lastSuccess = lastSuccess,
                        lastValidation = lastValidation,
                        lastTemplate = lastTemplate,
                        localRecordWarning = localRecordWarning,
                        message = message,
                        updatedAt = now(),
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            update(
                currentState(operationId, template.id, total).copy(
                    status = OperationStatus.CANCELLED,
                    processed = processed,
                    success = success,
                    failed = failed,
                    unknown = unknown,
                    skipped = (total - processed).coerceAtLeast(0),
                    lastSuccess = lastSuccess,
                    lastTemplate = lastTemplate,
                    localRecordWarning = localRecordWarning,
                    message = NekoMessage(NekoMessageKey.OPERATION_CANCELLED),
                    updatedAt = now(),
                ),
            )
            throw cancelled
        } catch (throwable: Throwable) {
            val failedProcessed = if (runSucceeded) processed else (processed + 1).coerceAtMost(total)
            val failedCount = if (runSucceeded) failed else failed + 1
            executionValuesPersisted = if (success > 0) {
                lastTemplate?.let { completed ->
                    runCatching { persistExecutionValues(completed) }.isSuccess
                }
            } else {
                null
            }
            updateTerminal(
                currentState(operationId, template.id, total).copy(
                    status = if (success > 0) OperationStatus.PARTIAL else OperationStatus.FAILED,
                    processed = failedProcessed,
                    success = success,
                    failed = failedCount,
                    unknown = unknown,
                    skipped = (total - failedProcessed).coerceAtLeast(0),
                    lastSuccess = lastSuccess,
                    lastValidation = lastValidation,
                    lastTemplate = lastTemplate,
                    executionValuesPersisted = executionValuesPersisted,
                    localRecordWarning = localRecordWarning,
                    message = NekoMessage(
                        NekoMessageKey.OPERATION_EXCEPTION,
                        listOf(throwable.message ?: throwable.javaClass.simpleName),
                    ),
                    updatedAt = now(),
                ),
            )
            return
        } finally {
            jobs.remove(operationId)
        }
        val status = when {
            success > 0 && processed == total && failed == 0 && unknown == 0 ->
                OperationStatus.SUCCESS
            success > 0 -> OperationStatus.PARTIAL
            unknown > 0 -> OperationStatus.UNKNOWN
            else -> OperationStatus.FAILED
        }
        if (success > 0) {
            executionValuesPersisted = lastTemplate?.let { completed ->
                runCatching { persistExecutionValues(completed) }.isSuccess
            }
        }
        updateTerminal(
            currentState(operationId, template.id, total).copy(
                status = status,
                processed = processed,
                success = success,
                failed = failed,
                unknown = unknown,
                skipped = (total - processed).coerceAtLeast(0),
                lastSuccess = lastSuccess,
                lastValidation = lastValidation,
                lastTemplate = lastTemplate,
                executionValuesPersisted = executionValuesPersisted,
                localRecordWarning = localRecordWarning,
                message = message,
                updatedAt = now(),
            ),
        )
    }

    private fun currentState(
        operationId: String,
        templateId: String,
        total: Int,
    ): SubmissionOperationState =
        _states.value[operationId] ?: SubmissionOperationState(
            operationId = operationId,
            templateId = templateId,
            status = OperationStatus.RUNNING,
            total = total,
            processed = 0,
            success = 0,
            failed = 0,
            skipped = 0,
        )

    private fun update(state: SubmissionOperationState) {
        _states.update { it + (state.operationId to state) }
    }

    private fun updateTerminal(state: SubmissionOperationState) {
        _states.update { states ->
            val current = states[state.operationId]
            if (current != null && current.status != OperationStatus.RUNNING) {
                states
            } else {
                states + (state.operationId to state)
            }
        }
    }

    private fun pruneExpired() {
        val cutoff = now() - OPERATION_TTL_MS
        _states.update { states ->
            states.filterValues { !(it.isTerminal && it.updatedAt < cutoff) }
        }
    }

    private companion object {
        const val OPERATION_TTL_MS = 10 * 60 * 1000L
    }
}

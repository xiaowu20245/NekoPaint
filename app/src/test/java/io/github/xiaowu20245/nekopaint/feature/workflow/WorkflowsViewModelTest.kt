package io.github.xiaowu20245.nekopaint.feature.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.data.operation.OperationCenter
import io.github.xiaowu20245.nekopaint.data.repository.TaskRecordRepository
import io.github.xiaowu20245.nekopaint.data.repository.TaskStore
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowRepository
import io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitResult
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkflowsViewModelTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

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
    fun runningStateDoesNotSwallowTerminalSummary() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val filesDir = File(
            System.getProperty("java.io.tmpdir"),
            "nekopaint-workflows-vm-${System.nanoTime()}",
        ).apply { mkdirs() }
        var viewModel: WorkflowsViewModel? = null
        try {
            val gate = CompletableDeferred<Unit>()
            val operationCenter = OperationCenter(
                scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                submitter = io.github.xiaowu20245.nekopaint.data.repository.WorkflowSubmitter {
                        _,
                        _,
                        _,
                    ->
                    gate.await()
                    WorkflowSubmitResult.Success("p1", 1)
                },
            )
            val repository = WorkflowRepository.forTest(
                rootDir = File(filesDir, "workflows").apply { mkdirs() },
                json = json,
                config = null,
                serverId = "server-a",
                clientId = "client-a",
                apiFactory = ComfyApiFactory(OkHttpClient(), json),
                taskRecordRepository = TaskRecordRepository(TaskStore(filesDir, json)),
            )
            val created = WorkflowsViewModel(
                repository = repository,
                operationCenter = operationCenter,
                activeServerId = flowOf("server-a"),
            )
            viewModel = created
            waitUntil { !created.uiState.value.loading }

            operationCenter.start(template, 1, SeedStrategy.FIXED).getOrThrow()
            waitUntil { created.uiState.value.runningTemplateId == template.id }
            assertNull(created.uiState.value.actionNotice)

            gate.complete(Unit)
            waitUntil { created.uiState.value.runningTemplateId == null }

            val notice = created.uiState.value.actionNotice
            assertTrue(notice is WorkflowActionNotice.QuickSubmitted)
            assertEquals(1, (notice as WorkflowActionNotice.QuickSubmitted).count)
        } finally {
            viewModel?.viewModelScope?.cancel()
            Dispatchers.resetMain()
            filesDir.deleteRecursively()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "等待 ViewModel 状态超时" }
            Thread.sleep(10)
        }
    }
}

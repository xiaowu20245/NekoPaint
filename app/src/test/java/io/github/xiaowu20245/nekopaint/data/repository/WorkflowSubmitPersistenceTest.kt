package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.TaskRecord
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkflowSubmitPersistenceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private lateinit var filesDir: File
    private lateinit var workflowsDir: File
    private lateinit var server: MockWebServer
    private lateinit var taskRepository: TaskRecordRepository
    private lateinit var repository: WorkflowRepository

    @Before
    fun setUp() {
        filesDir = File(
            System.getProperty("java.io.tmpdir"),
            "nekopaint-submit-${System.nanoTime()}",
        ).apply { mkdirs() }
        workflowsDir = File(filesDir, "workflows").apply { mkdirs() }
        server = MockWebServer()
        server.start()
        taskRepository = TaskRecordRepository(TaskStore(filesDir, json))
        repository = WorkflowRepository.forTest(
            rootDir = workflowsDir,
            json = json,
            config = ServerConfig(host = server.hostName, port = server.port),
            serverId = SERVER_ID,
            clientId = CLIENT_ID,
            apiFactory = ComfyApiFactory(OkHttpClient(), json),
            taskRecordRepository = taskRepository,
        )
        runBlocking {
            WorkflowTemplateStore(workflowsDir, json).saveTemplate(template(), RAW_JSON)
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        filesDir.deleteRecursively()
    }

    @Test
    fun prewriteFailureDoesNotPost() = runTest {
        File(filesDir, "servers").writeText("blocked")

        val result = repository.submit(template())

        assertTrue(result is WorkflowSubmitResult.Error)
        assertTrue((result as WorkflowSubmitResult.Error).message.contains("未提交"))
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun successPrewritesSubmittingThenQueuesWithTheRequestedId() = runTest {
        val statuses = mutableListOf<String>()
        taskRepository.addObserver(object : TaskRecordObserver {
            override fun onTaskRecord(serverId: String, record: TaskRecord, sequence: Long) {
                statuses += record.status
            }
        })
        server.dispatcher = echoDispatcher { promptId ->
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"prompt_id":"$promptId","number":1}""")
        }

        val result = repository.submit(template())

        assertTrue(result is WorkflowSubmitResult.Success)
        val success = result as WorkflowSubmitResult.Success
        assertEquals(listOf(TaskStatus.SUBMITTING, TaskStatus.QUEUED), statuses)
        assertEquals(success.promptId, taskRepository.get(SERVER_ID, success.promptId)?.promptId)
        val stored = taskRepository.get(SERVER_ID, success.promptId)!!
        assertEquals(TaskStatus.QUEUED, stored.status)
        assertEquals(success.promptId, stored.requestedPromptId)
        assertEquals("42", stored.values["1:seed"])
        assertEquals(1, server.requestCount)
    }

    @Test
    fun ioFailureBecomesUnknownWithTheSameRequestedId() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        }

        val result = repository.submit(template())

        assertTrue(result is WorkflowSubmitResult.Unknown)
        val unknown = result as WorkflowSubmitResult.Unknown
        val stored = taskRepository.get(SERVER_ID, unknown.promptId)!!
        assertEquals(TaskStatus.UNKNOWN, stored.status)
        assertEquals(unknown.promptId, stored.requestedPromptId)
    }

    @Test
    fun retryable429RetriesOnceWithTheSameIdAndThenSucceeds() = runTest {
        val ids = mutableListOf<String>()
        var calls = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val id = requestPromptId(request) ?: "missing"
                ids += id
                val call = calls++
                return if (call == 0) {
                    MockResponse()
                        .setResponseCode(429)
                        .setBody("""{"error":{"message":"busy"}}""")
                } else {
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"prompt_id":"$id","number":3}""")
                }
            }
        }

        val result = repository.submit(template())

        assertTrue(result is WorkflowSubmitResult.Success)
        assertEquals(2, ids.size)
        assertEquals(ids[0], ids[1])
        assertEquals(TaskStatus.QUEUED, taskRepository.get(SERVER_ID, ids[0])?.status)
    }

    @Test
    fun repeated500BecomesUnknownAfterOneRetry() = runTest {
        var calls = 0
        val ids = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                calls += 1
                ids += requestPromptId(request) ?: "missing"
                return MockResponse()
                    .setResponseCode(500)
                    .setBody("""{"error":{"message":"boom"}}""")
            }
        }

        val result = repository.submit(template())

        assertTrue(result is WorkflowSubmitResult.Unknown)
        assertEquals(2, calls)
        assertEquals(ids[0], ids[1])
        assertEquals(TaskStatus.UNKNOWN, taskRepository.get(SERVER_ID, ids[0])?.status)
    }

    @Test
    fun validationFailureDoesNotRetryAndStoresFailedRecord() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(
                    """{"error":{"message":"bad params"},"node_errors":""" +
                        """{"1":{"errors":[{"message":"seed invalid","input_name":"seed","details":"range"}]}}}""",
                ),
        )

        val result = repository.submit(template())

        assertTrue(result is WorkflowSubmitResult.Validation)
        val validation = result as WorkflowSubmitResult.Validation
        assertEquals("bad params", validation.summary)
        assertEquals(1, validation.nodeErrors.size)
        assertEquals("seed", validation.nodeErrors.single().inputName)
        assertEquals("range", validation.nodeErrors.single().details)
        assertEquals(1, server.requestCount)
        val stored = taskRecords().single()
        assertEquals(TaskStatus.FAILED, stored.status)
        assertEquals("bad params", stored.errorMessage)
    }

    @Test
    fun responseIdMigrationDeletesTheRequestedRecord() = runTest {
        server.dispatcher = echoDispatcher {
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"prompt_id":"server-new","number":2}""")
        }

        val result = repository.submit(template())

        assertTrue(result is WorkflowSubmitResult.Success)
        val success = result as WorkflowSubmitResult.Success
        val requestId = requestPromptId(server.takeRequest(2, TimeUnit.SECONDS)!!)!!
        assertEquals("server-new", success.promptId)
        assertNull(success.localRecordWarning)
        val migrated = taskRepository.get(SERVER_ID, "server-new")!!
        assertEquals(requestId, migrated.requestedPromptId)
        assertNull(taskRepository.get(SERVER_ID, requestId))
    }

    @Test
    fun emptySuccessBodyBecomesUnknown() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = repository.submit(template())

        assertTrue(result is WorkflowSubmitResult.Unknown)
        val unknown = result as WorkflowSubmitResult.Unknown
        assertEquals(TaskStatus.UNKNOWN, taskRepository.get(SERVER_ID, unknown.promptId)?.status)
    }

    @Test
    fun migrationDeleteFailureReturnsWarning() = runTest {
        val old = unknownRecord("old-id", "7")
        assertTrue(taskRepository.upsert(SERVER_ID, old).isSuccess)
        val oldFile = taskFile(SERVER_ID, "old-id")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestPromptId(request)
                // 让旧记录路径变成非空目录，模拟删除失败但记录仍在的异常存储状态。
                oldFile.delete()
                oldFile.mkdirs()
                File(oldFile, "block").writeText("x")
                return MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"prompt_id":"new-id","number":1}""")
            }
        }

        val result = repository.retryUnknown(old)

        assertTrue(result is WorkflowSubmitResult.Success)
        val warning = (result as WorkflowSubmitResult.Success).localRecordWarning.orEmpty()
        assertTrue(warning.contains("旧请求记录"))
    }

    @Test
    fun newRecordSaveFailureMarksOldRecordSuperseded() = runTest {
        val old = unknownRecord("old-id", "7")
        assertTrue(taskRepository.upsert(SERVER_ID, old).isSuccess)
        val blockedNewFile = taskFile(SERVER_ID, "new-id")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestPromptId(request)
                // 新记录路径不可写，模拟服务器已接收但本地新记录落盘失败。
                blockedNewFile.mkdirs()
                File(blockedNewFile, "block").writeText("x")
                return MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"prompt_id":"new-id","number":1}""")
            }
        }

        val result = repository.retryUnknown(old)

        assertTrue(result is WorkflowSubmitResult.Success)
        assertTrue(
            (result as WorkflowSubmitResult.Success)
                .localRecordWarning
                .orEmpty()
                .contains("本地任务记录保存失败"),
        )
        val storedOld = taskRepository.get(SERVER_ID, "old-id")!!
        assertEquals("new-id", storedOld.supersededBy)
        assertEquals(TaskStatus.UNKNOWN, storedOld.status)
        val retryAgain = repository.retryUnknown(old)
        assertTrue(retryAgain is WorkflowSubmitResult.Error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun retryUnknownUsesNewIdForMigratedNewRecord() = runTest {
        val old = unknownRecord("old-id", "7")
        val migratedNew = old.copy(
            promptId = "new-id",
            status = TaskStatus.UNKNOWN,
            requestedPromptId = "old-id",
            submittedAt = 2L,
        )
        assertTrue(taskRepository.upsert(SERVER_ID, old).isSuccess)
        assertTrue(taskRepository.upsert(SERVER_ID, migratedNew).isSuccess)
        server.dispatcher = echoDispatcher { promptId ->
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"prompt_id":"$promptId","number":1}""")
        }

        val result = repository.retryUnknown(migratedNew)

        assertTrue(result is WorkflowSubmitResult.Success)
        assertEquals("new-id", (result as WorkflowSubmitResult.Success).promptId)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("new-id", requestPromptId(request))
        val storedNew = taskRepository.get(SERVER_ID, "new-id")!!
        assertEquals(TaskStatus.QUEUED, storedNew.status)
        assertEquals("old-id", storedNew.requestedPromptId)
        assertEquals(TaskStatus.UNKNOWN, taskRepository.get(SERVER_ID, "old-id")?.status)

        // 新记录仍保留 old→new 映射，旧记录必须继续被阻断。
        val oldRetry = repository.retryUnknown(old)
        assertTrue(oldRetry is WorkflowSubmitResult.Error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun retryUnknownRejectsOriginalWhenMigratedNewIsUnknown() = runTest {
        val old = unknownRecord("old-id", "7")
        val migratedNew = old.copy(
            promptId = "new-id",
            status = TaskStatus.UNKNOWN,
            requestedPromptId = "old-id",
            submittedAt = 2L,
        )
        assertTrue(taskRepository.upsert(SERVER_ID, old).isSuccess)
        assertTrue(taskRepository.upsert(SERVER_ID, migratedNew).isSuccess)

        val result = repository.retryUnknown(old)

        assertTrue(result is WorkflowSubmitResult.Error)
        assertTrue((result as WorkflowSubmitResult.Error).message.contains("接管"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun retryUnknownRejectsPersistedSupersededMarker() = runTest {
        val old = unknownRecord("old-id", "7").copy(supersededBy = "new-id")
        assertTrue(taskRepository.upsert(SERVER_ID, old).isSuccess)

        val result = repository.retryUnknown(old)

        assertTrue(result is WorkflowSubmitResult.Error)
        assertTrue((result as WorkflowSubmitResult.Error).message.contains("接管"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun retryUnknownRejectsRecordMigratedToNewPromptId() = runTest {
        val old = unknownRecord("old-id", "7")
        assertTrue(taskRepository.upsert(SERVER_ID, old).isSuccess)
        assertTrue(
            taskRepository.upsert(
                SERVER_ID,
                old.copy(
                    promptId = "new-id",
                    status = TaskStatus.QUEUED,
                    queueNumber = 1,
                    requestedPromptId = "old-id",
                    submittedAt = 2L,
                ),
            ).isSuccess,
        )

        val result = repository.retryUnknown(old)

        assertTrue(result is WorkflowSubmitResult.Error)
        assertTrue((result as WorkflowSubmitResult.Error).message.contains("接管"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun finalPersistenceFailureStillReturnsSuccessWithWarning() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val id = requestPromptId(request) ?: "missing"
                runBlocking { taskRepository.delete(SERVER_ID, id) }
                return MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"prompt_id":"$id","number":1}""")
            }
        }

        val result = repository.submit(template())

        assertTrue(result is WorkflowSubmitResult.Success)
        assertNotNull((result as WorkflowSubmitResult.Success).localRecordWarning)
    }

    @Test
    fun retryUnknownReusesTheOriginalPromptIdAndValues() = runTest {
        val unknown = TaskRecord(
            promptId = "unknown-id",
            serverId = SERVER_ID,
            templateId = TEMPLATE_ID,
            templateName = TEMPLATE_NAME,
            clientId = CLIENT_ID,
            submittedAt = 1L,
            status = TaskStatus.UNKNOWN,
            values = mapOf("1:seed" to "77"),
            requestedAt = 1L,
            requestedPromptId = "unknown-id",
            statusUpdatedAt = 1L,
            statusSource = TaskStatusSource.LOCAL.name,
        )
        assertTrue(taskRepository.upsert(SERVER_ID, unknown).isSuccess)
        server.dispatcher = echoDispatcher { promptId ->
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"prompt_id":"$promptId","number":1}""")
        }

        val result = repository.retryUnknown(unknown)

        assertTrue(result is WorkflowSubmitResult.Success)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("unknown-id", requestPromptId(request))
        assertEquals(TaskStatus.QUEUED, taskRepository.get(SERVER_ID, "unknown-id")?.status)
    }

    private fun echoDispatcher(
        response: (String) -> MockResponse,
    ): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val id = requestPromptId(request)
                ?: return MockResponse().setResponseCode(400).setBody("missing prompt_id")
            return response(id)
        }
    }

    private fun requestPromptId(request: RecordedRequest): String? =
        Regex("\"prompt_id\"\\s*:\\s*\"([^\"]+)\"")
            .find(request.body.readUtf8())
            ?.groupValues
            ?.get(1)

    private fun taskRecords(): List<TaskRecord> = runBlocking {
        TaskStore(filesDir, json).list(SERVER_ID)
    }

    private fun taskFile(serverId: String, promptId: String): File = File(
        filesDir,
        "servers/${SafeFileNames.encode(serverId)}/tasks/" +
            "${SafeFileNames.encode(promptId)}.json",
    )

    private fun unknownRecord(promptId: String, seed: String) = TaskRecord(
        promptId = promptId,
        serverId = SERVER_ID,
        templateId = TEMPLATE_ID,
        templateName = TEMPLATE_NAME,
        clientId = CLIENT_ID,
        submittedAt = 1L,
        status = TaskStatus.UNKNOWN,
        values = mapOf("1:seed" to seed),
        requestedAt = 1L,
        requestedPromptId = promptId,
        statusUpdatedAt = 1L,
        statusSource = TaskStatusSource.LOCAL.name,
    )

    private fun template() = WorkflowTemplate(
        id = TEMPLATE_ID,
        name = TEMPLATE_NAME,
        sourceFileName = "workflow.json",
        sourceHash = sha256(RAW_JSON),
        importedAt = 1L,
        nodeCount = 1,
        reachableNodeCount = 1,
        outputNodeIds = listOf("1"),
        candidates = listOf(
            ParameterCandidate(
                id = "1:seed",
                nodeId = "1",
                inputName = "seed",
                label = "Seed",
                type = ParameterType.INT,
                group = ParameterGroup.SAMPLING,
                defaultValue = "1",
                value = "42",
                selected = true,
                classType = "TestSampler",
            ),
        ),
    )

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val SERVER_ID = "server-a"
        const val CLIENT_ID = "client-a"
        const val TEMPLATE_ID = "template-1"
        const val TEMPLATE_NAME = "测试工作流"
        const val RAW_JSON =
            """{"1":{"class_type":"TestSampler","inputs":{"seed":1,"steps":20}}}"""
    }
}

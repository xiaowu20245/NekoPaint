package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.common.NekoException
import io.github.xiaowu20245.nekopaint.core.common.NekoMessageKey
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPackage
import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPlan
import io.github.xiaowu20245.nekopaint.core.model.workflow.ServerBinding
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.workflow.ServerBindingPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NodeInstallRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }
    private lateinit var server: MockWebServer
    private lateinit var repository: NodeInstallRepository
    private lateinit var profile: ServerProfile
    private var reanalyzed = 0
    private var reanalyzeResult: io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate? = null
    private var lastReanalyzeBinding: ServerBinding? = null

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        profile = profile("server-1")
        repository = NodeInstallRepository(
            NodeInstallRepositoryDependencies(
                apiFactory = ComfyApiFactory(OkHttpClient(), json),
                activeProfile = { profile },
                templateProvider = { null },
                analyzeCompatibility = { _, _ -> null },
                saveCompatibility = { _, _, _ -> null },
                reanalyzeAndSave = { _, binding ->
                    lastReanalyzeBinding = binding
                    reanalyzed++
                    reanalyzeResult
                },
                pollDelay = {},
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun baselineBusySubmitsZeroInstalls() = runBlocking {
        server.enqueue(status(isProcessing = true, inProgress = 1, total = 1))

        val result = repository.install(plan(), restartAfterInstall = true)

        val outcome = result.getOrThrow()
        assertTrue(outcome.queueBusy)
        assertEquals(0, outcome.queuedCount)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun baselineUnavailableSubmitsZeroInstalls() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val outcome = repository.install(plan(), restartAfterInstall = false).getOrThrow()

        assertTrue(outcome.queueStatusUnavailable)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun missingBindingRejectsLegacyPlan() = runBlocking {
        val legacy = plan().copy(binding = null)

        val result = repository.install(legacy, restartAfterInstall = false)

        assertTrue(result.isFailure)
        assertEquals(
            NekoMessageKey.NODE_INSTALL_PLAN_NO_BINDING_NEW,
            (result.exceptionOrNull() as NekoException).nekoMessage.key,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun activeThenIdleCompletesWithoutRestart() = runBlocking {
        server.enqueue(status())
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(status(isProcessing = true, inProgress = 1, total = 1))
        server.enqueue(status())

        val outcome = repository.install(plan(), restartAfterInstall = false).getOrThrow()

        assertEquals(1, outcome.queuedCount)
        assertFalse(outcome.queueBusy)
        assertFalse(outcome.queueUnconfirmed)
        assertFalse(outcome.restartRequested)
        assertEquals(0, reanalyzed)
        assertEquals(
            listOf(
                "/manager/queue/status",
                "/manager/queue/install",
                "/manager/queue/start",
                "/manager/queue/status",
                "/manager/queue/status",
            ),
            recordedPaths(),
        )
    }

    @Test
    fun neverActiveIsUnconfirmedAndDoesNotReboot() = runBlocking {
        server.enqueue(status())
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        repeat(15) {
            server.enqueue(status())
        }

        val outcome = repository.install(plan(), restartAfterInstall = true).getOrThrow()

        assertTrue(outcome.queueUnconfirmed)
        assertFalse(outcome.restartRequested)
        assertFalse(recordedPaths().contains("/manager/reboot"))
    }

    @Test
    fun serverChangeAfterBaselineStopsBeforeInstall() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/manager/queue/status") {
                    profile = profile("server-2")
                    return status()
                }
                return MockResponse().setResponseCode(200)
            }
        }

        val outcome = repository.install(plan(), restartAfterInstall = true).getOrThrow()

        assertTrue(outcome.serverChanged)
        assertEquals(0, outcome.queuedCount)
        assertFalse(recordedPaths().contains("/manager/queue/install"))
    }

    @Test
    fun activeThenIdleRestartsOnlyAfterCompletion() = runBlocking {
        reanalyzeResult = workflowTemplate().copy(missingNodeTypes = emptyList())
        server.enqueue(status())
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(status(isProcessing = true, inProgress = 1, total = 1))
        server.enqueue(status())
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{}"),
        )

        val outcome = repository.install(plan(), restartAfterInstall = true).getOrThrow()

        assertTrue(outcome.restartRequested)
        assertTrue(outcome.serverReconnected)
        assertFalse(outcome.recheckFailed)
        assertEquals(emptyList<String>(), outcome.remainingMissingNodeTypes)
        assertEquals(1, reanalyzed)
        assertEquals(ServerBindingPolicy.fromProfile(profile), lastReanalyzeBinding)
        val paths = recordedPaths()
        assertTrue(paths.contains("/manager/reboot"))
        assertTrue(paths.contains("/system_stats"))
    }

    @Test
    fun preparePlanCapturesActiveBinding() = runBlocking {
        val template = workflowTemplate()
        val repository = NodeInstallRepository(
            NodeInstallRepositoryDependencies(
                apiFactory = ComfyApiFactory(OkHttpClient(), json),
                activeProfile = { profile },
                templateProvider = { template },
                analyzeCompatibility = { _, _ -> null },
                saveCompatibility = { _, _, _ -> null },
                reanalyzeAndSave = { _, _ -> null },
                pollDelay = {},
            ),
        )
        server.enqueue(MockResponse().setBody("1.2.3"))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"pack-a":[["NodeA"],{"title":"Pack A",""" +
                        """"files":["https://github.com/a/a.git"],"version":"1.0"}]}""",
                ),
        )

        val plan = repository.preparePlan("template-1", reanalyze = false).getOrThrow()

        assertEquals(ServerBindingPolicy.fromProfile(profile), plan.binding)
        assertEquals(1, plan.packageCount)
        assertEquals("pack-a", plan.packages.single().id)
    }

    @Test
    fun preparePlanRejectsServerChangeDuringPreparation() = runBlocking {
        val template = workflowTemplate()
        val repository = NodeInstallRepository(
            NodeInstallRepositoryDependencies(
                apiFactory = ComfyApiFactory(OkHttpClient(), json),
                activeProfile = { profile },
                templateProvider = { template },
                analyzeCompatibility = { _, _ -> null },
                saveCompatibility = { _, _, _ -> null },
                reanalyzeAndSave = { _, _ -> null },
                pollDelay = {},
            ),
        )
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path.orEmpty().startsWith("/manager/version")) {
                    profile = profile("server-2")
                    return MockResponse().setBody("1.2.3")
                }
                return MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"pack-a":[["NodeA"],{"title":"Pack A",""" +
                            """"files":["https://github.com/a/a.git"]}]}""",
                    )
            }
        }

        val result = repository.preparePlan("template-1", reanalyze = false)

        assertTrue(result.isFailure)
        assertEquals(
            NekoMessageKey.NODE_INSTALL_SERVER_CHANGED,
            (result.exceptionOrNull() as NekoException).nekoMessage.key,
        )
    }

    private fun workflowTemplate() = io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate(
        id = "template-1",
        name = "T2I",
        sourceFileName = "api.json",
        sourceHash = "hash",
        importedAt = 1L,
        nodeCount = 1,
        reachableNodeCount = 1,
        missingNodeTypes = listOf("NodeA"),
        catalogChecked = true,
    )

    @Test
    fun recheckWithoutBindingRejectsLegacyRequest() = runBlocking {
        val result = repository.recheck("template-1", binding = null)

        assertTrue(result.isFailure)
        assertEquals(
            NekoMessageKey.NODE_INSTALL_PLAN_NO_BINDING_RETRY,
            (result.exceptionOrNull() as NekoException).nekoMessage.key,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun recheckRejectsServerChangedAfterInstall() = runBlocking {
        val binding = ServerBindingPolicy.fromProfile(profile)
        profile = profile("server-2")

        val result = repository.recheck("template-1", binding)

        assertTrue(result.isFailure)
        assertEquals(
            NekoMessageKey.NODE_INSTALL_SERVER_CHANGED,
            (result.exceptionOrNull() as NekoException).nekoMessage.key,
        )
        assertEquals(0, reanalyzed)
    }

    @Test
    fun recheckServerChangeDuringAnalysisFails() = runBlocking {
        val binding = ServerBindingPolicy.fromProfile(profile)
        val repository = NodeInstallRepository(
            NodeInstallRepositoryDependencies(
                apiFactory = ComfyApiFactory(OkHttpClient(), json),
                activeProfile = { profile },
                templateProvider = { null },
                analyzeCompatibility = { _, _ -> null },
                saveCompatibility = { _, _, _ -> null },
                reanalyzeAndSave = { _, _ ->
                    profile = profile("server-2")
                    workflowTemplate()
                },
                pollDelay = {},
            ),
        )

        val result = repository.recheck("template-1", binding)

        assertTrue(result.isFailure)
        assertEquals(
            NekoMessageKey.NODE_INSTALL_SERVER_CHANGED,
            (result.exceptionOrNull() as NekoException).nekoMessage.key,
        )
    }

    @Test
    fun recheckNullResultAfterServerChangeReportsServerChanged() = runBlocking {
        val binding = ServerBindingPolicy.fromProfile(profile)
        val repository = NodeInstallRepository(
            NodeInstallRepositoryDependencies(
                apiFactory = ComfyApiFactory(OkHttpClient(), json),
                activeProfile = { profile },
                templateProvider = { null },
                analyzeCompatibility = { _, _ -> null },
                saveCompatibility = { _, _, _ -> null },
                reanalyzeAndSave = { _, _ ->
                    profile = profile("server-2")
                    null
                },
                pollDelay = {},
            ),
        )

        val result = repository.recheck("template-1", binding)

        assertTrue(result.isFailure)
        assertEquals(
            NekoMessageKey.NODE_INSTALL_SERVER_CHANGED,
            (result.exceptionOrNull() as NekoException).nekoMessage.key,
        )
    }

    @Test
    fun activeButBelowQueuedIsUnconfirmed() = runBlocking {
        server.enqueue(status())
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(status(isProcessing = true, inProgress = 1, total = 0))

        val outcome = repository.install(plan(), restartAfterInstall = true).getOrThrow()

        assertTrue(outcome.queueUnconfirmed)
        assertFalse(outcome.restartRequested)
        assertFalse(recordedPaths().contains("/manager/reboot"))
    }

    @Test
    fun rebootRejectedDoesNotReconnectOrRecheck() = runBlocking {
        server.enqueue(status())
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(status(isProcessing = true, total = 1))
        server.enqueue(status())
        server.enqueue(MockResponse().setResponseCode(500))

        val outcome = repository.install(plan(), restartAfterInstall = true).getOrThrow()

        assertTrue(outcome.restartRejected)
        assertFalse(outcome.serverReconnected)
        assertEquals(0, reanalyzed)
        assertFalse(recordedPaths().contains("/system_stats"))
    }

    @Test
    fun recheckFailureAfterRebootIsVisible() = runBlocking {
        server.enqueue(status())
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(status(isProcessing = true, total = 1))
        server.enqueue(status())
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))

        val outcome = repository.install(plan(), restartAfterInstall = true).getOrThrow()

        assertTrue(outcome.restartRequested)
        assertTrue(outcome.serverReconnected)
        assertTrue(outcome.recheckFailed)
        assertTrue(outcome.summary().contains("无法确认节点是否补齐"))
    }

    @Test
    fun nonSuccessfulSystemStatsDoesNotCountAsReconnected() = runBlocking {
        server.enqueue(status())
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(status(isProcessing = true, total = 1))
        server.enqueue(status())
        server.enqueue(MockResponse().setResponseCode(200))
        repeat(45) {
            server.enqueue(MockResponse().setResponseCode(500))
        }

        val outcome = repository.install(plan(), restartAfterInstall = true).getOrThrow()

        assertFalse(outcome.restartRejected)
        assertFalse(outcome.serverReconnected)
        assertEquals(0, reanalyzed)
    }

    private fun plan(
        binding: ServerBinding = ServerBindingPolicy.fromProfile(profile),
    ) = NodeInstallPlan(
        templateId = "template-1",
        templateName = "T2I",
        missingNodeTypes = listOf("NodeA"),
        packages = listOf(
            NodeInstallPackage(
                id = "pack-a",
                title = "Pack A",
                files = listOf("https://github.com/a/a.git"),
            ),
        ),
        binding = binding,
    )

    private fun profile(id: String) = ServerProfile(
        id = id,
        name = id,
        scheme = "http",
        host = server.hostName,
        port = server.port,
    )

    private fun status(
        isProcessing: Boolean = false,
        inProgress: Int = 0,
        total: Int = 0,
        done: Int = 0,
    ) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"total_count":$total,"done_count":$done,""" +
                """"in_progress_count":$inProgress,"is_processing":$isProcessing}""",
        )

    private fun recordedPaths(): List<String> = buildList {
        while (true) {
            val request = server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS)
                ?: break
            add(request.path.orEmpty())
        }
    }
}

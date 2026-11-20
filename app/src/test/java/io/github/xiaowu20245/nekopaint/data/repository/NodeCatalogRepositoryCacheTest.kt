package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.network.ComfyApiFactory
import io.github.xiaowu20245.nekopaint.core.workflow.NodeCatalogCache
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NodeCatalogRepositoryCacheTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var root: File
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "nekopaint-catalog-${System.nanoTime()}")
            .apply { mkdirs() }
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        root.deleteRecursively()
    }

    @Test
    fun repeatedAnalysisUsesCacheAndManualRefreshForcesRequest() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template(), RAW_API)
        val config = ServerConfig(host = server.hostName, port = server.port)
        val repository = WorkflowRepository.forTest(
            rootDir = root,
            json = json,
            config = config,
            serverId = "server-a",
            clientId = "client",
            apiFactory = ComfyApiFactory(OkHttpClient(), json),
            taskRecordRepository = TaskRecordRepository(TaskStore(root, json)),
            nodeCatalogCache = NodeCatalogCache(File(root, "node_catalog"), json),
        )
        val view = store.getTemplate("t1")!!

        server.enqueue(objectInfoResponse())
        server.enqueue(objectInfoResponse())

        repository.analyzeCompatibility(view)
        repository.analyzeCompatibility(view)
        assertEquals(1, server.requestCount)

        repository.reanalyzeAndSave("t1")
        assertEquals(2, server.requestCount)
    }

    @Test
    fun serverSwitchDuringRequestDoesNotWriteOldCatalogBack() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(
            template().copy(missingNodeTypes = listOf("OldMissing")),
            RAW_API,
        )
        val config = ServerConfig(host = server.hostName, port = server.port)
        var serverIdCalls = 0
        val repository = WorkflowRepository.forTest(
            rootDir = root,
            json = json,
            config = config,
            serverId = "server-a",
            clientId = "client",
            apiFactory = ComfyApiFactory(OkHttpClient(), json),
            taskRecordRepository = TaskRecordRepository(TaskStore(root, json)),
            nodeCatalogCache = NodeCatalogCache(File(root, "node_catalog"), json),
            activeServerIdProvider = {
                serverIdCalls += 1
                if (serverIdCalls <= 2) "server-a" else "server-b"
            },
        )
        server.enqueue(objectInfoResponse())

        val refreshed = repository.refreshAllCompatibility()

        assertEquals(null, refreshed)
        assertEquals(
            listOf("OldMissing"),
            store.getTemplate("t1")!!.missingNodeTypes,
        )
        assertTrue(
            File(root, "node_catalog/server-a").listFiles().orEmpty().isEmpty(),
        )
    }

    @Test
    fun cacheHitReturnsNullWhenIdentityChangesWhileReadingCache() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template(), RAW_API)
        val config = ServerConfig(host = server.hostName, port = server.port)
        val endpointKey = io.github.xiaowu20245.nekopaint.core.network.ServerEndpointValidator
            .require(config).endpointKey
        val cache = NodeCatalogCache(File(root, "node_catalog"), json)
        cache.save(
            "server-a",
            endpointKey,
            io.github.xiaowu20245.nekopaint.core.workflow.NodeCatalog(
                mapOf("KSampler" to io.github.xiaowu20245.nekopaint.core.network.NodeInfoDto()),
            ),
        )
        var serverIdCalls = 0
        val repository = WorkflowRepository.forTest(
            rootDir = root,
            json = json,
            config = config,
            serverId = "server-a",
            clientId = "client",
            apiFactory = ComfyApiFactory(OkHttpClient(), json),
            taskRecordRepository = TaskRecordRepository(TaskStore(root, json)),
            nodeCatalogCache = cache,
            activeServerIdProvider = {
                serverIdCalls += 1
                if (serverIdCalls == 1) "server-a" else "server-b"
            },
        )

        val analyzed = repository.analyzeCompatibility(store.getTemplate("t1")!!)

        // 身份已变化时旧缓存必须被丢弃，不能表现为“已检查过节点”。
        assertEquals(false, analyzed?.catalogChecked)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun guardFailureAfterAnalysisWritesNoTemplatesAndReturnsNull() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(
            template().copy(missingNodeTypes = listOf("OldMissing")),
            RAW_API,
        )
        val config = ServerConfig(host = server.hostName, port = server.port)
        var serverIdCalls = 0
        val repository = WorkflowRepository.forTest(
            rootDir = root,
            json = json,
            config = config,
            serverId = "server-a",
            clientId = "client",
            apiFactory = ComfyApiFactory(OkHttpClient(), json),
            taskRecordRepository = TaskRecordRepository(TaskStore(root, json)),
            nodeCatalogCache = NodeCatalogCache(File(root, "node_catalog"), json),
            activeServerIdProvider = {
                serverIdCalls += 1
                if (serverIdCalls <= 4) "server-a" else "server-b"
            },
        )
        server.enqueue(objectInfoResponse())

        val refreshed = repository.refreshAllCompatibility()

        assertEquals(null, refreshed)
        assertEquals(
            listOf("OldMissing"),
            store.getTemplate("t1")!!.missingNodeTypes,
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun unanalyzableTemplatesStayInReturnedList() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template("t1"), RAW_API)
        store.saveTemplate(
            template("t2").copy(repairRequired = true, missingNodeTypes = listOf("NeedsRepair")),
            RAW_API,
        )
        val catalog = io.github.xiaowu20245.nekopaint.core.workflow.NodeCatalog(
            mapOf("KSampler" to io.github.xiaowu20245.nekopaint.core.network.NodeInfoDto()),
        )

        val refreshed = store.refreshAllCompatibility(catalog)

        assertEquals(setOf("t1", "t2"), refreshed?.map { it.id }?.toSet())
        assertEquals(
            listOf("NeedsRepair"),
            store.getTemplate("t2")!!.missingNodeTypes,
        )
    }

    @Test
    fun writeLoopGuardFailureStopsFurtherTemplateWrites() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(
            template("t1").copy(missingNodeTypes = listOf("Old")),
            RAW_API,
        )
        store.saveTemplate(
            template("t2").copy(missingNodeTypes = listOf("Old")),
            RAW_API,
        )
        val catalog = io.github.xiaowu20245.nekopaint.core.workflow.NodeCatalog(
            mapOf("KSampler" to io.github.xiaowu20245.nekopaint.core.network.NodeInfoDto()),
        )
        var guardCalls = 0

        val refreshed = store.refreshAllCompatibility(catalog) {
            guardCalls += 1
            // 初始 guard + 第一个模板写入前通过；第二个模板写入前失败。
            guardCalls <= 2
        }

        assertEquals(null, refreshed)
        val remainingOld = listOf("t1", "t2").count { id ->
            store.getTemplate(id)!!.missingNodeTypes == listOf("Old")
        }
        assertEquals(1, remainingOld)
    }

    private fun objectInfoResponse(): MockResponse = MockResponse().setBody(
        """
        {
          "KSampler": {"input": {"required": {}}},
          "CheckpointLoaderSimple": {"input": {"required": {}}},
          "CLIPTextEncode": {"input": {"required": {}}},
          "EmptyLatentImage": {"input": {"required": {}}},
          "SaveImage": {"input": {"required": {}}}
        }
        """.trimIndent(),
    )

    private fun template(id: String = "t1"): WorkflowTemplate = WorkflowTemplate(
        id = id,
        name = "template",
        sourceFileName = "api.json",
        sourceHash = RAW_API.sha256(),
        importedAt = 1L,
        nodeCount = 6,
        reachableNodeCount = 6,
        outputNodeIds = listOf("6"),
    )

    private companion object {
        val RAW_API = """
            {
              "1": {"class_type": "KSampler", "inputs": {
                "seed": 1, "steps": 20, "cfg": 7.5, "sampler_name": "euler",
                "scheduler": "normal", "denoise": 1.0, "model": ["2", 0],
                "positive": ["3", 0], "negative": ["4", 0], "latent_image": ["5", 0]
              }},
              "2": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "model.safetensors"}},
              "3": {"class_type": "CLIPTextEncode", "inputs": {"text": "a cat", "clip": ["2", 1]}},
              "4": {"class_type": "CLIPTextEncode", "inputs": {"text": "bad", "clip": ["2", 1]}},
              "5": {"class_type": "EmptyLatentImage", "inputs": {"width": 512, "height": 512, "batch_size": 1}},
              "6": {"class_type": "SaveImage", "inputs": {"filename_prefix": "test", "images": ["1", 0]}}
            }
        """.trimIndent()

        private fun String.sha256(): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}

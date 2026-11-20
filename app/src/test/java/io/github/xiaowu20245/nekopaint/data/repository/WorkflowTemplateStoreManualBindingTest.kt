package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.network.NodeInfoDto
import io.github.xiaowu20245.nekopaint.core.workflow.NodeCatalog
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkflowTemplateStoreManualBindingTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var root: File

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "nekopaint-manual-${System.nanoTime()}")
            .apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun validManualBindingWritesProfileOnlyAndKeepsApiJsonBytes() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template(), API)
        val apiFile = File(root, "t1/api.json")
        val before = apiFile.readBytes()

        val updated = store.addManualCandidate(
            templateId = "t1",
            nodeId = "5",
            inputName = "batch_size",
            label = "批次大小",
            requestedType = ParameterType.INT,
            group = ParameterGroup.SAMPLING,
            value = "2",
            catalog = null,
        ).getOrThrow()

        assertEquals("5:batch_size", updated.candidates.single().id)
        assertTrue(apiFile.readBytes().contentEquals(before))
        assertEquals("2", store.getTemplate("t1")!!.candidates.single().value)
        // 读取旧模板时自动补节点摘要。
        assertTrue(store.getTemplate("t1")!!.nodeSummaries.isNotEmpty())
    }

    @Test
    fun missingNodeOrInputIsRejectedWithoutChangingFiles() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template(), API)
        val profileBefore = File(root, "t1/profile.json").readBytes()
        val apiBefore = File(root, "t1/api.json").readBytes()

        val missingNode = store.addManualCandidate(
            "t1", "99", "text", "提示词", ParameterType.MULTILINE_TEXT,
            ParameterGroup.PROMPT, "a cat", null,
        )
        val missingInput = store.addManualCandidate(
            "t1", "3", "missing", "提示词", ParameterType.MULTILINE_TEXT,
            ParameterGroup.PROMPT, "a cat", null,
        )

        assertTrue(missingNode.isFailure)
        assertTrue(missingInput.isFailure)
        assertTrue(File(root, "t1/profile.json").readBytes().contentEquals(profileBefore))
        assertTrue(File(root, "t1/api.json").readBytes().contentEquals(apiBefore))
    }

    @Test
    fun duplicateIdAndTypeMismatchAreRejected() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template(), API)
        store.addManualCandidate(
            "t1", "5", "batch_size", "批次大小", ParameterType.INT,
            ParameterGroup.SAMPLING, "2", null,
        ).getOrThrow()

        val duplicate = store.addManualCandidate(
            "t1", "5", "batch_size", "重复", ParameterType.INT,
            ParameterGroup.SAMPLING, "3", null,
        )
        val mismatch = store.addManualCandidate(
            "t1", "3", "text", "错误类型", ParameterType.INT,
            ParameterGroup.PROMPT, "1", null,
        )

        assertTrue(duplicate.isFailure)
        assertTrue(mismatch.isFailure)
        assertEquals(1, store.getTemplate("t1")!!.candidates.count { it.id == "5:batch_size" })
    }

    @Test
    fun apiJsonChangedAfterSnapshotIsRevalidatedAtWriteTime() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template(), API)
        // 模拟 UI 读取后 api.json 被替换：node 5 已不存在。
        File(root, "t1/api.json").writeText(API.replace("\"5\": {", "\"50\": {"))

        val result = store.addManualCandidate(
            "t1", "5", "batch_size", "批次大小", ParameterType.INT,
            ParameterGroup.SAMPLING, "2", null,
        )

        assertTrue(result.isFailure)
        assertTrue(store.getTemplate("t1")!!.candidates.none { it.id == "5:batch_size" })
    }

    @Test
    fun compatibilityRefreshRebuildsNodeSummaries() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template(), API)
        val catalog = NodeCatalog(
            mapOf(
                "KSampler" to NodeInfoDto(),
                "CheckpointLoaderSimple" to NodeInfoDto(),
                "EmptyLatentImage" to NodeInfoDto(),
                "SaveImage" to NodeInfoDto(),
            ),
        )

        val refreshed = store.refreshAllCompatibility(catalog)!!

        val summaries = refreshed.first().nodeSummaries
        assertTrue(
            summaries.filter { it.classType == "CLIPTextEncode" }.all { it.missing },
        )
        assertTrue(
            store.getTemplate("t1")!!.nodeSummaries
                .filter { it.classType == "CLIPTextEncode" }
                .all { it.missing },
        )
    }

    @Test
    fun concurrentManualBindingsDoNotCreateDuplicateIds() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template(), API)
        val results = coroutineScope {
            (1..6).map {
                async(Dispatchers.Default) {
                    store.addManualCandidate(
                        "t1", "5", "batch_size", "批次大小", ParameterType.INT,
                        ParameterGroup.SAMPLING, "2", null,
                    )
                }
            }.awaitAll()
        }

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, store.getTemplate("t1")!!.candidates.count { it.id == "5:batch_size" })
    }

    private fun template(): WorkflowTemplate = WorkflowTemplate(
        id = "t1",
        name = "template",
        sourceFileName = "api.json",
        sourceHash = API.sha256(),
        importedAt = 1L,
        nodeCount = 6,
        reachableNodeCount = 6,
        outputNodeIds = listOf("6"),
    )

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        val API = """
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
    }
}

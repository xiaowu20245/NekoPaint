package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DuplicateImportTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var root: File

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "nekopaint-dup-${System.nanoTime()}")
            .apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun firstImportSavesThenDuplicateIsReported() = runTest {
        val store = WorkflowTemplateStore(root, json)

        val first = store.importTemplate(template("t1", "one"), API, null)
        val second = store.importTemplate(template("t2", "two"), API, null)

        assertTrue(first is TemplateImportStoreResult.Saved)
        val duplicate = second as TemplateImportStoreResult.Duplicate
        assertEquals("one", duplicate.existing.name)
        assertEquals(1, store.listTemplates().size)
    }

    @Test
    fun overwriteKeepsIdAndNameButReplacesCandidates() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.importTemplate(template("t1", "one"), API, null)

        val result = store.importTemplate(
            template("t-new", "two", candidates = listOf(candidate("9:value", "new"))),
            API,
            DuplicateImportPolicy.OVERWRITE,
        )

        val saved = result as TemplateImportStoreResult.Saved
        assertEquals(true, saved.replaced)
        assertEquals("t1", saved.template.id)
        assertEquals("one", saved.template.name)
        assertEquals(listOf("9:value"), saved.template.candidates.map { it.id })
        assertEquals(1, store.listTemplates().size)
    }

    @Test
    fun copyGetsNewIdAndSuffixedName() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.importTemplate(template("t1", "one"), API, null)

        val result = store.importTemplate(
            template("t2", "one"),
            API,
            DuplicateImportPolicy.COPY,
        )

        val saved = result as TemplateImportStoreResult.Saved
        assertNotEquals("t1", saved.template.id)
        assertEquals("one 副本", saved.template.name)
        assertEquals(2, store.listTemplates().size)
    }

    @Test
    fun concurrentImportsNeverCreateDuplicateHashEntries() = runTest {
        val store = WorkflowTemplateStore(root, json)

        val results = coroutineScope {
            (1..4).map { index ->
                async(Dispatchers.Default) {
                    store.importTemplate(template("t$index", "name$index"), API, null)
                }
            }.awaitAll()
        }

        assertEquals(1, results.count { it is TemplateImportStoreResult.Saved })
        assertEquals(3, results.count { it is TemplateImportStoreResult.Duplicate })
        assertEquals(1, store.listTemplates().size)
    }

    private fun candidate(id: String, value: String) = ParameterCandidate(
        id = id,
        nodeId = id.substringBefore(':'),
        inputName = id.substringAfter(':'),
        label = value,
        type = ParameterType.TEXT,
        group = ParameterGroup.OTHER,
        value = value,
        selected = true,
    )

    private fun template(
        id: String,
        name: String,
        candidates: List<ParameterCandidate> = emptyList(),
    ) = WorkflowTemplate(
        id = id,
        name = name,
        sourceFileName = "api.json",
        sourceHash = API.sha256(),
        importedAt = 1L,
        nodeCount = 6,
        reachableNodeCount = 6,
        outputNodeIds = listOf("6"),
        candidates = candidates,
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

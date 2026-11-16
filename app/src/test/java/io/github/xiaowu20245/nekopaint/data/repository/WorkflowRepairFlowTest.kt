package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.PresetValueEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.workflow.ParameterDetector
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowParseResult
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowParser
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkflowRepairFlowTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private lateinit var root: File

    @Before
    fun setUp() {
        root = File(
            System.getProperty("java.io.tmpdir"),
            "nekopaint-repair-${System.nanoTime()}",
        ).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun hashMismatchViewStaysFlaggedAfterOrdinarySave() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template("t1"), API_V1)
        // api.json 内容变化但仍是同一份可解析工作流：只改 hash。
        File(root, "t1/api.json").writeText("$API_V1\n")

        val view = store.getTemplate("t1")!!
        assertTrue(view.repairRequired)

        store.updateTemplate(view.copy(name = "renamed", repairRequired = false))

        val stored = readStored("t1")
        assertTrue(stored.repairRequired)
        assertEquals("renamed", stored.name)
        assertTrue(store.getTemplate("t1")!!.repairRequired)
        assertTrue(WorkflowRepairFlag.blocksSubmit(view, stored))
    }

    @Test
    fun successfulRepairClearsFlagAndRebuildsCandidates() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template("t1"), API_V1)
        File(root, "t1/api.json").writeText("$API_V1\n")
        assertTrue(store.getTemplate("t1")!!.repairRequired)

        val repaired = store.repairTemplate("t1", null).getOrThrow()

        assertFalse(repaired.repairRequired)
        val stored = readStored("t1")
        assertFalse(stored.repairRequired)
        assertTrue(stored.candidates.any { it.id == "3:text" && it.value == "a cat" })
        assertFalse(WorkflowRepairFlag.blocksSubmit(repaired, stored))
    }

    @Test
    fun failedRepairKeepsProfileAndFlag() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template("t1"), API_V1)
        File(root, "t1/api.json").writeText("$API_V1\n")
        val flaggedView = store.getTemplate("t1")!!
        assertTrue(flaggedView.repairRequired)
        store.updateTemplate(flaggedView)
        File(root, "t1/api.json").writeText("{broken")

        val result = store.repairTemplate("t1", null)

        assertTrue(result.isFailure)
        val stored = readStored("t1")
        assertTrue(stored.repairRequired)
        assertEquals("template", stored.name)
        assertTrue(store.getTemplate("t1")!!.repairRequired)
    }

    @Test
    fun repairPreservesCompatibleValuesAndPrunesStalePresetEntries() = runTest {
        val store = WorkflowTemplateStore(root, json)
        val candidates = detectedCandidates(API_V1).map { candidate ->
            when (candidate.id) {
                "3:text" -> candidate.copy(value = "custom prompt", selected = true)
                "1:steps" -> candidate.copy(value = "33", selected = true)
                else -> candidate
            }
        }
        val textLabel = candidates.first { it.id == "3:text" }.label
        val validPreset = WorkflowPreset(
            id = "p1",
            name = "valid",
            entries = listOf(
                PresetValueEntry(
                    candidateId = "3:text",
                    classType = "CLIPTextEncode",
                    inputName = "text",
                    label = textLabel,
                    value = "custom prompt",
                ),
            ),
        )
        val stalePreset = WorkflowPreset(
            id = "p2",
            name = "stale",
            entries = listOf(
                PresetValueEntry(
                    candidateId = "99:text",
                    classType = "RemovedNode",
                    inputName = "text",
                    label = "removed",
                    value = "gone",
                ),
            ),
        )
        store.saveTemplate(
            template(
                id = "t1",
                candidates = candidates,
                primaryPromptCandidateId = "3:text",
                presets = listOf(validPreset, stalePreset),
                activePresetId = "p2",
            ),
            API_V1,
        )
        File(root, "t1/api.json").writeText("$API_V1\n")

        val repaired = store.repairTemplate("t1", null).getOrThrow()

        assertEquals(
            "custom prompt",
            repaired.candidates.first { it.id == "3:text" }.value,
        )
        assertEquals("33", repaired.candidates.first { it.id == "1:steps" }.value)
        assertTrue(repaired.candidates.first { it.id == "3:text" }.selected)
        assertEquals(listOf("p1"), repaired.presets.map { it.id })
        assertEquals(null, repaired.activePresetId)
        assertEquals("3:text", repaired.primaryPromptCandidateId)
        assertEquals(1, repaired.presets.single().entries.size)
    }

    @Test
    fun repairDoesNotReuseValuesWithIncompatibleType() = runTest {
        val store = WorkflowTemplateStore(root, json)
        val candidates = detectedCandidates(API_V1).map { candidate ->
            if (candidate.id == "3:text") {
                candidate.copy(
                    type = ParameterType.INT,
                    value = "123",
                    defaultValue = "1",
                    selected = true,
                )
            } else {
                candidate
            }
        }
        store.saveTemplate(template(id = "t1", candidates = candidates), API_V1)
        File(root, "t1/api.json").writeText("$API_V1\n")

        val repaired = store.repairTemplate("t1", null).getOrThrow()

        val text = repaired.candidates.first { it.id == "3:text" }
        assertEquals(ParameterType.MULTILINE_TEXT, text.type)
        assertEquals("a cat", text.value)
    }

    @Test
    fun updateRejectsReadOnlyFlipAndUnknownCandidates() = runTest {
        val store = WorkflowTemplateStore(root, json)
        val readOnly = ParameterCandidate(
            id = "3:text",
            nodeId = "3",
            inputName = "text",
            label = "内部提示词",
            type = ParameterType.MULTILINE_TEXT,
            group = ParameterGroup.PROMPT,
            value = "a cat",
            defaultValue = "a cat",
            selected = false,
            readOnly = true,
            classType = "CLIPTextEncode",
        )
        store.saveTemplate(template(id = "t1", candidates = listOf(readOnly)), API_V1)
        val storedView = store.getTemplate("t1")!!

        val flip = storedView.copy(
            candidates = storedView.candidates.map { it.copy(readOnly = false) },
        )
        val flipError = runCatching { store.updateTemplate(flip) }.exceptionOrNull()
        assertNotNull(flipError)
        assertEquals("template", readStored("t1").name)

        val unknown = storedView.copy(
            candidates = storedView.candidates + readOnly.copy(
                id = "99:text",
                nodeId = "99",
                inputName = "text",
            ),
        )
        val unknownError = runCatching { store.updateTemplate(unknown) }.exceptionOrNull()
        assertNotNull(unknownError)
        assertEquals("template", readStored("t1").name)
    }

    @Test
    fun updateRejectsInvalidScalarValueAndTypeChange() = runTest {
        val store = WorkflowTemplateStore(root, json)
        val base = template(id = "t1", candidates = detectedCandidates(API_V1))
        store.saveTemplate(base, API_V1)
        val view = store.getTemplate("t1")!!

        val invalidInt = view.copy(
            candidates = view.candidates.map {
                if (it.id == "1:steps") it.copy(value = "not-a-number") else it
            },
        )
        assertNotNull(runCatching { store.updateTemplate(invalidInt) }.exceptionOrNull())

        val changedType = view.copy(
            candidates = view.candidates.map {
                if (it.id == "1:steps") it.copy(type = ParameterType.FLOAT) else it
            },
        )
        assertNotNull(runCatching { store.updateTemplate(changedType) }.exceptionOrNull())
        assertEquals("template", readStored("t1").name)
    }

    @Test
    fun concurrentUpdatesRemainParseable() = runTest {
        val store = WorkflowTemplateStore(root, json)
        val base = template(id = "t1", candidates = detectedCandidates(API_V1))
        store.saveTemplate(base, API_V1)

        coroutineScope {
            (1..32).map { index ->
                async(Dispatchers.Default) {
                    store.updateTemplate(
                        base.copy(
                            name = "name-$index",
                            candidates = base.candidates.map { it.copy(selected = true) },
                        ),
                    )
                }
            }.awaitAll()
        }

        val stored = readStored("t1")
        assertTrue(stored.name.startsWith("name-"))
        assertEquals(base.candidates.size, stored.candidates.size)
        assertEquals(1, store.listTemplates().size)
    }

    @Test
    fun concurrentUpdateRepairRefreshAndCopyRemainConsistent() = runTest {
        val store = WorkflowTemplateStore(root, json)
        val base = template(id = "t1", candidates = detectedCandidates(API_V1))
        store.saveTemplate(base, API_V1)
        File(root, "t1/api.json").writeText("$API_V1\n")
        val flagged = store.getTemplate("t1")!!

        coroutineScope {
            val updates = (1..12).map { index ->
                async(Dispatchers.Default) {
                    runCatching {
                        store.updateTemplate(flagged.copy(name = "name-$index"))
                    }
                }
            }
            val repairs = (1..3).map {
                async(Dispatchers.Default) { store.repairTemplate("t1", null) }
            }
            val copy = async(Dispatchers.Default) { store.copyTemplate("t1") }
            val reads = (1..8).map {
                async(Dispatchers.Default) { store.listTemplates() }
            }
            updates.awaitAll()
            repairs.awaitAll()
            assertTrue(copy.await().isSuccess)
            reads.awaitAll()
        }

        val stored = readStored("t1")
        assertTrue(stored.candidates.isNotEmpty())
        val copies = store.listTemplates().filter { it.id != "t1" }
        assertEquals(1, copies.size)
        assertTrue(File(root, "${copies.single().id}/api.json").exists())
    }

    private fun template(
        id: String,
        candidates: List<ParameterCandidate> = emptyList(),
        primaryPromptCandidateId: String? = null,
        presets: List<WorkflowPreset> = emptyList(),
        activePresetId: String? = null,
    ) = WorkflowTemplate(
        id = id,
        name = "template",
        sourceFileName = "api.json",
        sourceHash = API_V1.sha256(),
        importedAt = 1L,
        nodeCount = 6,
        reachableNodeCount = 6,
        outputNodeIds = listOf("6"),
        candidates = candidates,
        primaryPromptCandidateId = primaryPromptCandidateId,
        presets = presets,
        activePresetId = activePresetId,
    )

    private fun detectedCandidates(raw: String): List<ParameterCandidate> {
        val success = WorkflowParser.parse(raw) as WorkflowParseResult.Success
        return ParameterDetector.detect(success.graph, null).candidates
    }

    private fun readStored(id: String): WorkflowTemplate {
        val file = File(root, "$id/profile.json")
        return json.decodeFromString(WorkflowTemplate.serializer(), file.readText())
    }

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val API_V1 = """
            {
              "1": {"class_type": "KSampler", "inputs": {
                "seed": 1, "steps": 20, "cfg": 7.5, "sampler_name": "euler", "scheduler": "normal",
                "denoise": 1.0, "model": ["2", 0], "positive": ["3", 0], "negative": ["4", 0],
                "latent_image": ["5", 0]
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

package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.PresetValueEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.PromptTagGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowPreset
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkflowMetadataPatchTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private lateinit var root: File
    private lateinit var store: WorkflowTemplateStore

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        root = File(
            System.getProperty("java.io.tmpdir"),
            "nekopaint-metadata-${System.nanoTime()}",
        ).apply { mkdirs() }
        store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template(), RAW_JSON)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun metadataPatchesDoNotPersistUnsavedCandidateValues() = runTest {
        store.patchPrimaryPrompt(ID, "2:text").getOrThrow()
        store.patchPromptTagGroups(
            ID,
            listOf(PromptTagGroup(id = "g1", name = "tags", tags = listOf("a"))),
        ).getOrThrow()

        val stored = store.getTemplate(ID)!!
        assertEquals("1", stored.candidates.first { it.id == "1:seed" }.value)
        assertEquals("2:text", stored.primaryPromptCandidateId)
        assertEquals(1, stored.promptTagGroups.size)
    }

    @Test
    fun applyPresetAtomicallyWritesValuesAndActivePreset() = runTest {
        val snapshot = template().candidates.map {
            it.copy(value = "42", selected = true)
        }

        val saved = store.patchApplyPreset(ID, snapshot, "preset-1").getOrThrow()

        assertEquals("42", saved.candidates.first { it.id == "1:seed" }.value)
        assertEquals("preset-1", saved.activePresetId)
        assertEquals("42", store.getTemplate(ID)!!.candidates.first { it.id == "1:seed" }.value)
    }

    @Test
    fun savePresetAtomicallyWritesPresetValuesAndActive() = runTest {
        val preset = WorkflowPreset(
            id = "preset-2",
            name = "New",
            entries = listOf(
                PresetValueEntry(candidateId = "1:seed", value = "7"),
            ),
        )
        val snapshot = template().candidates.map { it.copy(value = "7") }

        val saved = store.patchSavePreset(ID, preset, snapshot).getOrThrow()

        assertEquals("preset-2", saved.activePresetId)
        assertEquals("7", saved.candidates.first { it.id == "1:seed" }.value)
        assertTrue(saved.presets.any { it.id == "preset-2" })
        assertEquals("7", store.getTemplate(ID)!!.candidates.first { it.id == "1:seed" }.value)
    }

    @Test
    fun executionValuePatchOnlyAppliesValueAndSelected() = runTest {
        val snapshot = template().candidates.map {
            it.copy(value = "9", selected = true, label = "should-not-persist")
        }

        val saved = store.patchExecutionValues(ID, snapshot).getOrThrow()

        assertEquals("9", saved.candidates.first { it.id == "1:seed" }.value)
        assertEquals("Seed", saved.candidates.first { it.id == "1:seed" }.label)
    }

    @Test
    fun renameAndDeletePresetDoNotTouchCandidateValues() = runTest {
        val renamed = store.patchPreset(
            ID,
            WorkflowPreset(
                id = "preset-1",
                name = "Renamed",
                entries = listOf(PresetValueEntry(candidateId = "1:seed", value = "1")),
            ),
        ).getOrThrow()
        assertEquals("Renamed", renamed.presets.single().name)
        assertEquals("1", renamed.candidates.first { it.id == "1:seed" }.value)

        val deleted = store.deletePreset(ID, "preset-1").getOrThrow()
        assertTrue(deleted.presets.isEmpty())
        assertEquals("1", deleted.candidates.first { it.id == "1:seed" }.value)
    }

    @Test
    fun missingTemplateReturnsFailure() = runTest {
        val result = store.patchPrimaryPrompt("missing", null)

        assertTrue(result.isFailure)
    }

    @Test
    fun metadataPatchPreservesRepairRequiredFlag() = runTest {
        store.saveTemplate(template().copy(repairRequired = true), RAW_JSON)

        store.patchPromptTagGroups(
            ID,
            listOf(PromptTagGroup(id = "g1", name = "tags", tags = listOf("a"))),
        ).getOrThrow()

        assertTrue(store.getTemplate(ID)!!.repairRequired)
    }

    @Test
    fun invalidPresetEntriesAreDroppedWithoutChangingCandidateValues() = runTest {
        val preset = WorkflowPreset(
            id = "preset-bad",
            name = "Bad",
            entries = listOf(
                PresetValueEntry(candidateId = "missing", value = "1"),
                PresetValueEntry(candidateId = "1:seed", value = "1"),
            ),
        )

        val saved = store.patchPreset(ID, preset).getOrThrow()

        val storedPreset = saved.presets.first { it.id == "preset-bad" }
        assertEquals(listOf("1:seed"), storedPreset.entries.map { it.candidateId })
        assertEquals("1", store.getTemplate(ID)!!.candidates.first { it.id == "1:seed" }.value)
    }

    private fun template() = WorkflowTemplate(
        id = ID,
        name = "template",
        sourceFileName = "api.json",
        sourceHash = sha256(RAW_JSON),
        importedAt = 1L,
        nodeCount = 1,
        reachableNodeCount = 1,
        candidates = listOf(
            ParameterCandidate(
                id = "1:seed",
                nodeId = "1",
                inputName = "seed",
                label = "Seed",
                type = ParameterType.INT,
                group = ParameterGroup.SAMPLING,
                defaultValue = "1",
                value = "1",
                selected = true,
                classType = "TestSampler",
            ),
            ParameterCandidate(
                id = "2:text",
                nodeId = "2",
                inputName = "text",
                label = "Prompt",
                type = ParameterType.TEXT,
                group = ParameterGroup.PROMPT,
                defaultValue = "hello",
                value = "hello",
                selected = true,
                classType = "CLIPTextEncode",
            ),
        ),
        presets = listOf(
            WorkflowPreset(
                id = "preset-1",
                name = "Preset",
                entries = listOf(
                    PresetValueEntry(candidateId = "1:seed", value = "1"),
                ),
            ),
        ),
    )

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val ID = "template-1"
        const val RAW_JSON =
            """{"1":{"class_type":"TestSampler","inputs":{"seed":1,"steps":20}},""" +
                """"2":{"class_type":"CLIPTextEncode","inputs":{"text":"hello"}}}"""
    }
}

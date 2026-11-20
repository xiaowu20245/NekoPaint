package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryEntry
import io.github.xiaowu20245.nekopaint.core.model.workflow.HistoryImage
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterCandidate
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.model.workflow.PromptTagGroup
import io.github.xiaowu20245.nekopaint.core.model.workflow.SeedStrategy
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.workflow.PromptTagExpander
import io.github.xiaowu20245.nekopaint.core.workflow.WorkflowJsonApplier
import io.github.xiaowu20245.nekopaint.feature.history.HistoryFilter
import io.github.xiaowu20245.nekopaint.feature.history.HistorySelectionPolicy
import io.github.xiaowu20245.nekopaint.feature.history.HistoryVisibilityPolicy
import io.github.xiaowu20245.nekopaint.feature.history.visibleImages
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 第三轮收尾集成回归：跨批次组合 3E（自动保存）、3F-1（重复导入/手动绑定）、
 * 3A-2/3C（标签展开/提交选择）与 3A-1/3D-1（历史合并/可见性/选择）的核心路径。
 */
class Round3IntegrationRegressionTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var root: File

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "nekopaint-r3-${System.nanoTime()}")
            .apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun autoSaveDeduplicatesPerServerAndNotAcrossServers() = runTest {
        val savedUrls = mutableListOf<String>()
        val saver = HistoryAutoSaver(
            file = File(root, "auto_saved.json"),
            json = json,
            enabledProvider = AutoSaveEnabledProvider { true },
            imageWriter = HistoryImageWriter { url, _ ->
                savedUrls += url
                Result.success(Unit)
            },
        )
        val entry = historyEntry(
            promptId = "p1",
            timestamp = 100L,
            images = listOf(historyImage("same.png", type = "output")),
        )

        assertEquals(AutoSaveState.Saved(1), saver.saveNew("server-a", listOf(entry)))
        assertEquals(AutoSaveState.Idle, saver.saveNew("server-a", listOf(entry)))
        assertEquals(AutoSaveState.Saved(1), saver.saveNew("server-b", listOf(entry)))

        assertEquals(2, savedUrls.size)
    }

    @Test
    fun copyTemplateManualBindingDoesNotMutateOriginal() = runTest {
        val store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template("t1", "template"), API)
        val originalApi = File(root, "t1/api.json").readBytes()

        val copy = store.importTemplate(
            template("t2", "template"),
            API,
            DuplicateImportPolicy.COPY,
        ) as TemplateImportStoreResult.Saved
        val bound = store.addManualCandidate(
            templateId = copy.template.id,
            nodeId = "5",
            inputName = "batch_size",
            label = "批次大小",
            requestedType = ParameterType.INT,
            group = ParameterGroup.SAMPLING,
            value = "2",
            catalog = null,
        ).getOrThrow()

        assertEquals(listOf("5:batch_size"), bound.candidates.map { it.id })
        assertTrue(store.getTemplate("t1")!!.candidates.isEmpty())
        assertTrue(originalApi.contentEquals(File(root, "t1/api.json").readBytes()))
        assertEquals(2, store.listTemplates().size)

        store.deleteTemplate(copy.template.id)

        assertEquals(listOf("t1"), store.listTemplates().map { it.id })
    }

    @Test
    fun expandedPromptTagsFlowIntoSeedSelectionAndAppliedJson() = runTest {
        val template = template(
            id = "prompt-template",
            name = "prompt-template",
            candidates = listOf(
                ParameterCandidate(
                    id = "1:seed",
                    nodeId = "1",
                    inputName = "seed",
                    label = "种子",
                    type = ParameterType.INT,
                    group = ParameterGroup.SAMPLING,
                    value = "10",
                    selected = false,
                ),
                ParameterCandidate(
                    id = "2:text",
                    nodeId = "2",
                    inputName = "text",
                    label = "提示词",
                    type = ParameterType.TEXT,
                    group = ParameterGroup.PROMPT,
                    value = "#画质, 1girl",
                    selected = true,
                ),
            ),
            promptTagGroups = listOf(
                PromptTagGroup(id = "g1", name = "画质", tags = listOf("masterpiece", "best quality")),
            ),
        )
        val prompt = template.candidates.first { it.id == "2:text" }
        val expandedPrompt = PromptTagExpander.expand(prompt.value, template.promptTagGroups)
        var submitted: List<ParameterCandidate> = emptyList()
        val submitter = WorkflowSubmitter { _, selected, _ ->
            submitted = selected
            WorkflowSubmitResult.Success("p1", 1)
        }

        submitter.submit(
            template = template,
            overrides = listOf(prompt.copy(value = expandedPrompt)),
            seedStrategy = SeedStrategy.INCREMENT,
        )

        assertEquals("masterpiece, best quality, 1girl", expandedPrompt)
        assertEquals(expandedPrompt, submitted.first { it.id == "2:text" }.value)
        assertEquals("10", submitted.first { it.id == "1:seed" }.value)
        assertTrue(submitted.first { it.id == "1:seed" }.selected)

        val applied = WorkflowJsonApplier.apply(API, submitted)
        assertEquals(
            expandedPrompt,
            applied["2"]!!.jsonObject["inputs"]!!.jsonObject["text"]!!.jsonPrimitive.content,
        )
        assertEquals(
            10L,
            applied["1"]!!.jsonObject["inputs"]!!.jsonObject["seed"]!!.jsonPrimitive.long,
        )
    }

    @Test
    fun mergedHistoryUsesLatestEntryAndPrunesSelectionByVisibility() {
        val local = listOf(
            historyEntry(
                "p1",
                100L,
                listOf(historyImage("a.png", "output"), historyImage("preview.png", "temp")),
            ),
            historyEntry("p2", 200L, listOf(historyImage("b.png", "output"))),
        )
        val remote = listOf(
            historyEntry("p1", 300L, listOf(historyImage("c.png", "output"))),
            historyEntry("p3", 50L, listOf(historyImage("preview3.png", "temp"))),
        )

        val merged = mergeHistory(local, remote)

        assertEquals(listOf("p1", "p2", "p3"), merged.map { it.promptId })
        assertEquals(300L, merged[0].timestamp)
        assertEquals(listOf("c.png"), merged[0].visibleImages(showTempPreviews = false).map { it.filename })

        assertTrue(HistoryVisibilityPolicy.effectiveShowTemp(HistoryFilter.TEMP, globalShowTemp = false))
        assertFalse(HistoryVisibilityPolicy.effectiveShowTemp(HistoryFilter.ALL, globalShowTemp = false))
        assertTrue(HistoryVisibilityPolicy.isTempSwitchEnabled(HistoryFilter.TEMP).not())

        val outputSelectable = merged
            .filter { it.visibleImages(showTempPreviews = false).isNotEmpty() }
            .mapTo(mutableSetOf()) { it.promptId }
        val tempSelectable = merged
            .filter { it.visibleImages(showTempPreviews = true).isNotEmpty() }
            .mapTo(mutableSetOf()) { it.promptId }

        assertEquals(setOf("p1", "p2"), outputSelectable)
        assertEquals(setOf("p1", "p2", "p3"), tempSelectable)
        assertEquals(
            setOf("p1"),
            HistorySelectionPolicy.prune(setOf("p1", "p3", "gone"), outputSelectable),
        )
    }

    private fun template(
        id: String,
        name: String,
        candidates: List<ParameterCandidate> = emptyList(),
        promptTagGroups: List<PromptTagGroup> = emptyList(),
    ) = WorkflowTemplate(
        id = id,
        name = name,
        sourceFileName = "api.json",
        sourceHash = API.sha256(),
        importedAt = 1L,
        nodeCount = 6,
        reachableNodeCount = 6,
        candidates = candidates,
        promptTagGroups = promptTagGroups,
    )

    private fun historyEntry(
        promptId: String,
        timestamp: Long,
        images: List<HistoryImage>,
    ) = HistoryEntry(
        promptId = promptId,
        serverId = "server-a",
        status = "success",
        completed = true,
        timestamp = timestamp,
        startedAt = timestamp,
        finishedAt = timestamp,
        images = images,
        errorNodeId = null,
        errorMessage = null,
    )

    private fun historyImage(filename: String, type: String) = HistoryImage(
        filename = filename,
        subfolder = "",
        type = type,
        url = "http://192.0.2.10/view/$filename",
    )

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        val API = """
            {
              "1": {"class_type": "KSampler", "inputs": {
                "seed": 1, "steps": 20, "cfg": 7.5, "sampler_name": "euler",
                "scheduler": "normal", "denoise": 1.0, "model": ["2", 0]
              }},
              "2": {"class_type": "CLIPTextEncode", "inputs": {
                "text": "old prompt", "clip": ["3", 0]
              }},
              "3": {"class_type": "CheckpointLoaderSimple", "inputs": {
                "ckpt_name": "base.safetensors"
              }},
              "4": {"class_type": "EmptyLatentImage", "inputs": {
                "width": 512, "height": 512, "batch_size": 1
              }},
              "5": {"class_type": "EmptyLatentImage", "inputs": {
                "width": 512, "height": 512, "batch_size": 1
              }},
              "6": {"class_type": "SaveImage", "inputs": {
                "filename_prefix": "NekoPaint", "images": ["1", 0]
              }}
            }
        """.trimIndent()
    }
}

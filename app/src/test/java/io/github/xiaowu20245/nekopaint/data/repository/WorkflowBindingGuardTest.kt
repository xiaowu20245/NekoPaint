package io.github.xiaowu20245.nekopaint.data.repository

import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import io.github.xiaowu20245.nekopaint.core.workflow.NodeCatalog
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkflowBindingGuardTest {

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
            "nekopaint-binding-${System.nanoTime()}",
        ).apply { mkdirs() }
        store = WorkflowTemplateStore(root, json)
        store.saveTemplate(template(), RAW_JSON)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun reanalyzeGuardFailureDoesNotWriteTemplate() = runTest {
        val result = store.reanalyzeAndSave(
            templateId = ID,
            catalog = NodeCatalog(emptyMap()),
            bindingGuard = { false },
        )

        assertNull(result)
        assertNull(store.getTemplate(ID)!!.compatibility)
    }

    @Test
    fun compatibilityGuardFailureDoesNotWriteTemplate() = runTest {
        val analyzed = template().copy(
            missingNodeTypes = listOf("ShouldNotPersist"),
            catalogChecked = true,
        )

        val result = store.saveCompatibility(
            templateId = ID,
            analyzed = analyzed,
            bindingGuard = { false },
        )

        assertNull(result)
        assertTrue(store.getTemplate(ID)!!.missingNodeTypes.isEmpty())
    }

    private fun template() = WorkflowTemplate(
        id = ID,
        name = "template",
        sourceFileName = "api.json",
        sourceHash = sha256(RAW_JSON),
        importedAt = 1L,
        nodeCount = 1,
        reachableNodeCount = 1,
        missingNodeTypes = emptyList(),
    )

    private fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val ID = "template-1"
        const val RAW_JSON =
            """{"1":{"class_type":"TestSampler","inputs":{"seed":1}}}"""
    }
}

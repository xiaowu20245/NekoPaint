package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.NodeInstallPackage
import io.github.xiaowu20245.nekopaint.core.model.workflow.WorkflowTemplate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeInstallPlannerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `builds plan and deduplicates packages by coverage`() {
        val plan = NodeInstallPlanner.buildPlan(
            template = template("NodeA", "NodeB", "NodeC"),
            mappings = mappings(
                """
                {
                  "pack-a": [
                    ["NodeA"],
                    {"title": "Pack A", "files": ["https://github.com/a/a.git"]}
                  ],
                  "pack-b": [
                    ["NodeA", "NodeB"],
                    {"title": "Pack B", "files": ["https://github.com/b/b.git"]}
                  ],
                  "pack-c": [
                    ["NodeC"],
                    {"title": "Pack C", "files": ["https://github.com/c/c.git"]}
                  ]
                }
                """,
            ),
            managerVersion = "3.32.3",
        )

        assertEquals(listOf("NodeA", "NodeB", "NodeC"), plan.missingNodeTypes)
        assertEquals(listOf("Pack B", "Pack C"), plan.packages.map { it.title })
        assertTrue(plan.unresolvedNodeTypes.isEmpty())
        assertTrue(plan.canInstall)
        assertEquals("3.32.3", plan.managerVersion)
    }

    @Test
    fun `prefers package id over raw git url when coverage is equal`() {
        val plan = NodeInstallPlanner.buildPlan(
            template = template("NodeA"),
            mappings = mappings(
                """
                {
                  "https://github.com/legacy/pack": [
                    ["NodeA"],
                    {"title": "Legacy Pack", "files": ["https://github.com/legacy/pack"]}
                  ],
                  "pack-cnr": [
                    ["NodeA"],
                    {"title": "CNR Pack", "version": "1.0.0", "files": ["https://github.com/cnr/pack"]}
                  ]
                }
                """,
            ),
        )

        assertEquals("pack-cnr", plan.packages.single().id)
    }

    @Test
    fun `reports node types without a package`() {
        val plan = NodeInstallPlanner.buildPlan(
            template = template("KnownNode", "UnknownNode"),
            mappings = mappings(
                """
                {
                  "pack-a": [
                    ["KnownNode"],
                    {"title": "Pack A", "files": ["https://github.com/a/a.git"]}
                  ]
                }
                """,
            ),
        )

        assertEquals(listOf("Pack A"), plan.packages.map { it.title })
        assertEquals(listOf("UnknownNode"), plan.unresolvedNodeTypes)
    }

    @Test
    fun `uses reference when files are missing and skips packages without source`() {
        val plan = NodeInstallPlanner.buildPlan(
            template = template("RefNode", "NoSourceNode"),
            mappings = mappings(
                """
                {
                  "pack-ref": [
                    ["RefNode"],
                    {"title": "Pack Ref", "reference": "https://github.com/ref/pack"}
                  ],
                  "pack-none": [
                    ["NoSourceNode"],
                    {"title": "Pack None"}
                  ]
                }
                """,
            ),
        )

        assertEquals(listOf("Pack Ref"), plan.packages.map { it.title })
        assertEquals("https://github.com/ref/pack", plan.packages.single().files.single())
        assertEquals(listOf("NoSourceNode"), plan.unresolvedNodeTypes)
    }

    @Test
    fun `accepts a single string source`() {
        val plan = NodeInstallPlanner.buildPlan(
            template = template("NodeA"),
            mappings = mappings(
                """
                {
                  "pack-a": [
                    ["NodeA"],
                    {"title": "Pack A", "files": "https://github.com/a/a.git"}
                  ]
                }
                """,
            ),
        )

        assertEquals("https://github.com/a/a.git", plan.packages.single().files.single())
    }

    @Test
    fun `empty missing node list produces an empty plan`() {
        val plan = NodeInstallPlanner.buildPlan(
            template = template(),
            mappings = mappings(
                """
                {
                  "pack-a": [["NodeA"], {"title": "Pack A", "files": ["https://github.com/a/a.git"]}]
                }
                """,
            ),
        )

        assertFalse(plan.canInstall)
        assertTrue(plan.packages.isEmpty())
    }

    @Test
    fun `install request keeps unknown version for git packages`() {
        val request = NodeInstallPlanner.buildInstallRequest(
            NodeInstallPackage(
                id = "pack-a",
                title = "Pack A",
                files = listOf("https://github.com/a/a.git"),
                installType = "git",
                version = "unknown",
                channel = "default",
                mode = "cache",
            ),
        )

        assertEquals("pack-a", request["id"].toString().trim('"'))
        assertEquals("Pack A", request["title"].toString().trim('"'))
        assertEquals("unknown", request["version"].toString().trim('"'))
        assertEquals("unknown", request["selected_version"].toString().trim('"'))
        assertEquals("git", request["install_type"].toString().trim('"'))
        assertEquals("[\"https://github.com/a/a.git\"]", request["files"].toString())
    }

    private fun template(vararg missing: String): WorkflowTemplate = WorkflowTemplate(
        id = "workflow-1",
        name = "Anima",
        sourceFileName = "anima.json",
        sourceHash = "hash",
        importedAt = 0L,
        nodeCount = 3,
        reachableNodeCount = 3,
        missingNodeTypes = missing.toList(),
    )

    private fun mappings(raw: String) =
        json.parseToJsonElement(raw).jsonObject
}

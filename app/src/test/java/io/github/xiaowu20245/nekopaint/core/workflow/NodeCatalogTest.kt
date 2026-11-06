package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.workflow.ParameterType
import io.github.xiaowu20245.nekopaint.core.network.NodeInfoDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class NodeCatalogTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesModelListObjectFromObjectInfo() {
        val node = json.decodeFromString<NodeInfoDto>(
            """
            {
              "input": {
                "required": {
                  "ckpt_name": [
                    {"value": ["a.safetensors", "b.safetensors"], "Count": 2},
                    {"tooltip": "The name of the checkpoint"}
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        val schema = NodeCatalog(mapOf("CheckpointLoaderSimple" to node))
            .inputSchema("CheckpointLoaderSimple", "ckpt_name")

        assertEquals(ParameterType.ENUM, schema?.type)
        assertEquals(listOf("a.safetensors", "b.safetensors"), schema?.constraints?.options)
    }

    @Test
    fun parsesPlainEnumArray() {
        val node = json.decodeFromString<NodeInfoDto>(
            """
            {
              "input": {
                "required": {
                  "sampler_name": [["euler", "dpmpp_2m"]]
                }
              }
            }
            """.trimIndent(),
        )

        val schema = NodeCatalog(mapOf("KSampler" to node))
            .inputSchema("KSampler", "sampler_name")

        assertEquals(ParameterType.ENUM, schema?.type)
        assertEquals(listOf("euler", "dpmpp_2m"), schema?.constraints?.options)
    }
}

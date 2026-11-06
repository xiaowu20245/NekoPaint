package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelListParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesBareArrayResponse() {
        val element = json.parseToJsonElement("""["a.safetensors","b.safetensors"]""")

        assertEquals(listOf("a.safetensors", "b.safetensors"), ModelListParser.parse(element))
    }

    @Test
    fun parsesWrappedObjectResponse() {
        val element = json.parseToJsonElement(
            """{"value":["a.safetensors","b.safetensors"],"Count":2}""",
        )

        assertEquals(listOf("a.safetensors", "b.safetensors"), ModelListParser.parse(element))
    }

    @Test
    fun returnsEmptyForMissingOrInvalidValue() {
        assertTrue(ModelListParser.parse(null).isEmpty())
        assertTrue(ModelListParser.parse(json.parseToJsonElement("{}")).isEmpty())
        assertTrue(ModelListParser.parse(json.parseToJsonElement("\"oops\"")).isEmpty())
    }
}

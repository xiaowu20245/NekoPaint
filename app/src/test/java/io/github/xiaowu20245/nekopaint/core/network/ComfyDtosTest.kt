package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ComfyDtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun parsesSystemStats() {
        val payload = """
            {
              "system": {
                "os": "win32",
                "comfyui_version": "0.27.0",
                "ram_total": 16496545792,
                "ram_free": 2225713152
              },
              "devices": [
                {
                  "name": "NVIDIA GeForce RTX 5060 Laptop GPU",
                  "type": "cuda",
                  "index": 0,
                  "vram_total": 8546484224,
                  "vram_free": 7385120768,
                  "torch_vram_total": 0,
                  "torch_vram_free": 0
                }
              ]
            }
        """.trimIndent()

        val stats = json.decodeFromString<SystemStatsDto>(payload)

        assertEquals("0.27.0", stats.system?.comfyUiVersion)
        assertEquals(1, stats.devices.size)
        assertEquals(8_546_484_224L, stats.devices.first().vramTotal)
        assertNotNull(stats.devices.first().name)
    }

    @Test
    fun ignoresUnknownFields() {
        val stats = json.decodeFromString<SystemStatsDto>("""{"new_field": true}""")

        assertEquals(0, stats.devices.size)
    }
}

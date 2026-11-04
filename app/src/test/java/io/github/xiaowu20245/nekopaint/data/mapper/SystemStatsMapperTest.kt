package io.github.xiaowu20245.nekopaint.data.mapper

import io.github.xiaowu20245.nekopaint.core.network.DeviceInfoDto
import io.github.xiaowu20245.nekopaint.core.network.SystemInfoDto
import io.github.xiaowu20245.nekopaint.core.network.SystemStatsDto
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemStatsMapperTest {

    @Test
    fun mapsSystemAndGpuFields() {
        val dto = SystemStatsDto(
            system = SystemInfoDto(
                os = "win32",
                ramTotal = 16_000L,
                ramFree = 8_000L,
                comfyUiVersion = "0.27.0",
                pythonVersion = "3.13.11",
                pytorchVersion = "2.9.1+cu128",
            ),
            devices = listOf(
                DeviceInfoDto(
                    name = "RTX 5060",
                    vramTotal = 8_000L,
                    vramFree = 2_000L,
                ),
            ),
        )

        val stats = dto.toDomain()

        assertEquals("0.27.0", stats.comfyUiVersion)
        assertEquals("win32", stats.os)
        assertEquals(1, stats.devices.size)
        assertEquals(6_000L, stats.devices.first().vramUsed)
        assertEquals(0.75f, stats.devices.first().vramUsedFraction)
    }
}

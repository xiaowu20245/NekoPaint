package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SystemStatsDto(
    val system: SystemInfoDto? = null,
    val devices: List<DeviceInfoDto> = emptyList(),
)

@Serializable
data class SystemInfoDto(
    val os: String? = null,
    @SerialName("ram_total") val ramTotal: Long? = null,
    @SerialName("ram_free") val ramFree: Long? = null,
    @SerialName("comfyui_version") val comfyUiVersion: String? = null,
    @SerialName("python_version") val pythonVersion: String? = null,
    @SerialName("pytorch_version") val pytorchVersion: String? = null,
    val argv: List<String> = emptyList(),
)

@Serializable
data class DeviceInfoDto(
    val name: String? = null,
    val type: String? = null,
    val index: Int? = null,
    @SerialName("vram_total") val vramTotal: Long = 0L,
    @SerialName("vram_free") val vramFree: Long = 0L,
    @SerialName("torch_vram_total") val torchVramTotal: Long = 0L,
    @SerialName("torch_vram_free") val torchVramFree: Long = 0L,
)

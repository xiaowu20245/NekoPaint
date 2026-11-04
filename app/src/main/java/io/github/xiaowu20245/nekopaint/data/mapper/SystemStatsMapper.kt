package io.github.xiaowu20245.nekopaint.data.mapper

import io.github.xiaowu20245.nekopaint.core.model.GpuInfo
import io.github.xiaowu20245.nekopaint.core.model.ServerStats
import io.github.xiaowu20245.nekopaint.core.network.SystemStatsDto

fun SystemStatsDto.toDomain(): ServerStats = ServerStats(
    comfyUiVersion = system?.comfyUiVersion,
    os = system?.os,
    pythonVersion = system?.pythonVersion,
    pytorchVersion = system?.pytorchVersion,
    ramTotal = system?.ramTotal,
    ramFree = system?.ramFree,
    devices = devices.map { device ->
        GpuInfo(
            name = device.name,
            vramTotal = device.vramTotal,
            vramFree = device.vramFree,
        )
    },
)

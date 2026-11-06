package io.github.xiaowu20245.nekopaint.core.network

import kotlinx.serialization.Serializable

@Serializable
data class UploadImageDto(
    val name: String = "",
    val subfolder: String = "",
    val type: String = "input",
)

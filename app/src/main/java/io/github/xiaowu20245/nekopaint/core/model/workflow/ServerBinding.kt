package io.github.xiaowu20245.nekopaint.core.model.workflow

import kotlinx.serialization.Serializable

/**
 * 安装/长操作绑定的服务器身份快照。
 *
 * 只保存可序列化字段，不嵌入 ServerConfig；执行时用这些字段重建配置。
 */
@Serializable
data class ServerBinding(
    val serverId: String,
    val endpointKey: String,
    val scheme: String,
    val host: String,
    val port: Int,
)

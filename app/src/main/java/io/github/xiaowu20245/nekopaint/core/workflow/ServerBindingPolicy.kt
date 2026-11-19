package io.github.xiaowu20245.nekopaint.core.workflow

import io.github.xiaowu20245.nekopaint.core.model.ServerConfig
import io.github.xiaowu20245.nekopaint.core.model.ServerProfile
import io.github.xiaowu20245.nekopaint.core.model.workflow.ServerBinding

/**
 * 长操作（节点安装/重检）的服务器绑定策略。
 *
 * 绑定只保存 serverId + endpointKey 等可序列化字段；执行时必须与当前
 * active profile 完全一致，避免用户切换服务器后把安装/复检写到错误主机。
 */
object ServerBindingPolicy {

    fun fromProfile(profile: ServerProfile): ServerBinding = ServerBinding(
        serverId = profile.id,
        endpointKey = profile.endpointKey,
        scheme = profile.scheme,
        host = profile.host,
        port = profile.port,
    )

    fun matches(profile: ServerProfile?, binding: ServerBinding?): Boolean {
        if (profile == null || binding == null) return false
        return profile.id == binding.serverId &&
            profile.endpointKey == binding.endpointKey &&
            profile.scheme.equals(binding.scheme, ignoreCase = true) &&
            profile.host.equals(binding.host, ignoreCase = true) &&
            profile.port == binding.port
    }

    fun toConfig(binding: ServerBinding): ServerConfig = ServerConfig(
        scheme = binding.scheme,
        host = binding.host,
        port = binding.port,
    )
}

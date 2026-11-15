package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.model.ai.AiHttpConsent

sealed interface AiEndpointPolicyResult {
    val allowed: Boolean
    val warning: String?
    val requiresLocalTrust: Boolean
    val requiresKeyApproval: Boolean
    val message: String?

    data class Allowed(
        override val warning: String? = null,
    ) : AiEndpointPolicyResult {
        override val allowed: Boolean = true
        override val requiresLocalTrust: Boolean = false
        override val requiresKeyApproval: Boolean = false
        override val message: String? = null
    }

    data class NeedsLocalTrust(
        override val message: String,
    ) : AiEndpointPolicyResult {
        override val allowed: Boolean = false
        override val warning: String? = null
        override val requiresLocalTrust: Boolean = true
        override val requiresKeyApproval: Boolean = false
    }

    data class NeedsKeyApproval(
        override val message: String,
        override val warning: String? = null,
    ) : AiEndpointPolicyResult {
        override val allowed: Boolean = false
        override val requiresLocalTrust: Boolean = false
        override val requiresKeyApproval: Boolean = true
    }

    data class Blocked(
        override val message: String,
    ) : AiEndpointPolicyResult {
        override val allowed: Boolean = false
        override val warning: String? = null
        override val requiresLocalTrust: Boolean = false
        override val requiresKeyApproval: Boolean = false
    }
}

object AiEndpointPolicy {

    private const val HTTP_WARNING = "当前 AI 地址使用明文 HTTP，提示词和图片可能被同一网络观察。"

    fun evaluate(
        endpoint: AiEndpoint,
        hasApiKey: Boolean,
        consent: AiHttpConsent,
    ): AiEndpointPolicyResult {
        if (endpoint.scheme == "https") {
            return AiEndpointPolicyResult.Allowed()
        }
        if (endpoint.scheme != "http") {
            return AiEndpointPolicyResult.Blocked("不支持的 AI 地址协议")
        }

        when (endpoint.addressClass) {
            AiAddressClass.PUBLIC -> {
                return AiEndpointPolicyResult.Blocked(
                    "公网地址不能使用明文 HTTP；请改用 HTTPS。",
                )
            }

            AiAddressClass.LOCAL_DOMAIN -> {
                if (endpoint.origin !in consent.trustedOrigins) {
                    return AiEndpointPolicyResult.NeedsLocalTrust(
                        "该本地域名使用明文 HTTP，需要先确认信任此地址。",
                    )
                }
            }

            AiAddressClass.LOOPBACK,
            AiAddressClass.PRIVATE,
            -> Unit
        }

        if (hasApiKey && endpoint.origin !in consent.keyApprovedOrigins) {
            return AiEndpointPolicyResult.NeedsKeyApproval(
                message = "通过明文 HTTP 发送 API Key 需要额外确认。",
                warning = HTTP_WARNING,
            )
        }

        return AiEndpointPolicyResult.Allowed(warning = HTTP_WARNING)
    }
}

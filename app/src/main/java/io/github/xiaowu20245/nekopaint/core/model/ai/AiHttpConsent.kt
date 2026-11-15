package io.github.xiaowu20245.nekopaint.core.model.ai

/**
 * User confirmations for insecure AI HTTP transports.
 *
 * Origins are stored exactly as computed by the shared AI endpoint parser.
 * An approval never applies to a different scheme, host or port.
 */
data class AiHttpConsent(
    val trustedOrigins: Set<String> = emptySet(),
    val keyApprovedOrigins: Set<String> = emptySet(),
)

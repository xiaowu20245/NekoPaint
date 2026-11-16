package io.github.xiaowu20245.nekopaint.data.repository

object TaskEventGuard {
    fun accepts(
        eventEndpointKey: String,
        currentIdentity: ServerIdentity?,
        blockedIdentities: Set<String>,
    ): Boolean {
        if (currentIdentity == null) return false
        if (currentIdentity.endpointKey != eventEndpointKey) return false
        val key = "${currentIdentity.serverId}|${currentIdentity.endpointKey}"
        return key !in blockedIdentities
    }
}

package io.github.xiaowu20245.nekopaint.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerEditPlannerTest {

    private val old = ServerProfile(
        id = "one",
        name = "工作机",
        scheme = "http",
        host = "192.168.1.10",
        port = 8188,
    )

    @Test
    fun nameAndAvatarChangesKeepId() {
        assertEquals(
            ServerEditPlan.KEEP_ID,
            ServerEditPlanner.plan(
                old,
                old.toConfig().copy(name = "新名字", avatarUri = "file:///avatar.png"),
            ),
        )
    }

    @Test
    fun hostPortAndSchemeChangesNeedNewId() {
        assertEquals(
            ServerEditPlan.NEW_ID,
            ServerEditPlanner.plan(old, old.toConfig().copy(host = "192.168.1.11")),
        )
        assertEquals(
            ServerEditPlan.NEW_ID,
            ServerEditPlanner.plan(old, old.toConfig().copy(port = 8189)),
        )
        assertEquals(
            ServerEditPlan.NEW_ID,
            ServerEditPlanner.plan(old, old.toConfig().copy(scheme = "https")),
        )
    }

    @Test
    fun normalizedHostCaseAndTrailingDotKeepId() {
        assertEquals(
            ServerEditPlan.KEEP_ID,
            ServerEditPlanner.plan(old, old.toConfig().copy(host = " 192.168.1.10. ")),
        )
    }

    @Test
    fun bareAndBracketedIpv6ShareEndpoint() {
        val ipv6 = ServerProfile(
            id = "v6",
            scheme = "http",
            host = "fe80::1",
            port = 8188,
        )
        assertEquals(
            ServerEditPlan.KEEP_ID,
            ServerEditPlanner.plan(ipv6, ipv6.toConfig().copy(host = "[fe80::1]")),
        )
    }
}

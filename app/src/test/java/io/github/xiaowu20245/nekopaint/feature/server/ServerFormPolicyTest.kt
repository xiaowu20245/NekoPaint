package io.github.xiaowu20245.nekopaint.feature.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerFormPolicyTest {

    @Test
    fun emptyHostReportsEmptyHostError() {
        val result = ServerFormPolicy.validate(ServerForm(host = "", port = "8188"))

        assertNull(result.config)
        assertEquals(ServerError.EmptyHost, result.hostError)
        assertNull(result.portError)
    }

    @Test
    fun invalidHostReportsInvalidHostError() {
        val result = ServerFormPolicy.validate(ServerForm(host = "foo#bar", port = "8188"))

        assertNull(result.config)
        assertEquals(ServerError.InvalidHost, result.hostError)
    }

    @Test
    fun invalidPortReportsPortErrorEvenWhenHostIsValid() {
        val result = ServerFormPolicy.validate(ServerForm(host = "192.168.1.10", port = "70000"))

        assertNull(result.config)
        assertNull(result.hostError)
        assertEquals(ServerError.InvalidPort, result.portError)
    }

    @Test
    fun validFormNormalizesHostAndUsesDefaultName() {
        val result = ServerFormPolicy.validate(
            ServerForm(name = "  ", host = "::1", port = "8188"),
        )

        assertEquals("我的 ComfyUI", result.config?.name)
        assertEquals("[::1]", result.config?.host)
        assertEquals(8188, result.config?.port)
        assertNull(result.hostError)
        assertNull(result.portError)
    }
}

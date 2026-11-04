package io.github.xiaowu20245.nekopaint.feature.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerFormTest {

    @Test
    fun toConfigOrNull_returnsNullForMissingHost() {
        val form = ServerForm(host = "", port = "8188")

        assertNull(form.toConfigOrNull())
    }

    @Test
    fun toConfigOrNull_usesDefaultNameWhenBlank() {
        val form = ServerForm(name = "  ", host = "192.168.1.10", port = "8188")

        assertEquals("我的 ComfyUI", form.toConfigOrNull()?.name)
    }

    @Test
    fun fromConfig_roundTrips() {
        val config = io.github.xiaowu20245.nekopaint.core.model.ServerConfig(
            name = "客厅电脑",
            scheme = "http",
            host = "192.168.1.10",
            port = 8188,
        )

        val form = ServerForm.fromConfig(config)

        assertEquals(config, form.toConfigOrNull())
    }
}

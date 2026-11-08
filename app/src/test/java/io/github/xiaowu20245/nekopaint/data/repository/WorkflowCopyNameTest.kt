package io.github.xiaowu20245.nekopaint.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowCopyNameTest {

    @Test
    fun appendsCopySuffix() {
        assertEquals(
            "工作流 副本",
            buildCopyTemplateName("工作流", emptySet()),
        )
    }

    @Test
    fun addsNumberWhenCopyExists() {
        assertEquals(
            "工作流 副本 2",
            buildCopyTemplateName("工作流", setOf("工作流 副本")),
        )
    }

    @Test
    fun keepsIncreasingUntilFree() {
        assertEquals(
            "工作流 副本 3",
            buildCopyTemplateName(
                "工作流",
                setOf("工作流 副本", "工作流 副本 2"),
            ),
        )
    }
}

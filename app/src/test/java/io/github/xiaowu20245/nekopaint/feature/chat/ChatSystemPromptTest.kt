package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSystemPromptTest {

    @Test
    fun `blank settings keep base prompt unchanged`() {
        assertEquals(
            "BASE",
            buildNekoSystemPrompt("BASE", persona = "", preInstruction = ""),
        )
    }

    @Test
    fun `persona is placed before base prompt`() {
        val prompt = buildNekoSystemPrompt(
            basePrompt = "BASE",
            persona = "你是猫娘助手",
            preInstruction = "",
        )

        assertTrue(prompt.startsWith("人设设定："))
        assertTrue(prompt.indexOf("你是猫娘助手") < prompt.indexOf("BASE"))
    }

    @Test
    fun `pre instruction is placed after base prompt with safety note`() {
        val prompt = buildNekoSystemPrompt(
            basePrompt = "BASE",
            persona = "",
            preInstruction = "先给结论",
        )

        assertTrue(prompt.indexOf("BASE") < prompt.indexOf("先给结论"))
        assertTrue(prompt.contains("安全规则"))
    }

    @Test
    fun `both settings are included`() {
        val prompt = buildNekoSystemPrompt(
            basePrompt = "BASE",
            persona = "温柔",
            preInstruction = "简洁",
        )

        assertTrue(prompt.contains("温柔"))
        assertTrue(prompt.contains("简洁"))
        assertTrue(prompt.contains("BASE"))
    }
}

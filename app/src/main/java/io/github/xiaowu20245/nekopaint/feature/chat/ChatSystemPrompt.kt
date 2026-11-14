package io.github.xiaowu20245.nekopaint.feature.chat

/**
 * 把用户配置的人设和预指令注入 Neko I 的系统提示。
 *
 * 预指令必须服从安全规则、工具确认和事实约束，不能覆盖这些基础规则。
 */
internal fun buildNekoSystemPrompt(
    basePrompt: String,
    persona: String,
    preInstruction: String,
): String {
    val personaText = persona.trim()
    val instructionText = preInstruction.trim()
    if (personaText.isBlank() && instructionText.isBlank()) return basePrompt.trim()
    return buildString {
        if (personaText.isNotBlank()) {
            appendLine("人设设定：")
            appendLine(personaText)
            appendLine()
        }
        appendLine(basePrompt.trim())
        if (instructionText.isNotBlank()) {
            appendLine()
            appendLine("用户预指令（在不违反安全规则、工具确认和事实约束的前提下遵循）：")
            appendLine(instructionText)
        }
    }.trim()
}

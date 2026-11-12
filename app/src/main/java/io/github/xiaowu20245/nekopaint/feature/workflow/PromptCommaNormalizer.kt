package io.github.xiaowu20245.nekopaint.feature.workflow

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** 提示词统一使用英文逗号加空格；中文逗号在输入过程中实时纠偏。 */
internal fun normalizePromptCommas(text: String): String = text.replace("，", ", ")

/**
 * 纠偏文本并同步光标。
 *
 * 每个被替换的中文逗号会额外插入一个空格，因此光标要按前后文数量补偿，
 * 避免用户继续输入时落到逗号前面。
 */
internal fun normalizePromptTextFieldValue(value: TextFieldValue): TextFieldValue {
    val normalizedText = normalizePromptCommas(value.text)
    if (normalizedText == value.text) return value

    fun mapOffset(offset: Int): Int {
        val safe = offset.coerceIn(0, value.text.length)
        val extra = value.text.take(safe).count { it == '，' }
        return (safe + extra).coerceAtMost(normalizedText.length)
    }

    return TextFieldValue(
        text = normalizedText,
        selection = TextRange(
            mapOffset(value.selection.start),
            mapOffset(value.selection.end),
        ),
    )
}

package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.privacy.SensitiveHostProvider

/**
 * Redacts local paths, URLs and configured server hosts from AI system/tool
 * context. User-authored prompt text is not passed through this class.
 */
class AiPrivacySanitizer(
    private val hostProvider: SensitiveHostProvider,
) {

    suspend fun awaitReady(): Boolean = hostProvider.awaitReady()

    fun sanitize(text: String): String {
        if (text.isBlank()) return text
        var result = text
        result = result.replace(WINDOWS_PATH) { redactPath(it.value) }
        result = result.replace(UNC_PATH) { redactPath(it.value) }
        result = result.replace(UNIX_PATH) { redactPath(it.value) }
        result = result.replace(URL, LINK)
        result = result.replace(IPV4, HOST)
        result = result.replace(IPV6, HOST)
        result = result.replace(IPV6_BARE, HOST)

        hostProvider.hosts()
            .asSequence()
            .map { it.trim().trim('[', ']') }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { host ->
                host.equals("localhost", ignoreCase = true) ||
                    host.contains(':') ||
                    host.length >= 4 && host.contains('.')
            }
            .forEach { host ->
                val pattern = Regex(
                    "(?i)(?<![\\w.-])${Regex.escape(host)}(?![\\w.-])",
                )
                result = result.replace(pattern, HOST)
            }
        return result
    }

    private fun redactPath(value: String): String {
        val marker = PARAMETER_MARKER.find(value)
        if (marker != null) return LOCAL_PATH + value.substring(marker.range.first)
        // Keep trailing Chinese diagnostics after a file name, e.g.
        // "C:\Users\alice\a.png 处理失败" -> "<本地路径> 处理失败".
        // Requiring a file extension avoids exposing directory names that
        // merely contain spaces.
        val prose = CJK_PROSE_AFTER_FILE.find(value)
        if (prose != null) {
            val proseStart = prose.value.indexOfFirst { it.isWhitespace() }
            return LOCAL_PATH + prose.value.substring(proseStart)
        }
        return LOCAL_PATH
    }

    private companion object {
        const val LOCAL_PATH = "<本地路径>"
        const val LINK = "<链接>"
        const val HOST = "<主机>"

        val WINDOWS_PATH = Regex("""[A-Za-z]:\\[^\r\n，。；;,"]+""")
        val UNC_PATH = Regex("""\\\\[^\r\n，。；;,"]+""")
        val UNIX_PATH = Regex(
            """(?<![\w.-])/(?:home|Users|root|data|storage|sdcard|mnt)/[^\r\n，。；;,"]+""",
        )
        val PARAMETER_MARKER = Regex(
            """(?:\s+[A-Za-z_][A-Za-z0-9_-]*\s*=|\s+[\u4e00-\u9fff]{1,10}[:：])""",
        )
        val CJK_PROSE_AFTER_FILE = Regex(
            """\.[A-Za-z0-9]{1,12}(?:\s+[\u4e00-\u9fff][^\s\\/]*)+$""",
        )
        val URL = Regex("""https?://[^\s，。；;"}]+""")
        val IPV4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
        val IPV6 = Regex("""(?<![\w])\[[0-9a-fA-F:]+\](?![\w])""")
        val IPV6_BARE = Regex(
            """(?<![\w:])(?:(?:[0-9a-fA-F]{0,4}:){3,}[0-9a-fA-F]{0,4}|[0-9a-fA-F]*::[0-9a-fA-F:]+)(?![\w:])""",
        )
    }
}

package io.github.xiaowu20245.nekopaint.core.network

import io.github.xiaowu20245.nekopaint.core.privacy.SensitiveHostProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPrivacySanitizerTest {

    private val sanitizer = AiPrivacySanitizer(
        SensitiveHostProvider {
            setOf("192.168.1.10", "comfy.local", "短", "::1")
        },
    )

    @Test
    fun redactsWindowsUncAndUnixHomePathsButKeepsParameters() {
        val text = "失败：C:\\Users\\alice\\a.png, seed=123, steps=20\n" +
            "共享：\\\\nas\\share\\a.png, cfg=7 /home/alice/workflow.json, seed=9\n" +
            "Android：/data/user/0/a.png, status=failed /storage/emulated/0/b.png"

        val result = sanitizer.sanitize(text)

        assertFalse(result.contains("C:\\Users"))
        assertFalse(result.contains("\\\\nas"))
        assertFalse(result.contains("/home/alice"))
        assertFalse(result.contains("/data/user/0"))
        assertFalse(result.contains("/storage/emulated/0"))
        assertTrue(result.contains("seed=123"))
        assertTrue(result.contains("steps=20"))
        assertTrue(result.contains("cfg=7"))
        assertTrue(result.contains("status=failed"))
    }

    @Test
    fun pathFollowedBySpaceParametersStillKeepsParameters() {
        val result = sanitizer.sanitize("路径 C:\\Users\\alice\\a.png seed=123 steps=20")

        assertFalse(result.contains("C:\\Users"))
        assertTrue(result.contains("seed=123"))
        assertTrue(result.contains("steps=20"))
    }

    @Test
    fun redactsUrlsIpsAndConfiguredHosts() {
        val text = "连接 http://192.168.1.10:8188/view?filename=a.png，" +
            "主机 comfy.local，另一个 8.8.8.8，IPv6 [::1]。"

        val result = sanitizer.sanitize(text)

        assertFalse(result.contains("192.168.1.10"))
        assertFalse(result.contains("comfy.local"))
        assertFalse(result.contains("8.8.8.8"))
        assertFalse(result.contains("[::1]"))
        assertTrue(result.contains("<链接>"))
        assertTrue(result.contains("<主机>"))
    }

    @Test
    fun redactsBareIpv6Hosts() {
        val result = sanitizer.sanitize("连接 ::1 和 fe80::1，端口 8188")

        assertFalse(result.contains("::1"))
        assertFalse(result.contains("fe80::1"))
        assertTrue(result.contains("8188"))
    }

    @Test
    fun doesNotRedactShortHostSubstrings() {
        val result = sanitizer.sanitize("状态：短任务完成，参数 seed=123，steps=20")

        assertTrue(result.contains("短任务"))
        assertTrue(result.contains("seed=123"))
        assertTrue(result.contains("steps=20"))
    }

    @Test
    fun preservesNormalDiagnosticTextAndTimes() {
        val result = sanitizer.sanitize("时间 12:30，状态 failed，错误 unknown，参数 cfg=7")

        assertEquals("时间 12:30，状态 failed，错误 unknown，参数 cfg=7", result)
    }

    @Test
    fun keepsChineseDiagnosticsAfterLocalFilePath() {
        val result = sanitizer.sanitize("路径 C:\\Users\\alice\\a.png 处理失败")

        assertEquals("路径 <本地路径> 处理失败", result)
    }

    @Test
    fun stillRedactsDirectoryWithSpacesButNoExtension() {
        val result = sanitizer.sanitize("路径 C:\\Users\\alice\\my folder")

        assertEquals("路径 <本地路径>", result)
    }

    @Test
    fun redactsJsonPathWithoutBreakingJsonStructure() {
        val result = sanitizer.sanitize("""{"path":"C:\\Users\\alice\\a.png","cfg":7}""")

        assertEquals("""{"path":"<本地路径>","cfg":7}""", result)
    }

    @Test
    fun awaitReadyReturnsFalseWhenHostProviderFails() = runTest {
        val failing = object : SensitiveHostProvider {
            override fun hosts(): Set<String> = emptySet()

            override suspend fun awaitReady(): Boolean = false
        }
        val sanitizer = AiPrivacySanitizer(failing)

        assertFalse(sanitizer.awaitReady())
    }
}

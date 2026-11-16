package io.github.xiaowu20245.nekopaint.data.repository

import java.security.MessageDigest

/**
 * 把外部 ID 映射为安全的磁盘文件名片段。
 *
 * 合法值保持原样（UUID、default、普通标识符），避免迁移既有任务文件；
 * 其余值使用 `sha256_` + 64 位十六进制摘要，固定 71 字符。已经是该格式的值
 * 直接返回，防止二次哈希。
 */
internal object SafeFileNames {

    private val SAFE_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    private val HASHED_PATTERN = Regex("sha256_[0-9a-f]{64}")

    fun encode(value: String): String {
        if (SAFE_PATTERN.matches(value)) return value
        if (HASHED_PATTERN.matches(value)) return value
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return "sha256_" + digest.joinToString("") { "%02x".format(it) }
    }
}

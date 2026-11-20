package io.github.xiaowu20245.nekopaint.core.security

/**
 * 解密结果到 UI 状态的纯映射。
 *
 * 密文为空表示“未配置 Key”；密文非空但解密失败表示 Key 已损坏/密钥丢失，
 * 此时必须提示用户重新输入，不能把密文或异常细节暴露到界面。
 */
object SecretCipherPolicy {

    fun isInvalid(encrypted: String, decrypted: String?): Boolean =
        encrypted.isNotBlank() && decrypted == null
}

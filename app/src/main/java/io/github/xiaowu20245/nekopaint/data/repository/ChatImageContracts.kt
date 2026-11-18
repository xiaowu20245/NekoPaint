package io.github.xiaowu20245.nekopaint.data.repository

import android.net.Uri

/** ChatImageEncoder 的接口，供 ChatViewModel 在 JVM 测试中替换。 */
interface ChatImageEncoderApi {
    suspend fun toDataUrl(
        uri: Uri,
        maxDimension: Int = ChatImageDecodePolicy.TARGET_DIMENSION,
        quality: Int = 85,
    ): Result<String>
}

/** ChatImageStore 的接口，供 ChatViewModel 在 JVM 测试中替换。 */
interface ChatImageStorage {
    suspend fun save(id: String, dataUrl: String)

    suspend fun load(id: String): String?

    suspend fun delete(ids: Collection<String>)
}

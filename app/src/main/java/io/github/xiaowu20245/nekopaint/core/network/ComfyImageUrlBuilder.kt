package io.github.xiaowu20245.nekopaint.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 构造 ComfyUI `/view` 图片地址。
 *
 * 文件名和子目录可能包含空格、中文、`#`、`?` 等字符，必须交给 HttpUrl 编码，
 * 不能直接字符串拼接，否则服务器会收到错误的查询参数。
 */
object ComfyImageUrlBuilder {

    fun build(
        baseUrl: String,
        filename: String,
        subfolder: String,
        type: String,
    ): String? = runCatching {
        baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegment("view")
            .addQueryParameter("filename", filename)
            .addQueryParameter("subfolder", subfolder)
            .addQueryParameter("type", type)
            .build()
            .toString()
    }.getOrNull()
}

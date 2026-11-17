package io.github.xiaowu20245.nekopaint.data.repository

/**
 * ComfyUI 提交响应分类。
 *
 * 429/5xx 可能代表服务器已收到但未完成处理，因此保持同一个 promptId 退避重试一次；
 * 超时/IO 不自动重试，由恢复流程先查队列/历史，避免重复任务。
 */
object PromptSubmissionPolicy {

    const val MAX_HTTP_ATTEMPTS = 2
    const val RETRY_DELAY_MS = 750L

    enum class HttpClassification {
        ACCEPTED,
        REJECTED,
        UNKNOWN_RETRYABLE,
    }

    fun classify(code: Int): HttpClassification = when {
        code in 200..299 -> HttpClassification.ACCEPTED
        code == 429 || code in 500..599 -> HttpClassification.UNKNOWN_RETRYABLE
        else -> HttpClassification.REJECTED
    }

    /** [attempt] 为已经收到的响应次数，从 1 开始。 */
    fun shouldRetry(code: Int, attempt: Int): Boolean =
        classify(code) == HttpClassification.UNKNOWN_RETRYABLE &&
            attempt < MAX_HTTP_ATTEMPTS
}

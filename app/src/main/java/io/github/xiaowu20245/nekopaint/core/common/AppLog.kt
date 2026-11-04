package io.github.xiaowu20245.nekopaint.core.common

import android.util.Log
import io.github.xiaowu20245.nekopaint.BuildConfig

/**
 * 统一日志入口。Release 构建不输出任何日志。
 *
 * 不要记录提示词正文、图片内容或完整服务器地址。
 */
object AppLog {

    private const val TAG = "NekoPaint"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(TAG, message, throwable)
    }
}

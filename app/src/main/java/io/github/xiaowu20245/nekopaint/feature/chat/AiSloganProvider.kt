package io.github.xiaowu20245.nekopaint.feature.chat

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.xiaowu20245.nekopaint.R
import java.time.LocalTime
import kotlin.random.Random

/**
 * AI 助手空状态标语。
 *
 * 时间规则与文本来自 `D:\Projects\text.txt`：
 * - 00:00–04:30：深夜文案
 * - 04:30–06:00：zzZ...
 * - 06:00–09:00：早上好喵~
 * - 09:00–18:00：常规文案
 * - 18:00–24:00：晚上好，喵~
 * - 00:00–09:00 的标语只搭配睡眠颜文字池
 * - 09:00–24:00 的标语只搭配常规颜文字池
 * - Ciallo~! 自身包含固定颜文字，不再追加
 */
object AiSloganProvider {

    private val generalFallback = listOf("需要帮忙嘛")

    @Volatile
    private var lastKaomoji: String? = null

    fun pick(
        generalSlogans: List<String>,
        lateNightSlogans: List<String>,
        morningSlogan: String,
        eveningSlogan: String,
        sleepSlogan: String,
        generalKaomoji: List<String>,
        sleepKaomoji: List<String>,
        now: LocalTime = LocalTime.now(),
        random: Random = Random.Default,
        previousKaomoji: String? = null,
    ): String {
        val minuteOfDay = now.hour * 60 + now.minute
        val slogan = when {
            minuteOfDay < 4 * 60 + 30 -> lateNightSlogans.ifEmpty { generalSlogans }
                .random(random)
            minuteOfDay < 6 * 60 -> sleepSlogan
            minuteOfDay < 9 * 60 -> morningSlogan
            minuteOfDay >= 18 * 60 -> eveningSlogan
            else -> generalSlogans.ifEmpty { generalFallback }.random(random)
        }

        // Ciallo~! 自带固定颜文字，不能再追加。
        if (slogan.contains("Ciallo~!")) {
            lastKaomoji = null
            return slogan
        }

        // 00:00–09:00 的标语只使用睡眠颜文字池；
        // 09:00–24:00 的标语只使用常规颜文字池。
        val kaomoji = if (minuteOfDay < 9 * 60) sleepKaomoji else generalKaomoji
        val previous = previousKaomoji ?: lastKaomoji
        val candidates = if (kaomoji.size > 1 && previous != null) {
            kaomoji.filter { it != previous }.ifEmpty { kaomoji }
        } else {
            kaomoji
        }
        val picked = candidates.ifEmpty { listOf("(・ω・)ノ") }.random(random)
        lastKaomoji = picked
        return "$slogan $picked"
    }

    @Composable
    fun rememberSlogan(): String {
        val context = LocalContext.current
        return remember { pick(context) }
    }

    private fun pick(context: Context): String {
        val resources = context.resources
        return pick(
            generalSlogans = resources.getStringArray(R.array.ai_slogan_general).toList(),
            lateNightSlogans = resources.getStringArray(R.array.ai_slogan_late_night).toList(),
            morningSlogan = resources.getString(R.string.ai_slogan_morning),
            eveningSlogan = resources.getString(R.string.ai_slogan_evening),
            sleepSlogan = resources.getString(R.string.ai_slogan_sleep),
            generalKaomoji = resources.getStringArray(R.array.ai_kaomoji_general).toList(),
            sleepKaomoji = resources.getStringArray(R.array.ai_kaomoji_sleep).toList(),
        )
    }

    internal fun resetForTest() {
        lastKaomoji = null
    }
}

package io.github.xiaowu20245.nekopaint.feature.chat

import java.time.LocalTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSloganProviderTest {

    private val general = listOf("喵...喵喵喵？！", "需要帮忙嘛", "让我看看")
    private val lateNight = listOf("还在忙嘛", "夜深了喵~", "今天也辛苦了你啦")
    private val generalKaomoji = listOf("(✧ω✧)ₚ!", "(・ω・)ノ", "(･ω･ฅ)")
    private val sleepKaomoji = listOf("(^-ω-^)", "(-ω-｡)", "(つω-｀)｡oO")

    private fun pick(
        time: String,
        random: Random = Random(1),
        previousKaomoji: String? = null,
        generalSlogans: List<String> = general,
    ): String = AiSloganProvider.pick(
        generalSlogans = generalSlogans,
        lateNightSlogans = lateNight,
        morningSlogan = "早上好喵~",
        eveningSlogan = "晚上好，喵~",
        sleepSlogan = "zzZ...",
        generalKaomoji = generalKaomoji,
        sleepKaomoji = sleepKaomoji,
        now = LocalTime.parse(time),
        random = random,
        previousKaomoji = previousKaomoji,
    )

    @Test
    fun `daytime uses general slogan and general kaomoji`() {
        val result = pick("12:00")
        assertTrue(general.any { result.startsWith("$it ") })
        assertTrue(generalKaomoji.any { result.endsWith(" $it") })
    }

    @Test
    fun `morning uses morning slogan and sleep kaomoji`() {
        val result = pick("07:00")
        assertTrue(result.startsWith("早上好喵~ "))
        assertTrue(sleepKaomoji.any { result.endsWith(" $it") })
    }

    @Test
    fun `evening uses evening slogan and general kaomoji`() {
        val result = pick("20:00")
        assertTrue(result.startsWith("晚上好，喵~ "))
        assertTrue(generalKaomoji.any { result.endsWith(" $it") })
    }

    @Test
    fun `late night uses late night slogan and sleep kaomoji`() {
        val result = pick("03:00")
        assertTrue(lateNight.any { result.startsWith("$it ") })
        assertTrue(sleepKaomoji.any { result.endsWith(" $it") })
    }

    @Test
    fun `early morning uses sleep slogan and sleep kaomoji`() {
        val result = pick("05:00")
        assertTrue(result.startsWith("zzZ... "))
        assertTrue(sleepKaomoji.any { result.endsWith(" $it") })
    }

    @Test
    fun `ciallo keeps its fixed kaomoji`() {
        val fixed = "Ciallo~! (∠・ω< )⌒☆"
        val result = pick("12:00", generalSlogans = listOf(fixed))
        assertEquals(fixed, result)
    }

    @Test
    fun `previous kaomoji is avoided when possible`() {
        val result = pick("12:00", previousKaomoji = generalKaomoji.first())
        assertFalse(result.endsWith(" ${generalKaomoji.first()}"))
    }
}

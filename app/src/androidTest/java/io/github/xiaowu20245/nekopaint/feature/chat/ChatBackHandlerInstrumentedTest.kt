package io.github.xiaowu20245.nekopaint.feature.chat

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatBackHandlerInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun actionExecutingBlocksSystemAndTopBarBack() {
        var handler: (() -> Boolean)? = null
        var executing by mutableStateOf(true)

        compose.setContent {
            ChatBackHandler(
                actionExecuting = executing,
                onBackHandlerChange = { handler = it },
                blockedMessage = "动作执行中，请等待完成",
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) { handler != null }
        assertNotNull(handler)
        compose.runOnIdle { assertTrue(handler?.invoke() == true) }

        // 系统返回：BackHandler 应消费，Activity 不进入 finishing。
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertFalse(compose.activity.isFinishing)

        compose.runOnUiThread { executing = false }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(handler?.invoke() == true) }
    }
}

package io.github.xiaowu20245.nekopaint.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatIntentRouterTest {

    @Test
    fun `queue questions route to queue`() {
        assertEquals(ChatIntent.QUEUE, ChatIntentRouter.route("队列里现在有哪些任务？"))
        assertEquals(ChatIntent.QUEUE, ChatIntentRouter.route("还有几个在跑"))
        assertEquals(ChatIntent.QUEUE, ChatIntentRouter.route("what is in the queue"))
    }

    @Test
    fun `history questions route to history`() {
        assertEquals(ChatIntent.HISTORY, ChatIntentRouter.route("最近的历史记录怎么样"))
        assertEquals(ChatIntent.HISTORY, ChatIntentRouter.route("生成过哪些图"))
    }

    @Test
    fun `workflow questions route to workflow`() {
        assertEquals(ChatIntent.WORKFLOW, ChatIntentRouter.route("解释一下这个工作流的参数"))
        assertEquals(ChatIntent.WORKFLOW, ChatIntentRouter.route("ControlNet 模板有哪些节点"))
    }

    @Test
    fun `failure questions take priority`() {
        assertEquals(ChatIntent.FAILURE, ChatIntentRouter.route("队列里的任务为什么失败了"))
        assertEquals(ChatIntent.FAILURE, ChatIntentRouter.route("最近一次 OOM 是什么原因"))
    }

    @Test
    fun `general chat falls back`() {
        assertEquals(ChatIntent.GENERAL, ChatIntentRouter.route("你好，介绍一下你自己"))
    }
}

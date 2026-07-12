package com.iccyuan.hush.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvalThrottleTest {

    private val interval = 30_000L

    /**
     * 头一次必须放行——若这里错成 false，针对常驻通知的规则将**永不生效**（它们只在
     * 通知刷新时被求值，而刷新会被一路拦下）。这是这段逻辑最要命的一个分支。
     */
    @Test
    fun `first call is always allowed`() {
        val throttle = EvalThrottle(interval)
        assertTrue(throttle.due("k", now = 1_000))
    }

    @Test
    fun `repeat within the interval is throttled`() {
        val throttle = EvalThrottle(interval)
        throttle.due("k", now = 1_000)
        assertFalse(throttle.due("k", now = 1_000 + 1))
        assertFalse(throttle.due("k", now = 1_000 + interval - 1))
    }

    @Test
    fun `call at or after the interval is allowed again`() {
        val throttle = EvalThrottle(interval)
        throttle.due("k", now = 1_000)
        assertTrue(throttle.due("k", now = 1_000 + interval))
    }

    /** 放行会重置计时，否则一条持续刷新的通知会每隔 intervalMs 之后次次放行。 */
    @Test
    fun `allowing resets the window`() {
        val throttle = EvalThrottle(interval)
        throttle.due("k", now = 0)
        assertTrue(throttle.due("k", now = interval))
        assertFalse(throttle.due("k", now = interval + 1))
    }

    /** 节流是按 key 的：一条常驻通知的刷新不该拖累另一条。 */
    @Test
    fun `keys are throttled independently`() {
        val throttle = EvalThrottle(interval)
        assertTrue(throttle.due("a", now = 0))
        assertTrue(throttle.due("b", now = 0))
        assertFalse(throttle.due("a", now = 1))
    }

    /** 通知消失后重建（如 VPN 重连），应当立刻被求值，而不是继续吃上一轮的节流。 */
    @Test
    fun `forgetting a key allows immediate re-evaluation`() {
        val throttle = EvalThrottle(interval)
        throttle.due("k", now = 0)
        assertFalse(throttle.due("k", now = 1))
        throttle.forget("k")
        assertTrue(throttle.due("k", now = 2))
    }
}

package com.iccyuan.hush.engine

import com.iccyuan.hush.data.model.Action
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.DayType
import com.iccyuan.hush.data.model.LogicMode
import com.iccyuan.hush.data.model.MatchMode
import com.iccyuan.hush.data.model.NotificationField
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.data.model.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleEngineTest {

    private val engine = RuleEngine()

    @Before fun reset() = VariableStore.clear()

    private fun device(
        minuteOfDay: Int = 12 * 60,
        isoDayOfWeek: Int = 1,
        nowMillis: Long = 1_000_000L,
        dayType: DayType = DayType.WORKDAY,
        charging: Boolean = false,
        batteryPercent: Int = 50,
    ) = DeviceContext(charging, true, batteryPercent, minuteOfDay, isoDayOfWeek, dayType, nowMillis)

    private fun ctx(
        pkg: String = "com.chat",
        title: String = "",
        text: String = "",
        device: DeviceContext = device(),
        userId: Int = 0,
        isPersistent: Boolean = false,
    ): MatchContext {
        val fields = mutableMapOf<NotificationField, String>()
        if (title.isNotEmpty()) fields[NotificationField.TITLE] = title
        if (text.isNotEmpty()) fields[NotificationField.TEXT] = text
        return MatchContext(pkg, "Chat", fields, false, false, device, userId = userId, isPersistent = isPersistent)
    }

    private fun textRule(
        id: Long,
        query: String,
        pkg: List<String> = emptyList(),
        actions: List<Action> = listOf(Action.DiscardAction("a$id")),
        negate: Boolean = false,
        stop: Boolean = false,
        conditions: List<Condition> = emptyList(),
    ) = Rule(
        id = id,
        appPackages = pkg,
        triggers = listOf(
            Trigger.TextTrigger("t$id", NotificationField.ANY, MatchMode.CONTAINS, query, negate = negate)
        ),
        conditions = conditions,
        actions = actions,
        stopProcessing = stop,
    )

    @Test fun matchingTextTriggerDiscards() {
        val d = engine.evaluate(ctx(text = "spam offer"), listOf(textRule(1, "spam")))
        assertTrue(d.matched)
        assertTrue(d.discard)
        assertTrue(d.firedRuleIds.contains(1L))
    }

    @Test fun nonMatchingTextDoesNothing() {
        val d = engine.evaluate(ctx(text = "hello"), listOf(textRule(1, "spam")))
        assertFalse(d.matched)
        assertFalse(d.discard)
    }

    @Test fun appPackageFilterRestrictsRule() {
        val rule = textRule(1, "x", pkg = listOf("com.other"))
        assertFalse(engine.evaluate(ctx(pkg = "com.chat", text = "x"), listOf(rule)).matched)
        assertTrue(engine.evaluate(ctx(pkg = "com.other", text = "x"), listOf(rule)).matched)
    }

    @Test fun bareTokenMatchesAnyUser() {
        // 旧格式裸包名：匹配任意用户空间（本体 + 分身）。
        val rule = textRule(1, "x", pkg = listOf("com.chat"))
        assertTrue(engine.evaluate(ctx(pkg = "com.chat", text = "x", userId = 0), listOf(rule)).matched)
        assertTrue(engine.evaluate(ctx(pkg = "com.chat", text = "x", userId = 999), listOf(rule)).matched)
    }

    @Test fun mainTokenMatchesOnlyMainUser() {
        val rule = textRule(1, "x", pkg = listOf("com.chat@0"))
        assertTrue(engine.evaluate(ctx(pkg = "com.chat", text = "x", userId = 0), listOf(rule)).matched)
        assertFalse(engine.evaluate(ctx(pkg = "com.chat", text = "x", userId = 999), listOf(rule)).matched)
    }

    @Test fun cloneTokenMatchesOnlyCloneUser() {
        val rule = textRule(1, "x", pkg = listOf("com.chat@999"))
        assertFalse(engine.evaluate(ctx(pkg = "com.chat", text = "x", userId = 0), listOf(rule)).matched)
        assertTrue(engine.evaluate(ctx(pkg = "com.chat", text = "x", userId = 999), listOf(rule)).matched)
    }

    @Test fun mixedTokensSelectMainAndCloneIndependently() {
        // 只选分身：本体不受影响（用户报告的核心诉求）。
        val cloneOnly = textRule(1, "x", pkg = listOf("com.chat@999"))
        assertFalse(engine.evaluate(ctx(pkg = "com.chat", text = "x", userId = 0), listOf(cloneOnly)).matched)
        assertTrue(engine.evaluate(ctx(pkg = "com.chat", text = "x", userId = 999), listOf(cloneOnly)).matched)
    }

    @Test fun danmakuShownForNonPersistent() {
        val rule = textRule(1, "x").copy(showDanmaku = true) // 默认丢弃动作
        val d = engine.evaluate(ctx(text = "x", isPersistent = false), listOf(rule))
        assertTrue(d.sideEffects.any { it is SideEffect.Danmaku })
    }

    @Test fun danmakuExcludesPersistentByDefault() {
        // 普通规则遇到常驻通知（VPN 等）→ 仍丢弃，但不弹幕。
        val rule = textRule(1, "x").copy(showDanmaku = true)
        val d = engine.evaluate(ctx(text = "x", isPersistent = true), listOf(rule))
        assertTrue(d.discard)
        assertFalse(d.sideEffects.any { it is SideEffect.Danmaku })
    }

    @Test fun danmakuAllowedForPersistentWhenRuleTargetsOngoing() {
        // 规则带「必须是常驻通知」触发器 = 明确针对常驻 → 对常驻放行弹幕。
        val rule = Rule(
            id = 1,
            triggers = listOf(Trigger.OngoingTrigger("t", mustBeOngoing = true)),
            actions = listOf(Action.DiscardAction("a")),
            showDanmaku = true,
        )
        // 常驻通知：触发器命中（isPersistent==true）→ 丢弃 + 弹幕。
        val persistent = engine.evaluate(ctx(text = "x", isPersistent = true), listOf(rule))
        assertTrue(persistent.discard)
        assertTrue(persistent.sideEffects.any { it is SideEffect.Danmaku })
        // 非常驻通知：该触发器不命中 → 规则不触发。
        val normal = engine.evaluate(ctx(text = "x", isPersistent = false), listOf(rule))
        assertFalse(normal.matched)
    }

    @Test fun danmakuOnlyWhenRuleDiscards() {
        // 未丢弃（仅标重要）→ 不弹幕，避免原生通知与弹幕并存。
        val rule = textRule(1, "x", actions = listOf(Action.MarkImportantAction("a1"))).copy(showDanmaku = true)
        val d = engine.evaluate(ctx(text = "x"), listOf(rule))
        assertFalse(d.sideEffects.any { it is SideEffect.Danmaku })
    }

    @Test fun negateInvertsTrigger() {
        val rule = textRule(1, "spam", negate = true)
        assertTrue(engine.evaluate(ctx(text = "hello"), listOf(rule)).matched)
        assertFalse(engine.evaluate(ctx(text = "spam"), listOf(rule)).matched)
    }

    @Test fun triggerLogicAllVsAny() {
        val triggers = listOf(
            Trigger.TextTrigger("t1", NotificationField.ANY, MatchMode.CONTAINS, "a"),
            Trigger.TextTrigger("t2", NotificationField.ANY, MatchMode.CONTAINS, "b"),
        )
        val all = Rule(id = 1, triggers = triggers, triggerLogic = LogicMode.ALL,
            actions = listOf(Action.DiscardAction("x")))
        val any = all.copy(triggerLogic = LogicMode.ANY)
        assertFalse(engine.evaluate(ctx(text = "only a"), listOf(all)).matched)
        assertTrue(engine.evaluate(ctx(text = "a and b"), listOf(all)).matched)
        assertTrue(engine.evaluate(ctx(text = "only a"), listOf(any)).matched)
    }

    @Test fun stopProcessingHaltsLaterRules() {
        val r1 = textRule(1, "x", actions = listOf(Action.MarkImportantAction("imp")), stop = true)
        val r2 = textRule(2, "x")
        val d = engine.evaluate(ctx(text = "x"), listOf(r1, r2))
        assertTrue(d.firedRuleIds.contains(1L))
        assertFalse("r2 应因 stopProcessing 而未被评估", d.firedRuleIds.contains(2L))
    }

    @Test fun timeWindowAcrossMidnightIncludesLateNight() {
        // 窗口 22:00–07:00 跨午夜。23:30 周一应在窗口内。
        val rule = textRule(
            1, "x",
            conditions = listOf(Condition.TimeCondition("c", 22 * 60, 7 * 60, setOf(1, 2, 3, 4, 5, 6, 7))),
        )
        val inside = engine.evaluate(ctx(text = "x", device = device(minuteOfDay = 23 * 60 + 30)), listOf(rule))
        val outside = engine.evaluate(ctx(text = "x", device = device(minuteOfDay = 12 * 60)), listOf(rule))
        assertTrue(inside.matched)
        assertFalse(outside.matched)
    }

    @Test fun cooldownSuppressesSecondFire() {
        val rule = textRule(1, "x", conditions = listOf(Condition.CooldownCondition("c", 60)))
        val first = engine.evaluate(ctx(text = "x", device = device(nowMillis = 1_000L)), listOf(rule))
        assertTrue(first.matched)
        // 仍在 60s 冷却窗口内。
        val second = engine.evaluate(ctx(text = "x", device = device(nowMillis = 30_000L)), listOf(rule))
        assertFalse("冷却期内不应再次触发", second.matched)
        // 冷却结束后恢复。
        val third = engine.evaluate(ctx(text = "x", device = device(nowMillis = 61_001L)), listOf(rule))
        assertTrue(third.matched)
    }

    @Test fun mutedAppShortCircuitsToDiscard() {
        // 无条件的「静音应用」规则在场：其静音始终生效，丢弃该应用的一切通知（连不匹配触发器的也丢）。
        val muteRule = Rule(id = 100_000L, appPackages = listOf("com.chat"),
            actions = listOf(Action.MuteAppAction("m")))
        VariableStore.muteApp("com.chat", 100_000L)
        val d = engine.evaluate(ctx(pkg = "com.chat", text = "anything", device = device(nowMillis = 50_000L)),
            listOf(muteRule, textRule(1, "neverused")))
        assertTrue(d.matched)
        assertTrue(d.discard)
    }

    @Test fun mutedAppClearedWhenMutingRuleGone() {
        // 设置静音的规则已删除/停用（不在 rules 中）→ 静音自动解除，不再丢弃。
        VariableStore.muteApp("com.chat", 999L)
        val d = engine.evaluate(ctx(pkg = "com.chat", text = "x"), listOf(textRule(1, "unrelated")))
        assertFalse(d.discard)
        assertFalse(VariableStore.isAppMuted("com.chat"))
    }

    @Test fun mutedAppRespectsTimeConditionOfMutingRule() {
        // 「仅在 12:00–14:00 静音应用」：过了时段静音失效，回到时段自动恢复。这正是用户反馈的场景。
        val rule = Rule(
            id = 7, appPackages = listOf("com.chat"),
            conditions = listOf(Condition.TimeCondition("tc", 12 * 60, 14 * 60, setOf(1, 2, 3, 4, 5, 6, 7))),
            actions = listOf(Action.MuteAppAction("m")),
        )
        // 时段内命中并设置静音（模拟 SideEffectExecutor 执行 MuteApp）。
        assertTrue(engine.evaluate(ctx(pkg = "com.chat", text = "a", device = device(minuteOfDay = 13 * 60)), listOf(rule)).matched)
        VariableStore.muteApp("com.chat", 7)
        // 时段内后续通知：仍静音。
        assertTrue(engine.evaluate(ctx(pkg = "com.chat", text = "b", device = device(minuteOfDay = 13 * 60 + 30)), listOf(rule)).discard)
        // 出了时段（15:00）：不再丢弃。
        assertFalse(engine.evaluate(ctx(pkg = "com.chat", text = "c", device = device(minuteOfDay = 15 * 60)), listOf(rule)).discard)
        // 回到时段（13:00）：自动恢复静音。
        assertTrue(engine.evaluate(ctx(pkg = "com.chat", text = "d", device = device(minuteOfDay = 13 * 60)), listOf(rule)).discard)
    }

    // --- 时间窗口条件：全面核验（in/out/跨午夜/星期/边界）---

    private fun firesAt(rule: Rule, minute: Int, day: Int = 1): Boolean =
        engine.evaluate(ctx(text = "x", device = device(minuteOfDay = minute, isoDayOfWeek = day)), listOf(rule)).discard

    private fun timeRule(startMin: Int, endMin: Int, days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)) = Rule(
        id = 1,
        conditions = listOf(Condition.TimeCondition("tc", startMin, endMin, days)),
        actions = listOf(Action.DiscardAction("a")),
    )

    @Test fun timeWindowNonCrossing() {
        val r = timeRule(12 * 60, 14 * 60) // 12:00–14:00
        assertTrue(firesAt(r, 13 * 60))   // 内部
        assertTrue(firesAt(r, 12 * 60))   // 起点含
        assertFalse(firesAt(r, 14 * 60))  // 终点不含
        assertFalse(firesAt(r, 11 * 60))  // 之前
        assertFalse(firesAt(r, 15 * 60))  // 之后 ← 用户反馈的「已过时段」
    }

    @Test fun timeWindowCrossingMidnight() {
        val r = timeRule(22 * 60, 7 * 60) // 22:00–07:00
        assertTrue(firesAt(r, 23 * 60))            // 深夜
        assertTrue(firesAt(r, 5 * 60, day = 2))    // 凌晨（归属前一天窗口）
        assertFalse(firesAt(r, 12 * 60))           // 正午 ← 窗口外
        assertFalse(firesAt(r, 21 * 60))           // 起点前
        assertTrue(firesAt(r, 22 * 60))            // 起点含
        assertFalse(firesAt(r, 7 * 60, day = 2))   // 终点不含
    }

    @Test fun timeWindowRespectsDays() {
        val r = timeRule(12 * 60, 14 * 60, days = setOf(1)) // 仅周一
        assertTrue(firesAt(r, 13 * 60, day = 1))   // 周一
        assertFalse(firesAt(r, 13 * 60, day = 2))  // 周二
    }

    @Test fun timeWindowCrossingMidnightDayAttribution() {
        val r = timeRule(22 * 60, 7 * 60, days = setOf(1)) // 周一 22:00 – 周二 07:00
        assertTrue(firesAt(r, 23 * 60, day = 1))   // 周一 23:00
        assertTrue(firesAt(r, 5 * 60, day = 2))    // 周二 05:00 属周一窗口
        assertFalse(firesAt(r, 5 * 60, day = 1))   // 周一 05:00 属周日窗口（未选）
    }

    @Test fun previewIgnoresConditions() {
        val rule = textRule(
            1, "sale",
            conditions = listOf(Condition.TimeCondition("c", 0, 1, setOf(1))), // 几乎永不成立的窗口
        )
        // evaluate 会因条件失败而不匹配，但 previewMatches 只看应用 + 触发器。
        assertTrue(engine.previewMatches(rule, "com.chat", "Big sale", ""))
    }

    @Test fun setFieldActionRendersTemplateIntoEdit() {
        val rule = Rule(
            id = 1,
            triggers = listOf(Trigger.TextTrigger("t", NotificationField.ANY, MatchMode.CONTAINS, "hi")),
            actions = listOf(Action.SetFieldAction("a", NotificationField.TITLE, "From {app}")),
        )
        val d = engine.evaluate(ctx(text = "hi there"), listOf(rule))
        assertTrue(d.needsRepost)
        assertEquals("From Chat", d.fieldEdits[NotificationField.TITLE])
    }
}

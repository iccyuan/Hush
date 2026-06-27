package com.buzzkill.engine

import com.buzzkill.data.model.Action
import com.buzzkill.data.model.Condition
import com.buzzkill.data.model.DayType
import com.buzzkill.data.model.LogicMode
import com.buzzkill.data.model.MatchMode
import com.buzzkill.data.model.NotificationField
import com.buzzkill.data.model.Rule
import com.buzzkill.data.model.Trigger
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
    ): MatchContext {
        val fields = mutableMapOf<NotificationField, String>()
        if (title.isNotEmpty()) fields[NotificationField.TITLE] = title
        if (text.isNotEmpty()) fields[NotificationField.TEXT] = text
        return MatchContext(pkg, "Chat", fields, false, false, device)
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
        VariableStore.muteApp("com.chat", 100_000L)
        val d = engine.evaluate(ctx(pkg = "com.chat", text = "anything", device = device(nowMillis = 50_000L)),
            listOf(textRule(1, "neverused")))
        assertTrue(d.matched)
        assertTrue(d.discard)
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

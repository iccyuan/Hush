package com.iccyuan.hush.engine

import com.iccyuan.hush.data.model.Action
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.DayType
import com.iccyuan.hush.data.model.LogicMode
import com.iccyuan.hush.data.model.NotificationField
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.data.model.Trigger
import com.iccyuan.hush.data.model.VibrationPreset

/**
 * 纯评估内核：给定一条通知的快照和当前生效的规则，
 * 产出一个 [Decision]。不依赖 Android，因此可进行单元测试。
 */
class RuleEngine {

    /**
     * 编辑器实时预览：这条通知的应用 + 触发器是否匹配该规则？
     * 忽略时间/节假日/设备条件（那些关乎*何时*生效，而非内容）。
     */
    fun previewMatches(rule: Rule, packageName: String, title: String, text: String): Boolean {
        val fields = mutableMapOf<NotificationField, String>()
        if (title.isNotEmpty()) fields[NotificationField.TITLE] = title
        if (text.isNotEmpty()) fields[NotificationField.TEXT] = text
        val ctx = MatchContext(packageName, "", fields, false, false, PREVIEW_DEVICE)
        return appMatches(rule, packageName) && triggersMatch(rule, ctx, mutableMapOf())
    }

    /**
     * 编辑器测试器：对一条样例通知做内容层面的完整模拟——评估应用 + 触发器，
     * 应用所有动作并产出结果 [Decision]（忽略时间/设备条件，与 [previewMatches] 同口径）。
     */
    fun simulate(
        rule: Rule,
        packageName: String,
        appName: String,
        title: String,
        text: String,
    ): Decision {
        val fields = mutableMapOf<NotificationField, String>()
        if (title.isNotEmpty()) fields[NotificationField.TITLE] = title
        if (text.isNotEmpty()) fields[NotificationField.TEXT] = text
        val ctx = MatchContext(packageName, appName, fields, false, false, PREVIEW_DEVICE)
        val decision = Decision()
        val captures = mutableMapOf<String, String>()
        if (appMatches(rule, packageName) && triggersMatch(rule, ctx, captures)) {
            ctx.captures.putAll(captures)
            applyActions(rule, ctx, decision)
            if (rule.showDanmaku && decision.discard) {
                decision.sideEffects.add(
                    SideEffect.Danmaku(TemplateEngine.render(DANMAKU_TEMPLATE, ctx), DANMAKU_DURATION_MS)
                )
            }
            decision.matched = true
        }
        return decision
    }

    fun evaluate(ctx: MatchContext, rules: List<Rule>): Decision {
        val decision = Decision()
        // 被静音的应用会短路一切处理。
        if (VariableStore.isAppMuted(ctx.packageName)) {
            decision.matched = true
            decision.discard = true
            return decision
        }

        for (rule in rules) {
            if (!appMatches(rule, ctx.packageName)) continue

            val captures = mutableMapOf<String, String>()
            if (!triggersMatch(rule, ctx, captures)) continue
            if (!conditionsHold(rule, ctx)) continue

            ctx.captures.putAll(captures)
            applyActions(rule, ctx, decision)

            // 弹幕用于「替代」被屏蔽的通知，因此仅在该规则确实丢弃了通知时才显示——
            // 否则原生通知仍在、又叠加弹幕，既矛盾又会出现时有时无的竞态。
            if (rule.showDanmaku && decision.discard) {
                decision.sideEffects.add(
                    SideEffect.Danmaku(TemplateEngine.render(DANMAKU_TEMPLATE, ctx), DANMAKU_DURATION_MS)
                )
            }

            decision.matched = true
            decision.firedRuleIds.add(rule.id)
            startCooldownIfAny(rule, ctx)

            if (decision.discard || rule.stopProcessing) break
        }
        return decision
    }

    private fun appMatches(rule: Rule, pkg: String): Boolean =
        rule.appPackages.isEmpty() || rule.appPackages.contains(pkg)

    private fun triggersMatch(
        rule: Rule,
        ctx: MatchContext,
        captures: MutableMap<String, String>,
    ): Boolean {
        if (rule.matchesEverything) return true
        val results = rule.triggers.map { evalTrigger(it, ctx, captures) }
        return when (rule.triggerLogic) {
            LogicMode.ALL -> results.all { it }
            LogicMode.ANY -> results.any { it }
        }
    }

    private fun evalTrigger(
        trigger: Trigger,
        ctx: MatchContext,
        captures: MutableMap<String, String>,
    ): Boolean = when (trigger) {
        is Trigger.TextTrigger -> {
            val value = ctx.field(trigger.field)
            val res = TextMatcher.evaluate(trigger.mode, trigger.query, value, trigger.caseSensitive)
            if (res.matched) captures.putAll(res.groups)
            res.matched != trigger.negate
        }
        is Trigger.OngoingTrigger -> ctx.isOngoing == trigger.mustBeOngoing
        is Trigger.HasReplyTrigger -> ctx.hasReply == trigger.mustHaveReply
        // 防骚扰：仅按通知类别识别营销/推广（Notification.CATEGORY_PROMO == "promo"）。
        is Trigger.PromoTrigger ->
            ctx.field(NotificationField.CATEGORY).equals(PROMO_CATEGORY, ignoreCase = true)
    }

    private fun conditionsHold(rule: Rule, ctx: MatchContext): Boolean =
        rule.conditions.all { evalCondition(it, rule, ctx) }

    private fun evalCondition(c: Condition, rule: Rule, ctx: MatchContext): Boolean = when (c) {
        is Condition.TimeCondition -> inTimeWindow(c, ctx)
        is Condition.ChargingCondition -> ctx.device.charging == c.mustBeCharging
        is Condition.ScreenCondition -> ctx.device.screenOn == c.mustBeOn
        is Condition.BatteryLevelCondition ->
            if (c.whenBelow) ctx.device.batteryPercent < c.percent
            else ctx.device.batteryPercent > c.percent
        is Condition.CooldownCondition ->
            !VariableStore.isInCooldown(rule.id, ctx.device.nowMillis)
        is Condition.HolidayCondition ->
            c.dayTypes.contains(ctx.device.dayType)
    }

    private fun inTimeWindow(c: Condition.TimeCondition, ctx: MatchContext): Boolean {
        val day = ctx.device.isoDayOfWeek
        val minute = ctx.device.minuteOfDay
        val inDay: Boolean
        val inTime: Boolean
        if (c.startMinute <= c.endMinute) {
            inTime = minute in c.startMinute until c.endMinute
            inDay = c.days.contains(day)
        } else {
            // 时间窗口跨越午夜（例如 22:00–07:00）。
            if (minute >= c.startMinute) {
                inTime = true
                inDay = c.days.contains(day)
            } else {
                inTime = minute < c.endMinute
                // 归属于前一天的时间窗口。
                val prevDay = if (day == 1) 7 else day - 1
                inDay = c.days.contains(prevDay)
            }
        }
        return inTime && inDay
    }

    private fun startCooldownIfAny(rule: Rule, ctx: MatchContext) {
        val cd = rule.conditions.filterIsInstance<Condition.CooldownCondition>().firstOrNull()
        if (cd != null) {
            VariableStore.startCooldown(rule.id, ctx.device.nowMillis + cd.seconds * 1000L)
        }
    }

    private fun applyActions(rule: Rule, ctx: MatchContext, decision: Decision) {
        for (action in rule.actions) {
            applyAction(action, rule.id, ctx, decision)
            if (decision.discard) return
        }
    }

    private fun applyAction(action: Action, ruleId: Long, ctx: MatchContext, decision: Decision) {
        when (action) {
            is Action.ReplaceTextAction -> {
                val current = currentField(action.field, ctx, decision)
                val updated = replace(action, current)
                writeField(action.field, updated, ctx, decision)
            }
            is Action.SetFieldAction -> {
                val rendered = TemplateEngine.render(action.template, ctx)
                writeField(action.field, rendered, ctx, decision)
            }
            is Action.DiscardAction -> decision.discard = true
            is Action.DismissAction -> {
                decision.dismiss = true
                decision.dismissDelayMs = maxOf(decision.dismissDelayMs, action.delayMs)
            }
            is Action.SnoozeAction -> decision.snoozeMinutes = action.minutes
            is Action.MarkImportantAction -> {
                decision.importance = action.importance
                decision.bypassDnd = decision.bypassDnd || action.bypassDnd
            }
            is Action.SoundVibrationAction -> {
                decision.sound = SoundOverride(
                    silent = action.silent,
                    soundUri = action.soundUri,
                    vibration = if (action.silent) VibrationPreset.NONE else action.vibration,
                )
            }
            is Action.AutoReplyAction ->
                decision.sideEffects.add(
                    SideEffect.AutoReply(TemplateEngine.render(action.message, ctx))
                )
            is Action.ReadAloudAction ->
                decision.sideEffects.add(
                    SideEffect.ReadAloud(TemplateEngine.render(action.template, ctx))
                )
            is Action.WakeScreenAction ->
                decision.sideEffects.add(SideEffect.WakeScreen(action.durationMs))
            is Action.ToastAction ->
                decision.sideEffects.add(
                    SideEffect.Toast(TemplateEngine.render(action.template, ctx))
                )
            is Action.SetVariableAction ->
                VariableStore.setVariable(action.name, TemplateEngine.render(action.valueTemplate, ctx))
            is Action.RunTaskerAction ->
                decision.sideEffects.add(SideEffect.RunTasker(action.taskName))
            is Action.WebhookAction ->
                decision.sideEffects.add(
                    SideEffect.Webhook(
                        url = TemplateEngine.render(action.url, ctx),
                        method = action.method,
                        body = TemplateEngine.render(action.bodyTemplate, ctx),
                    )
                )
            is Action.MuteAppAction ->
                decision.sideEffects.add(SideEffect.MuteApp(ctx.packageName, ruleId))
            is Action.DigestAction -> {
                decision.sideEffects.add(
                    SideEffect.Digest(
                        pkg = ctx.packageName,
                        appName = ctx.appName,
                        line = TemplateEngine.render(action.template, ctx),
                        windowMinutes = action.windowMinutes,
                    )
                )
                // 抑制单条通知；摘要会在时间窗结束时统一发布。
                decision.discard = true
            }
        }
    }

    /** 字段的最新值，优先采用本轮已暂存的编辑结果。 */
    private fun currentField(
        field: NotificationField,
        ctx: MatchContext,
        decision: Decision,
    ): String = decision.fieldEdits[field] ?: ctx.field(field)

    private fun writeField(
        field: NotificationField,
        value: String,
        ctx: MatchContext,
        decision: Decision,
    ) {
        val target = if (field == NotificationField.ANY) NotificationField.TEXT else field
        decision.fieldEdits[target] = value
        // 保持 ctx 同步，以便后续的动作/模板能看到新值。
        ctx.fields[target] = value
    }

    private fun replace(action: Action.ReplaceTextAction, input: String): String {
        if (action.pattern.isEmpty()) return input
        return try {
            if (action.isRegex) {
                val options = if (action.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(action.pattern, options).replace(input, action.replacement)
            } else {
                input.replace(action.pattern, action.replacement, ignoreCase = !action.caseSensitive)
            }
        } catch (_: Exception) {
            input
        }
    }

    private companion object {
        /** 营销/推广类别（对应 Notification.CATEGORY_PROMO，此处保持引擎与 Android 无关）。 */
        const val PROMO_CATEGORY = "promo"

        /** 按规则开关使用的默认弹幕渲染模板。 */
        const val DANMAKU_TEMPLATE = "{app}: {title} {text}"
        const val DANMAKU_DURATION_MS = 7000L

        /** 用于仅内容预览匹配的中性设备状态。 */
        val PREVIEW_DEVICE = DeviceContext(
            charging = false,
            screenOn = false,
            batteryPercent = 100,
            minuteOfDay = 0,
            isoDayOfWeek = 1,
            dayType = DayType.WORKDAY,
            nowMillis = 0L,
        )
    }
}

package com.iccyuan.hush.engine

import com.iccyuan.hush.data.model.Action
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.DayType
import com.iccyuan.hush.data.model.GapOp
import com.iccyuan.hush.data.model.DeviceEventType
import com.iccyuan.hush.data.model.HttpMethod
import com.iccyuan.hush.data.model.LogicMode
import com.iccyuan.hush.data.model.NotificationField
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.data.model.WebhookBodyType
import com.iccyuan.hush.data.model.Trigger
import com.iccyuan.hush.data.model.VibrationPreset
import com.iccyuan.hush.data.model.isEventDriven

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
     * 预览用：给定「此刻」的设备状态，规则的条件当前是否成立（时段 / 节假日 / 充电 / 屏幕 /
     * 网络 / 电量 / 位置 / 冷却等）。编辑器实时预览据此判断"此刻是否真的会执行"，
     * 避免过了所选时段仍把通知显示为命中（[previewMatches] 只看内容，不看条件）。
     */
    fun conditionsActiveNow(rule: Rule, device: DeviceContext): Boolean =
        conditionsHold(rule, MatchContext("", "", mutableMapOf(), false, false, device))

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
            if (rule.showDanmaku && decision.discard && !ctx.isOngoing) {
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
            // 事件驱动规则（Wi-Fi 连断等）由 evaluateEvent 处理，不参与通知匹配。
            if (rule.isEventDriven) continue
            if (!appMatches(rule, ctx.packageName, ctx.userId)) continue

            val captures = mutableMapOf<String, String>()
            if (!triggersMatch(rule, ctx, captures)) continue
            if (!conditionsHold(rule, ctx)) continue

            ctx.captures.putAll(captures)
            applyActions(rule, ctx, decision)

            // 弹幕用于「替代」被屏蔽的通知，因此仅在该规则确实丢弃了通知时才显示——
            // 否则原生通知仍在、又叠加弹幕，既矛盾又会出现时有时无的竞态。
            if (rule.showDanmaku && decision.discard && !ctx.isOngoing) {
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

    /**
     * 应用是否匹配。选择项（[Rule.appPackages]）支持两种令牌：
     *  - 裸包名 `pkg`：匹配该包的**任意**用户空间（旧格式 / 「本体+分身都要」）。
     *  - `pkg@userId`：仅匹配该包在指定用户空间的通知——用于区分本体（如 `pkg@0`）与
     *    应用分身/双开（如 `pkg@999`）。本体与分身包名相同，只能靠所属用户区分。
     */
    private fun appMatches(rule: Rule, pkg: String, userId: Int = 0): Boolean {
        if (rule.appPackages.isEmpty()) return true
        return rule.appPackages.any { token ->
            val at = token.indexOf('@')
            if (at < 0) {
                token == pkg
            } else {
                token.substring(0, at) == pkg && token.substring(at + 1).toIntOrNull() == userId
            }
        }
    }

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
        // 事件驱动触发器永不匹配通知——它们由 evaluateEvent / evaluateLocationEvent 单独处理。
        is Trigger.DeviceEvent -> false
        is Trigger.LocationTrigger -> false
    }

    /**
     * 事件驱动路径：某个设备事件（如 Wi-Fi 连上）发生的那一刻，对所有监听该事件的规则
     * 评估其条件并执行动作，产出携带副作用的 [Decision]（由服务执行通知提醒等副作用）。
     */
    fun evaluateEvent(
        event: DeviceEventType,
        rules: List<Rule>,
        device: DeviceContext,
        selfPackage: String,
        selfAppName: String,
    ): Decision {
        val decision = Decision()
        for (rule in rules) {
            val matches = rule.triggers.any { it is Trigger.DeviceEvent && it.event == event }
            if (!matches) continue
            // 每条规则用独立的 ctx：事件无通知内容，仅承载设备状态供条件/模板使用。
            val ctx = MatchContext(selfPackage, selfAppName, mutableMapOf(), false, false, device)
            if (!conditionsHold(rule, ctx)) continue
            applyActions(rule, ctx, decision)
            decision.matched = true
            decision.firedRuleIds.add(rule.id)
            startCooldownIfAny(rule, ctx)
        }
        return decision
    }

    /**
     * 位置事件路径：进入/离开某围栏（[fenceKey]）的那一刻，对监听该围栏且方向匹配的规则
     * 评估条件并执行动作。与 [evaluateEvent] 同构，只是触发器是 [Trigger.LocationTrigger]。
     */
    fun evaluateLocationEvent(
        fenceKey: String,
        entered: Boolean,
        rules: List<Rule>,
        device: DeviceContext,
        selfPackage: String,
        selfAppName: String,
    ): Decision {
        val want = if (entered) com.iccyuan.hush.data.model.LocationEventType.ENTER
        else com.iccyuan.hush.data.model.LocationEventType.EXIT
        val decision = Decision()
        for (rule in rules) {
            val matches = rule.triggers.any {
                it is Trigger.LocationTrigger && it.fenceKey() == fenceKey && it.event == want
            }
            if (!matches) continue
            val ctx = MatchContext(selfPackage, selfAppName, mutableMapOf(), false, false, device)
            if (!conditionsHold(rule, ctx)) continue
            applyActions(rule, ctx, decision)
            decision.matched = true
            decision.firedRuleIds.add(rule.id)
            startCooldownIfAny(rule, ctx)
        }
        return decision
    }

    /**
     * 条件按相邻间隔的连接符（[Rule.conditionJoins]）**从左到右**求值：
     * `A op1 B op2 C` = `((A op1 B) op2 C)`。GROUP（同组）按「或」处理。缺失的间隔回退为「且」。
     */
    private fun conditionsHold(rule: Rule, ctx: MatchContext): Boolean {
        val conds = rule.conditions
        if (conds.isEmpty()) return true
        var result = evalCondition(conds[0], rule, ctx)
        for (i in 1 until conds.size) {
            val op = rule.conditionJoins.getOrNull(i - 1) ?: GapOp.AND
            val v = evalCondition(conds[i], rule, ctx)
            result = if (op == GapOp.AND) result && v else result || v
        }
        return result
    }

    private fun evalCondition(c: Condition, rule: Rule, ctx: MatchContext): Boolean = when (c) {
        is Condition.TimeCondition -> inTimeWindow(c, ctx)
        is Condition.ChargingCondition -> ctx.device.charging == c.mustBeCharging
        is Condition.ScreenCondition -> ctx.device.screenOn == c.mustBeOn
        is Condition.HeadphonesCondition -> ctx.device.headphonesConnected == c.mustBeConnected
        is Condition.WifiCondition -> ctx.device.onWifi == c.mustBeConnected
        is Condition.LocationCondition -> (c.fenceKey() in ctx.device.insideGeofences) == c.mustBeInside
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
            is Action.NotifyAction ->
                decision.sideEffects.add(
                    SideEffect.Notify(TemplateEngine.render(action.template, ctx))
                )
            is Action.SetVariableAction ->
                VariableStore.setVariable(action.name, TemplateEngine.render(action.valueTemplate, ctx))
            is Action.WebhookAction ->
                decision.sideEffects.add(
                    SideEffect.Webhook(
                        url = TemplateEngine.render(action.url, ctx),
                        method = action.method,
                        params = action.queryParams
                            .filter { it.name.isNotBlank() }
                            .map { it.name.trim() to TemplateEngine.render(it.value, ctx) },
                        headers = action.headers
                            .filter { it.name.isNotBlank() }
                            .map { it.name.trim() to TemplateEngine.render(it.value, ctx) },
                        // 仅 POST 携带请求体；GET 不发（contentType 为空即表示不写 body）。
                        contentType = if (action.method == HttpMethod.POST) action.bodyType.contentType else "",
                        // JSON 请求体：自动转义占位符值里的引号/换行等，避免破坏 JSON 结构。
                        body = if (action.method == HttpMethod.POST && action.bodyType == WebhookBodyType.JSON) {
                            TemplateEngine.render(action.bodyTemplate, ctx, TemplateEngine::jsonEscape)
                        } else {
                            TemplateEngine.render(action.bodyTemplate, ctx)
                        },
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
            is Action.LaunchAppAction -> {
                if (action.packageName.isNotBlank()) {
                    decision.sideEffects.add(SideEffect.LaunchApp(action.packageName, action.activityName))
                }
            }
            is Action.RunMacroAction -> {
                if (action.steps.isNotEmpty()) {
                    decision.sideEffects.add(
                        SideEffect.RunMacro(
                            steps = action.steps,
                            screenWidth = action.screenWidth,
                            screenHeight = action.screenHeight,
                            repeat = action.repeat.coerceAtLeast(1),
                        )
                    )
                }
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

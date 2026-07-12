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
     * 该规则此刻是否应产出弹幕。前提：开启了「弹幕显示」且规则确实丢弃了通知（弹幕用于替代被屏蔽的
     * 原生通知）。此外**默认排除常驻通知**（VPN / 音乐 / 下载 / 前台服务等，否则会不断刷屏）——
     * 除非该规则显式带有「必须是常驻通知」触发器（[Trigger.OngoingTrigger] 且 mustBeOngoing），
     * 即用户明确表示这条规则就是要针对常驻通知，此时才对常驻放行。
     */
    private fun shouldDanmaku(rule: Rule, ctx: MatchContext, decision: Decision): Boolean {
        if (!rule.showDanmaku || !decision.discard) return false
        if (!ctx.isPersistent) return true
        return rule.triggers.any { it is Trigger.OngoingTrigger && it.mustBeOngoing }
    }

    /**
     * 对一条通知求值。
     *
     * [readOnly] = true 时**只算结论、不改任何状态**：不启动冷却、不写变量、不解除静音。
     * 供通知助手（[com.iccyuan.hush.service.SilenceGate]）在通知提醒前抢先判断「是否只需静音」——
     * 同一条通知随后仍会被监听器正常求值一次，状态变更必须只发生在那一次，否则冷却会被提前
     * 消耗、变量被写两遍。
     */
    fun evaluate(ctx: MatchContext, rules: List<Rule>, readOnly: Boolean = false): Decision {
        val decision = Decision()
        // 被「静音应用」动作静音的应用：短路静音其所有通知——只是不发声不震动，横幅是否弹出、是否
        // 留在通知栏都跟随源通知本来的重要性，不额外拉高或压低。
        // 但静音必须尊重设置它的那条规则的条件——例如「仅在某时段静音应用」，一旦过了该时段，
        // 静音就应失效，而不是无限期生效。
        // 因此这里复查设置静音的规则此刻条件是否仍成立：
        //  · 成立 → 静音生效（回到时段内会自动恢复静音）。
        //  · 规则已删除/停用（不在 rules 中）→ 解除该静音。
        //  · 规则在、但条件此刻不成立（如已过时段）→ 静音暂不生效，照常继续处理，不解除，
        //    以便回到时段内再次自动生效。
        if (VariableStore.isAppMuted(ctx.packageName)) {
            val muteRuleId = VariableStore.mutedRuleId(ctx.packageName)
            val muteRule = rules.firstOrNull { it.id == muteRuleId }
            when {
                muteRule == null -> if (!readOnly) VariableStore.unmuteApp(ctx.packageName)
                conditionsHold(muteRule, ctx) -> {
                    decision.matched = true
                    decision.sound = SoundOverride(silent = true, vibration = VibrationPreset.NONE)
                    // 不改重要性、不改字段——这样 silenceOnly() 成立，服务会保留原通知
                    // （就地静音或不作处理，绝不重发副本），横幅行为跟随源通知本来的重要性。
                    //
                    // 这条短路不走规则循环，取证记录得在这里补上——否则历史里会出现「已静音，
                    // 但没有任何规则命中」的记录，用户无从知道是谁静音了它。触发器留空：静音一旦
                    // 生效就作用于该应用的每一条通知，本就与触发器无关。
                    decision.traces.add(
                        MatchTrace(
                            ruleId = muteRule.id,
                            ruleName = muteRule.name,
                            conditions = holdingConditions(muteRule, ctx),
                            actions = muteRule.actions,
                        )
                    )
                    return decision
                }
                // else：条件此刻不成立——不静音、不解除，继续常规求值。
            }
        }

        for (rule in rules) {
            // 事件驱动规则（Wi-Fi 连断等）由 evaluateEvent 处理，不参与通知匹配。
            if (rule.isEventDriven) continue
            if (!appMatches(rule, ctx.packageName, ctx.userId)) continue

            val captures = mutableMapOf<String, String>()
            val firedTriggers = mutableListOf<Trigger>()
            // 应用已经对上了，却卡在后面某一关——这才是用户翻历史时想问的「差在哪」。
            // 应用都不沾边的规则与这条通知毫无关系，不记（那只是噪音）。
            if (!triggersMatch(rule, ctx, captures, firedTriggers)) {
                decision.nearMisses.add(
                    NearMiss(
                        ruleId = rule.id,
                        ruleName = rule.name,
                        blockedAt = NearMiss.Stage.TRIGGER,
                        passedTriggers = firedTriggers,
                    )
                )
                continue
            }
            if (!conditionsHold(rule, ctx)) {
                decision.nearMisses.add(
                    NearMiss(
                        ruleId = rule.id,
                        ruleName = rule.name,
                        blockedAt = NearMiss.Stage.CONDITION,
                        passedTriggers = firedTriggers,
                        failedConditions = failingConditions(rule, ctx),
                    )
                )
                continue
            }

            ctx.captures.putAll(captures)
            applyActions(rule, ctx, decision, readOnly)
            // 记下「为什么命中」：哪条规则、被哪个触发器命中、当时哪些条件成立、执行了什么动作。
            decision.traces.add(
                MatchTrace(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    triggers = firedTriggers,
                    conditions = holdingConditions(rule, ctx),
                    actions = rule.actions,
                )
            )

            // 弹幕用于「替代」被屏蔽的通知，因此仅在该规则确实丢弃了通知时才显示——
            // 否则原生通知仍在、又叠加弹幕，既矛盾又会出现时有时无的竞态。默认排除常驻通知，
            // 除非规则带「必须是常驻通知」触发器（见 shouldDanmaku）。
            if (shouldDanmaku(rule, ctx, decision)) {
                decision.sideEffects.add(
                    SideEffect.Danmaku(TemplateEngine.render(DANMAKU_TEMPLATE, ctx))
                )
            }

            decision.matched = true
            decision.firedRuleIds.add(rule.id)
            if (!readOnly) startCooldownIfAny(rule, ctx)

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

    /**
     * [firedTriggers] 收集**实际命中**的触发器，供 [MatchTrace] / [NearMiss] 向用户解释
     * 「为什么命中」与「差在哪」。
     *
     * 命中与否都要收集：「全部满足」的规则挂了三个触发器却只命中两个，正是最典型的「差一点」，
     * 用户要看的就是那命中的两个——不然只知道"没命中"，仍不知道差的是哪一个。
     */
    private fun triggersMatch(
        rule: Rule,
        ctx: MatchContext,
        captures: MutableMap<String, String>,
        firedTriggers: MutableList<Trigger>? = null,
    ): Boolean {
        if (rule.matchesEverything) return true
        val hits = rule.triggers.filter { evalTrigger(it, ctx, captures) }
        firedTriggers?.addAll(hits)
        return when (rule.triggerLogic) {
            LogicMode.ALL -> hits.size == rule.triggers.size
            LogicMode.ANY -> hits.isNotEmpty()
        }
    }

    /** 当前成立的条件——用来解释「为什么是此刻生效」（时段、充电、Wi-Fi 等）。 */
    private fun holdingConditions(rule: Rule, ctx: MatchContext): List<Condition> =
        rule.conditions.filter { conditionHolds(it, rule, ctx) }

    /** 当前**不**成立的条件——用来解释「为什么此刻没生效」。 */
    private fun failingConditions(rule: Rule, ctx: MatchContext): List<Condition> =
        rule.conditions.filterNot { conditionHolds(it, rule, ctx) }

    private fun conditionHolds(c: Condition, rule: Rule, ctx: MatchContext): Boolean =
        runCatching { evalCondition(c, rule, ctx) }.getOrDefault(false)

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
        // 「必须是常驻通知」：常驻 = 完整判定（进行中 / 不可清除 / 前台服务 / 常驻类别），
        // 与弹幕的常驻排除口径一致，从而 VPN、前台服务这类只带 NO_CLEAR/FGS 的也能被识别。
        is Trigger.OngoingTrigger -> ctx.isPersistent == trigger.mustBeOngoing
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
        ssid: String?,
        rules: List<Rule>,
        device: DeviceContext,
        selfPackage: String,
        selfAppName: String,
    ): Decision {
        val decision = Decision()
        for (rule in rules) {
            // Wi-Fi 事件可限定 SSID（多选）：ssids 为空则任意网络；否则需当前 SSID 命中其一。
            val matches = rule.triggers.any {
                it is Trigger.DeviceEvent && it.event == event &&
                    (it.ssids.isEmpty() || (ssid != null && it.ssids.contains(ssid)))
            }
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

    private fun applyActions(rule: Rule, ctx: MatchContext, decision: Decision, readOnly: Boolean = false) {
        for (action in rule.actions) {
            applyAction(action, rule.id, ctx, decision, readOnly)
            if (decision.discard) return
        }
    }

    private fun applyAction(
        action: Action,
        ruleId: Long,
        ctx: MatchContext,
        decision: Decision,
        readOnly: Boolean = false,
    ) {
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
                if (!readOnly) {
                    VariableStore.setVariable(action.name, TemplateEngine.render(action.valueTemplate, ctx))
                }
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
            is Action.MuteAppAction -> {
                decision.sideEffects.add(SideEffect.MuteApp(ctx.packageName, ruleId, ctx.userId))
                // 「静音」= 不发声不震动但仍弹出，而非丢弃（与「丢弃」动作区分开）；见 evaluate() 顶部
                // 对后续通知的同等处理。设置当前这条——否则触发静音的这一条会被漏过，仍按原生提醒。
                decision.sound = SoundOverride(silent = true, vibration = VibrationPreset.NONE)
            }
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
                val regex = replaceCache.getOrPut(action.pattern to options) { Regex(action.pattern, options) }
                regex.replace(input, action.replacement)
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

        // 按 (pattern, options) 缓存编译结果，避免每条通知都重新编译同一条替换动作的正则。
        // 用 companion 而非实例字段共享缓存：编辑器预览会创建多个 RuleEngine 实例。
        val replaceCache = java.util.concurrent.ConcurrentHashMap<Pair<String, Set<RegexOption>>, Regex>()

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

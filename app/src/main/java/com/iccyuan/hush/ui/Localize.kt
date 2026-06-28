package com.iccyuan.hush.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.iccyuan.hush.R
import com.iccyuan.hush.data.model.Action
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.DayType
import com.iccyuan.hush.data.model.Importance
import com.iccyuan.hush.data.model.LogicMode
import com.iccyuan.hush.data.model.MatchMode
import com.iccyuan.hush.data.model.NotificationField
import com.iccyuan.hush.data.model.Trigger
import com.iccyuan.hush.data.model.VibrationPreset

/**
 * 模型类型的区域感知显示字符串。模型本身保持英文 /
 * 不依赖 Android；所有面向用户的措辞都在这里通过字符串资源解析，
 * 以便同一条规则以用户选择的语言渲染。
 */
object Localize {

    @StringRes
    fun fieldRes(f: NotificationField): Int = when (f) {
        NotificationField.TITLE -> R.string.field_title
        NotificationField.TEXT -> R.string.field_text
        NotificationField.BIG_TEXT -> R.string.field_bigtext
        NotificationField.SUB_TEXT -> R.string.field_subtext
        NotificationField.INFO_TEXT -> R.string.field_infotext
        NotificationField.TICKER -> R.string.field_ticker
        NotificationField.CATEGORY -> R.string.field_category
        NotificationField.CHANNEL -> R.string.field_channel
        NotificationField.SENDER -> R.string.field_sender
        NotificationField.APP_NAME -> R.string.field_appname
        NotificationField.ANY -> R.string.field_any
    }

    @StringRes
    fun matchRes(m: MatchMode): Int = when (m) {
        MatchMode.CONTAINS -> R.string.match_contains
        MatchMode.EQUALS -> R.string.match_equals
        MatchMode.STARTS_WITH -> R.string.match_starts
        MatchMode.ENDS_WITH -> R.string.match_ends
        MatchMode.REGEX -> R.string.match_regex
        MatchMode.WILDCARD -> R.string.match_wildcard
    }

    @StringRes
    fun importanceRes(i: Importance): Int = when (i) {
        Importance.MIN -> R.string.imp_min
        Importance.LOW -> R.string.imp_low
        Importance.DEFAULT -> R.string.imp_default
        Importance.HIGH -> R.string.imp_high
        Importance.URGENT -> R.string.imp_urgent
    }

    @StringRes
    fun vibrationRes(v: VibrationPreset): Int = when (v) {
        VibrationPreset.NONE -> R.string.vib_none
        VibrationPreset.SHORT -> R.string.vib_short
        VibrationPreset.NORMAL -> R.string.vib_normal
        VibrationPreset.DOUBLE -> R.string.vib_double
        VibrationPreset.LONG -> R.string.vib_long
        VibrationPreset.HEARTBEAT -> R.string.vib_heartbeat
    }

    @StringRes
    fun dayTypeRes(d: DayType): Int = when (d) {
        DayType.LEGAL_HOLIDAY -> R.string.daytype_legal_holiday
        DayType.MAKEUP_WORKDAY -> R.string.daytype_makeup_workday
        DayType.WEEKEND -> R.string.daytype_weekend
        DayType.WORKDAY -> R.string.daytype_workday
    }

    @StringRes
    fun logicRes(l: LogicMode): Int = when (l) {
        LogicMode.ALL -> R.string.match_all
        LogicMode.ANY -> R.string.match_any
    }

    @Composable fun field(f: NotificationField) = stringResource(fieldRes(f))
    @Composable fun match(m: MatchMode) = stringResource(matchRes(m))
    @Composable fun importance(i: Importance) = stringResource(importanceRes(i))
    @Composable fun vibration(v: VibrationPreset) = stringResource(vibrationRes(v))
    @Composable fun dayType(d: DayType) = stringResource(dayTypeRes(d))

    // --- 组件摘要（已本地化）---

    @Composable
    fun summary(t: Trigger): String = when (t) {
        is Trigger.TextTrigger -> {
            val not = if (t.negate) stringResource(R.string.sum_not_prefix) else ""
            not + stringResource(R.string.sum_text, field(t.field), match(t.mode), t.query)
        }
        is Trigger.OngoingTrigger ->
            stringResource(if (t.mustBeOngoing) R.string.sum_ongoing_yes else R.string.sum_ongoing_no)
        is Trigger.HasReplyTrigger ->
            stringResource(if (t.mustHaveReply) R.string.sum_reply_yes else R.string.sum_reply_no)
        is Trigger.PromoTrigger -> stringResource(R.string.sum_promo)
    }

    @Composable
    fun summary(c: Condition): String = when (c) {
        is Condition.TimeCondition -> {
            fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)
            val abbr = stringArrayResource(R.array.weekday_abbr)
            val days = if (c.days.size == 7) stringResource(R.string.sum_every_day)
            else c.days.sorted().joinToString(",") { abbr[it - 1] }
            stringResource(R.string.sum_time, fmt(c.startMinute), fmt(c.endMinute), days)
        }
        is Condition.ChargingCondition ->
            stringResource(if (c.mustBeCharging) R.string.sum_charging else R.string.sum_on_battery)
        is Condition.ScreenCondition ->
            stringResource(if (c.mustBeOn) R.string.sum_screen_on else R.string.sum_screen_off)
        is Condition.HeadphonesCondition ->
            stringResource(if (c.mustBeConnected) R.string.sum_headphones_on else R.string.sum_headphones_off)
        is Condition.WifiCondition ->
            stringResource(if (c.mustBeConnected) R.string.sum_wifi_on else R.string.sum_wifi_off)
        is Condition.LocationCondition -> {
            val place = c.placeName.ifBlank { stringResource(R.string.location_unset) }
            stringResource(if (c.mustBeInside) R.string.sum_location_inside else R.string.sum_location_outside, place)
        }
        is Condition.BatteryLevelCondition ->
            stringResource(if (c.whenBelow) R.string.sum_battery_below else R.string.sum_battery_above, c.percent)
        is Condition.CooldownCondition ->
            stringResource(R.string.sum_cooldown, c.seconds)
        is Condition.HolidayCondition -> {
            // 使用 for 循环（而非 joinToString），以便 @Composable 的 dayType() 保持在
            // 可组合上下文中。
            val parts = ArrayList<String>(c.dayTypes.size)
            for (type in c.dayTypes) parts.add(dayType(type))
            stringResource(R.string.sum_holiday, parts.joinToString("/"))
        }
    }

    @Composable
    fun summary(a: Action): String = when (a) {
        is Action.ReplaceTextAction ->
            stringResource(R.string.sum_act_replace, a.pattern, a.replacement, field(a.field))
        is Action.SetFieldAction ->
            stringResource(R.string.sum_act_setfield, field(a.field), a.template)
        is Action.DiscardAction -> stringResource(R.string.sum_act_discard)
        is Action.DismissAction ->
            if (a.delayMs > 0) stringResource(R.string.sum_act_dismiss_delay, a.delayMs.toInt())
            else stringResource(R.string.sum_act_dismiss)
        is Action.SnoozeAction -> stringResource(R.string.sum_act_snooze, a.minutes)
        is Action.MarkImportantAction -> {
            val base = stringResource(R.string.sum_act_importance, importance(a.importance))
            if (a.bypassDnd) base + stringResource(R.string.sum_act_bypass_dnd) else base
        }
        is Action.SoundVibrationAction ->
            if (a.silent) stringResource(R.string.sum_act_silence)
            else stringResource(R.string.sum_act_sound, vibration(a.vibration))
        is Action.AutoReplyAction -> stringResource(R.string.sum_act_autoreply, a.message)
        is Action.ReadAloudAction -> stringResource(R.string.sum_act_readaloud, a.template)
        is Action.WakeScreenAction -> stringResource(R.string.sum_act_wake, a.durationMs.toInt())
        is Action.ToastAction -> stringResource(R.string.sum_act_toast, a.template)
        is Action.NotifyAction -> stringResource(R.string.sum_act_notify, a.template)
        is Action.SetVariableAction -> stringResource(R.string.sum_act_setvar, a.name, a.valueTemplate)
        is Action.RunTaskerAction -> stringResource(R.string.sum_act_tasker, a.taskName)
        is Action.WebhookAction -> stringResource(R.string.sum_act_webhook, a.method.name, a.url)
        is Action.MuteAppAction -> stringResource(R.string.sum_act_mute)
        is Action.DigestAction -> stringResource(R.string.sum_act_digest, a.windowMinutes)
    }
}

package com.iccyuan.hush.ui.editor

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.iccyuan.hush.R
import com.iccyuan.hush.ui.theme.IOSColors
import com.iccyuan.hush.data.model.Action
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.Trigger
import com.iccyuan.hush.ui.Ids

// iOS 系统强调色取自统一的 [IOSColors] 调色板，避免在各处硬编码色值。
private val Blue = IOSColors.Blue
private val Green = IOSColors.Green
private val Orange = IOSColors.Orange
private val Red = IOSColors.Red
private val Purple = IOSColors.Purple
private val Indigo = IOSColors.Indigo
private val Teal = IOSColors.Teal
private val Pink = IOSColors.Pink
private val Gray = IOSColors.Gray

/** "添加组件"选择器的定义：本地化的标签/描述 + 图标徽章 + 工厂函数。 */
data class CatalogEntry<T>(
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int,
    val icon: ImageVector,
    val color: Color,
    val create: () -> T,
)

object ComponentCatalog {

    val triggers: List<CatalogEntry<Trigger>> = listOf(
        CatalogEntry(R.string.cat_trig_text, R.string.cat_trig_text_desc, Icons.Filled.Search, Blue) {
            Trigger.TextTrigger(Ids.next())
        },
        CatalogEntry(R.string.cat_trig_ongoing, R.string.cat_trig_ongoing_desc, Icons.Filled.MusicNote, Indigo) {
            Trigger.OngoingTrigger(Ids.next())
        },
        CatalogEntry(R.string.cat_trig_reply, R.string.cat_trig_reply_desc, Icons.AutoMirrored.Filled.Reply, Teal) {
            Trigger.HasReplyTrigger(Ids.next())
        },
        CatalogEntry(R.string.cat_trig_promo, R.string.cat_trig_promo_desc, Icons.Filled.Campaign, Orange) {
            Trigger.PromoTrigger(Ids.next())
        },
    )

    val conditions: List<CatalogEntry<Condition>> = listOf(
        CatalogEntry(R.string.cat_cond_time, R.string.cat_cond_time_desc, Icons.Filled.Schedule, Orange) {
            Condition.TimeCondition(Ids.next())
        },
        CatalogEntry(R.string.cat_cond_holiday, R.string.cat_cond_holiday_desc, Icons.Filled.CalendarMonth, Red) {
            Condition.HolidayCondition(Ids.next())
        },
        CatalogEntry(R.string.cat_cond_charging, R.string.cat_cond_charging_desc, Icons.Filled.BatteryChargingFull, Green) {
            Condition.ChargingCondition(Ids.next())
        },
        CatalogEntry(R.string.cat_cond_screen, R.string.cat_cond_screen_desc, Icons.Filled.Smartphone, Indigo) {
            Condition.ScreenCondition(Ids.next())
        },
        CatalogEntry(R.string.cat_cond_battery, R.string.cat_cond_battery_desc, Icons.Filled.BatteryStd, Green) {
            Condition.BatteryLevelCondition(Ids.next())
        },
        CatalogEntry(R.string.cat_cond_cooldown, R.string.cat_cond_cooldown_desc, Icons.Filled.HourglassBottom, Gray) {
            Condition.CooldownCondition(Ids.next())
        },
    )

    // 按使用频率排序：日常的通知管理动作排在前面，
    // 小众/高级用户动作（字段编辑、变量、集成）排在最后。
    val actions: List<CatalogEntry<Action>> = listOf(
        CatalogEntry(R.string.cat_act_discard, R.string.cat_act_discard_desc, Icons.Filled.VisibilityOff, Gray) {
            Action.DiscardAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_dismiss, R.string.cat_act_dismiss_desc, Icons.Filled.Close, Red) {
            Action.DismissAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_mute, R.string.cat_act_mute_desc, Icons.Filled.NotificationsOff, Red) {
            Action.MuteAppAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_digest, R.string.cat_act_digest_desc, Icons.Filled.Inbox, Indigo) {
            Action.DigestAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_snooze, R.string.cat_act_snooze_desc, Icons.Filled.Snooze, Indigo) {
            Action.SnoozeAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_importance, R.string.cat_act_importance_desc, Icons.Filled.PriorityHigh, Orange) {
            Action.MarkImportantAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_sound, R.string.cat_act_sound_desc, Icons.Filled.Vibration, Pink) {
            Action.SoundVibrationAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_readaloud, R.string.cat_act_readaloud_desc, Icons.Filled.RecordVoiceOver, Purple) {
            Action.ReadAloudAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_autoreply, R.string.cat_act_autoreply_desc, Icons.AutoMirrored.Filled.Reply, Teal) {
            Action.AutoReplyAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_wake, R.string.cat_act_wake_desc, Icons.Filled.LightMode, Orange) {
            Action.WakeScreenAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_toast, R.string.cat_act_toast_desc, Icons.Filled.Sms, Green) {
            Action.ToastAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_setfield, R.string.cat_act_setfield_desc, Icons.Filled.Edit, Blue) {
            Action.SetFieldAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_replace, R.string.cat_act_replace_desc, Icons.Filled.FindReplace, Blue) {
            Action.ReplaceTextAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_setvar, R.string.cat_act_setvar_desc, Icons.Filled.DataObject, Indigo) {
            Action.SetVariableAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_webhook, R.string.cat_act_webhook_desc, Icons.Filled.Cloud, Blue) {
            Action.WebhookAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_tasker, R.string.cat_act_tasker_desc, Icons.Filled.Bolt, Orange) {
            Action.RunTaskerAction(Ids.next())
        },
    )
}

/** 已配置组件的图标 + 颜色，显示在编辑器中对应的行上。 */
object ComponentVisuals {
    fun of(t: Trigger): Pair<ImageVector, Color> = when (t) {
        is Trigger.TextTrigger -> Icons.Filled.Search to Blue
        is Trigger.OngoingTrigger -> Icons.Filled.MusicNote to Indigo
        is Trigger.HasReplyTrigger -> Icons.AutoMirrored.Filled.Reply to Teal
        is Trigger.PromoTrigger -> Icons.Filled.Campaign to Orange
    }

    fun of(c: Condition): Pair<ImageVector, Color> = when (c) {
        is Condition.TimeCondition -> Icons.Filled.Schedule to Orange
        is Condition.HolidayCondition -> Icons.Filled.CalendarMonth to Red
        is Condition.ChargingCondition -> Icons.Filled.BatteryChargingFull to Green
        is Condition.ScreenCondition -> Icons.Filled.Smartphone to Indigo
        is Condition.BatteryLevelCondition -> Icons.Filled.BatteryStd to Green
        is Condition.CooldownCondition -> Icons.Filled.HourglassBottom to Gray
    }

    fun of(a: Action): Pair<ImageVector, Color> = when (a) {
        is Action.ReplaceTextAction -> Icons.Filled.FindReplace to Blue
        is Action.SetFieldAction -> Icons.Filled.Edit to Blue
        is Action.DiscardAction -> Icons.Filled.VisibilityOff to Gray
        is Action.DismissAction -> Icons.Filled.Close to Red
        is Action.SnoozeAction -> Icons.Filled.Snooze to Indigo
        is Action.MarkImportantAction -> Icons.Filled.PriorityHigh to Orange
        is Action.SoundVibrationAction -> Icons.Filled.Vibration to Pink
        is Action.AutoReplyAction -> Icons.AutoMirrored.Filled.Reply to Teal
        is Action.ReadAloudAction -> Icons.Filled.RecordVoiceOver to Purple
        is Action.WakeScreenAction -> Icons.Filled.LightMode to Orange
        is Action.ToastAction -> Icons.Filled.Sms to Green
        is Action.SetVariableAction -> Icons.Filled.DataObject to Indigo
        is Action.RunTaskerAction -> Icons.Filled.Bolt to Orange
        is Action.WebhookAction -> Icons.Filled.Cloud to Blue
        is Action.MuteAppAction -> Icons.Filled.NotificationsOff to Red
        is Action.DigestAction -> Icons.Filled.Inbox to Indigo
    }
}

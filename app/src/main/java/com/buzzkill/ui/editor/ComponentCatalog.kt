package com.buzzkill.ui.editor

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.HourglassBottom
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
import com.buzzkill.R
import com.buzzkill.data.model.Action
import com.buzzkill.data.model.Condition
import com.buzzkill.data.model.Trigger
import com.buzzkill.ui.Ids

// iOS system accent palette for the icon badges.
private val Blue = Color(0xFF007AFF)
private val Green = Color(0xFF34C759)
private val Orange = Color(0xFFFF9500)
private val Red = Color(0xFFFF3B30)
private val Purple = Color(0xFFAF52DE)
private val Indigo = Color(0xFF5856D6)
private val Teal = Color(0xFF32ADE6)
private val Pink = Color(0xFFFF2D55)
private val Gray = Color(0xFF8E8E93)

/** Definitions for the "add component" picker: localized label/desc + icon badge + factory. */
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

    val actions: List<CatalogEntry<Action>> = listOf(
        CatalogEntry(R.string.cat_act_replace, R.string.cat_act_replace_desc, Icons.Filled.FindReplace, Blue) {
            Action.ReplaceTextAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_setfield, R.string.cat_act_setfield_desc, Icons.Filled.Edit, Blue) {
            Action.SetFieldAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_discard, R.string.cat_act_discard_desc, Icons.Filled.VisibilityOff, Gray) {
            Action.DiscardAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_dismiss, R.string.cat_act_dismiss_desc, Icons.Filled.Close, Red) {
            Action.DismissAction(Ids.next())
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
        CatalogEntry(R.string.cat_act_autoreply, R.string.cat_act_autoreply_desc, Icons.AutoMirrored.Filled.Reply, Teal) {
            Action.AutoReplyAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_readaloud, R.string.cat_act_readaloud_desc, Icons.Filled.RecordVoiceOver, Purple) {
            Action.ReadAloudAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_wake, R.string.cat_act_wake_desc, Icons.Filled.LightMode, Orange) {
            Action.WakeScreenAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_toast, R.string.cat_act_toast_desc, Icons.Filled.Sms, Green) {
            Action.ToastAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_setvar, R.string.cat_act_setvar_desc, Icons.Filled.DataObject, Indigo) {
            Action.SetVariableAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_tasker, R.string.cat_act_tasker_desc, Icons.Filled.Bolt, Orange) {
            Action.RunTaskerAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_webhook, R.string.cat_act_webhook_desc, Icons.Filled.Cloud, Blue) {
            Action.WebhookAction(Ids.next())
        },
        CatalogEntry(R.string.cat_act_mute, R.string.cat_act_mute_desc, Icons.Filled.NotificationsOff, Red) {
            Action.MuteAppAction(Ids.next())
        },
    )
}

/** Icon + colour for a configured component, shown on its row in the editor. */
object ComponentVisuals {
    fun of(t: Trigger): Pair<ImageVector, Color> = when (t) {
        is Trigger.TextTrigger -> Icons.Filled.Search to Blue
        is Trigger.OngoingTrigger -> Icons.Filled.MusicNote to Indigo
        is Trigger.HasReplyTrigger -> Icons.AutoMirrored.Filled.Reply to Teal
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
    }
}

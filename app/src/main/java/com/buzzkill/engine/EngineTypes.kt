package com.buzzkill.engine

import com.buzzkill.data.model.DayType
import com.buzzkill.data.model.HttpMethod
import com.buzzkill.data.model.Importance
import com.buzzkill.data.model.NotificationField
import com.buzzkill.data.model.VibrationPreset

/** Ambient device state sampled once per notification, used by conditions. */
data class DeviceContext(
    val charging: Boolean,
    val screenOn: Boolean,
    val batteryPercent: Int,
    val minuteOfDay: Int,
    val isoDayOfWeek: Int,
    val dayType: DayType,
    val nowMillis: Long,
)

/**
 * Mutable snapshot of an incoming notification. Triggers read [fields]; the engine
 * also records regex [captures] used by template placeholders.
 */
class MatchContext(
    val packageName: String,
    val appName: String,
    val fields: MutableMap<NotificationField, String>,
    val isOngoing: Boolean,
    val hasReply: Boolean,
    val device: DeviceContext,
) {
    /** {1}..{9} from regex groups and named captures from variables. */
    val captures: MutableMap<String, String> = mutableMapOf()

    fun field(f: NotificationField): String = when (f) {
        NotificationField.ANY -> listOf(
            NotificationField.TITLE, NotificationField.TEXT, NotificationField.BIG_TEXT,
            NotificationField.SUB_TEXT, NotificationField.INFO_TEXT, NotificationField.TICKER,
        ).joinToString(" ") { fields[it].orEmpty() }.trim()
        NotificationField.APP_NAME -> appName
        else -> fields[f].orEmpty()
    }
}

/** Side effects that require an Android context; executed by the service. */
sealed class SideEffect {
    data class AutoReply(val message: String) : SideEffect()
    data class ReadAloud(val text: String) : SideEffect()
    data class WakeScreen(val durationMs: Long) : SideEffect()
    data class Toast(val text: String) : SideEffect()
    data class RunTasker(val taskName: String) : SideEffect()
    data class Webhook(val url: String, val method: HttpMethod, val body: String) : SideEffect()
    data class MuteApp(val pkg: String, val minutes: Int) : SideEffect()
    data class Danmaku(val text: String, val durationMs: Long) : SideEffect()
}

/** Desired changes to the reposted notification's alerting behaviour. */
data class SoundOverride(
    val silent: Boolean = false,
    val soundUri: String? = null,
    val vibration: VibrationPreset? = null,
)

/**
 * The accumulated outcome of running every rule against one notification. The
 * service turns this into concrete platform operations.
 */
class Decision {
    var matched: Boolean = false
    /** Suppress the notification — never repost it. */
    var discard: Boolean = false
    /** Cancel the original notification (after [dismissDelayMs]). */
    var dismiss: Boolean = false
    var dismissDelayMs: Long = 0
    var snoozeMinutes: Int? = null
    var importance: Importance? = null
    var bypassDnd: Boolean = false
    var sound: SoundOverride? = null
    /** Field overrides applied to the reposted copy. */
    val fieldEdits: MutableMap<NotificationField, String> = mutableMapOf()
    val sideEffects: MutableList<SideEffect> = mutableListOf()
    /** Rule ids that fired, for fire-count bookkeeping. */
    val firedRuleIds: MutableSet<Long> = mutableSetOf()

    /** True when the notification content/alerting must be rebuilt and reposted. */
    val needsRepost: Boolean
        get() = !discard && (fieldEdits.isNotEmpty() || importance != null || sound != null)
}

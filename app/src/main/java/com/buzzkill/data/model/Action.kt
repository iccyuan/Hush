package com.buzzkill.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An action mutates the notification or performs a side effect once a rule fires.
 * Actions run in order. Some actions ([DiscardAction], [DismissAction]) short-circuit
 * further posting of the notification.
 *
 * Templates ([SetFieldAction.template], [ReadAloudAction.template], …) support
 * placeholders resolved by the template engine: {title} {text} {app} {1}…{9}
 * (regex captures) and {var:name} (user variables).
 */
@Serializable
sealed class Action {
    abstract val id: String
    abstract fun summary(): String

    /** Find/replace inside a field; supports regex with $1 back-references. */
    @Serializable
    @SerialName("replace")
    data class ReplaceTextAction(
        override val id: String,
        val field: NotificationField = NotificationField.TEXT,
        val pattern: String = "",
        val replacement: String = "",
        val isRegex: Boolean = false,
        val caseSensitive: Boolean = false,
    ) : Action() {
        override fun summary() =
            "Replace \"$pattern\" → \"$replacement\" in ${field.label}"
    }

    /** Overwrite a field with a rendered template. */
    @Serializable
    @SerialName("setField")
    data class SetFieldAction(
        override val id: String,
        val field: NotificationField = NotificationField.TITLE,
        val template: String = "",
    ) : Action() {
        override fun summary() = "Set ${field.label} to \"$template\""
    }

    /** Suppress the notification entirely — it never reaches the shade. */
    @Serializable
    @SerialName("discard")
    data class DiscardAction(override val id: String) : Action() {
        override fun summary() = "Discard notification"
    }

    /** Cancel/dismiss the notification, optionally after a delay (ms). */
    @Serializable
    @SerialName("dismiss")
    data class DismissAction(
        override val id: String,
        val delayMs: Long = 0,
    ) : Action() {
        override fun summary() =
            if (delayMs > 0) "Dismiss after ${delayMs}ms" else "Dismiss notification"
    }

    /** Snooze the notification for [minutes]; it returns to the shade later. */
    @Serializable
    @SerialName("snooze")
    data class SnoozeAction(
        override val id: String,
        val minutes: Int = 30,
    ) : Action() {
        override fun summary() = "Snooze for ${minutes}m"
    }

    /** Change importance / DND bypass of the reposted notification. */
    @Serializable
    @SerialName("importance")
    data class MarkImportantAction(
        override val id: String,
        val importance: Importance = Importance.HIGH,
        val bypassDnd: Boolean = false,
    ) : Action() {
        override fun summary() =
            "Set importance ${importance.name}" + if (bypassDnd) " + bypass DND" else ""
    }

    /** Override sound / vibration of the reposted notification. */
    @Serializable
    @SerialName("soundVibration")
    data class SoundVibrationAction(
        override val id: String,
        val soundUri: String? = null,
        val silent: Boolean = false,
        val vibration: VibrationPreset = VibrationPreset.NORMAL,
    ) : Action() {
        override fun summary(): String = when {
            silent -> "Silence sound & vibration"
            else -> "Sound/vibration: ${vibration.label}"
        }
    }

    /** Auto-reply using the notification's inline RemoteInput, if present. */
    @Serializable
    @SerialName("autoReply")
    data class AutoReplyAction(
        override val id: String,
        val message: String = "",
    ) : Action() {
        override fun summary() = "Auto-reply \"$message\""
    }

    /** Speak a rendered template aloud through text-to-speech. */
    @Serializable
    @SerialName("readAloud")
    data class ReadAloudAction(
        override val id: String,
        val template: String = "{app}: {title} {text}",
    ) : Action() {
        override fun summary() = "Read aloud \"$template\""
    }

    /** Briefly turn the screen on. */
    @Serializable
    @SerialName("wakeScreen")
    data class WakeScreenAction(
        override val id: String,
        val durationMs: Long = 3000,
    ) : Action() {
        override fun summary() = "Wake screen for ${durationMs}ms"
    }

    /** Show a transient toast with a rendered template. */
    @Serializable
    @SerialName("toast")
    data class ToastAction(
        override val id: String,
        val template: String = "{title}",
    ) : Action() {
        override fun summary() = "Toast \"$template\""
    }

    /** Set/update a user variable from a rendered template. */
    @Serializable
    @SerialName("setVariable")
    data class SetVariableAction(
        override val id: String,
        val name: String = "",
        val valueTemplate: String = "",
    ) : Action() {
        override fun summary() = "Set \$$name = \"$valueTemplate\""
    }

    /** Broadcast an intent to trigger a Tasker task by name. */
    @Serializable
    @SerialName("tasker")
    data class RunTaskerAction(
        override val id: String,
        val taskName: String = "",
    ) : Action() {
        override fun summary() = "Run Tasker task \"$taskName\""
    }

    /** Fire an HTTP request, e.g. to a webhook / home automation. */
    @Serializable
    @SerialName("webhook")
    data class WebhookAction(
        override val id: String,
        val url: String = "",
        val method: HttpMethod = HttpMethod.POST,
        val bodyTemplate: String = "{\"app\":\"{app}\",\"title\":\"{title}\",\"text\":\"{text}\"}",
    ) : Action() {
        override fun summary() = "${method.name} $url"
    }

    /**
     * Mute every notification from the triggering app for [minutes]. Implemented
     * as a temporary discard window keyed by package name.
     */
    @Serializable
    @SerialName("muteApp")
    data class MuteAppAction(
        override val id: String,
        val minutes: Int = 30,
    ) : Action() {
        override fun summary() = "Mute this app for ${minutes}m"
    }

    /**
     * Show the notification as a scrolling "danmaku" bullet across the screen via an
     * overlay window, instead of the native notification (the native one is suppressed).
     * Requires the draw-over-other-apps permission.
     */
    @Serializable
    @SerialName("danmaku")
    data class DanmakuAction(
        override val id: String,
        val template: String = "{app}: {title} {text}",
        val durationMs: Long = 7000,
    ) : Action() {
        override fun summary() = "Danmaku \"$template\""
    }
}

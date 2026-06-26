package com.buzzkill.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 当规则触发后，动作会修改通知或执行副作用。
 * 动作按顺序执行。某些动作（[DiscardAction]、[DismissAction]）会中断
 * 通知的后续发布。
 *
 * 模板（[SetFieldAction.template]、[ReadAloudAction.template] 等）支持
 * 由模板引擎解析的占位符：{title} {text} {app} {1}…{9}
 *（正则捕获组）以及 {var:name}（用户变量）。
 */
@Serializable
sealed class Action {
    abstract val id: String
    abstract fun summary(): String

    /** 在某个字段内进行查找/替换；支持使用 $1 反向引用的正则表达式。 */
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

    /** 用渲染后的模板覆盖某个字段。 */
    @Serializable
    @SerialName("setField")
    data class SetFieldAction(
        override val id: String,
        val field: NotificationField = NotificationField.TITLE,
        val template: String = "",
    ) : Action() {
        override fun summary() = "Set ${field.label} to \"$template\""
    }

    /** 完全抑制该通知——它永远不会出现在通知栏中。 */
    @Serializable
    @SerialName("discard")
    data class DiscardAction(override val id: String) : Action() {
        override fun summary() = "Discard notification"
    }

    /** 取消/移除该通知，可选择在延迟（毫秒）之后执行。 */
    @Serializable
    @SerialName("dismiss")
    data class DismissAction(
        override val id: String,
        val delayMs: Long = 0,
    ) : Action() {
        override fun summary() =
            if (delayMs > 0) "Dismiss after ${delayMs}ms" else "Dismiss notification"
    }

    /** 将通知延后 [minutes] 分钟；稍后它会重新回到通知栏。 */
    @Serializable
    @SerialName("snooze")
    data class SnoozeAction(
        override val id: String,
        val minutes: Int = 30,
    ) : Action() {
        override fun summary() = "Snooze for ${minutes}m"
    }

    /** 修改重新发布通知的重要性 / 免打扰绕过设置。 */
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

    /** 覆盖重新发布通知的声音 / 振动设置。 */
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

    /** 若通知存在内联 RemoteInput，则使用它进行自动回复。 */
    @Serializable
    @SerialName("autoReply")
    data class AutoReplyAction(
        override val id: String,
        val message: String = "",
    ) : Action() {
        override fun summary() = "Auto-reply \"$message\""
    }

    /** 通过文字转语音朗读渲染后的模板。 */
    @Serializable
    @SerialName("readAloud")
    data class ReadAloudAction(
        override val id: String,
        val template: String = "{app}: {title} {text}",
    ) : Action() {
        override fun summary() = "Read aloud \"$template\""
    }

    /** 短暂点亮屏幕。 */
    @Serializable
    @SerialName("wakeScreen")
    data class WakeScreenAction(
        override val id: String,
        val durationMs: Long = 3000,
    ) : Action() {
        override fun summary() = "Wake screen for ${durationMs}ms"
    }

    /** 用渲染后的模板显示一个短暂的 toast 提示。 */
    @Serializable
    @SerialName("toast")
    data class ToastAction(
        override val id: String,
        val template: String = "{title}",
    ) : Action() {
        override fun summary() = "Toast \"$template\""
    }

    /** 根据渲染后的模板设置/更新一个用户变量。 */
    @Serializable
    @SerialName("setVariable")
    data class SetVariableAction(
        override val id: String,
        val name: String = "",
        val valueTemplate: String = "",
    ) : Action() {
        override fun summary() = "Set \$$name = \"$valueTemplate\""
    }

    /** 广播一个 intent，按名称触发指定的 Tasker 任务。 */
    @Serializable
    @SerialName("tasker")
    data class RunTaskerAction(
        override val id: String,
        val taskName: String = "",
    ) : Action() {
        override fun summary() = "Run Tasker task \"$taskName\""
    }

    /** 发起一个 HTTP 请求，例如发送到 webhook / 智能家居自动化。 */
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
     * 在 [minutes] 分钟内静音触发应用的所有通知。该功能实现为
     * 以包名为键的临时丢弃时间窗口。
     */
    @Serializable
    @SerialName("muteApp")
    data class MuteAppAction(
        override val id: String,
        val minutes: Int = 30,
    ) : Action() {
        override fun summary() = "Mute this app for ${minutes}m"
    }
}

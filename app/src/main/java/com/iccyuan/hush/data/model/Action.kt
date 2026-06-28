package com.iccyuan.hush.data.model

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
 *
 * 面向用户的一行描述由 [com.iccyuan.hush.ui.Localize.summary] 按所选语言生成。
 */
@Serializable
sealed class Action {
    abstract val id: String

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
    ) : Action()

    /** 用渲染后的模板覆盖某个字段。 */
    @Serializable
    @SerialName("setField")
    data class SetFieldAction(
        override val id: String,
        val field: NotificationField = NotificationField.TITLE,
        val template: String = "",
    ) : Action()

    /** 完全抑制该通知——它永远不会出现在通知栏中。 */
    @Serializable
    @SerialName("discard")
    data class DiscardAction(override val id: String) : Action()

    /** 取消/移除该通知，可选择在延迟（毫秒）之后执行。 */
    @Serializable
    @SerialName("dismiss")
    data class DismissAction(
        override val id: String,
        val delayMs: Long = 0,
    ) : Action()

    /** 将通知延后 [minutes] 分钟；稍后它会重新回到通知栏。 */
    @Serializable
    @SerialName("snooze")
    data class SnoozeAction(
        override val id: String,
        val minutes: Int = 30,
    ) : Action()

    /** 修改重新发布通知的重要性 / 免打扰绕过设置。 */
    @Serializable
    @SerialName("importance")
    data class MarkImportantAction(
        override val id: String,
        val importance: Importance = Importance.HIGH,
        val bypassDnd: Boolean = false,
    ) : Action()

    /** 覆盖重新发布通知的声音 / 振动设置。 */
    @Serializable
    @SerialName("soundVibration")
    data class SoundVibrationAction(
        override val id: String,
        val soundUri: String? = null,
        val silent: Boolean = false,
        val vibration: VibrationPreset = VibrationPreset.NORMAL,
    ) : Action()

    /** 若通知存在内联 RemoteInput，则使用它进行自动回复。 */
    @Serializable
    @SerialName("autoReply")
    data class AutoReplyAction(
        override val id: String,
        val message: String = "",
    ) : Action()

    /** 通过文字转语音朗读渲染后的模板。 */
    @Serializable
    @SerialName("readAloud")
    data class ReadAloudAction(
        override val id: String,
        val template: String = "{app}: {title} {text}",
    ) : Action()

    /** 短暂点亮屏幕。 */
    @Serializable
    @SerialName("wakeScreen")
    data class WakeScreenAction(
        override val id: String,
        val durationMs: Long = 3000,
    ) : Action()

    /** 用渲染后的模板显示一个短暂的 toast 提示。 */
    @Serializable
    @SerialName("toast")
    data class ToastAction(
        override val id: String,
        val template: String = "{title}",
    ) : Action()

    /** 命中后额外发送一条提醒通知（内容由模板渲染）。 */
    @Serializable
    @SerialName("notify")
    data class NotifyAction(
        override val id: String,
        val template: String = "{app}: {title}",
    ) : Action()

    /** 根据渲染后的模板设置/更新一个用户变量。 */
    @Serializable
    @SerialName("setVariable")
    data class SetVariableAction(
        override val id: String,
        val name: String = "",
        val valueTemplate: String = "",
    ) : Action()

    /**
     * 发起一个 HTTP 请求，例如发送到 webhook / 智能家居自动化。
     * 类 Postman 形式：查询参数 [queryParams]（拼到 URL）、请求头 [headers]、
     * 以及仅 POST 携带的请求体（[bodyType] 决定 Content-Type，[bodyTemplate] 为内容）。
     * [url]、参数/请求头的值、以及 [bodyTemplate] 均支持模板占位符。
     */
    @Serializable
    @SerialName("webhook")
    data class WebhookAction(
        override val id: String,
        val url: String = "",
        val method: HttpMethod = HttpMethod.POST,
        val queryParams: List<KeyValue> = emptyList(),
        val headers: List<KeyValue> = emptyList(),
        val bodyType: WebhookBodyType = WebhookBodyType.JSON,
        val bodyTemplate: String = "{\"app\":\"{app}\",\"title\":\"{title}\",\"text\":\"{text}\"}",
    ) : Action()

    /**
     * 无限期静音触发应用的所有通知，直到用户在设置中手动取消。
     * 实现为以包名为键的丢弃开关。
     */
    @Serializable
    @SerialName("muteApp")
    data class MuteAppAction(
        override val id: String,
    ) : Action()

    /**
     * 聚合：抑制单条通知，在 [windowMinutes] 分钟的时间窗内把命中规则的多条通知
     * 攒成一条摘要通知发布，治理高频应用的刷屏。[template] 决定每条被收纳的内容。
     */
    @Serializable
    @SerialName("digest")
    data class DigestAction(
        override val id: String,
        val windowMinutes: Int = 5,
        val template: String = "{title} {text}",
    ) : Action()
}

/** [Action.WebhookAction] 用到的键值对（请求头或查询参数）。[value] 支持模板占位符。 */
@Serializable
data class KeyValue(
    val name: String = "",
    val value: String = "",
)

/** Webhook 请求体类型——决定 POST 的 Content-Type。 */
@Serializable
enum class WebhookBodyType(val contentType: String) {
    JSON("application/json; charset=utf-8"),
    TEXT("text/plain; charset=utf-8"),
    XML("application/xml; charset=utf-8"),
}

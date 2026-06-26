package com.buzzkill.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 触发器决定一条传入通知是否为某条规则的候选。
 * 规则的 [Rule.triggerLogic] 以 ALL/ANY 语义组合多个触发器。
 */
@Serializable
sealed class Trigger {
    abstract val id: String

    /** 供编辑器列表使用的、便于人阅读的一行描述。 */
    abstract fun summary(): String

    /** 匹配通知某个字段中的文本，可选地捕获正则分组。 */
    @Serializable
    @SerialName("text")
    data class TextTrigger(
        override val id: String,
        val field: NotificationField = NotificationField.ANY,
        val mode: MatchMode = MatchMode.CONTAINS,
        val query: String = "",
        val caseSensitive: Boolean = false,
        val negate: Boolean = false,
    ) : Trigger() {
        override fun summary(): String {
            val not = if (negate) "NOT " else ""
            return "$not${field.label} ${mode.label} \"$query\""
        }
    }

    /** 根据通知是否为常驻通知（例如音乐、下载）进行匹配。 */
    @Serializable
    @SerialName("ongoing")
    data class OngoingTrigger(
        override val id: String,
        val mustBeOngoing: Boolean = false,
    ) : Trigger() {
        override fun summary(): String =
            if (mustBeOngoing) "Notification is ongoing" else "Notification is dismissible"
    }

    /** 当通知带有内联回复动作（聊天类）时进行匹配。 */
    @Serializable
    @SerialName("hasReply")
    data class HasReplyTrigger(
        override val id: String,
        val mustHaveReply: Boolean = true,
    ) : Trigger() {
        override fun summary(): String =
            if (mustHaveReply) "Has an inline reply action" else "Has no reply action"
    }
}

package com.iccyuan.hush.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 触发器决定一条传入通知是否为某条规则的候选。
 * 规则的 [Rule.triggerLogic] 以 ALL/ANY 语义组合多个触发器。
 *
 * 面向用户的一行描述由 [com.iccyuan.hush.ui.Localize.summary] 按所选语言生成；
 * 模型本身保持与 Android 无关、不含展示文案。
 */
@Serializable
sealed class Trigger {
    abstract val id: String

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
    ) : Trigger()

    /** 根据通知是否为常驻通知（例如音乐、下载）进行匹配。 */
    @Serializable
    @SerialName("ongoing")
    data class OngoingTrigger(
        override val id: String,
        val mustBeOngoing: Boolean = false,
    ) : Trigger()

    /** 当通知带有内联回复动作（聊天类）时进行匹配。 */
    @Serializable
    @SerialName("hasReply")
    data class HasReplyTrigger(
        override val id: String,
        val mustHaveReply: Boolean = true,
    ) : Trigger()

    /**
     * 防骚扰：匹配系统标记为「营销 / 推广」类别（Notification.CATEGORY_PROMO）的通知。
     * 纯本地按类别识别,无需配置。
     */
    @Serializable
    @SerialName("promo")
    data class PromoTrigger(
        override val id: String,
    ) : Trigger()
}

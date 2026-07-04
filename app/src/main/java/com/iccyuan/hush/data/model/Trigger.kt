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
     * 设备事件触发器：在某个设备状态切换的那一刻（如 Wi-Fi 连上/断开）触发规则的动作，
     * 与通知无关。带有此触发器的规则是「事件驱动」的，不参与通知匹配（见 RuleEngine）。
     */
    @Serializable
    @SerialName("device_event")
    data class DeviceEvent(
        override val id: String,
        val event: DeviceEventType = DeviceEventType.WIFI_CONNECTED,
        /** 仅对 Wi-Fi 事件生效：限定的网络 SSID（多选）；为空 = 任意 Wi-Fi。 */
        val ssids: List<String> = emptyList(),
    ) : Trigger()

    /**
     * 位置事件触发器：在进入/离开某个地理围栏的那一刻触发规则的动作（与通知无关）。
     * 由系统级地理围栏监控，省电；与 [Trigger.DeviceEvent] 一样属于「事件驱动」。
     */
    @Serializable
    @SerialName("location_event")
    data class LocationTrigger(
        override val id: String,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val radiusMeters: Int = 300,
        val placeName: String = "",
        val event: LocationEventType = LocationEventType.ENTER,
    ) : Trigger() {
        /** 同一坐标 + 半径对应同一个围栏，与 LocationCondition 共用同一套 key。 */
        fun fenceKey(): String = "geo_${latitude}_${longitude}_${radiusMeters}"
    }
}

/** 规则是否由事件驱动（[Trigger.DeviceEvent] / [Trigger.LocationTrigger]）——事件规则不走通知匹配路径。 */
val Rule.isEventDriven: Boolean
    get() = triggers.any { it is Trigger.DeviceEvent || it is Trigger.LocationTrigger }

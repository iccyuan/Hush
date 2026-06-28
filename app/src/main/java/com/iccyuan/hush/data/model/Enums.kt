package com.iccyuan.hush.data.model

import kotlinx.serialization.Serializable

/** 触发器或动作所针对的通知部位。 */
@Serializable
enum class NotificationField(val label: String) {
    TITLE("Title"),
    TEXT("Text"),
    BIG_TEXT("Expanded text"),
    SUB_TEXT("Sub text"),
    INFO_TEXT("Info text"),
    TICKER("Ticker"),
    /** 通知类别（如 msg / email / call），来自 Notification.category。 */
    CATEGORY("Category"),
    /** 通知渠道 id，来自 Notification.channelId。 */
    CHANNEL("Channel"),
    /** 发信人/会话名（聊天类通知，best-effort 从 MessagingStyle 提取）。 */
    SENDER("Sender"),
    APP_NAME("App name"),
    ANY("Any field");
}

/** [TextTrigger] / [ReplaceTextAction] 的查询内容与文本进行比较的方式。 */
@Serializable
enum class MatchMode(val label: String) {
    CONTAINS("contains"),
    EQUALS("equals"),
    STARTS_WITH("starts with"),
    ENDS_WITH("ends with"),
    REGEX("matches regex"),
    WILDCARD("matches wildcard");
}

/** 规则触发时，是要求所有（ALL）还是任意（ANY）触发器成立。 */
@Serializable
enum class LogicMode(val label: String) {
    ALL("Match all"),
    ANY("Match any");
}

/**
 * 规则各条件之间的组合方式（高级设置）。
 * - [SMART]（默认）：同类条件「或」（如多个时间段），且把「时间段 + 节假日」并为同一「时间」组
 *   一起「或」；不同类的组之间「与」。
 * - [ALL]：全部条件都必须成立（且）。
 * - [ANY]：任一条件成立即可（或）。
 */
@Serializable
enum class ConditionLogic {
    SMART,
    ALL,
    ANY,
}

/** 映射到 NotificationManager 常量的通知重要性级别。 */
@Serializable
enum class Importance(val label: String) {
    MIN("Min — no sound, collapsed"),
    LOW("Low — no sound"),
    DEFAULT("Default"),
    HIGH("High — peek"),
    URGENT("Urgent — peek + sound");
}

/** [SoundVibrationAction] 的振动强度预设。 */
@Serializable
enum class VibrationPreset(val label: String, val pattern: LongArray) {
    NONE("None", longArrayOf(0)),
    SHORT("Short tick", longArrayOf(0, 40)),
    NORMAL("Normal", longArrayOf(0, 250)),
    DOUBLE("Double buzz", longArrayOf(0, 120, 100, 120)),
    LONG("Long", longArrayOf(0, 600)),
    HEARTBEAT("Heartbeat", longArrayOf(0, 100, 80, 100, 80, 300));
}

/** [WebhookAction] 使用的 HTTP 方法。 */
@Serializable
enum class HttpMethod { GET, POST, PUT }

/**
 * [Trigger.DeviceEvent] 监听的设备事件——在状态切换的那一刻触发规则（无需通知）。
 * 这是一个可扩展集合：后续可加入「进入/离开围栏」「开始/停止充电」等。
 */
@Serializable
enum class DeviceEventType {
    /** 连上 Wi-Fi 的那一刻。 */
    WIFI_CONNECTED,
    /** 断开 Wi-Fi 的那一刻。 */
    WIFI_DISCONNECTED,
}

/**
 * 用于节假日条件的日历日期分类。由内置的中国法定节假日日历
 * 加上工作日/周末的兜底规则决定。
 */
@Serializable
enum class DayType {
    /** 法定休息日（法定节假日）。 */
    LEGAL_HOLIDAY,
    /** 日历将其声明为工作日的周末（调休上班）。 */
    MAKEUP_WORKDAY,
    /** 没有特殊调整的普通周六/周日。 */
    WEEKEND,
    /** 普通的周一至周五工作日。 */
    WORKDAY,
}

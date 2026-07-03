package com.iccyuan.hush.engine

import com.iccyuan.hush.data.model.DayType
import com.iccyuan.hush.data.model.HttpMethod
import com.iccyuan.hush.data.model.Importance
import com.iccyuan.hush.data.model.NotificationField
import com.iccyuan.hush.data.model.VibrationPreset

/** 每条通知采样一次的设备环境状态，供条件使用。 */
data class DeviceContext(
    val charging: Boolean,
    val screenOn: Boolean,
    val batteryPercent: Int,
    val minuteOfDay: Int,
    val isoDayOfWeek: Int,
    val dayType: DayType,
    val nowMillis: Long,
    /** 是否插着/连着耳机（有线或蓝牙音频输出）。 */
    val headphonesConnected: Boolean = false,
    /** 当前活动网络是否为 Wi-Fi。 */
    val onWifi: Boolean = false,
    /** 当前处于其内部的地理围栏 key 集合（见 LocationCondition.fenceKey）。 */
    val insideGeofences: Set<String> = emptySet(),
)

/**
 * 传入通知的可变快照。触发器读取 [fields]；引擎还会记录
 * 模板占位符所使用的正则 [captures]。
 */
class MatchContext(
    val packageName: String,
    val appName: String,
    val fields: MutableMap<NotificationField, String>,
    val isOngoing: Boolean,
    val hasReply: Boolean,
    val device: DeviceContext,
    /** 该通知所属的用户空间 id：本体为主用户（通常 0），应用分身/双开为独立用户（如 999）。 */
    val userId: Int = 0,
    /** 是否为常驻通知（VPN / 音乐 / 下载 / 前台服务等）。默认不触发弹幕。 */
    val isPersistent: Boolean = false,
) {
    /** 来自正则分组的 {1}..{9} 以及来自变量的命名捕获。 */
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

/** 需要 Android context 的副作用；由服务执行。 */
sealed class SideEffect {
    data class AutoReply(val message: String) : SideEffect()
    data class ReadAloud(val text: String) : SideEffect()
    data class WakeScreen(val durationMs: Long) : SideEffect()
    data class Toast(val text: String) : SideEffect()
    data class Notify(val text: String) : SideEffect()
    data class Webhook(
        val url: String,
        val method: HttpMethod,
        val params: List<Pair<String, String>>,
        val headers: List<Pair<String, String>>,
        /** POST 请求体的 Content-Type；为空表示不带请求体。 */
        val contentType: String,
        val body: String,
    ) : SideEffect()
    data class MuteApp(val pkg: String, val ruleId: Long) : SideEffect()
    data class Danmaku(val text: String) : SideEffect()
    data class Digest(
        val pkg: String,
        val appName: String,
        val line: String,
        val windowMinutes: Int,
    ) : SideEffect()
    /** 打开应用 / 指定 Activity（[activity] 为空则用默认启动 Intent）。 */
    data class LaunchApp(val pkg: String, val activity: String) : SideEffect()
    /** 用无障碍服务回放一段录制好的手势宏。 */
    data class RunMacro(
        val steps: List<com.iccyuan.hush.data.model.MacroStep>,
        val screenWidth: Int,
        val screenHeight: Int,
        val repeat: Int,
    ) : SideEffect()
}

/** 对重新发布通知的提醒行为所期望的更改。 */
data class SoundOverride(
    val silent: Boolean = false,
    val soundUri: String? = null,
    val vibration: VibrationPreset? = null,
)

/**
 * 针对一条通知运行所有规则后累积得到的结果。
 * 服务会将其转化为具体的平台操作。
 */
class Decision {
    var matched: Boolean = false
    /** 抑制该通知——绝不重新发布。 */
    var discard: Boolean = false
    /** 取消原始通知（在 [dismissDelayMs] 之后）。 */
    var dismiss: Boolean = false
    var dismissDelayMs: Long = 0
    var snoozeMinutes: Int? = null
    var importance: Importance? = null
    var bypassDnd: Boolean = false
    var sound: SoundOverride? = null
    /** 应用到重新发布副本上的字段覆盖。 */
    val fieldEdits: MutableMap<NotificationField, String> = mutableMapOf()
    val sideEffects: MutableList<SideEffect> = mutableListOf()
    /** 已触发的规则 id，用于触发计数的记账。 */
    val firedRuleIds: MutableSet<Long> = mutableSetOf()

    /** 当通知内容/提醒必须重新构建并重新发布时为 true。 */
    val needsRepost: Boolean
        get() = !discard && (fieldEdits.isNotEmpty() || importance != null || sound != null)
}

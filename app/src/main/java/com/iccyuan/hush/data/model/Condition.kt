package com.iccyuan.hush.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 条件根据设备/上下文状态对规则进行限制，仅在触发器已匹配之后才进行评估。
 * 规则上的所有条件都必须成立（AND 语义）。
 *
 * 面向用户的一行描述由 [com.iccyuan.hush.ui.Localize.summary] 按所选语言生成。
 */
@Serializable
sealed class Condition {
    abstract val id: String

    /**
     * 仅在所选 [days]（1 = 周一 … 7 = 周日，ISO 标准）的 [startMinute] 与
     * [endMinute]（从午夜起算的分钟数）之间生效。若开始时间晚于结束时间，
     * 则时间窗口会跨越午夜。
     */
    @Serializable
    @SerialName("time")
    data class TimeCondition(
        override val id: String,
        val startMinute: Int = 22 * 60,
        val endMinute: Int = 7 * 60,
        val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    ) : Condition()

    /** 仅在设备正在充电（或未在充电）时生效。 */
    @Serializable
    @SerialName("charging")
    data class ChargingCondition(
        override val id: String,
        val mustBeCharging: Boolean = true,
    ) : Condition()

    /** 仅在屏幕开启/关闭时生效。 */
    @Serializable
    @SerialName("screen")
    data class ScreenCondition(
        override val id: String,
        val mustBeOn: Boolean = false,
    ) : Condition()

    /** 仅在电量低于/高于某个阈值时生效。 */
    @Serializable
    @SerialName("battery")
    data class BatteryLevelCondition(
        override val id: String,
        val percent: Int = 20,
        val whenBelow: Boolean = true,
    ) : Condition()

    /**
     * 速率限制：规则每 [seconds] 秒只能触发一次。可防止来自频繁推送应用的
     * 重复动作。
     */
    @Serializable
    @SerialName("cooldown")
    data class CooldownCondition(
        override val id: String,
        val seconds: Int = 60,
    ) : Condition()

    /**
     * 仅当今天的 [DayType]（依据内置的中国法定节假日日历）属于 [dayTypes]
     * 之一时生效。可与 [TimeCondition] 组合（AND）以表达诸如
     *“在法定节假日的 09:00–18:00 期间”之类的条件。
     */
    @Serializable
    @SerialName("holiday")
    data class HolidayCondition(
        override val id: String,
        val dayTypes: Set<DayType> = setOf(DayType.LEGAL_HOLIDAY),
    ) : Condition()
}

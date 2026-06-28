package com.iccyuan.hush.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 一个完整的自动化：它监视哪些应用、用于选中通知的触发器、
 * 对其加以限制的条件，以及它所执行的动作。
 *
 * 各组成部分的列表通过 [com.iccyuan.hush.data.db.Converters] 以 JSON 列的形式持久化。
 */
@Entity(tableName = "rules")
@Serializable
data class Rule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "Untitled rule",
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    /** 为空 = 适用于所有应用。 */
    val appPackages: List<String> = emptyList(),
    val triggerLogic: LogicMode = LogicMode.ALL,
    val triggers: List<Trigger> = emptyList(),
    val conditions: List<Condition> = emptyList(),
    /** 条件组合方式（高级）：SMART = 智能分组（默认），ALL = 全部满足（且），ANY = 任一满足（或）。 */
    val conditionLogic: ConditionLogic = ConditionLogic.SMART,
    val actions: List<Action> = emptyList(),
    /** 若为 true，则一旦本规则触发，后续规则将不再被评估。 */
    val stopProcessing: Boolean = false,
    /**
     * 若为 true，匹配的通知将以滚动的“弹幕”悬浮条形式显示，
     * 而非原生通知（需要“显示在其他应用上层”权限）。
     */
    val showDanmaku: Boolean = false,
    /** 本规则累计处理过的通知数量。 */
    val fireCount: Long = 0,
    val notes: String = "",
) {
    /** 没有触发器的规则会匹配其所属应用的所有通知。 */
    val matchesEverything: Boolean get() = triggers.isEmpty()
}

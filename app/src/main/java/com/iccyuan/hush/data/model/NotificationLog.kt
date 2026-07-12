package com.iccyuan.hush.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 监听器观察到并记录的一条通知，以及引擎对它所做的处理。 */
@Entity(
    tableName = "notification_log",
    // 为 ORDER BY time DESC 的列表查询以及按应用分组/筛选建立索引。
    indices = [Index("time"), Index("packageName")],
)
data class NotificationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val time: Long,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    /** 是否有任意规则匹配了这条通知。 */
    val matched: Boolean,
    /** 触发的规则 id，以逗号分隔（若无则为空）。 */
    val firedRuleIds: String,
    /** 发生的处理结果：none / modified / silenced / discarded / dismissed / snoozed。 */
    val outcome: String,
    /**
     * 「为什么被这样处理」的取证：命中规则 + 实际命中的触发器 + 当时成立的条件，
     * 序列化的 [com.iccyuan.hush.engine.MatchTrace] 列表（JSON）。旧记录为空串。
     *
     * 之所以随日志存一份而不是回查规则：规则随时会被改、被删，事后回查只能得到**现在**的样子，
     * 答不了「当时为什么命中」——那恰恰是用户翻历史时要问的。
     */
    val traces: String = "",
) {
    companion object {
        const val OUTCOME_NONE = "none"
        const val OUTCOME_MODIFIED = "modified"
        const val OUTCOME_SILENCED = "silenced"
        const val OUTCOME_DISCARDED = "discarded"
        const val OUTCOME_DISMISSED = "dismissed"
        const val OUTCOME_SNOOZED = "snoozed"
    }
}

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

package com.iccyuan.hush.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.iccyuan.hush.data.model.NotificationLog
import kotlinx.coroutines.flow.Flow

/** 某个应用在历史日志中的通知计数（用于洞察面板）。 */
data class AppCount(val packageName: String, val appName: String, val count: Int)

@Dao
interface NotificationLogDao {
    @Insert
    suspend fun insert(log: NotificationLog)

    @Query("SELECT * FROM notification_log ORDER BY time DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NotificationLog>>

    @Query("SELECT * FROM notification_log ORDER BY time DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<NotificationLog>

    @Query("SELECT COUNT(*) FROM notification_log")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM notification_log WHERE matched = 1")
    suspend fun matchedCount(): Int

    /** 按应用聚合的通知数量，从多到少排列，用于“最吵的应用”洞察。 */
    @Query(
        "SELECT packageName, appName, COUNT(*) AS count FROM notification_log " +
            "GROUP BY packageName ORDER BY count DESC LIMIT :limit"
    )
    suspend fun topApps(limit: Int): List<AppCount>

    @Query("DELETE FROM notification_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 仅保留最新的 [keep] 行记录。 */
    @Query("DELETE FROM notification_log WHERE id NOT IN (SELECT id FROM notification_log ORDER BY time DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM notification_log")
    suspend fun clear()
}

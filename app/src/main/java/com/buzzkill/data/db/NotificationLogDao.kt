package com.buzzkill.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.buzzkill.data.model.NotificationLog
import kotlinx.coroutines.flow.Flow

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

    @Query("DELETE FROM notification_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 仅保留最新的 [keep] 行记录。 */
    @Query("DELETE FROM notification_log WHERE id NOT IN (SELECT id FROM notification_log ORDER BY time DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM notification_log")
    suspend fun clear()
}

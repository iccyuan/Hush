package com.iccyuan.hush.data

import android.content.Context
import com.iccyuan.hush.data.db.AppDatabase
import com.iccyuan.hush.data.db.NotificationLogDao
import com.iccyuan.hush.data.model.NotificationLog
import kotlinx.coroutines.flow.Flow

/** 访问滚动式的通知活动日志。 */
class NotificationLogRepository private constructor(private val dao: NotificationLogDao) {

    // 自上次清理以来的插入次数。我们每插入 PRUNE_EVERY 次才清理一次，而不是在每条通知上
    // 都执行 COUNT(*)——这样热路径上始终只有一次 INSERT。
    private val sincePrune = java.util.concurrent.atomic.AtomicInteger(0)

    fun observeRecent(limit: Int = MAX_ROWS): Flow<List<NotificationLog>> = dao.observeRecent(limit)
    suspend fun recent(limit: Int = MAX_ROWS): List<NotificationLog> = dao.recent(limit)
    suspend fun clear() = dao.clear()
    suspend fun deleteById(id: Long) = dao.deleteById(id)

    // --- 洞察 ---
    suspend fun total(): Int = dao.count()
    suspend fun matched(): Int = dao.matchedCount()
    suspend fun topApps(limit: Int = 8): List<com.iccyuan.hush.data.db.AppCount> = dao.topApps(limit)

    suspend fun add(log: NotificationLog) {
        dao.insert(log)
        // 在不对每次插入都计数的情况下，使表的大小保持有界。
        if (sincePrune.incrementAndGet() >= PRUNE_EVERY) {
            sincePrune.set(0)
            dao.prune(MAX_ROWS)
        }
    }

    companion object {
        const val MAX_ROWS = 1000
        private const val PRUNE_EVERY = 100

        @Volatile
        private var instance: NotificationLogRepository? = null

        fun get(context: Context): NotificationLogRepository =
            instance ?: synchronized(this) {
                instance ?: NotificationLogRepository(
                    AppDatabase.get(context).notificationLogDao()
                ).also { instance = it }
            }
    }
}

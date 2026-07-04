package com.iccyuan.hush.data
import com.iccyuan.hush.data.db.AppCount

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

    /** 历史列表分页（响应式，递增 LIMIT）；可选按包名过滤。 */
    fun observePage(pkg: String?, limit: Int): Flow<List<NotificationLog>> =
        dao.observeRecentFiltered(pkg, limit.coerceAtMost(MAX_ROWS))

    /** 统计用时间戳（响应式），按实际数据、不设上限（表大小已由 prune 约束）。 */
    fun observeTimes(pkg: String?): Flow<List<Long>> = dao.observeTimes(pkg)

    /** 过滤行的应用聚合（响应式）。 */
    fun observeAppCounts(limit: Int = APP_CHIPS_LIMIT): Flow<List<com.iccyuan.hush.data.db.AppCount>> =
        dao.observeAppCounts(limit)
    suspend fun clear() = dao.clear()
    suspend fun deleteById(id: Long) = dao.deleteById(id)

    // --- 洞察 ---
    suspend fun total(): Int = dao.count()
    suspend fun matched(): Int = dao.matchedCount()
    suspend fun topApps(limit: Int = 8): List<AppCount> = dao.topApps(limit)

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
        /** 历史「按应用过滤」行里展示的应用数量上限。 */
        const val APP_CHIPS_LIMIT = 40

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

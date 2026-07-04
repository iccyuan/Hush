package com.iccyuan.hush.data

import android.content.Context
import com.iccyuan.hush.data.db.AppDatabase
import com.iccyuan.hush.data.db.BuzzJson
import com.iccyuan.hush.data.db.RuleDao
import com.iccyuan.hush.data.db.RuleFireStatsDao
import com.iccyuan.hush.data.model.Rule
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer

/**
 * 访问规则存储的单一入口。持有一个进程级单例，使监听服务和 UI 共享同一个数据库实例。
 */
class RuleRepository private constructor(
    private val dao: RuleDao,
    private val fireStatsDao: RuleFireStatsDao,
) {

    fun observeAll(): Flow<List<Rule>> = dao.observeAll()
    fun observeById(id: Long): Flow<Rule?> = dao.observeById(id)
    suspend fun enabledRules(): List<Rule> = dao.enabledRules()
    suspend fun byId(id: Long): Rule? = dao.byId(id)

    /** 一次性读取全部规则，并把独立表里的触发计数（见 [RuleFireStatsDao]）合并进来供 UI 展示。 */
    suspend fun allOnce(): List<Rule> {
        val rules = dao.allOnce()
        if (rules.isEmpty()) return rules
        val counts = fireStatsDao.allOnce().associate { it.ruleId to it.fireCount }
        return rules.map { rule -> counts[rule.id]?.let { rule.copy(fireCount = it) } ?: rule }
    }

    suspend fun upsert(rule: Rule): Long {
        return if (rule.id == 0L) {
            val order = dao.maxSortOrder() + 1
            dao.insert(rule.copy(sortOrder = order))
        } else {
            dao.update(rule)
            rule.id
        }
    }

    suspend fun delete(rule: Rule) = dao.delete(rule)
    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
    suspend fun incrementFireCount(id: Long) = fireStatsDao.incrementFireCount(id)

    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> dao.setSortOrder(id, index) }
    }

    /** 将所有规则序列化为 JSON，用于备份/导出。 */
    suspend fun exportJson(): String =
        BuzzJson.encodeToString(ListSerializer(Rule.serializer()), allOnce())

    /** 从导出的 JSON 导入规则，作为新行插入。 */
    suspend fun importJson(json: String): Int {
        val rules = BuzzJson.decodeFromString(ListSerializer(Rule.serializer()), json)
        rules.forEach { dao.insert(it.copy(id = 0)) }
        return rules.size
    }

    companion object {
        @Volatile
        private var instance: RuleRepository? = null

        fun get(context: Context): RuleRepository =
            instance ?: synchronized(this) {
                instance ?: RuleRepository(
                    AppDatabase.get(context).ruleDao(),
                    AppDatabase.get(context).ruleFireStatsDao(),
                ).also { instance = it }
            }
    }
}

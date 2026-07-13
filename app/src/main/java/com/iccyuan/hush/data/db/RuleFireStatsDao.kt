package com.iccyuan.hush.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.iccyuan.hush.data.model.RuleFireStats

@Dao
interface RuleFireStatsDao {

    @Query("SELECT * FROM rule_fire_stats")
    suspend fun allOnce(): List<RuleFireStats>

    @Query("INSERT OR IGNORE INTO rule_fire_stats(ruleId, fireCount) VALUES (:ruleId, 0)")
    suspend fun ensureRow(ruleId: Long)

    @Query("UPDATE rule_fire_stats SET fireCount = fireCount + 1 WHERE ruleId = :ruleId")
    suspend fun bumpFireCount(ruleId: Long)

    /** minSdk 26 的系统 SQLite 版本不保证支持 UPSERT 语法，故拆成两步而非单条 upsert 查询。 */
    @Transaction
    suspend fun incrementFireCount(ruleId: Long) {
        ensureRow(ruleId)
        bumpFireCount(ruleId)
    }

    @Query("DELETE FROM rule_fire_stats")
    suspend fun clear()
}

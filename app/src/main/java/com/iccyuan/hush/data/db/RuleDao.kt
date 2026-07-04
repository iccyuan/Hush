package com.iccyuan.hush.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.iccyuan.hush.data.model.Rule
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Rule>>

    /** 监听服务在每条通知到达时使用的快照。 */
    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    suspend fun enabledRules(): List<Rule>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun byId(id: Long): Rule?

    @Query("SELECT * FROM rules ORDER BY sortOrder ASC, id ASC")
    suspend fun allOnce(): List<Rule>

    @Query("SELECT * FROM rules WHERE id = :id")
    fun observeById(id: Long): Flow<Rule?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: Rule): Long

    @Update
    suspend fun update(rule: Rule)

    @Delete
    suspend fun delete(rule: Rule)

    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE rules SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM rules")
    suspend fun maxSortOrder(): Int
}

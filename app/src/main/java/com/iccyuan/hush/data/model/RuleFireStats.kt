package com.iccyuan.hush.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 规则触发计数，与 [Rule] 分表存放。
 *
 * 通知命中规则的频率很高，而 Room 的 Flow 失效追踪按表粒度工作：若计数写在 `rules` 表里，
 * 每次触发都会让监听服务对 `rules` 的 [com.iccyuan.hush.data.db.RuleDao.observeAll] 订阅
 * 重新发射，进而重新反序列化全部规则、重算设备状态采样开关、重同步地理围栏/Wi-Fi 监听——
 * 单条通知的处理因此退化成 O(全部规则数)。分表后，写入热路径不再触碰 `rules` 表。
 */
@Entity(tableName = "rule_fire_stats")
data class RuleFireStats(
    @PrimaryKey val ruleId: Long,
    val fireCount: Long = 0,
)

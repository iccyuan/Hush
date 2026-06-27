package com.buzzkill.data

import android.content.Context
import com.buzzkill.data.db.BuzzJson
import com.buzzkill.engine.VariableStore
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * 将 [VariableStore] 的运行时状态（用户变量、规则冷却、应用静音窗口）持久化到
 * SharedPreferences，使其在进程被回收或设备重启后依然有效。
 *
 * 在启动时调用 [init] 一次：它会读取已保存的状态、剔除已过期的冷却/静音条目，
 * 灌入内存，并注册一个回调以便后续变化时落盘。引擎核心保持与 Android 无关。
 */
object RuntimeStateStore {

    private const val PREFS = "buzzkill_runtime"
    private const val KEY_VARS = "variables"
    private const val KEY_COOLDOWNS = "cooldowns"
    private const val KEY_MUTES = "mutes"

    private val stringMap = MapSerializer(String.serializer(), String.serializer())
    private val longMap = MapSerializer(String.serializer(), Long.serializer())

    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()

            val vars = decode(prefs.getString(KEY_VARS, null), stringMap, emptyMap())
            // 剔除已经到期的冷却/静音，避免无限累积陈旧条目。
            val cooldowns = decode(prefs.getString(KEY_COOLDOWNS, null), longMap, emptyMap())
                .mapKeys { it.key.toLongOrNull() ?: -1L }
                .filter { it.key >= 0 && it.value > now }
            val mutes = decode(prefs.getString(KEY_MUTES, null), longMap, emptyMap())
                .filterValues { it > now }

            VariableStore.restore(vars, cooldowns, mutes)
            VariableStore.setPersistence { persist(prefs) }
            initialized = true
        }
    }

    private fun persist(prefs: android.content.SharedPreferences) {
        runCatching {
            prefs.edit()
                .putString(KEY_VARS, BuzzJson.encodeToString(stringMap, VariableStore.snapshot()))
                .putString(
                    KEY_COOLDOWNS,
                    BuzzJson.encodeToString(
                        longMap,
                        VariableStore.cooldownsSnapshot().mapKeys { it.key.toString() },
                    ),
                )
                .putString(KEY_MUTES, BuzzJson.encodeToString(longMap, VariableStore.mutesSnapshot()))
                .apply()
        }
    }

    private fun <T> decode(
        json: String?,
        serializer: kotlinx.serialization.KSerializer<T>,
        fallback: T,
    ): T = if (json.isNullOrEmpty()) fallback
    else runCatching { BuzzJson.decodeFromString(serializer, json) }.getOrDefault(fallback)
}

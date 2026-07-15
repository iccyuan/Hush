package com.iccyuan.hush.data

import android.content.Context
import com.iccyuan.hush.data.db.BuzzJson
import com.iccyuan.hush.engine.VariableStore
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * 将 [VariableStore] 的运行时状态（用户变量、规则冷却、应用静音窗口）持久化到 MMKV，
 * 使其在进程被回收或设备重启后依然有效。
 *
 * 在启动时调用 [init] 一次：它会读取已保存的状态（老版本存在 SharedPreferences 里的
 * 一次性迁入）、剔除已过期的冷却条目，灌入内存，并注册一个回调以便后续变化时落盘。
 * 引擎核心保持与 Android 无关。
 *
 * ## 写入策略
 *
 * 落盘是**合并去抖**的：带「设置变量」动作的规则每命中一条通知都会触发一次变更，逐次全量
 * 序列化没有意义——变更先攒 [PERSIST_DEBOUNCE_MS]，连珠的变更合并成一次写。MMKV 本身是
 * mmap 写入（微秒级、崩溃安全），去抖省的是 JSON 序列化的 CPU。代价是进程在窗口内被杀会
 * 丢最后不到一秒的状态（冷却/变量回退一步），可以接受。
 */
object RuntimeStateStore {

    private const val MMKV_ID = "hush_runtime"
    /** 老版本的 SharedPreferences 文件名，仅用于一次性迁移。 */
    private const val LEGACY_PREFS = "hush_runtime"
    private const val KEY_VARS = "variables"
    private const val KEY_COOLDOWNS = "cooldowns"
    private const val KEY_MUTES = "mutes"
    private const val KEY_OEM_REALERTS = "oem_realerts_on_putback"

    /** 变更到落盘的合并窗口。 */
    private const val PERSIST_DEBOUNCE_MS = 500L

    private val stringMap = MapSerializer(String.serializer(), String.serializer())
    private val longMap = MapSerializer(String.serializer(), Long.serializer())

    @Volatile private var initialized = false
    private var kv: MMKV? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // 合流的落盘信号：窗口内的多次变更只写一遍（persist 读的是写入时刻的最新快照）。
    private val persistSignal = Channel<Unit>(Channel.CONFLATED)

    /**
     * 这台机器是否会在 snooze 放回时**重新播放提示音**（破坏 Android 11+ 的静默放回约定，
     * 实测 ColorOS 16 / Android 16 如此）。由服务在首次就地静音后实测判定并持久化，
     * 之后就地静音会顺带静音通知音量流把那声响铃吞掉。
     */
    @Volatile var oemRealertsOnPutback: Boolean = false
        private set

    fun setOemRealertsOnPutback(value: Boolean) {
        if (oemRealertsOnPutback == value) return
        oemRealertsOnPutback = value
        // 这类一次性标记直接写，不走去抖——它一年变不了一次，丢了却要再错响一声才能重新学到。
        runCatching { kv?.encode(KEY_OEM_REALERTS, value) }
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val store = MMKV.mmkvWithID(MMKV_ID)
            kv = store
            migrateFromPrefs(context.applicationContext, store)
            oemRealertsOnPutback = store.decodeBool(KEY_OEM_REALERTS, false)
            val now = System.currentTimeMillis()

            val vars = decode(store.decodeString(KEY_VARS), stringMap, emptyMap())
            // 冷却带到期时间戳，剔除已过期的；静音是“包名 -> 规则 id”，无到期时间，原样恢复。
            val cooldowns = decode(store.decodeString(KEY_COOLDOWNS), longMap, emptyMap())
                .mapKeys { it.key.toLongOrNull() ?: -1L }
                .filter { it.key >= 0 && it.value > now }
            val mutes = decode(store.decodeString(KEY_MUTES), longMap, emptyMap())

            VariableStore.restore(vars, cooldowns, mutes)
            VariableStore.setPersistence { persistSignal.trySend(Unit) }
            scope.launch {
                for (signal in persistSignal) {
                    delay(PERSIST_DEBOUNCE_MS)
                    runCatching { persist(store) }
                }
            }
            initialized = true
        }
    }

    /** 老版本存在 SharedPreferences 里的状态一次性搬进 MMKV；搬完清空老文件，之后不再触碰。 */
    private fun migrateFromPrefs(app: Context, store: MMKV) {
        runCatching {
            val prefs = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            if (prefs.all.isEmpty()) return
            store.importFromSharedPreferences(prefs)
            prefs.edit().clear().apply()
        }
    }

    private fun persist(store: MMKV) {
        store.encode(KEY_VARS, BuzzJson.encodeToString(stringMap, VariableStore.snapshot()))
        store.encode(
            KEY_COOLDOWNS,
            BuzzJson.encodeToString(
                longMap,
                VariableStore.cooldownsSnapshot().mapKeys { it.key.toString() },
            ),
        )
        store.encode(KEY_MUTES, BuzzJson.encodeToString(longMap, VariableStore.mutesSnapshot()))
    }

    private fun <T> decode(
        json: String?,
        serializer: kotlinx.serialization.KSerializer<T>,
        fallback: T,
    ): T = if (json.isNullOrEmpty()) fallback
    else runCatching { BuzzJson.decodeFromString(serializer, json) }.getOrDefault(fallback)
}

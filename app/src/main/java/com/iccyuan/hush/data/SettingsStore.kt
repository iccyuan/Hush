package com.iccyuan.hush.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hush_settings")

/** 应用级偏好设置（非按规则维度）。 */
class SettingsStore private constructor(private val context: Context) {

    private val masterEnabledKey = booleanPreferencesKey("master_enabled")
    private val logActivityKey = booleanPreferencesKey("log_activity")
    private val onboardedKey = booleanPreferencesKey("onboarded")
    private val hideFromRecentsKey = booleanPreferencesKey("hide_from_recents")
    private val immersiveDanmakuKey = booleanPreferencesKey("immersive_danmaku")
    // 弹幕外观/行为（全局）。默认取 [DanmakuConfig] 的默认值。
    private val dmSizeKey = floatPreferencesKey("danmaku_size_sp")
    private val dmColorKey = intPreferencesKey("danmaku_color")
    private val dmBgAlphaKey = intPreferencesKey("danmaku_bg_alpha")
    private val dmDurationKey = intPreferencesKey("danmaku_duration_ms")
    private val dmRowsKey = intPreferencesKey("danmaku_rows")
    private val dmTopOffsetKey = floatPreferencesKey("danmaku_top_offset_dp")

    /** 全局总开关——为 false 时，引擎将被完全绕过。 */
    val masterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[masterEnabledKey] ?: true }

    val logActivity: Flow<Boolean> =
        context.dataStore.data.map { it[logActivityKey] ?: true }

    val onboarded: Flow<Boolean> =
        context.dataStore.data.map { it[onboardedKey] ?: false }

    /** 是否将应用从「最近任务」中隐藏（默认开启）。 */
    val hideFromRecents: Flow<Boolean> =
        context.dataStore.data.map { it[hideFromRecentsKey] ?: true }

    /** 沉浸弹幕：检测到全屏（横屏看视频/玩游戏）时，屏蔽原生通知、改以弹幕显示（默认关闭）。 */
    val immersiveDanmaku: Flow<Boolean> =
        context.dataStore.data.map { it[immersiveDanmakuKey] ?: false }

    /** 弹幕全局外观/行为配置（缺省回退到 [DanmakuConfig] 默认值）。 */
    val danmakuConfig: Flow<DanmakuConfig> =
        context.dataStore.data.map { p ->
            val d = DanmakuConfig()
            DanmakuConfig(
                fontSizeSp = p[dmSizeKey] ?: d.fontSizeSp,
                color = p[dmColorKey] ?: d.color,
                bgAlpha = p[dmBgAlphaKey] ?: d.bgAlpha,
                durationMs = (p[dmDurationKey] ?: d.durationMs.toInt()).toLong(),
                rows = p[dmRowsKey] ?: d.rows,
                topOffsetDp = p[dmTopOffsetKey] ?: d.topOffsetDp,
            )
        }

    suspend fun setMasterEnabled(value: Boolean) =
        context.dataStore.edit { it[masterEnabledKey] = value }.let {}

    suspend fun setLogActivity(value: Boolean) =
        context.dataStore.edit { it[logActivityKey] = value }.let {}

    suspend fun setOnboarded(value: Boolean) =
        context.dataStore.edit { it[onboardedKey] = value }.let {}

    suspend fun setHideFromRecents(value: Boolean) =
        context.dataStore.edit { it[hideFromRecentsKey] = value }.let {}

    suspend fun setImmersiveDanmaku(value: Boolean) =
        context.dataStore.edit { it[immersiveDanmakuKey] = value }.let {}

    suspend fun setDanmakuConfig(c: DanmakuConfig) {
        context.dataStore.edit {
            it[dmSizeKey] = c.fontSizeSp
            it[dmColorKey] = c.color
            it[dmBgAlphaKey] = c.bgAlpha
            it[dmDurationKey] = c.durationMs.toInt()
            it[dmRowsKey] = c.rows
            it[dmTopOffsetKey] = c.topOffsetDp
        }
    }

    companion object {
        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}

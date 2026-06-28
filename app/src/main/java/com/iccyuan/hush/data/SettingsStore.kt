package com.iccyuan.hush.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "buzzkill_settings")

/** 应用级偏好设置（非按规则维度）。 */
class SettingsStore private constructor(private val context: Context) {

    private val masterEnabledKey = booleanPreferencesKey("master_enabled")
    private val logActivityKey = booleanPreferencesKey("log_activity")
    private val onboardedKey = booleanPreferencesKey("onboarded")
    private val hideFromRecentsKey = booleanPreferencesKey("hide_from_recents")
    private val immersiveDanmakuKey = booleanPreferencesKey("immersive_danmaku")

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

    companion object {
        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}

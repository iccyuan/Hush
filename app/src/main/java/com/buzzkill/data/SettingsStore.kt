package com.buzzkill.data

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

    /** 全局总开关——为 false 时，引擎将被完全绕过。 */
    val masterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[masterEnabledKey] ?: true }

    val logActivity: Flow<Boolean> =
        context.dataStore.data.map { it[logActivityKey] ?: true }

    val onboarded: Flow<Boolean> =
        context.dataStore.data.map { it[onboardedKey] ?: false }

    suspend fun setMasterEnabled(value: Boolean) =
        context.dataStore.edit { it[masterEnabledKey] = value }.let {}

    suspend fun setLogActivity(value: Boolean) =
        context.dataStore.edit { it[logActivityKey] = value }.let {}

    suspend fun setOnboarded(value: Boolean) =
        context.dataStore.edit { it[onboardedKey] = value }.let {}

    companion object {
        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}

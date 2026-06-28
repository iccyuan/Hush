package com.iccyuan.hush.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 外观（浅色/深色/跟随系统）偏好设置。基于 SharedPreferences，并同步镜像到一个 StateFlow，
 * 使主题能即时切换（无需重建 Activity）。
 */
object ThemeStore {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    val options = listOf(SYSTEM, LIGHT, DARK)

    private const val PREFS = "hush_theme"
    private const val KEY = "theme_mode"

    private val flow = MutableStateFlow(SYSTEM)
    private var loaded = false

    val mode: StateFlow<String> = flow

    fun ensureLoaded(context: Context) {
        if (loaded) return
        flow.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM) ?: SYSTEM
        loaded = true
    }

    fun set(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode).apply()
        flow.value = mode
    }
}

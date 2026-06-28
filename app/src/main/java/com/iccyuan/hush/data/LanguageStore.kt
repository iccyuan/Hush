package com.iccyuan.hush.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用语言覆盖设置。基于 SharedPreferences（在 Activity.attachBaseContext 中同步读取），
 * 并同步镜像到一个 StateFlow，使 UI 能即时重新本地化——无需重建 Activity。
 */
object LanguageStore {
    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val CHINESE = "zh"

    val options = listOf(SYSTEM, ENGLISH, CHINESE)

    private const val PREFS = "hush_lang"
    private const val KEY = "app_language"

    private val flow = MutableStateFlow(SYSTEM)
    private var loaded = false

    val language: StateFlow<String> = flow

    fun ensureLoaded(context: Context) {
        if (loaded) return
        flow.value = read(context)
        loaded = true
    }

    /** 供 attachBaseContext 使用的同步读取（在任何 flow 能完成解析之前）。 */
    fun get(context: Context): String = read(context)

    fun set(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, language).apply()
        flow.value = language
    }

    private fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM
}

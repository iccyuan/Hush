package com.iccyuan.hush.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.iccyuan.hush.data.LanguageStore
import java.util.Locale

/** 使用用户选择的区域设置包装基础 context（用于 Activity.attachBaseContext）。 */
object LocaleManager {

    fun localeFor(language: String): Locale = when (language) {
        LanguageStore.ENGLISH -> Locale.ENGLISH
        LanguageStore.CHINESE -> Locale.SIMPLIFIED_CHINESE
        else -> Resources.getSystem().configuration.locales[0]
    }

    fun wrap(base: Context): Context {
        val locale = localeFor(LanguageStore.get(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}

/**
 * 在不重新创建 Activity 的情况下，将整个合成重新本地化为 [language]：
 * 提供一个配置了区域设置的 Context/Configuration，使每个 stringResource 在
 * 下次重组时以所选语言重新读取。
 */
@Composable
fun ProvideAppLocale(language: String, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val locale = LocaleManager.localeFor(language)
    val localizedContext = remember(language, baseContext) {
        Locale.setDefault(locale)
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        baseContext.createConfigurationContext(config)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
    ) {
        content()
    }
}

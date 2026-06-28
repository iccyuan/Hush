package com.iccyuan.hush.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.iccyuan.hush.data.LanguageStore
import com.iccyuan.hush.data.SettingsStore
import com.iccyuan.hush.data.ThemeStore
import com.iccyuan.hush.ui.nav.HushNavHost
import com.iccyuan.hush.ui.theme.HushTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private companion object {
        /** 国产 ROM（移动安全联盟）约定的「读取应用列表」运行时权限。 */
        const val GET_INSTALLED_APPS = "com.android.permission.GET_INSTALLED_APPS"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        ThemeStore.ensureLoaded(this)
        LanguageStore.ensureLoaded(this)
        super.onCreate(savedInstanceState)

        // 运行时权限：通知（Android 13+，否则改写通知/摘要/保活常驻通知都不显示）+
        // 「读取应用列表」（国产 ROM 上规则的应用选择器需要）。仅请求尚未授予的。
        val toRequest = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
            if (ContextCompat.checkSelfPermission(this@MainActivity, GET_INSTALLED_APPS) !=
                PackageManager.PERMISSION_GRANTED
            ) add(GET_INSTALLED_APPS)
        }
        if (toRequest.isNotEmpty()) permLauncher.launch(toRequest.toTypedArray())

        // 跟随「隐藏后台」开关：开启时把本任务从「最近任务」列表中排除（默认开启）。
        lifecycleScope.launch {
            SettingsStore.get(this@MainActivity).hideFromRecents.collect { hide ->
                runCatching {
                    getSystemService(android.app.ActivityManager::class.java)
                        ?.appTasks?.forEach { it.setExcludeFromRecents(hide) }
                }
            }
        }
        setContent {
            val mode by ThemeStore.mode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeStore.LIGHT -> false
                ThemeStore.DARK -> true
                else -> isSystemInDarkTheme()
            }
            val language by LanguageStore.language.collectAsStateWithLifecycle()
            ProvideAppLocale(language) {
                HushTheme(darkTheme = dark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        HushNavHost()
                    }
                }
            }
        }
    }
}

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

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        ThemeStore.ensureLoaded(this)
        LanguageStore.ensureLoaded(this)
        super.onCreate(savedInstanceState)

        // Android 13+ 需运行时授予通知权限，否则改写后的通知、摘要、保活常驻通知都无法显示。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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

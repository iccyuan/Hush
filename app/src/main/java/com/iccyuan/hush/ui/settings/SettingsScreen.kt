@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iccyuan.hush.ui.settings
import com.iccyuan.hush.service.DanmakuController
import com.iccyuan.hush.data.ApkInstaller

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iccyuan.hush.R
import com.iccyuan.hush.data.LanguageStore
import com.iccyuan.hush.data.ThemeStore
import com.iccyuan.hush.data.UpdateChecker
import com.iccyuan.hush.service.NotificationAccess
import com.iccyuan.hush.ui.components.GlassScaffold
import com.iccyuan.hush.ui.components.HairlineDivider
import com.iccyuan.hush.ui.components.IOSRow
import com.iccyuan.hush.ui.components.IOSSegmented
import com.iccyuan.hush.ui.components.IOSSwitch
import com.iccyuan.hush.ui.components.IOSTintedButton
import com.iccyuan.hush.ui.components.InsetGroupedSection
import com.iccyuan.hush.ui.common.rememberListenerConnected
import com.iccyuan.hush.ui.common.rememberNotificationAccessGranted
import com.iccyuan.hush.ui.theme.IOSColors
import com.iccyuan.hush.ui.findActivity
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    onOpenInsights: () -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val masterEnabled by vm.masterEnabled.collectAsStateWithLifecycle()
    val logActivity by vm.logActivity.collectAsStateWithLifecycle()
    val hideFromRecents by vm.hideFromRecents.collectAsStateWithLifecycle()
    val immersiveDanmaku by vm.immersiveDanmaku.collectAsStateWithLifecycle()
    val accessGranted = rememberNotificationAccessGranted()
    val listenerConnected = rememberListenerConnected()
    var showImport by remember { mutableStateOf(false) }
    val currentLang by LanguageStore.language.collectAsStateWithLifecycle()
    val updateChecking by vm.updateChecking.collectAsStateWithLifecycle()
    var pendingUpdate by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    GlassScaffold(
        title = stringResource(R.string.settings),
        onBack = onBack,
        bottomBar = bottomBar,
        overlay = {
            Box(
                Modifier.fillMaxSize().padding(bottom = 88.dp),
                contentAlignment = Alignment.BottomCenter,
            ) { SnackbarHost(snackbar) }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // 通知访问权限——最重要：没有它整个应用都无法工作，因此置顶。
            InsetGroupedSection(header = stringResource(R.string.settings_access)) {
                val (statusLabel, statusColor) = when {
                    !accessGranted -> stringResource(R.string.status_no_access) to IOSColors.Gray
                    !listenerConnected -> stringResource(R.string.status_disconnected) to IOSColors.Red
                    else -> stringResource(R.string.status_connected) to IOSColors.Green
                }
                IOSRow(
                    title = stringResource(R.string.settings_access),
                    subtitle = stringResource(
                        when {
                            !accessGranted -> R.string.access_not_granted
                            !listenerConnected -> R.string.access_disconnected
                            else -> R.string.access_granted
                        }
                    ),
                )
                HairlineDivider(startInset = 16.dp)
                IOSRow(
                    title = stringResource(R.string.settings_connection),
                    trailing = { ConnectionStatus(statusLabel, statusColor) },
                )
                HairlineDivider(startInset = 16.dp)
                Column(Modifier.padding(16.dp)) {
                    IOSTintedButton(
                        text = stringResource(R.string.open_access_settings),
                        onClick = { context.startActivity(NotificationAccess.settingsIntent()) },
                    )
                }
            }

            // 通用
            InsetGroupedSection(header = stringResource(R.string.settings_general)) {
                IOSRow(
                    title = stringResource(R.string.settings_master),
                    icon = Icons.Filled.Bolt,
                    iconColor = IOSColors.Green,
                    trailing = { IOSSwitch(masterEnabled) { vm.setMasterEnabled(it) } },
                )
                HairlineDivider(startInset = 16.dp)
                IOSRow(
                    title = stringResource(R.string.settings_log),
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    iconColor = IOSColors.Blue,
                    trailing = { IOSSwitch(logActivity) { vm.setLogActivity(it) } },
                )
                HairlineDivider(startInset = 16.dp)
                IOSRow(
                    title = stringResource(R.string.settings_hide_recents),
                    subtitle = stringResource(R.string.settings_hide_recents_desc),
                    icon = Icons.Filled.VisibilityOff,
                    iconColor = IOSColors.Indigo,
                    trailing = { IOSSwitch(hideFromRecents) { vm.setHideFromRecents(it) } },
                )
                HairlineDivider(startInset = 16.dp)
                IOSRow(
                    title = stringResource(R.string.settings_immersive_danmaku),
                    subtitle = stringResource(R.string.settings_immersive_danmaku_desc),
                    icon = Icons.Filled.Subtitles,
                    iconColor = IOSColors.Purple,
                    trailing = { IOSSwitch(immersiveDanmaku) { vm.setImmersiveDanmaku(it) } },
                )
                // 沉浸弹幕依赖悬浮窗权限；开启但未授权时给出授予入口。
                if (immersiveDanmaku && !DanmakuController.canShow(context)) {
                    HairlineDivider(startInset = 16.dp)
                    IOSRow(
                        title = stringResource(R.string.grant_overlay),
                        icon = Icons.Filled.OpenInNew,
                        iconColor = IOSColors.Orange,
                        onClick = {
                            context.startActivity(
                                DanmakuController.overlaySettingsIntent(context)
                            )
                        },
                    )
                }
            }

            // 自动操作（无障碍）：打卡宏的录制/回放依赖它。
            InsetGroupedSection(
                header = stringResource(R.string.settings_accessibility),
                footer = stringResource(R.string.settings_accessibility_desc),
            ) {
                val accOn = com.iccyuan.hush.service.HushAccessibilityService.instance != null
                IOSRow(
                    title = stringResource(R.string.settings_accessibility),
                    icon = Icons.Filled.TouchApp,
                    iconColor = IOSColors.Purple,
                    trailing = {
                        ConnectionStatus(
                            if (accOn) stringResource(R.string.status_enabled) else stringResource(R.string.status_disabled),
                            if (accOn) IOSColors.Green else IOSColors.Gray,
                        )
                    },
                )
                HairlineDivider(startInset = 16.dp)
                Column(Modifier.padding(16.dp)) {
                    IOSTintedButton(
                        text = stringResource(R.string.open_accessibility_settings),
                        onClick = {
                            // LocalContext 在本应用里是经语言包装的非 Activity 上下文，必须带 NEW_TASK，
                            // 否则 startActivity 会抛异常导致崩溃退回桌面。
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                    )
                }
            }

            // 统计洞察
            InsetGroupedSection {
                IOSRow(
                    title = stringResource(R.string.settings_insights),
                    icon = Icons.Filled.BarChart,
                    iconColor = IOSColors.Purple,
                    onClick = onOpenInsights,
                )
            }

            // 外观：主题 + 语言
            val themeMode by ThemeStore.mode.collectAsStateWithLifecycle()
            InsetGroupedSection(header = stringResource(R.string.settings_appearance)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(10.dp))
                    IOSSegmented(
                        options = ThemeStore.options,
                        selected = themeMode,
                        label = { themeLabel(it) },
                        onSelect = { ThemeStore.set(context, it) },
                    )
                }
                HairlineDivider(startInset = 16.dp)
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(10.dp))
                    IOSSegmented(
                        options = LanguageStore.options,
                        selected = currentLang,
                        label = { langLabel(it) },
                        onSelect = { lang -> LanguageStore.set(context, lang) },
                    )
                }
            }

            // 节假日（中国）——来源于官方的数据，在本地获取并缓存。
            val holUpdated by vm.holidayUpdated.collectAsStateWithLifecycle()
            val holUpdating by vm.holidayUpdating.collectAsStateWithLifecycle()
            InsetGroupedSection(header = stringResource(R.string.settings_holidays)) {
                IOSRow(
                    title = stringResource(R.string.settings_holidays),
                    subtitle = if (holUpdated > 0)
                        stringResource(R.string.holiday_last_updated, formatTime(holUpdated))
                    else stringResource(R.string.holiday_never),
                    icon = Icons.Filled.CalendarMonth,
                    iconColor = IOSColors.Red,
                )
                HairlineDivider(startInset = 16.dp)
                Column(Modifier.padding(16.dp)) {
                    IOSTintedButton(
                        text = if (holUpdating) "…" else stringResource(R.string.holiday_update),
                        onClick = {
                            if (!holUpdating) vm.updateHolidays { ok ->
                                showMessage(
                                    context.getString(
                                        if (ok) R.string.holiday_update_done else R.string.holiday_update_failed
                                    )
                                )
                            }
                        },
                    )
                }
            }

            // 备份
            InsetGroupedSection(header = stringResource(R.string.settings_backup)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IOSTintedButton(
                        text = stringResource(R.string.export_rules),
                        onClick = {
                            vm.exportRules { json ->
                                copyToClipboard(context, json)
                                showMessage(context.getString(R.string.export_copied))
                            }
                        },
                    )
                    IOSTintedButton(
                        text = stringResource(R.string.import_rules),
                        onClick = { showImport = true },
                    )
                }
            }

            // 关于 / 检查更新
            InsetGroupedSection(header = stringResource(R.string.settings_about_section)) {
                IOSRow(
                    title = stringResource(R.string.settings_version),
                    subtitle = vm.appVersionDisplay,
                    icon = Icons.Filled.Info,
                    iconColor = IOSColors.Blue,
                )
                HairlineDivider(startInset = 16.dp)
                IOSRow(
                    title = stringResource(R.string.check_update),
                    subtitle = if (updateChecking) stringResource(R.string.update_checking) else null,
                    icon = Icons.Filled.SystemUpdate,
                    iconColor = IOSColors.Green,
                    onClick = {
                        if (!updateChecking) vm.checkUpdate { result ->
                            when {
                                result == null -> showMessage(context.getString(R.string.update_failed))
                                result.hasUpdate -> pendingUpdate = result
                                else -> showMessage(context.getString(R.string.update_latest))
                            }
                        }
                    },
                )
            }

            Text(
                stringResource(R.string.settings_about),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showImport) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text(stringResource(R.string.import_rules)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.import_paste)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.importRules(text) { count ->
                        showMessage(
                            if (count >= 0) context.getString(R.string.import_done, count)
                            else context.getString(R.string.import_failed)
                        )
                    }
                    showImport = false
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            title = { Text(stringResource(R.string.update_available)) },
            text = {
                Text(
                    stringResource(
                        R.string.update_available_msg,
                        update.latestVersion,
                        update.currentVersion,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = update.downloadUrl
                    if (ApkInstaller.isApk(url)) {
                        // 内置下载：系统 DownloadManager 下到应用私有目录，完成后拉起安装器，不经浏览器。
                        ApkInstaller.downloadAndInstall(context, url, update.latestVersion)
                        showMessage(context.getString(R.string.update_downloading))
                    } else {
                        // 非 APK 直链（如发布页）——回退到浏览器，并整体 runCatching 防崩溃。
                        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                        val opened = runCatching {
                            context.startActivity(
                                Intent.createChooser(view, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.isSuccess
                        if (!opened) {
                            copyToClipboard(context, url)
                            showMessage(context.getString(R.string.update_download_fallback))
                        }
                    }
                    pendingUpdate = null
                }) { Text(stringResource(R.string.update_download)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUpdate = null }) { Text(stringResource(R.string.update_later)) }
            },
        )
    }
}

/** 服务连接状态指示：彩色圆点 + 文字（已连接 / 已断开 / 未授权）。 */
@Composable
private fun ConnectionStatus(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun langLabel(code: String): String = when (code) {
    LanguageStore.ENGLISH -> stringResource(R.string.lang_en)
    LanguageStore.CHINESE -> stringResource(R.string.lang_zh)
    else -> stringResource(R.string.lang_system)
}

@Composable
private fun themeLabel(code: String): String = when (code) {
    ThemeStore.LIGHT -> stringResource(R.string.theme_light)
    ThemeStore.DARK -> stringResource(R.string.theme_dark)
    else -> stringResource(R.string.theme_system)
}

private fun formatTime(epochMs: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochMs))

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Hush rules", text))
}


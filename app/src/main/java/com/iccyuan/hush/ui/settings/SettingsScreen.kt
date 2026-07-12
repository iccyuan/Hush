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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iccyuan.hush.R
import com.iccyuan.hush.data.DanmakuConfig
import com.iccyuan.hush.data.HolidayProvider
import com.iccyuan.hush.data.LanguageStore
import com.iccyuan.hush.data.ThemeStore
import com.iccyuan.hush.data.UpdateChecker
import com.iccyuan.hush.service.NotificationAccess
import androidx.compose.ui.platform.LocalView
import com.iccyuan.hush.ui.common.rememberOnResume
import com.iccyuan.hush.ui.components.GlassScaffold
import com.iccyuan.hush.ui.components.TipBubble
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

/** 设置里的分类，每个跳到二级详情页。 */
enum class SettingsCategory { KEEPALIVE, GENERAL, DANMAKU, ACCESSIBILITY, APPEARANCE, HOLIDAYS, BACKUP, ABOUT }

@Composable
private fun categoryTitle(c: SettingsCategory): String = stringResource(
    when (c) {
        SettingsCategory.KEEPALIVE -> R.string.settings_keepalive
        SettingsCategory.GENERAL -> R.string.settings_general
        SettingsCategory.DANMAKU -> R.string.settings_danmaku
        SettingsCategory.ACCESSIBILITY -> R.string.settings_accessibility
        SettingsCategory.APPEARANCE -> R.string.settings_appearance
        SettingsCategory.HOLIDAYS -> R.string.settings_holidays
        SettingsCategory.BACKUP -> R.string.settings_backup
        SettingsCategory.ABOUT -> R.string.settings_about_section
    }
)

/** 顶层设置：仅保留关键项（通知使用权 + 总开关），其余收进分类行跳二级页，减少滚动长度。 */
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    onOpenInsights: () -> Unit = {},
    onOpenCategory: (SettingsCategory) -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val masterEnabled by vm.masterEnabled.collectAsStateWithLifecycle()
    val accessGranted = rememberNotificationAccessGranted()
    val listenerConnected = rememberListenerConnected()

    GlassScaffold(
        title = stringResource(R.string.settings),
        onBack = onBack,
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            AccessCard(accessGranted, listenerConnected, context)
            // 后台保活引导（可靠性关键项，紧随使用权卡片）
            InsetGroupedSection {
                IOSRow(
                    title = stringResource(R.string.settings_keepalive),
                    subtitle = stringResource(R.string.settings_keepalive_sub),
                    icon = Icons.Filled.BatteryChargingFull,
                    iconColor = IOSColors.Green,
                    onClick = { onOpenCategory(SettingsCategory.KEEPALIVE) },
                    trailing = { Chevron() },
                )
            }
            // 系统级静音：需要一次配套设备关联，之后「静音应用」才能直接改目标应用的通知渠道
            // （不发声不振动、通知原样保留）。未开通时静音退回到 snooze 掐断，在部分 ROM 上会失效。
            if (CompanionPairing.isSupported(context)) {
                // 关联状态由系统持有：弹窗关闭后回到前台时重新读一次即可（无需接收 Activity 结果）。
                val paired = rememberOnResume { CompanionPairing.isPaired(context) }
                // 发起关联要顺出宿主 Activity，必须用 View 的 context：LocalContext 已被
                // ProvideAppLocale 换成脱离 Activity 链的 ContextImpl（多语言所需）。
                val hostContext = LocalView.current.context
                var showChannelMuteInfo by remember { mutableStateOf(false) }
                InsetGroupedSection(footer = stringResource(R.string.settings_channel_mute_footer)) {
                    IOSRow(
                        title = stringResource(R.string.settings_channel_mute),
                        subtitle = stringResource(
                            if (paired) R.string.settings_channel_mute_on
                            else R.string.settings_channel_mute_off
                        ),
                        icon = Icons.Filled.NotificationsOff,
                        iconColor = IOSColors.Red,
                        onClick = { if (!paired) CompanionPairing.requestPairing(hostContext) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 详情入口：完整原理（含「为什么要选一个蓝牙设备」）较长，收进气泡，
                                // 行内只留一句结论。Box 是气泡的锚点，尖角会指回这个 ⓘ。
                                Box {
                                    IconButton(onClick = { showChannelMuteInfo = true }) {
                                        Icon(
                                            Icons.Filled.Info,
                                            contentDescription = stringResource(R.string.settings_channel_mute),
                                            tint = IOSColors.Gray,
                                        )
                                    }
                                    TipBubble(
                                        visible = showChannelMuteInfo,
                                        onDismiss = { showChannelMuteInfo = false },
                                        text = stringResource(R.string.settings_channel_mute_info),
                                    )
                                }
                                IOSSwitch(paired) { on ->
                                    if (on) CompanionPairing.requestPairing(hostContext)
                                    else CompanionPairing.unpair(context)
                                }
                            }
                        },
                    )
                }
            }
            // 总开关（关键项，置顶保留）
            InsetGroupedSection {
                IOSRow(
                    title = stringResource(R.string.settings_master),
                    icon = Icons.Filled.Bolt,
                    iconColor = IOSColors.Green,
                    trailing = { IOSSwitch(masterEnabled) { vm.setMasterEnabled(it) } },
                )
            }
            InsetGroupedSection {
                CategoryRow(SettingsCategory.GENERAL, Icons.Filled.Tune, IOSColors.Gray, onOpenCategory)
                HairlineDivider(startInset = 16.dp)
                CategoryRow(SettingsCategory.DANMAKU, Icons.Filled.Subtitles, IOSColors.Purple, onOpenCategory)
                HairlineDivider(startInset = 16.dp)
                CategoryRow(SettingsCategory.ACCESSIBILITY, Icons.Filled.TouchApp, IOSColors.Indigo, onOpenCategory)
                HairlineDivider(startInset = 16.dp)
                CategoryRow(SettingsCategory.APPEARANCE, Icons.Filled.Palette, IOSColors.Blue, onOpenCategory)
            }
            InsetGroupedSection {
                IOSRow(
                    title = stringResource(R.string.settings_insights),
                    icon = Icons.Filled.BarChart,
                    iconColor = IOSColors.Purple,
                    onClick = onOpenInsights,
                    trailing = { Chevron() },
                )
                HairlineDivider(startInset = 16.dp)
                CategoryRow(SettingsCategory.HOLIDAYS, Icons.Filled.CalendarMonth, IOSColors.Red, onOpenCategory)
                HairlineDivider(startInset = 16.dp)
                CategoryRow(SettingsCategory.BACKUP, Icons.Filled.Save, IOSColors.Teal, onOpenCategory)
                HairlineDivider(startInset = 16.dp)
                CategoryRow(SettingsCategory.ABOUT, Icons.Filled.Info, IOSColors.Blue, onOpenCategory)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CategoryRow(
    category: SettingsCategory,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onOpen: (SettingsCategory) -> Unit,
) {
    IOSRow(
        title = categoryTitle(category),
        icon = icon,
        iconColor = color,
        onClick = { onOpen(category) },
        trailing = { Chevron() },
    )
}

@Composable
private fun Chevron() {
    androidx.compose.material3.Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    )
}

/** 二级详情页：按分类渲染对应设置区块，自带滚动/消息条/对话框。 */
@Composable
fun SettingsDetailScreen(
    category: SettingsCategory,
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    var showImport by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    GlassScaffold(
        title = categoryTitle(category),
        onBack = onBack,
        overlay = {
            Box(Modifier.fillMaxSize().padding(bottom = 88.dp), contentAlignment = Alignment.BottomCenter) {
                SnackbarHost(snackbar)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            when (category) {
                SettingsCategory.KEEPALIVE -> KeepAliveContent(context)
                SettingsCategory.GENERAL -> GeneralContent(vm)
                SettingsCategory.DANMAKU -> DanmakuCategoryContent(vm, context)
                SettingsCategory.ACCESSIBILITY -> AccessibilityContent(context)
                SettingsCategory.APPEARANCE -> AppearanceContent(context)
                SettingsCategory.HOLIDAYS -> HolidaysContent(vm, context, showMessage)
                SettingsCategory.BACKUP -> BackupContent(vm, context, showMessage) { showImport = true }
                SettingsCategory.ABOUT -> AboutContent(vm, context, showMessage) { pendingUpdate = it }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showImport) ImportDialog(vm, context, showMessage) { showImport = false }
    pendingUpdate?.let { update -> UpdateDialog(update, context, showMessage) { pendingUpdate = null } }
}

@Composable
private fun AccessCard(accessGranted: Boolean, listenerConnected: Boolean, context: Context) {
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
}

@Composable
private fun KeepAliveContent(context: Context) {
    val notifOk = rememberNotificationAccessGranted()
    val connected = rememberListenerConnected()
    val batteryOk = com.iccyuan.hush.ui.common.rememberOnResume { com.iccyuan.hush.service.OemKeepAlive.isIgnoringBattery(context) }
    val overlayOk = com.iccyuan.hush.ui.common.rememberOnResume { DanmakuController.canShow(context) }

    InsetGroupedSection(
        footer = stringResource(R.string.keepalive_footer, com.iccyuan.hush.service.OemKeepAlive.oemLabel()),
    ) {
        // 1) 通知使用权：已授权但断开时标红（正是被省电策略杀掉的表现）。
        KeepAliveStep(
            title = stringResource(R.string.settings_access),
            done = if (notifOk && !connected) null else notifOk && connected,
            attention = notifOk && !connected,
        ) { runCatching { context.startActivity(NotificationAccess.settingsIntent()) } }
        HairlineDivider(startInset = 16.dp)
        // 2) 电池优化白名单（可检测）。
        KeepAliveStep(
            title = stringResource(R.string.keepalive_battery),
            done = batteryOk,
        ) { com.iccyuan.hush.service.OemKeepAlive.openBattery(context) }
        HairlineDivider(startInset = 16.dp)
        // 3) 自启动（无法检测状态，仅提供入口）。
        KeepAliveStep(title = stringResource(R.string.keepalive_autostart), done = null) {
            com.iccyuan.hush.service.OemKeepAlive.openAutostart(context)
        }
        HairlineDivider(startInset = 16.dp)
        // 4) 后台运行 / 省电策略（无法检测，入口）。
        KeepAliveStep(title = stringResource(R.string.keepalive_background), done = null) {
            com.iccyuan.hush.service.OemKeepAlive.openBackground(context)
        }
        HairlineDivider(startInset = 16.dp)
        // 5) 悬浮窗（弹幕显示所需，可检测）。
        KeepAliveStep(
            title = stringResource(R.string.grant_overlay),
            done = overlayOk,
        ) { runCatching { context.startActivity(DanmakuController.overlaySettingsIntent(context)) } }
    }
}

/** 保活向导的一步：done=true 已完成 / false 未完成 / null 无法检测（只给入口）；attention 标红。 */
@Composable
private fun KeepAliveStep(title: String, done: Boolean?, attention: Boolean = false, onClick: () -> Unit) {
    IOSRow(
        title = title,
        onClick = onClick,
        trailing = {
            when {
                attention -> ConnectionStatus(stringResource(R.string.status_disconnected), IOSColors.Red)
                done == true -> ConnectionStatus(stringResource(R.string.keepalive_done), IOSColors.Green)
                done == false -> ConnectionStatus(stringResource(R.string.keepalive_todo), IOSColors.Orange)
                else -> Chevron()
            }
        },
    )
}

@Composable
private fun GeneralContent(vm: SettingsViewModel) {
    val logActivity by vm.logActivity.collectAsStateWithLifecycle()
    val hideFromRecents by vm.hideFromRecents.collectAsStateWithLifecycle()
    InsetGroupedSection {
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
    }
}

@Composable
private fun DanmakuCategoryContent(vm: SettingsViewModel, context: Context) {
    val immersiveDanmaku by vm.immersiveDanmaku.collectAsStateWithLifecycle()
    val dmConfig by vm.danmakuConfig.collectAsStateWithLifecycle()
    InsetGroupedSection(footer = stringResource(R.string.settings_immersive_danmaku_desc)) {
        IOSRow(
            title = stringResource(R.string.settings_immersive_danmaku),
            icon = Icons.Filled.Subtitles,
            iconColor = IOSColors.Purple,
            trailing = { IOSSwitch(immersiveDanmaku) { vm.setImmersiveDanmaku(it) } },
        )
        if (immersiveDanmaku && !DanmakuController.canShow(context)) {
            HairlineDivider(startInset = 16.dp)
            IOSRow(
                title = stringResource(R.string.grant_overlay),
                icon = Icons.Filled.OpenInNew,
                iconColor = IOSColors.Orange,
                onClick = { context.startActivity(DanmakuController.overlaySettingsIntent(context)) },
            )
        }
    }
    DanmakuSettingsSection(
        config = dmConfig,
        onChange = { vm.setDanmakuConfig(it) },
        onPreview = {
            if (DanmakuController.canShow(context)) vm.previewDanmaku()
            else runCatching { context.startActivity(DanmakuController.overlaySettingsIntent(context)) }
        },
    )
}

@Composable
private fun AccessibilityContent(context: Context) {
    InsetGroupedSection(footer = stringResource(R.string.settings_accessibility_desc)) {
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
}

@Composable
private fun AppearanceContent(context: Context) {
    val currentLang by LanguageStore.language.collectAsStateWithLifecycle()
    val themeMode by ThemeStore.mode.collectAsStateWithLifecycle()
    InsetGroupedSection {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
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
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            IOSSegmented(
                options = LanguageStore.options,
                selected = currentLang,
                label = { langLabel(it) },
                onSelect = { lang -> LanguageStore.set(context, lang) },
            )
        }
    }
}

@Composable
private fun HolidaysContent(vm: SettingsViewModel, context: Context, showMessage: (String) -> Unit) {
    val holUpdated by vm.holidayUpdated.collectAsStateWithLifecycle()
    val holUpdating by vm.holidayUpdating.collectAsStateWithLifecycle()
    InsetGroupedSection(
        footer = stringResource(R.string.holiday_source_footer, HolidayProvider.SOURCE_NAME),
    ) {
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
}

@Composable
private fun BackupContent(
    vm: SettingsViewModel,
    context: Context,
    showMessage: (String) -> Unit,
    onImport: () -> Unit,
) {
    InsetGroupedSection {
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
            IOSTintedButton(text = stringResource(R.string.import_rules), onClick = onImport)
        }
    }
}

@Composable
private fun AboutContent(
    vm: SettingsViewModel,
    context: Context,
    showMessage: (String) -> Unit,
    onUpdateFound: (UpdateChecker.Result) -> Unit,
) {
    val updateChecking by vm.updateChecking.collectAsStateWithLifecycle()
    InsetGroupedSection {
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
                        result.hasUpdate -> onUpdateFound(result)
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
}

@Composable
private fun ImportDialog(
    vm: SettingsViewModel,
    context: Context,
    showMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                onDismiss()
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun UpdateDialog(
    update: UpdateChecker.Result,
    context: Context,
    showMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available)) },
        text = {
            Text(stringResource(R.string.update_available_msg, update.latestVersion, update.currentVersion))
        },
        confirmButton = {
            TextButton(onClick = {
                val url = update.downloadUrl
                if (ApkInstaller.isApk(url)) {
                    ApkInstaller.downloadAndInstall(context, url, update.latestVersion)
                    showMessage(context.getString(R.string.update_downloading))
                } else {
                    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
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
                onDismiss()
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
    )
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

/** 弹幕外观/行为设置（全局）：字号 / 速度 / 行数 / 颜色 / 背景不透明度 / 顶部偏移 + 预览。 */
@Composable
private fun DanmakuSettingsSection(
    config: DanmakuConfig,
    onChange: (DanmakuConfig) -> Unit,
    onPreview: () -> Unit,
) {
    InsetGroupedSection(
        header = stringResource(R.string.settings_danmaku),
        footer = stringResource(R.string.settings_danmaku_desc),
    ) {
        DanmakuLabeled(stringResource(R.string.danmaku_size)) {
            IOSSegmented(
                options = DanmakuConfig.SIZES,
                selected = config.fontSizeSp,
                label = {
                    stringResource(
                        when {
                            it <= DanmakuConfig.SIZES.first() -> R.string.size_small
                            it >= DanmakuConfig.SIZES.last() -> R.string.size_large
                            else -> R.string.size_medium
                        }
                    )
                },
                onSelect = { onChange(config.copy(fontSizeSp = it)) },
            )
        }
        HairlineDivider(startInset = 16.dp)
        DanmakuLabeled(stringResource(R.string.danmaku_speed)) {
            IOSSegmented(
                options = DanmakuConfig.SPEEDS,
                selected = config.durationMs,
                label = {
                    stringResource(
                        when {
                            it >= DanmakuConfig.SPEEDS.first() -> R.string.speed_slow
                            it <= DanmakuConfig.SPEEDS.last() -> R.string.speed_fast
                            else -> R.string.speed_normal
                        }
                    )
                },
                onSelect = { onChange(config.copy(durationMs = it)) },
            )
        }
        HairlineDivider(startInset = 16.dp)
        DanmakuLabeled(stringResource(R.string.danmaku_rows)) {
            IOSSegmented(
                options = DanmakuConfig.ROWS_OPTIONS,
                selected = config.rows.coerceIn(2, 5),
                label = { it.toString() },
                onSelect = { onChange(config.copy(rows = it)) },
            )
        }
        HairlineDivider(startInset = 16.dp)
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.danmaku_color), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DanmakuConfig.COLORS.forEach { c ->
                    val sel = c == config.color
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(
                                width = if (sel) 3.dp else 1.dp,
                                color = if (sel) MaterialTheme.colorScheme.primary else Color(0x33000000),
                                shape = CircleShape,
                            )
                            .clickable { onChange(config.copy(color = c)) },
                    )
                }
            }
        }
        HairlineDivider(startInset = 16.dp)
        Column(Modifier.padding(16.dp)) {
            var a by remember(config.bgAlpha) { mutableStateOf(config.bgAlpha.toFloat()) }
            Text(
                stringResource(R.string.danmaku_bg_opacity, (a * 100 / 255).toInt()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            IOSSlider(
                value = a,
                valueRange = 0f..255f,
                onValueChange = { a = it },
                onValueChangeFinished = { onChange(config.copy(bgAlpha = a.toInt())) },
            )
        }
        HairlineDivider(startInset = 16.dp)
        Column(Modifier.padding(16.dp)) {
            var off by remember(config.topOffsetDp) { mutableStateOf(config.topOffsetDp) }
            Text(
                stringResource(R.string.danmaku_top_offset, off.toInt()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            IOSSlider(
                value = off,
                valueRange = 0f..140f,
                onValueChange = { off = it },
                onValueChangeFinished = { onChange(config.copy(topOffsetDp = off)) },
            )
        }
        HairlineDivider(startInset = 16.dp)
        Column(Modifier.padding(16.dp)) {
            IOSTintedButton(text = stringResource(R.string.danmaku_preview), onClick = onPreview)
        }
    }
}

/** 极简 iOS 风格滑块：细圆角轨道 + 着色填充 + 白色圆点滑块（点按或拖动均可）。 */
@Composable
private fun IOSSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val fillColor = MaterialTheme.colorScheme.primary
    val thumb = 24.dp
    // pointerInput 的块只在 key 变化时重启；valueRange 恒定，故用 rememberUpdatedState 让手势
    // 始终调用**最新**的回调（否则会捕获首次组合时的旧回调，导致拖动本滑块时把其他设置一并回退）。
    val start = valueRange.start
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    onChange(start + (p.x / size.width).coerceIn(0f, 1f) * span)
                    onFinished?.invoke()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { onFinished?.invoke() }) { change, _ ->
                    change.consume()
                    onChange(start + (change.position.x / size.width).coerceIn(0f, 1f) * span)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(trackColor))
        Box(Modifier.fillMaxWidth(fraction).height(3.dp).clip(CircleShape).background(fillColor))
        Box(
            Modifier
                .offset(x = (maxWidth - thumb) * fraction)
                .size(thumb)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun DanmakuLabeled(label: String, control: @Composable () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        control()
    }
}


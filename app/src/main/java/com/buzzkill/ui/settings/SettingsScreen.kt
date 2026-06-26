@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.buzzkill.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buzzkill.R
import com.buzzkill.data.LanguageStore
import com.buzzkill.data.ThemeStore
import com.buzzkill.service.NotificationAccess
import com.buzzkill.ui.components.GlassScaffold
import com.buzzkill.ui.components.HairlineDivider
import com.buzzkill.ui.components.IOSRow
import com.buzzkill.ui.components.IOSSegmented
import com.buzzkill.ui.components.IOSSwitch
import com.buzzkill.ui.components.IOSTintedButton
import com.buzzkill.ui.components.InsetGroupedSection
import com.buzzkill.ui.common.rememberNotificationAccessGranted
import com.buzzkill.ui.findActivity

@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    vm: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val masterEnabled by vm.masterEnabled.collectAsStateWithLifecycle()
    val logActivity by vm.logActivity.collectAsStateWithLifecycle()
    val accessGranted = rememberNotificationAccessGranted()
    var showImport by remember { mutableStateOf(false) }
    val currentLang by LanguageStore.language.collectAsStateWithLifecycle()

    GlassScaffold(title = stringResource(R.string.settings), onBack = onBack, bottomBar = bottomBar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // General
            InsetGroupedSection(header = stringResource(R.string.settings_general)) {
                IOSRow(
                    title = stringResource(R.string.settings_master),
                    icon = Icons.Filled.Bolt,
                    iconColor = Color(0xFF34C759),
                    trailing = { IOSSwitch(masterEnabled) { vm.setMasterEnabled(it) } },
                )
                HairlineDivider(startInset = 16.dp)
                IOSRow(
                    title = stringResource(R.string.settings_log),
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    iconColor = Color(0xFF007AFF),
                    trailing = { IOSSwitch(logActivity) { vm.setLogActivity(it) } },
                )
            }

            // Appearance: theme + language
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

            // Holidays (China) — official-derived data, fetched & cached locally.
            val holUpdated by vm.holidayUpdated.collectAsStateWithLifecycle()
            val holUpdating by vm.holidayUpdating.collectAsStateWithLifecycle()
            InsetGroupedSection(header = stringResource(R.string.settings_holidays)) {
                IOSRow(
                    title = stringResource(R.string.settings_holidays),
                    subtitle = if (holUpdated > 0)
                        stringResource(R.string.holiday_last_updated, formatTime(holUpdated))
                    else stringResource(R.string.holiday_never),
                    icon = Icons.Filled.CalendarMonth,
                    iconColor = Color(0xFFFF3B30),
                )
                HairlineDivider(startInset = 16.dp)
                Column(Modifier.padding(16.dp)) {
                    IOSTintedButton(
                        text = if (holUpdating) "…" else stringResource(R.string.holiday_update),
                        onClick = {
                            if (!holUpdating) vm.updateHolidays { ok ->
                                toast(
                                    context,
                                    context.getString(
                                        if (ok) R.string.holiday_update_done else R.string.holiday_update_failed
                                    ),
                                )
                            }
                        },
                    )
                }
            }

            // Notification access
            InsetGroupedSection(header = stringResource(R.string.settings_access)) {
                IOSRow(
                    title = stringResource(R.string.settings_access),
                    subtitle = stringResource(
                        if (accessGranted) R.string.access_granted else R.string.access_not_granted
                    ),
                )
                HairlineDivider(startInset = 16.dp)
                Column(Modifier.padding(16.dp)) {
                    IOSTintedButton(
                        text = stringResource(R.string.open_access_settings),
                        onClick = { context.startActivity(NotificationAccess.settingsIntent()) },
                    )
                }
            }

            // Backup
            InsetGroupedSection(header = stringResource(R.string.settings_backup)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IOSTintedButton(
                        text = stringResource(R.string.export_rules),
                        onClick = {
                            vm.exportRules { json ->
                                copyToClipboard(context, json)
                                toast(context, context.getString(R.string.export_copied))
                            }
                        },
                    )
                    IOSTintedButton(
                        text = stringResource(R.string.import_rules),
                        onClick = { showImport = true },
                    )
                }
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
                        toast(
                            context,
                            if (count >= 0) context.getString(R.string.import_done, count)
                            else context.getString(R.string.import_failed),
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
    cm.setPrimaryClip(ClipData.newPlainText("BuzzKill rules", text))
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

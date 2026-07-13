package com.iccyuan.hush.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iccyuan.hush.R
import com.iccyuan.hush.ui.components.GlassDialog
import com.iccyuan.hush.ui.components.GlassScaffold
import com.iccyuan.hush.ui.components.HairlineDivider
import com.iccyuan.hush.ui.components.IOSRow
import com.iccyuan.hush.ui.components.InsetGroupedSection
import com.iccyuan.hush.ui.theme.IOSColors

@Composable
fun InsightsScreen(
    onBack: (() -> Unit)? = null,
    vm: InsightsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    GlassScaffold(
        title = stringResource(R.string.insights_title),
        onBack = onBack,
        actions = {
            // 清空会连带删掉通知记录并把规则命中次数清零——不可撤销，所以要先确认。
            if (state.total > 0 || state.topRules.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.history_clear)) }
            }
        },
        overlay = {
            if (confirmClear) {
                ClearStatsDialog(
                    onConfirm = {
                        confirmClear = false
                        vm.clearStats()
                    },
                    onDismiss = { confirmClear = false },
                )
            }
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

            // 概览
            InsetGroupedSection {
                IOSRow(
                    title = stringResource(R.string.insights_total),
                    icon = Icons.Filled.NotificationsActive,
                    iconColor = IOSColors.Blue,
                    trailing = { CountText(state.total) },
                )
                HairlineDivider(startInset = 16.dp)
                IOSRow(
                    title = stringResource(R.string.insights_matched),
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    iconColor = IOSColors.Green,
                    trailing = { CountText(state.matched) },
                )
            }

            // 最吵的应用
            InsetGroupedSection(header = stringResource(R.string.insights_top_apps)) {
                if (state.topApps.isEmpty()) {
                    IOSRow(title = stringResource(R.string.insights_none))
                } else {
                    state.topApps.forEachIndexed { i, app ->
                        if (i > 0) HairlineDivider(startInset = 16.dp)
                        IOSRow(
                            title = app.appName.ifBlank { app.packageName },
                            trailing = { CountText(app.count) },
                        )
                    }
                }
            }

            // 命中最多的规则
            InsetGroupedSection(header = stringResource(R.string.insights_top_rules)) {
                if (state.topRules.isEmpty()) {
                    IOSRow(title = stringResource(R.string.insights_none))
                } else {
                    state.topRules.forEachIndexed { i, rule ->
                        if (i > 0) HairlineDivider(startInset = 16.dp)
                        IOSRow(
                            title = rule.name,
                            subtitle = stringResource(R.string.insights_fires, rule.fireCount.toInt()),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 清空统计的 iOS 风格磨砂确认框（同规则删除确认，见 RuleListScreen 的 DeleteRuleDialog）。 */
@Composable
private fun ClearStatsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.insights_clear_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.insights_clear_msg),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.history_clear), color = IOSColors.Red, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CountText(count: Int) {
    Text(
        count.toString(),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

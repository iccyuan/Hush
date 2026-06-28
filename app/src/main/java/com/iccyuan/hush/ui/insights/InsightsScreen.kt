package com.iccyuan.hush.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iccyuan.hush.R
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

    GlassScaffold(title = stringResource(R.string.insights_title), onBack = onBack) { padding ->
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

@Composable
private fun CountText(count: Int) {
    Text(
        count.toString(),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

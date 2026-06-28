@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iccyuan.hush.ui.list
import com.iccyuan.hush.data.HolidayProvider
import com.iccyuan.hush.ui.components.IOSFilledButton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iccyuan.hush.R
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.service.NotificationAccess
import com.iccyuan.hush.ui.Localize
import com.iccyuan.hush.ui.common.rememberNotificationAccessGranted
import com.iccyuan.hush.ui.components.GlassDialog
import com.iccyuan.hush.ui.components.GlassScaffold
import com.iccyuan.hush.ui.components.HairlineDivider
import com.iccyuan.hush.ui.components.IOSRow
import com.iccyuan.hush.ui.components.IOSSwitch
import com.iccyuan.hush.ui.components.InsetGroupedSection
import com.iccyuan.hush.ui.components.cardFrost
import com.iccyuan.hush.ui.theme.Alpha
import com.iccyuan.hush.ui.theme.IOSColors

@Composable
fun RuleListScreen(
    onOpenRule: (Long) -> Unit,
    onNewRule: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    vm: RuleListViewModel = viewModel(),
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val accessGranted = rememberNotificationAccessGranted()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Rule?>(null) }

    GlassScaffold(
        title = stringResource(R.string.rules_title),
        bottomBar = bottomBar,
        overlay = {
            pendingDelete?.let { rule ->
                DeleteRuleDialog(
                    onConfirm = { vm.delete(rule); pendingDelete = null },
                    onDismiss = { pendingDelete = null },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            if (!accessGranted) {
                item {
                    AccessBanner(onGrant = {
                        context.startActivity(NotificationAccess.settingsIntent())
                    })
                }
            }

            if (rules.isEmpty()) {
                item { EmptyState() }
            } else {
                item {
                    InsetGroupedSection {
                        rules.forEachIndexed { index, rule ->
                            SwipeableRuleRow(
                                rule = rule,
                                onClick = { onOpenRule(rule.id) },
                                onToggle = { vm.setEnabled(rule, it) },
                                onRequestDelete = { pendingDelete = rule },
                            )
                            if (index < rules.lastIndex) HairlineDivider(startInset = 16.dp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SwipeableRuleRow(
    rule: Rule,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    // 始终返回 false，使该行回弹复位；真正的删除仅在确认对话框之后才执行。
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) onRequestDelete()
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(IOSColors.Red)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = Color.White)
            }
        },
    ) {
        // 使用磨砂玻璃表面（而非纯色），让卡片的模糊效果透出来，同时
        // 在该行被滑动之前仍完全遮住红色的删除背景。
        Box(Modifier.cardFrost()) {
            IOSRow(
                title = rule.name,
                subtitle = summarize(rule),
                onClick = onClick,
                trailing = { IOSSwitch(checked = rule.enabled, onCheckedChange = onToggle) },
            )
        }
    }
}


/** 用于删除规则的 iOS 风格磨砂确认框（替代 Material 的 AlertDialog）。 */
@Composable
private fun DeleteRuleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.delete_rule_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.delete_rule_msg),
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
                Text(stringResource(R.string.delete), color = IOSColors.Red, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun summarize(rule: Rule): String {
    val apps = if (rule.appPackages.isEmpty()) stringResource(R.string.summary_all_apps)
    else stringResource(R.string.summary_n_apps, rule.appPackages.size)
    val triggers = if (rule.triggers.isEmpty()) stringResource(R.string.summary_any_notification)
    else stringResource(R.string.summary_n_triggers, rule.triggers.size)
    val actions = stringResource(R.string.summary_n_actions, rule.actions.size)
    return "$apps · $triggers · $actions"
}

@Composable
private fun AccessBanner(onGrant: () -> Unit) {
    InsetGroupedSection {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.access_required_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(R.string.access_required_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            IOSFilledButton(
                text = stringResource(R.string.grant_access),
                onClick = onGrant,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).height(320.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.no_rules_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.no_rules_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

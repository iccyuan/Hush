@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.buzzkill.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buzzkill.R
import com.buzzkill.data.model.Rule
import com.buzzkill.service.NotificationAccess
import com.buzzkill.ui.Localize
import com.buzzkill.ui.common.rememberNotificationAccessGranted
import com.buzzkill.ui.components.GlassScaffold
import com.buzzkill.ui.components.HairlineDivider
import com.buzzkill.ui.components.IOSRow
import com.buzzkill.ui.components.IOSSwitch
import com.buzzkill.ui.components.InsetGroupedSection
import com.buzzkill.ui.components.cardFrost

@Composable
fun RuleListScreen(
    onOpenRule: (Long) -> Unit,
    onNewRule: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    vm: RuleListViewModel = viewModel(),
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val accessGranted = rememberNotificationAccessGranted()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Rule?>(null) }

    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_rule_title)) },
            text = { Text(stringResource(R.string.delete_rule_msg)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(rule); pendingDelete = null }) {
                    Text(stringResource(R.string.delete), color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    GlassScaffold(
        title = stringResource(R.string.rules_title),
        actions = {
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Default.History, contentDescription = stringResource(R.string.nav_history))
            }
            IconButton(onClick = onNewRule) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_rule))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
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
    // Always returns false so the row snaps back; the actual delete happens only after
    // the confirmation dialog.
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
                    .background(Color(0xFFFF3B30))
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = Color.White)
            }
        },
    ) {
        // Frosted-glass surface (not flat colour) so the card blur shows through, while
        // still fully covering the red delete background until the row is swiped.
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
            com.buzzkill.ui.components.IOSFilledButton(
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

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.buzzkill.ui.list

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Work
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
import com.buzzkill.R
import com.buzzkill.data.model.Rule
import com.buzzkill.service.NotificationAccess
import com.buzzkill.ui.Localize
import com.buzzkill.ui.common.rememberNotificationAccessGranted
import com.buzzkill.ui.components.GlassDialog
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

            item { TodayOverrideCard() }

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

/** Quick "today is rest / today is work" toggles that override today's day-type for
 *  the holiday condition. Tapping the active one again clears the override. */
@Composable
private fun TodayOverrideCard() {
    val context = LocalContext.current
    var override by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        override = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.buzzkill.data.HolidayProvider.todayOverride(context)
        }
    }
    InsetGroupedSection(header = stringResource(R.string.today_header)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TodayButton(
                label = stringResource(R.string.today_rest),
                icon = Icons.Outlined.Weekend,
                color = Color(0xFFFF3B30),
                active = override == com.buzzkill.data.HolidayProvider.OVERRIDE_REST,
                modifier = Modifier.weight(1f),
            ) {
                val next = if (override == com.buzzkill.data.HolidayProvider.OVERRIDE_REST) null
                else com.buzzkill.data.HolidayProvider.OVERRIDE_REST
                com.buzzkill.data.HolidayProvider.setTodayOverride(context, next)
                override = next
            }
            TodayButton(
                label = stringResource(R.string.today_work),
                icon = Icons.Outlined.Work,
                color = Color(0xFFFF9500),
                active = override == com.buzzkill.data.HolidayProvider.OVERRIDE_WORK,
                modifier = Modifier.weight(1f),
            ) {
                val next = if (override == com.buzzkill.data.HolidayProvider.OVERRIDE_WORK) null
                else com.buzzkill.data.HolidayProvider.OVERRIDE_WORK
                com.buzzkill.data.HolidayProvider.setTodayOverride(context, next)
                override = next
            }
        }
    }
}

@Composable
private fun TodayButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Always use the accent colour (not black) so the buttons stay light and uncluttered.
    val bg = color.copy(alpha = if (active) 0.18f else 0.08f)
    val fg = color
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/** iOS-style frosted confirmation for deleting a rule (replaces the Material AlertDialog). */
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
                Text(stringResource(R.string.delete), color = Color(0xFFFF3B30), fontWeight = FontWeight.SemiBold)
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

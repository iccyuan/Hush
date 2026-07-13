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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.iccyuan.hush.ui.components.ListRowPlacementSpec
import com.iccyuan.hush.ui.components.groupSegmentShape
import com.iccyuan.hush.ui.components.rowFrost
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
        // 不再用 spacedBy 排间距：规则行现在是逐行的 lazy item（拼成一张卡），行间不能有缝，
        // 区块之间的间距改由各 item 自己的 padding 表达。
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item(key = "top_spacer", contentType = "spacer") { Spacer(Modifier.height(4.dp)) }

            if (!accessGranted) {
                item(key = "access", contentType = "banner") {
                    Box(Modifier.padding(top = 18.dp)) {
                        AccessBanner(onGrant = {
                            context.startActivity(NotificationAccess.settingsIntent())
                        })
                    }
                }
            }

            if (rules.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Box(Modifier.padding(top = 18.dp)) { EmptyState() }
                }
            } else {
                // 每条规则各自是一个 lazy item（而非整组塞进一个 item）：滚动按行增量组合，
                // 行底色用 rowFrost（静态、无逐行实时模糊），卡片观感由首尾行圆角拼出。
                itemsIndexed(rules, key = { _, r -> r.id }, contentType = { _, _ -> "rule" }) { i, rule ->
                    Column(
                        Modifier
                            .animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = ListRowPlacementSpec,
                            )
                            .padding(start = 16.dp, end = 16.dp, top = if (i == 0) 18.dp else 0.dp)
                            .clip(groupSegmentShape(i, rules.size))
                            .rowFrost(),
                    ) {
                        if (i > 0) HairlineDivider(startInset = 16.dp)
                        SwipeableRuleRow(
                            rule = rule,
                            onClick = { onOpenRule(rule.id) },
                            onToggle = { vm.setEnabled(rule, it) },
                            onRequestDelete = { pendingDelete = rule },
                        )
                    }
                }
            }
            item(key = "bottom_spacer", contentType = "spacer") { Spacer(Modifier.height(24.dp)) }
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
            // 行底色是半透明的 rowFrost（由所在分组行提供），红色背景若一直垫在整行底下会透出来。
            // 改为与前景同步从右缘滑入：位移在绘制阶段读（graphicsLayer），拖动不触发重组。
            // 图标锚在滑入条带的前缘（盒子的 start 侧）——盒子整体右移了一个行宽，锚在 end 侧会跑出屏幕。
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = size.width + runCatching { state.requireOffset() }.getOrDefault(0f)
                    }
                    .background(IOSColors.Red)
                    .padding(start = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = Color.White)
            }
        },
    ) {
        IOSRow(
            title = rule.name,
            subtitle = summarize(rule),
            onClick = onClick,
            trailing = { IOSSwitch(checked = rule.enabled, onCheckedChange = onToggle) },
        )
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

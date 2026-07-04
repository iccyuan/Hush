package com.iccyuan.hush.ui.nav

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.iccyuan.hush.ui.editor.RuleEditorScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iccyuan.hush.R
import com.iccyuan.hush.ui.history.HistoryScreen
import com.iccyuan.hush.ui.list.RuleListScreen
import com.iccyuan.hush.ui.settings.SettingsScreen

enum class MainTab { RULES, HISTORY, ADD, SETTINGS }

/**
 * 标签式主页：规则 / 历史 / 添加 / 设置 共用一个磨砂的底部标签栏。
 * "添加"是一个真正的标签页，承载新建规则的编辑器（不会有突兀的页面推入）；
 * 每次打开它都会获得一个全新的编辑器会话。
 *
 * （标签之间故意不使用滑动翻页：规则和历史列表使用横向滑动删除，
 * 它无法与标签翻页的滑动手势区分开来。）
 */
@Composable
fun MainScaffold(
    onOpenRule: (Long) -> Unit,
    onOpenInsights: () -> Unit = {},
    onOpenSettingsCategory: (com.iccyuan.hush.ui.settings.SettingsCategory) -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.RULES) }
    var addSession by remember { mutableIntStateOf(0) }
    val bar: @Composable () -> Unit = {
        BottomTabBar(
            current = tab,
            onSelect = { selected ->
                if (selected == MainTab.ADD && tab != MainTab.ADD) addSession++
                tab = selected
            },
        )
    }
    // 标签切换用淡入淡出过渡（二级页由 NavHost 负责 iOS 滑动动画）。底部栏在各页内相同，
    // 交叉淡化时视觉上重叠不变，仅内容与选中态平滑切换。
    androidx.compose.animation.AnimatedContent(
        targetState = tab,
        transitionSpec = {
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))
                .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)))
        },
        label = "tab",
    ) { t ->
        when (t) {
            MainTab.RULES -> RuleListScreen(
                onOpenRule = onOpenRule,
                onNewRule = { addSession++; tab = MainTab.ADD },
                bottomBar = bar,
            )
            MainTab.HISTORY -> HistoryScreen(bottomBar = bar, onCreateRule = onOpenRule)
            MainTab.ADD -> androidx.compose.runtime.key(addSession) {
                RuleEditorScreen(
                    ruleId = 0L,
                    onDone = { tab = MainTab.RULES },
                    bottomBar = bar,
                    vm = androidx.lifecycle.viewmodel.compose.viewModel(key = "new-rule-$addSession"),
                )
            }
            MainTab.SETTINGS -> SettingsScreen(
                bottomBar = bar,
                onOpenInsights = onOpenInsights,
                onOpenCategory = onOpenSettingsCategory,
            )
        }
    }
}

@Composable
private fun BottomTabBar(current: MainTab, onSelect: (MainTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem(
            Icons.AutoMirrored.Filled.ListAlt, stringResource(R.string.rules_title),
            current == MainTab.RULES, Modifier.weight(1f),
        ) { onSelect(MainTab.RULES) }
        TabItem(
            Icons.Filled.History, stringResource(R.string.nav_history),
            current == MainTab.HISTORY, Modifier.weight(1f),
        ) { onSelect(MainTab.HISTORY) }
        TabItem(
            Icons.Filled.AddCircleOutline, stringResource(R.string.tab_add),
            current == MainTab.ADD, Modifier.weight(1f),
        ) { onSelect(MainTab.ADD) }
        TabItem(
            Icons.Filled.Settings, stringResource(R.string.settings),
            current == MainTab.SETTINGS, Modifier.weight(1f),
        ) { onSelect(MainTab.SETTINGS) }
    }
}

@Composable
private fun TabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val noRipple = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = noRipple, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

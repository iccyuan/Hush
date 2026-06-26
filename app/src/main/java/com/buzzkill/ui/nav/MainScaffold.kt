package com.buzzkill.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buzzkill.R
import com.buzzkill.ui.history.HistoryScreen
import com.buzzkill.ui.list.RuleListScreen
import com.buzzkill.ui.settings.SettingsScreen

enum class MainTab { RULES, HISTORY, SETTINGS }

/**
 * The tabbed home: Rules / History / Settings share a frosted bottom tab bar, with a
 * prominent Add action between History and Settings that pushes the rule editor.
 */
@Composable
fun MainScaffold(onOpenRule: (Long) -> Unit, onNewRule: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(MainTab.RULES) }
    val bar: @Composable () -> Unit = {
        BottomTabBar(current = tab, onSelect = { tab = it }, onAdd = onNewRule)
    }
    when (tab) {
        MainTab.RULES -> RuleListScreen(onOpenRule = onOpenRule, onNewRule = onNewRule, bottomBar = bar)
        MainTab.HISTORY -> HistoryScreen(bottomBar = bar)
        MainTab.SETTINGS -> SettingsScreen(bottomBar = bar)
    }
}

@Composable
private fun BottomTabBar(current: MainTab, onSelect: (MainTab) -> Unit, onAdd: () -> Unit) {
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
        AddTab(Modifier.weight(1f), onAdd)
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

@Composable
private fun AddTab(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val noRipple = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = noRipple, indication = null, onClick = onAdd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.tab_add), style = MaterialTheme.typography.labelSmall, color = primary)
    }
}

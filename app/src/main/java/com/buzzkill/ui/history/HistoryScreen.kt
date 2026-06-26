@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.buzzkill.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buzzkill.R
import com.buzzkill.data.model.NotificationLog
import com.buzzkill.ui.components.GlassScaffold
import com.buzzkill.ui.components.HairlineDivider
import com.buzzkill.ui.components.InsetGroupedSection
import com.buzzkill.ui.components.cardFrost
import com.buzzkill.ui.theme.Alpha
import com.buzzkill.ui.theme.IOSColors
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class Grouping { DAY, WEEK }

@Composable
fun HistoryScreen(
    onBack: (() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    vm: HistoryViewModel = viewModel(),
) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    var grouping by remember { mutableStateOf(Grouping.DAY) }
    var selectedApp by remember { mutableStateOf<String?>(null) }

    val filtered = remember(logs, selectedApp) {
        if (selectedApp == null) logs else logs.filter { it.packageName == selectedApp }
    }
    // 日志中出现过的应用，按出现频率从高到低排列（这样即使应用很多，
    // 常用应用也会排在可横向滚动的过滤行靠前的位置）。
    val appList = remember(logs) {
        logs.groupingBy { it.packageName }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { entry -> entry.key to (logs.first { it.packageName == entry.key }.appName) }
    }

    GlassScaffold(
        title = stringResource(R.string.nav_history),
        onBack = onBack,
        bottomBar = bottomBar,
        actions = {
            if (logs.isNotEmpty()) {
                TextButton(onClick = { vm.clear() }) { Text(stringResource(R.string.history_clear)) }
            }
        },
    ) { padding ->
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) { EmptyHistory() }
            return@GlassScaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 固定表头：统计 + 分组切换 + 应用过滤始终保持固定，这样无需
            // 滚回顶部就能更改它们。
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(4.dp))
                StatsCard(filtered)
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.buzzkill.ui.components.IOSSegmented(
                        options = listOf(Grouping.DAY, Grouping.WEEK),
                        selected = grouping,
                        label = { stringResource(if (it == Grouping.DAY) R.string.group_by_day else R.string.group_by_week) },
                        onSelect = { grouping = it },
                    )
                }
                AppFilterChips(appList, selectedApp) { selectedApp = it }
            }
            Spacer(Modifier.height(12.dp))

            // 只有分组后的日志列表会滚动。
            val groups = groupLogs(filtered, grouping)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            groups.forEach { (header, items) ->
                item {
                    Text(
                        header,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp, top = 4.dp),
                    )
                }
                item {
                    InsetGroupedSection {
                        items.forEachIndexed { i, log ->
                            if (i > 0) HairlineDivider(startInset = 16.dp)
                            SwipeableLogRow(log = log, onDelete = { vm.delete(log) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun StatsCard(logs: List<NotificationLog>) {
    val weekdays = stringArrayResource(R.array.weekday_full)
    val hours = IntArray(24)
    val days = IntArray(7)
    val cal = Calendar.getInstance()
    logs.forEach {
        cal.timeInMillis = it.time
        hours[cal.get(Calendar.HOUR_OF_DAY)]++
        days[((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7)]++ // ISO 0=周一
    }
    val peakHour = hours.indices.maxByOrNull { hours[it] } ?: 0
    val busiestDay = days.indices.maxByOrNull { days[it] } ?: 0
    val maxHour = (hours.maxOrNull() ?: 1).coerceAtLeast(1)

    InsetGroupedSection {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stat_total, logs.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    "${stringResource(R.string.stat_peak_hour)}: %02d:00".format(peakHour),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${stringResource(R.string.stat_busiest_day)}: ${weekdays.getOrElse(busiestDay) { "" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            // 24 小时直方图。
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                for (h in 0..23) {
                    val frac = hours[h].toFloat() / maxHour
                    Box(
                        Modifier
                            .weight(1f)
                            .height((4 + frac * 44).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (h == peakHour) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppFilterChips(
    apps: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    // 单行可横向滚动，无论有多少应用产生过通知，都能让过滤行保持紧凑。
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Chip(stringResource(R.string.filter_all_apps), selected == null) { onSelect(null) } }
        items(apps, key = { it.first }) { (pkg, name) ->
            Chip(name, selected == pkg) { onSelect(pkg) }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) primary else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一条日志行，向左滑动可露出一个可点击的删除按钮。 */
@Composable
private fun SwipeableLogRow(log: NotificationLog, onDelete: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val revealPx = with(density) { 80.dp.toPx() }
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }

    Box(Modifier.fillMaxWidth()) {
        // 在行后方的尾部边缘露出删除按钮。
        Box(
            Modifier.matchParentSize().background(IOSColors.Red),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .clickable {
                        scope.launch { offsetX.animateTo(0f) }
                        onDelete()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = Color.White,
                )
                Text(stringResource(R.string.delete), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        // 前景（磨砂效果，关闭时遮住红色按钮），拖动时随之滑动。
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.toInt(), 0) }
                .fillMaxWidth()
                .cardFrost()
                .draggable(
                    orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                        scope.launch { offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f)) }
                    },
                    onDragStopped = {
                        scope.launch {
                            offsetX.animateTo(if (offsetX.value < -revealPx / 2) -revealPx else 0f)
                        }
                    },
                ),
        ) {
            LogRow(log)
        }
    }
}

@Composable
private fun LogRow(log: NotificationLog) {
    // 以 id 作为 key，这样当列表发生变化（例如删除后）时，展开状态能跟随其对应的日志。
    var expanded by remember(log.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        log.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        timeOf(log.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!expanded) {
                    val sub = listOf(log.title, log.text).filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            OutcomeBadge(log)
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                if (log.title.isNotBlank()) DetailLine(stringResource(R.string.detail_title), log.title)
                if (log.text.isNotBlank()) DetailLine(stringResource(R.string.detail_text), log.text)
                DetailLine(stringResource(R.string.detail_package), log.packageName)
                DetailLine(stringResource(R.string.detail_time), fullTimeOf(log.time))
                if (log.matched) {
                    val count = log.firedRuleIds.split(",").count { it.isNotBlank() }
                    DetailLine(stringResource(R.string.detail_rules), count.toString())
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OutcomeBadge(log: NotificationLog) {
    if (!log.matched) return
    val (label, color) = when (log.outcome) {
        NotificationLog.OUTCOME_MODIFIED -> stringResource(R.string.outcome_modified) to IOSColors.Blue
        NotificationLog.OUTCOME_DISCARDED -> stringResource(R.string.outcome_discarded) to IOSColors.Red
        NotificationLog.OUTCOME_DISMISSED -> stringResource(R.string.outcome_dismissed) to IOSColors.Orange
        NotificationLog.OUTCOME_SNOOZED -> stringResource(R.string.outcome_snoozed) to IOSColors.Indigo
        else -> stringResource(R.string.outcome_matched) to IOSColors.Green
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = Alpha.Badge))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        Modifier.fillMaxSize().height(360.dp).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun timeOf(t: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(t))

private fun fullTimeOf(t: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(t))

private fun groupLogs(logs: List<NotificationLog>, grouping: Grouping): List<Pair<String, List<NotificationLog>>> {
    val cal = Calendar.getInstance()
    val dayFmt = java.text.SimpleDateFormat("yyyy-MM-dd EEE", java.util.Locale.getDefault())
    return logs.groupBy { log ->
        cal.timeInMillis = log.time
        when (grouping) {
            Grouping.DAY -> dayFmt.format(java.util.Date(log.time))
            Grouping.WEEK -> {
                val year = cal.get(Calendar.YEAR)
                val week = cal.get(Calendar.WEEK_OF_YEAR)
                "%d · W%02d".format(year, week)
            }
        }
    }.toList()
}

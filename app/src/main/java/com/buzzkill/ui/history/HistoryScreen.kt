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
    // Apps that appear in the log, most frequent first (so the common ones lead the
    // horizontally-scrollable filter row even when there are many apps).
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
            // Fixed header: stats + grouping toggle + app filter stay pinned so you never
            // have to scroll back up to change them.
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

            // Only the grouped log list scrolls.
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
        days[((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7)]++ // ISO 0=Mon
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
            // 24-hour histogram.
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
    // A single horizontally-scrollable row keeps the filter compact no matter how many
    // apps have produced notifications.
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

/** A log row that slides left to reveal a tappable delete button. */
@Composable
private fun SwipeableLogRow(log: NotificationLog, onDelete: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val revealPx = with(density) { 80.dp.toPx() }
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }

    Box(Modifier.fillMaxWidth()) {
        // Delete button revealed behind the row on the trailing edge.
        Box(
            Modifier.matchParentSize().background(Color(0xFFFF3B30)),
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
        // Foreground (frosted so it hides the red button when closed), slides on drag.
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
    // Keyed by id so the expanded row follows its log when the list changes (e.g. after a delete).
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
        NotificationLog.OUTCOME_MODIFIED -> stringResource(R.string.outcome_modified) to Color(0xFF007AFF)
        NotificationLog.OUTCOME_DISCARDED -> stringResource(R.string.outcome_discarded) to Color(0xFFFF3B30)
        NotificationLog.OUTCOME_DISMISSED -> stringResource(R.string.outcome_dismissed) to Color(0xFFFF9500)
        NotificationLog.OUTCOME_SNOOZED -> stringResource(R.string.outcome_snoozed) to Color(0xFF5856D6)
        else -> stringResource(R.string.outcome_matched) to Color(0xFF34C759)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
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

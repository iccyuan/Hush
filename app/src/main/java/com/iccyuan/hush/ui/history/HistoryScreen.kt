@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.iccyuan.hush.ui.history
import com.iccyuan.hush.ui.components.IOSSegmented

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iccyuan.hush.R
import com.iccyuan.hush.data.model.NotificationLog
import com.iccyuan.hush.ui.components.GlassScaffold
import com.iccyuan.hush.ui.components.HairlineDivider
import com.iccyuan.hush.ui.components.InsetGroupedSection
import com.iccyuan.hush.ui.components.cardFrost
import com.iccyuan.hush.ui.components.iosPressable
import com.iccyuan.hush.ui.theme.Alpha
import com.iccyuan.hush.ui.theme.IOSColors
import com.iccyuan.hush.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private enum class Grouping { DAY, WEEK }

// 统一集中的数值常量，避免散落硬编码。
private const val ICON_PX = 48               // 历史行应用图标解析像素
private const val LOAD_MORE_AHEAD = 4        // 距列表末尾多少项时预取下一批
private val LoadingIndicatorSize = 22.dp
private val LoadingIndicatorStroke = 2.dp
private val ChartHeight = 48.dp
private val ChartBarGap = 2.dp
private val ChartBarMinHeight = 4.dp         // 空桶也留一点高度，便于点击
private val ChartBarRange = 44.dp            // 满桶相对最小高度的额外高度

// 列表条目「位移」动画：柔和的缓入缓出补间，无弹簧回弹（避免突兀）。仅用于位置变化，不做淡入淡出。
private val ListPlacementSpec = tween<androidx.compose.ui.unit.IntOffset>(
    durationMillis = 240,
    easing = FastOutSlowInEasing,
)

/**
 * 一周从哪天开始——与**应用语言**绑定，而非系统区域：中文→周一，英文→周日，其余语言用该地区默认。
 * 返回 Calendar 的 DAY_OF_WEEK 常量（SUNDAY=1..SATURDAY=7）。同时用于图表与「按周」列表分组，保持一致。
 */
@Composable
private fun rememberWeekStart(): Int {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return remember(locale) {
        when (locale.language) {
            "zh" -> Calendar.MONDAY
            "en" -> Calendar.SUNDAY
            else -> Calendar.getInstance(locale).firstDayOfWeek
        }
    }
}

@Composable
fun HistoryScreen(
    onBack: (() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    onCreateRule: ((Long) -> Unit)? = null,
    vm: HistoryViewModel = viewModel(),
) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val times by vm.times.collectAsStateWithLifecycle()
    val appCounts by vm.appCounts.collectAsStateWithLifecycle()
    val selectedApp by vm.selectedApp.collectAsStateWithLifecycle()
    val canLoadMore by vm.canLoadMore.collectAsStateWithLifecycle()
    val ruleNames by vm.ruleNames.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 增量解析应用图标：只为尚未缓存的新包名解析，避免每次「加载更多」都重解析全部。
    val appIcons = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(logs) {
        val missing = logs.map { it.packageName }.distinct().filter { it !in appIcons }
        if (missing.isNotEmpty()) {
            val resolved = withContext(Dispatchers.IO) {
                missing.mapNotNull { pkg ->
                    runCatching {
                        pkg to context.packageManager.getApplicationIcon(pkg).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
                    }.getOrNull()
                }
            }
            resolved.forEach { (pkg, bmp) -> appIcons[pkg] = bmp }
        }
    }
    var grouping by remember { mutableStateOf(Grouping.DAY) }
    val weekStart = rememberWeekStart()

    // vm.logs 已按所选应用在查询层过滤；这里只再滤掉空白通知（既无标题也无正文）。
    val filtered = remember(logs) {
        logs.filter { it.title.isNotBlank() || it.text.isNotBlank() }
    }
    // 过滤行的应用列表（按通知数从多到少），来自独立聚合查询，与分页无关。
    val appList = remember(appCounts) { appCounts.map { it.packageName to it.appName } }

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
            // 固定表头：统计 + 分组切换 + 应用过滤始终保持固定，无需滚回顶部即可更改。
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Spacer(Modifier.height(Spacing.xs))
                StatsCard(times, grouping, weekStart)
                Row(
                    Modifier.padding(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    IOSSegmented(
                        options = listOf(Grouping.DAY, Grouping.WEEK),
                        selected = grouping,
                        label = { stringResource(if (it == Grouping.DAY) R.string.group_by_day else R.string.group_by_week) },
                        onSelect = { grouping = it },
                    )
                }
                AppFilterChips(appList, selectedApp) { vm.setSelectedApp(it) }
            }
            Spacer(Modifier.height(Spacing.md))

            // 上拉加载更多：接近列表末尾时请求下一批（VM 侧再做「上一批已加载完」的守卫）。
            val listState = rememberLazyListState()
            LaunchedEffect(listState, canLoadMore) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
                }.collect { (last, total) ->
                    if (canLoadMore && total > 0 && last >= total - LOAD_MORE_AHEAD) vm.loadMore()
                }
            }

            // 只有分组后的日志列表会滚动。用 weight 占据表头之后的剩余空间——
            // 若用 fillMaxSize 会撑出父容器、把列表底部裁掉，导致显示不全。
            // 分组是 O(n) 的 groupBy + 日期格式化；用 remember 固定到实际影响分组结果的输入上，
            // 避免 times/appCounts/ruleNames 等无关 StateFlow 更新触发的重组白白重算一遍。
            val groups = remember(filtered, grouping, weekStart) { groupLogs(filtered, grouping, weekStart) }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                groups.forEach { (header, items) ->
                    // 稳定 key（按分组标题）让 LazyColumn 能识别条目增删/重排。
                    // 只保留「位移」动画（去掉淡入淡出与弹簧回弹）：加载更多/删除时下方条目柔和上移，
                    // 不闪不弹；按天/周切换直接换位而非整屏淡出淡入，避免突兀。
                    item(key = "h:$header", contentType = "header") {
                        Text(
                            header,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = ListPlacementSpec,
                            ).padding(start = Spacing.xxl, top = Spacing.xs),
                        )
                    }
                    item(key = "s:$header", contentType = "section") {
                        // animateContentSize：组内某行被删除后，卡片高度平滑收缩，而非瞬间跳变。
                        InsetGroupedSection(
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = ListPlacementSpec,
                                )
                                .animateContentSize(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)),
                        ) {
                            items.forEachIndexed { i, log ->
                                if (i > 0) HairlineDivider(startInset = Spacing.lg)
                                SwipeableLogRow(
                                    log = log,
                                    ruleNames = ruleNames,
                                    icon = appIcons[log.packageName],
                                    onDelete = { vm.delete(log) },
                                    onCreateRule = onCreateRule?.let { cb ->
                                        { vm.createRuleFrom(log) { id -> cb(id) } }
                                    },
                                )
                            }
                        }
                    }
                }
                if (canLoadMore) {
                    item(key = "loading", contentType = "loading") {
                        Box(
                            Modifier.fillMaxWidth().padding(Spacing.lg).animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = ListPlacementSpec,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(LoadingIndicatorSize),
                                strokeWidth = LoadingIndicatorStroke,
                            )
                        }
                    }
                }
                item(key = "bottom_spacer", contentType = "spacer") { Spacer(Modifier.height(Spacing.xl)) }
            }
        }
    }
}

@Composable
private fun StatsCard(times: List<Long>, grouping: Grouping, firstDow: Int) {
    val weekdays = stringArrayResource(R.array.weekday_full)
    val hours = IntArray(24)
    val days = IntArray(7)
    val cal = Calendar.getInstance()
    times.forEach {
        cal.timeInMillis = it
        hours[cal.get(Calendar.HOUR_OF_DAY)]++
        // 周分布按本地一周起始日排序（与「按周」列表分组一致）：slot 0 = 一周起始日。
        days[(cal.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7]++
    }
    // 图表跟随分组：按天=24 小时分布，按周=一周内各天分布（起始日随地区）。
    val byWeek = grouping == Grouping.WEEK
    val values = if (byWeek) days.toList() else hours.toList()
    val peak = values.indices.maxByOrNull { values[it] } ?: 0
    val maxVal = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    // 点击某根柱：选中并在概要行显示其数值；再次点击取消。分组切换时清空选中。
    var selected by remember(byWeek) { mutableStateOf<Int?>(null) }

    InsetGroupedSection {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                stringResource(R.string.stat_total, times.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Spacing.xs))
            val summary = when {
                selected != null -> "${barLabel(byWeek, selected!!, weekdays, firstDow)} · " +
                    stringResource(R.string.stat_bar_count, values[selected!!])
                byWeek -> "${stringResource(R.string.stat_busiest_day)}: ${barLabel(true, peak, weekdays, firstDow)}"
                else -> "${stringResource(R.string.stat_peak_hour)}: %02d:00".format(peak)
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier.fillMaxWidth().height(ChartHeight),
                horizontalArrangement = Arrangement.spacedBy(ChartBarGap),
                verticalAlignment = Alignment.Bottom,
            ) {
                val primary = MaterialTheme.colorScheme.primary
                values.indices.forEach { i ->
                    val frac = values[i].toFloat() / maxVal
                    val isSel = selected == i
                    val highlight = isSel || (selected == null && i == peak)
                    // 整列（全高）都可点击，便于点中很矮/为空的柱子。
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { selected = if (isSel) null else i },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(ChartBarMinHeight + ChartBarRange * frac)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (highlight) primary else primary.copy(alpha = Alpha.Muted)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 直方图某根柱的标签：按天=「HH:00」；按周=该柱对应的星期几。
 * 周柱的 [index] 是「距一周起始日的偏移」（slot），需按 [firstDow] 换算回真实星期，
 * 再映射到 ISO 顺序（周一=0…周日=6）的 [weekdays] 数组。
 */
private fun barLabel(byWeek: Boolean, index: Int, weekdays: Array<String>, firstDow: Int): String =
    if (!byWeek) "%02d:00".format(index)
    else {
        val calDow = ((firstDow - 1 + index) % 7) + 1 // 1=周日..7=周六（Calendar 约定）
        weekdays.getOrElse((calDow + 5) % 7) { "" }   // 转 ISO 下标
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
private fun SwipeableLogRow(
    log: NotificationLog,
    ruleNames: Map<Long, String>,
    icon: ImageBitmap?,
    onDelete: () -> Unit,
    onCreateRule: (() -> Unit)? = null,
) {
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
            LogRow(log, ruleNames, icon, onCreateRule)
        }
    }
}

@Composable
private fun LogRow(
    log: NotificationLog,
    ruleNames: Map<Long, String>,
    icon: ImageBitmap?,
    onCreateRule: (() -> Unit)? = null,
) {
    // 以 id 作为 key，这样当列表发生变化（例如删除后）时，展开状态能跟随其对应的日志。
    var expanded by remember(log.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            // iOS 风格反馈：按下整行轻微变灰（无 Material 水波纹）。
            .iosPressable { expanded = !expanded }
            // 用连续的高度动画平滑「展开/收起」，避免默认 AnimatedVisibility 那种淡入弹跳的突兀感。
            .animateContentSize(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(10.dp))
            }
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
        if (expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                if (log.title.isNotBlank()) DetailLine(stringResource(R.string.detail_title), log.title)
                if (log.text.isNotBlank()) DetailLine(stringResource(R.string.detail_text), log.text)
                DetailLine(stringResource(R.string.detail_package), log.packageName)
                DetailLine(stringResource(R.string.detail_time), fullTimeOf(log.time))
                if (log.matched) {
                    val ids = log.firedRuleIds.split(",").mapNotNull { it.trim().toLongOrNull() }
                    val names = ids.mapNotNull { ruleNames[it] }
                    val display = when {
                        names.isNotEmpty() -> names.joinToString("、")
                        ids.isNotEmpty() -> stringResource(R.string.rule_deleted)
                        else -> null
                    }
                    if (display != null) DetailLine(stringResource(R.string.detail_rules), display)
                }
                if (onCreateRule != null) {
                    TextButton(
                        onClick = onCreateRule,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(stringResource(R.string.history_create_rule))
                    }
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
        NotificationLog.OUTCOME_SILENCED -> stringResource(R.string.outcome_silenced) to IOSColors.Gray
        NotificationLog.OUTCOME_DISMISSED -> stringResource(R.string.outcome_dismissed) to IOSColors.Orange
        NotificationLog.OUTCOME_SNOOZED -> stringResource(R.string.outcome_snoozed) to IOSColors.Indigo
        // 丢弃同样属于「被规则命中」，按命中（绿色）展示更贴切，避免满屏红色「已丢弃」。
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

private fun groupLogs(
    logs: List<NotificationLog>,
    grouping: Grouping,
    firstDow: Int,
): List<Pair<String, List<NotificationLog>>> {
    val cal = Calendar.getInstance()
    val dayFmt = java.text.SimpleDateFormat("yyyy-MM-dd EEE", java.util.Locale.getDefault())
    val rangeFmt = java.text.SimpleDateFormat("M-d", java.util.Locale.getDefault())
    return logs.groupBy { log ->
        cal.timeInMillis = log.time
        when (grouping) {
            Grouping.DAY -> dayFmt.format(java.util.Date(log.time))
            Grouping.WEEK -> {
                // 用「本周起始日 ~ 结束日」的日期范围作为分组标题；起始日与语言绑定（见 rememberWeekStart），
                // 与上方图表保持一致。回退到本周起始日：
                while (cal.get(Calendar.DAY_OF_WEEK) != firstDow) cal.add(Calendar.DAY_OF_MONTH, -1)
                val start = cal.time
                cal.add(Calendar.DAY_OF_MONTH, 6)
                val end = cal.time
                "${rangeFmt.format(start)} ~ ${rangeFmt.format(end)}"
            }
        }
    }.toList()
}

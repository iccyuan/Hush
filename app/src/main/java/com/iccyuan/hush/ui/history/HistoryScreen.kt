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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iccyuan.hush.R
import com.iccyuan.hush.data.db.BuzzJson
import com.iccyuan.hush.data.model.NotificationLog
import com.iccyuan.hush.engine.MatchTrace
import com.iccyuan.hush.engine.NearMiss
import com.iccyuan.hush.ui.Localize
import kotlinx.serialization.builtins.ListSerializer
import com.iccyuan.hush.ui.components.GlassScaffold
import com.iccyuan.hush.ui.components.HairlineDivider
import com.iccyuan.hush.ui.components.InsetGroupedSection
import com.iccyuan.hush.ui.components.ListRowPlacementSpec
import com.iccyuan.hush.ui.components.groupSegmentShape
import com.iccyuan.hush.ui.components.iosPressable
import com.iccyuan.hush.ui.components.rowFrost
import com.iccyuan.hush.ui.theme.Alpha
import com.iccyuan.hush.ui.theme.IOSColors
import com.iccyuan.hush.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

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
    // 应用图标缓存与解析在 VM 里（跨标签切换存活、随预热的日志流增量解析），这里只读。
    val appIcons = vm.appIcons
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
        // 只让出顶部（导航栏）；底部不整体让出，改由列表的 contentPadding 承担——
        // 这样列表从毛玻璃底栏下方穿过，而不是被顶在底栏上方留出一条空隙。
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
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
                // 底部让位以 contentPadding 表达：内容能滚到底栏下方，滚到底时最后一行仍在底栏之上。
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + Spacing.sm),
            ) {
                groups.forEachIndexed { gi, (header, items) ->
                    // 稳定 key（分组标题 / 日志 id）让 LazyColumn 能识别条目增删/重排。
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
                                placementSpec = ListRowPlacementSpec,
                            ).padding(
                                start = Spacing.xxl,
                                top = if (gi == 0) Spacing.xs else Spacing.lg + Spacing.xs,
                                bottom = Spacing.lg,
                            ),
                        )
                    }
                    // 每条日志各自是一个 lazy item，而不是整组塞进一个 item：滚动时按行增量组合，
                    // 进到一个上百条的分组不再卡一大帧；展开/删除也只重测量所在的那一行。
                    // 「一张卡」的观感由首/尾行的圆角拼出来（见 groupSegmentShape），
                    // 行底色用 rowFrost（静态、无逐行实时模糊）。
                    itemsIndexed(items, key = { _, log -> "r:${log.id}" }, contentType = { _, _ -> "row" }) { i, log ->
                        Column(
                            Modifier
                                .animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null,
                                    placementSpec = ListRowPlacementSpec,
                                )
                                .padding(horizontal = Spacing.lg)
                                .clip(groupSegmentShape(i, items.size))
                                .rowFrost(),
                        ) {
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
                if (canLoadMore) {
                    item(key = "loading", contentType = "loading") {
                        Box(
                            Modifier.fillMaxWidth().padding(Spacing.lg).animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = ListRowPlacementSpec,
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
            }
        }
    }
}

@Composable
private fun StatsCard(times: List<Long>, grouping: Grouping, firstDow: Int) {
    val weekdays = stringArrayResource(R.array.weekday_full)
    // times 可达上千条且 Calendar 逐条换算不便宜；直方图缓存到实际输入上，
    // 别在每次点击柱子（selected 变化）引起的重组里白白重算一遍。
    val (hourDist, dayDist) = remember(times, firstDow) {
        val hours = IntArray(24)
        val days = IntArray(7)
        val cal = Calendar.getInstance()
        times.forEach {
            cal.timeInMillis = it
            hours[cal.get(Calendar.HOUR_OF_DAY)]++
            // 周分布按本地一周起始日排序（与「按周」列表分组一致）：slot 0 = 一周起始日。
            days[(cal.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7]++
        }
        hours.toList() to days.toList()
    }
    // 图表跟随分组：按天=24 小时分布，按周=一周内各天分布（起始日随地区）。
    val byWeek = grouping == Grouping.WEEK
    val values = if (byWeek) dayDist else hourDist
    val peak = values.indices.maxByOrNull { values[it] } ?: 0
    val maxVal = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    // 点击某根柱：选中并在概要行显示其数值；再次点击取消。分组切换时清空选中。
    var selected by remember(byWeek) { mutableStateOf<Int?>(null) }

    // 总数是**全部历史**的累计（可跨越好几周），单说一个「384 条通知」会被读成「今天 384 条」。
    // 补上覆盖天数与日均，数字才有尺度。
    val spanDays = remember(times) {
        val first = times.minOrNull() ?: return@remember 0
        val last = times.maxOrNull() ?: return@remember 0
        (((last - first) / 86_400_000L) + 1).toInt().coerceAtLeast(1)
    }

    InsetGroupedSection {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                stringResource(R.string.stat_total, times.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (times.isNotEmpty()) {
                Text(
                    stringResource(R.string.stat_span, spanDays, times.size / spanDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        // 删除按钮不再垫在整行底下（行底色是半透明的 rowFrost，垫底会透出红色），
        // 而是与前景同步从行的右缘滑入：关闭时整体停在裁剪区之外，完全不可见。
        Box(
            Modifier
                .matchParentSize()
                .offset { androidx.compose.ui.unit.IntOffset((revealPx + offsetX.value).roundToInt(), 0) },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .background(IOSColors.Red)
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
        // 前景随拖动滑动；底色由所在分组行（rowFrost）提供，这里保持透明。
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
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
                MatchReason(log, ruleNames)
                NearMissReason(log)
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
    // 每种结果各自成色，「已丢弃」尤其不能混进泛泛的「命中」里——通知被拦下了是最该看清的一件事。
    val (label, color) = when (log.outcome) {
        NotificationLog.OUTCOME_DISCARDED -> stringResource(R.string.outcome_discarded) to IOSColors.Red
        NotificationLog.OUTCOME_MODIFIED -> stringResource(R.string.outcome_modified) to IOSColors.Blue
        NotificationLog.OUTCOME_SILENCED -> stringResource(R.string.outcome_silenced) to IOSColors.Gray
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

/**
 * 展开后的「为什么」：命中了哪条规则、被其中哪个触发器命中、当时哪些条件成立、执行了什么动作。
 *
 * 读的是随日志存下的取证快照，而不是回查规则——规则会被改、被删，回查只能给出**现在**的样子，
 * 答不了「当时为什么命中」。取证缺失（旧记录）时退回只列规则名。
 */
@Composable
private fun MatchReason(log: NotificationLog, ruleNames: Map<Long, String>) {
    if (!log.matched) return
    val traces = remember(log.traces) {
        if (log.traces.isBlank()) emptyList()
        else runCatching { BuzzJson.decodeFromString(TRACE_LIST, log.traces) }.getOrDefault(emptyList())
    }

    if (traces.isEmpty()) {
        // 旧记录没有取证，只能列出规则名（还得看规则是否仍存在）。
        val ids = log.firedRuleIds.split(",").mapNotNull { it.trim().toLongOrNull() }
        val names = ids.mapNotNull { ruleNames[it] }
        val display = when {
            names.isNotEmpty() -> names.joinToString("、")
            ids.isNotEmpty() -> stringResource(R.string.rule_deleted)
            else -> null
        }
        if (display != null) DetailLine(stringResource(R.string.detail_rules), display)
        return
    }

    for (trace in traces) {
        DetailLine(stringResource(R.string.detail_rules), trace.ruleName)
        // 触发器为空 = 该规则没设触发器，即「这个应用的所有通知都算命中」。说清楚它，
        // 否则用户会以为是漏记了。
        val why = if (trace.triggers.isEmpty()) {
            stringResource(R.string.trace_matches_all)
        } else {
            summaries(trace.triggers) { Localize.summary(it) }
        }
        DetailLine(stringResource(R.string.trace_trigger), why)
        if (trace.conditions.isNotEmpty()) {
            DetailLine(
                stringResource(R.string.trace_condition),
                summaries(trace.conditions) { Localize.summary(it) },
            )
        }
        if (trace.actions.isNotEmpty()) {
            DetailLine(
                stringResource(R.string.trace_action),
                summaries(trace.actions) { Localize.summary(it) },
            )
        }
    }
}

/**
 * 展开后的「为什么**没**命中」：哪条规则差一点、卡在了哪一关。
 *
 * 通知没被处理时，用户最想问的就是这个——此前只能靠翻 logcat 才能回答。用 ✓/✗ 标出走到哪、
 * 卡在哪，一眼就能看出差的是触发器还是时机。
 */
@Composable
private fun NearMissReason(log: NotificationLog) {
    if (log.matched || log.nearMisses.isBlank()) return
    val misses = remember(log.nearMisses) {
        runCatching { BuzzJson.decodeFromString(NEAR_MISS_LIST, log.nearMisses) }
            .getOrDefault(emptyList())
    }
    if (misses.isEmpty()) return

    for (miss in misses) {
        DetailLine(stringResource(R.string.trace_near_miss), miss.ruleName)
        when (miss.blockedAt) {
            NearMiss.Stage.TRIGGER -> {
                // 命中过一部分触发器，说明是「全部满足」的规则没凑齐——把凑齐的那些列出来，
                // 用户才知道差的是哪一个；一个都没命中就直接说没命中。
                val passed = if (miss.passedTriggers.isEmpty()) {
                    stringResource(R.string.trace_no_trigger_matched)
                } else {
                    stringResource(
                        R.string.trace_partial_triggers,
                        summaries(miss.passedTriggers) { Localize.summary(it) },
                    )
                }
                DetailLine(stringResource(R.string.trace_blocked_trigger), passed)
            }
            NearMiss.Stage.CONDITION -> {
                if (miss.passedTriggers.isNotEmpty()) {
                    DetailLine(
                        stringResource(R.string.trace_passed_trigger),
                        summaries(miss.passedTriggers) { Localize.summary(it) },
                    )
                }
                DetailLine(
                    stringResource(R.string.trace_blocked_condition),
                    summaries(miss.failedConditions) { Localize.summary(it) },
                )
            }
        }
    }
}

/**
 * 把一组规则组件渲染成一行摘要。用 for 循环而非 joinToString：[Localize] 的摘要是
 * @Composable（要按当前语言取字符串资源），不能在普通 lambda 里调用。
 */
@Composable
private fun <T> summaries(items: List<T>, summary: @Composable (T) -> String): String {
    val parts = mutableListOf<String>()
    for (item in items) parts += summary(item)
    return parts.joinToString("、")
}

private val TRACE_LIST = ListSerializer(MatchTrace.serializer())
private val NEAR_MISS_LIST = ListSerializer(NearMiss.serializer())

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

/**
 * 行时间格式化器按语言缓存：SimpleDateFormat 的构造要解析模式串，不该在每条行的
 * 每次重组里新建。SimpleDateFormat 非线程安全，此缓存仅供主线程的组合阶段使用。
 */
private var timeFmtCache: Pair<java.util.Locale, Pair<java.text.SimpleDateFormat, java.text.SimpleDateFormat>>? = null

private fun timeFmts(): Pair<java.text.SimpleDateFormat, java.text.SimpleDateFormat> {
    val locale = java.util.Locale.getDefault()
    timeFmtCache?.let { (cached, fmts) -> if (cached == locale) return fmts }
    val fmts = java.text.SimpleDateFormat("HH:mm", locale) to
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale)
    timeFmtCache = locale to fmts
    return fmts
}

private fun timeOf(t: Long): String = timeFmts().first.format(java.util.Date(t))

private fun fullTimeOf(t: Long): String = timeFmts().second.format(java.util.Date(t))

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

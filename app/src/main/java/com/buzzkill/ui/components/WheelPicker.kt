package com.buzzkill.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buzzkill.ui.theme.IOSColors
import com.buzzkill.ui.theme.LocalIsDarkTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * iOS 风格的滚动"滚筒"选择器。项目吸附到中央条带；行随着与中心的
 * 距离增大而淡出并缩小，以营造弯曲滚轮的错觉。居中的行
 * 即为选中的值。
 */
@Composable
fun WheelPicker(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
    itemHeight: Dp = 38.dp,
) {
    val density = LocalDensity.current
    val itemPx = with(density) { itemHeight.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, (count - 1).coerceAtLeast(0)))
    val fling = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()
    val half = visibleCount / 2

    // 滚轮稳定后报告居中的项目。
    LaunchedEffect(state, count) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val centred = (state.firstVisibleItemIndex +
                    (state.firstVisibleItemScrollOffset / itemPx).roundToInt())
                    .coerceIn(0, (count - 1).coerceAtLeast(0))
                if (centred != selected) onSelected(centred)
            }
        }
    }
    // 反映对 `selected` 的外部更改（同时不与正在进行的拖动冲突）。
    LaunchedEffect(selected) {
        if (!state.isScrollInProgress && state.firstVisibleItemIndex != selected) {
            state.scrollToItem(selected.coerceIn(0, (count - 1).coerceAtLeast(0)))
        }
    }

    Box(
        modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // 中央选择条带。
        val bandColor = if (LocalIsDarkTheme.current) Color0x14White else Color0x0ABlack
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(bandColor),
        )

        LazyColumn(
            state = state,
            flingBehavior = fling,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = itemHeight * half),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count) { i ->
                // 与视口中心的距离（以行计），用于淡出 + 缩放。
                val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }
                val viewportCentre =
                    (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2f
                val dist = if (info != null) {
                    abs((info.offset + info.size / 2f) - viewportCentre) / itemPx
                } else {
                    half.toFloat()
                }
                val alpha = (1f - dist * 0.34f).coerceIn(0.22f, 1f)
                val scale = (1f - dist * 0.12f).coerceIn(0.74f, 1f)
                val noRipple = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .clickable(interactionSource = noRipple, indication = null) {
                            scope.launch { state.animateScrollToItem(i) }
                        }
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(i),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (dist < 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** 两个联动的滚轮（小时 : 分钟），用于选择一天中的时间。 */
@Composable
fun TimeWheel(
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelPicker(
            count = 24,
            selected = hour,
            onSelected = { onChange(it, minute) },
            label = { "%02d".format(it) },
            modifier = Modifier.width(72.dp),
        )
        Text(
            ":",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        WheelPicker(
            count = 60,
            selected = minute,
            onSelected = { onChange(hour, it) },
            label = { "%02d".format(it) },
            modifier = Modifier.width(72.dp),
        )
    }
}

private val Color0x14White = androidx.compose.ui.graphics.Color(0x14FFFFFF)
private val Color0x0ABlack = androidx.compose.ui.graphics.Color(0x0A000000)

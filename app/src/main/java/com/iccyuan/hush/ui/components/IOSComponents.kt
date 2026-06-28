package com.iccyuan.hush.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iccyuan.hush.ui.theme.IOSColors
import com.iccyuan.hush.ui.theme.LocalIsDarkTheme

private val NavBarHeight = 44.dp

/**
 * 全屏 iOS 风格容器：柔和的渐变背景，内容在毛玻璃导航栏
 * *下方* 滚动，以及可选的毛玻璃底部栏。
 */
@Composable
fun GlassScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    /** 对话框/面板，绘制在所有内容之上，但位于模糊树内部，因此它们会产生磨砂效果。 */
    overlay: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // 唯一的模糊源——彩色背景。卡片和栏都是它的 hazeChild。
    // （第二个包裹内容的 haze 源会破坏其中嵌套卡片的 hazeChild 捕获，
    // 这就是它们被渲染成一片平白的原因。）
    val haze = rememberAppHazeState()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeight = if (bottomBar != null) 64.dp else 0.dp

    Box(modifier.fillMaxSize()) {
        // 淡彩色背景是唯一的模糊源；卡片、栏和对话框
        // 都对其进行磨砂处理。
        GlassBackdrop(haze)

        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalHazeState provides haze,
                LocalCardHazeState provides haze,
            ) {
                content(
                    PaddingValues(
                        top = statusTop + NavBarHeight,
                        bottom = navBars + bottomBarHeight,
                    )
                )
            }
        }

        IOSNavBar(
            title = title,
            statusTop = statusTop,
            onBack = onBack,
            actions = actions,
            modifier = Modifier.frostedOverlay(haze),
        )

        if (bottomBar != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .frostedOverlay(haze)
                    .padding(bottom = navBars),
            ) { bottomBar() }
        }

        // 叠加层（对话框/面板）位于所有内容之上，并使模糊源处于作用域内。
        CompositionLocalProvider(
            LocalHazeState provides haze,
            LocalCardHazeState provides haze,
        ) {
            overlay()
        }
    }
}

@Composable
private fun IOSNavBar(
    title: String,
    statusTop: androidx.compose.ui.unit.Dp,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.height(statusTop).fillMaxWidth())
        Box(Modifier.height(NavBarHeight).fillMaxWidth().padding(horizontal = 8.dp)) {
            if (onBack != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(18.dp),
                    )
                }
            }
            Text(
                title,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                content = { actions() },
            )
        }
        HairlineDivider()
    }
}

/** 0.5dp 的 iOS 分隔线。 */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier, startInset: androidx.compose.ui.unit.Dp = 0.dp) {
    val color = if (LocalIsDarkTheme.current) IOSColors.SeparatorDark else IOSColors.SeparatorLight
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(0.5.dp)
            .background(color),
    )
}

/**
 * iOS 内嵌分组列表区块：一个可选的头部，然后是一个圆角卡片，其
 * 子项之间用细线分隔。
 */
@Composable
fun InsetGroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(
                header.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 8.dp),
            )
        }
        Column(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .cardFrost(),
            content = { content() },
        )
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 8.dp),
            )
        }
    }
}

/** iOS 风格的圆角方形彩色图标徽章，内含白色字形。 */
@Composable
fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 29.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

/** 单个 iOS 列表行：可选的前导徽章、标签、尾部内容。 */
@Composable
fun IOSRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, iconColor)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** 填充式圆角 iOS 风格按钮（强调色或破坏性操作）。 */
@Composable
fun IOSFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val container = if (destructive) IOSColors.Red else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/** 带边框的中性 iOS 风格按钮。 */
@Composable
fun IOSTintedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (LocalIsDarkTheme.current) IOSColors.ControlFillDark else IOSColors.ControlFillLight
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
}

/** iOS 绿色调的开关。 */
@Composable
fun IOSSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    // 自绘 iOS 开关：统一大小的白色圆头 + 柔和投影，轨道色与滑块位置均做弹性动画，
    // 比 Material3 的 Switch（关态滑块更小、带描边）更精致、更贴近原生 iOS 观感。
    val trackOff = if (LocalIsDarkTheme.current) IOSColors.SwitchTrackOffDark else IOSColors.SwitchTrackOffLight
    val trackColor by animateColorAsState(
        targetValue = if (checked) IOSColors.Green else trackOff,
        animationSpec = spring(stiffness = 700f),
        label = "switchTrack",
    )
    val bias by animateFloatAsState(
        targetValue = if (checked) 1f else -1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "switchThumb",
    )
    Box(
        Modifier
            .size(width = 51.dp, height = 31.dp)
            .background(trackColor, RoundedCornerShape(50))
            .then(
                if (onCheckedChange != null)
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onCheckedChange(!checked) }
                else Modifier
            )
            .padding(2.dp),
        contentAlignment = BiasAlignment(horizontalBias = bias, verticalBias = 0f),
    ) {
        Box(
            Modifier
                .size(27.dp)
                .shadow(2.dp, RoundedCornerShape(50))
                .background(Color.White, RoundedCornerShape(50)),
        )
    }
}

/**
 * iOS 分段控件，带有滑动、带阴影的选中胶囊以及在活动分段旁
 * 淡出的细分隔线。
 */
@Composable
fun <T> IOSSegmented(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalIsDarkTheme.current
    val trackColor = if (dark) IOSColors.SegmentTrackDark else IOSColors.ControlFillLight
    val pillColor = if (dark) IOSColors.SegmentPillDark else Color.White
    val count = options.size
    val selIndex = options.indexOf(selected).coerceAtLeast(0)
    val anim by animateFloatAsState(
        targetValue = selIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "segPill",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(trackColor)
            .padding(2.dp),
    ) {
        // 滑动的选中胶囊。
        if (count > 0) {
            val bias = if (count > 1) -1f + 2f * anim / (count - 1) else 0f
            Box(
                Modifier
                    .align(BiasAlignment(bias, 0f))
                    .fillMaxHeight()
                    .fillMaxWidth(1f / count)
                    .shadow(3.dp, RoundedCornerShape(7.dp))
                    .clip(RoundedCornerShape(7.dp))
                    .background(pillColor),
            )
        }
        // 分段之间的分隔线（紧邻选中胶囊处隐藏）。
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, _ ->
                if (i > 0) {
                    val near = i == selIndex || i - 1 == selIndex
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .padding(vertical = 7.dp)
                            .width(1.dp)
                            .background(
                                if (near) Color.Transparent
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
                            ),
                    )
                }
                Box(Modifier.weight(1f))
            }
        }
        // 标签（位于胶囊上方）。
        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val noRipple = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(7.dp))
                        .clickable(interactionSource = noRipple, indication = null) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}


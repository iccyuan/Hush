package com.iccyuan.hush.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.iccyuan.hush.ui.theme.IOSColors
import com.iccyuan.hush.ui.theme.LocalIsDarkTheme
import com.iccyuan.hush.ui.theme.Radius
import com.iccyuan.hush.ui.theme.Sizes
import com.iccyuan.hush.ui.theme.Spacing

/**
 * 气泡提示：从锚点（跟在标题后的 ⓘ）正下方弹出，带一个指回锚点的小尖角。
 *
 * 用于「一句话结论放行内、完整原理点开再看」这类说明——比居中对话框更轻，不打断阅读位置，
 * 也不必为一段说明配一个「确定」按钮。点击气泡外任意处即收起。
 *
 * 调用方把它放进锚点所在的 Box 内，气泡便会相对锚点定位：
 * ```
 * Box {
 *     Icon(Icons.Filled.Info, null, Modifier.clickable { show = true })
 *     TipBubble(visible = show, onDismiss = { show = false }, text = "…")
 * }
 * ```
 */
@Composable
fun TipBubble(
    visible: Boolean,
    onDismiss: () -> Unit,
    text: String,
) {
    if (!visible) return
    val density = LocalDensity.current
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * Sizes.bubbleMaxHeightFraction
    val edgeMarginPx = with(density) { Spacing.lg.roundToPx() }

    // 尖角必须跟着**最终**落位走：气泡靠近屏幕右缘时会被推回来，此时它相对锚点已经移位，
    // 尖角若按固定偏移画就会指偏。由定位器把「锚点中心相对气泡左边缘」的距离回传，据此画尖角。
    var arrowCenterPx by remember { mutableIntStateOf(0) }
    val shape = remember(arrowCenterPx) { BubbleShape(arrowCenterPx.toFloat()) }
    val positionProvider = remember(edgeMarginPx) {
        BubblePositionProvider(edgeMarginPx) { arrowCenterPx = it }
    }
    // 取卡片的表面色，气泡才像是从这张卡片里长出来的。刻意不套毛玻璃：模糊源在主窗口，
    // 而 Popup 自成一个窗口，跨窗口取景不可靠——宁可用与卡片一致的实色，也不要糊出个错位的背景。
    val surface = if (LocalIsDarkTheme.current) IOSColors.SurfaceDark else IOSColors.SurfaceLight

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(spring(stiffness = 900f)) + scaleIn(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
                initialScale = 0.9f,
                // 从尖角处「长」出来，观感上才像是由 ⓘ 弹出的。
                transformOrigin = TransformOrigin(pivotFractionX = 0f, pivotFractionY = 0f),
            ),
        ) {
            Column(
                Modifier
                    .widthIn(max = Sizes.bubbleMaxWidth)
                    .shadow(Spacing.sm, shape)
                    .background(surface, shape)
                    .padding(
                        top = Sizes.bubbleArrowHeight + Spacing.md,
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.lg,
                    ),
            ) {
                Column(
                    Modifier
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 把气泡放到锚点正下方、左边缘与锚点对齐；若右侧放不下就整体左推，但不越过屏幕左边距。
 * 通过 [onArrowCenter] 回传锚点中心相对气泡左边缘的水平距离，供 [BubbleShape] 定位尖角。
 */
private class BubblePositionProvider(
    private val edgeMarginPx: Int,
    private val onArrowCenter: (Int) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = windowSize.width - edgeMarginPx - popupContentSize.width
        val x = anchorBounds.left.coerceAtMost(maxX).coerceAtLeast(edgeMarginPx)
        onArrowCenter(anchorBounds.center.x - x)
        return IntOffset(x, anchorBounds.bottom)
    }
}

/** 圆角矩形 + 顶部指向锚点的小尖角。[arrowCenterPx] 为尖角中心距气泡左边缘的像素距离。 */
private class BubbleShape(private val arrowCenterPx: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val arrowH = with(density) { Sizes.bubbleArrowHeight.toPx() }
        val arrowW = with(density) { Sizes.bubbleArrowWidth.toPx() }
        val radius = with(density) { Radius.md.toPx() }
        // 夹住尖角，别让它压到左右圆角上。
        val cx = arrowCenterPx.coerceIn(radius + arrowW, size.width - radius - arrowW)

        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = arrowH,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(radius, radius),
                )
            )
            moveTo(cx - arrowW / 2, arrowH)
            lineTo(cx, 0f)
            lineTo(cx + arrowW / 2, arrowH)
            close()
        }
        return Outline.Generic(path)
    }
}

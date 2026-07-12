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
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.iccyuan.hush.ui.theme.Radius
import com.iccyuan.hush.ui.theme.Sizes
import com.iccyuan.hush.ui.theme.Spacing

/**
 * 气泡提示：从锚点（通常是行尾的 ⓘ 图标）下方弹出，带一个指回锚点的小尖角。
 *
 * 用于「一句话结论放行内、完整原理点开再看」这类说明——比居中对话框更轻，不打断阅读位置，
 * 也不必为一段说明配一个「确定」按钮。点击气泡外任意处即收起。
 *
 * 调用方把它放进锚点所在的 Box 内，气泡便会相对锚点定位：
 * ```
 * Box {
 *     IconButton(onClick = { show = true }) { Icon(Icons.Filled.Info, null) }
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
    // 右边缘对齐锚点右边缘、向左铺开：锚点（ⓘ）总在行尾，居中对齐会溢出屏幕右侧。
    // 尖角落在锚点中心正下方，即距气泡右边缘「锚点半宽」处。
    val shape = BubbleShape(arrowInsetFromEnd = Sizes.iconButton / 2)

    Popup(
        // TopEnd + 下移一个锚点高度 = 气泡顶边贴着锚点底边，从 ⓘ 正下方长出来。
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(x = 0, y = Sizes.iconButton.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(spring(stiffness = 900f)) + scaleIn(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
                initialScale = 0.9f,
                // 从尖角处「长」出来，观感上才像是由 ⓘ 弹出的。
                transformOrigin = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 0f),
            ),
        ) {
            Column(
                Modifier
                    .widthIn(max = Sizes.bubbleMaxWidth)
                    .shadow(Spacing.sm, shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape)
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

/** 圆角矩形 + 顶部指向锚点的小尖角。[arrowInsetFromEnd] 为尖角中心距锚点右边缘的距离。 */
private class BubbleShape(private val arrowInsetFromEnd: androidx.compose.ui.unit.Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val arrowH = with(density) { Sizes.bubbleArrowHeight.toPx() }
        val arrowW = with(density) { Sizes.bubbleArrowWidth.toPx() }
        val radius = with(density) { Radius.md.toPx() }
        // 尖角对准锚点中心：自气泡右边缘向左量一个锚点半宽；夹住以免压到圆角上。
        val arrowCx = (size.width - with(density) { arrowInsetFromEnd.toPx() })
            .coerceIn(radius + arrowW, size.width - radius - arrowW)

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
            moveTo(arrowCx - arrowW / 2, arrowH)
            lineTo(arrowCx, 0f)
            lineTo(arrowCx + arrowW / 2, arrowH)
            close()
        }
        return Outline.Generic(path)
    }
}

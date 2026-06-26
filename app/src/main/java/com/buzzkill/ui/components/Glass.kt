package com.buzzkill.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.buzzkill.ui.theme.IOSColors
import com.buzzkill.ui.theme.LocalIsDarkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * 所有 Haze（毛玻璃）的使用都集中通过此文件处理。模糊源是一个
 * 非常淡的彩色背景（[GlassBackdrop]）；卡片、栏和对话框对其进行磨砂处理。
 * 平滑/空白的背景模糊后什么也看不到，所以一点点颜色才能让
 * 玻璃效果真正显现——这里特意保持得很微妙。
 */

/** 提供给栏/对话框的单一背景模糊源。 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** 同一来源，暴露给卡片使用（单独保留以便调用处更清晰易读）。 */
val LocalCardHazeState = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun rememberAppHazeState(): HazeState = remember { HazeState() }

/** 将某个可组合项标记为模糊源。 */
fun Modifier.hazeSourceLayer(state: HazeState): Modifier = this.haze(state)

/** 在 [state] 所捕获内容之上叠加的毛玻璃材质（由栏和对话框使用）。 */
@Composable
fun Modifier.frostedOverlay(state: HazeState): Modifier {
    val dark = LocalIsDarkTheme.current
    val container = if (dark) IOSColors.SurfaceDark else IOSColors.SurfaceLight
    return this.hazeChild(state = state, style = HazeMaterials.thin(container))
}

/** 卡片表面作为半透明毛玻璃叠加在淡色背景之上。 */
@Composable
fun Modifier.cardFrost(): Modifier {
    val dark = LocalIsDarkTheme.current
    val container = if (dark) IOSColors.SurfaceDark else IOSColors.SurfaceLight
    val state = LocalCardHazeState.current
    return if (state != null) {
        this.hazeChild(state = state, style = HazeMaterials.ultraThin(container))
    } else {
        this.background(container)
    }
}

/**
 * 模糊源：一个柔和的渐变加上几个非常淡的彩色圆形。绘制在
 * 子 [Canvas] 中以便 Haze 捕获它。足够淡以免成为喧闹的壁纸，但
 * 圆形的硬边缘仍能给毛玻璃提供可供柔化的内容。
 */
@Composable
fun GlassBackdrop(state: HazeState, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current
    val grad = if (dark) {
        listOf(IOSColors.GradientTopDark, IOSColors.GradientBottomDark)
    } else {
        listOf(IOSColors.GradientTopLight, IOSColors.GradientBottomLight)
    }
    // 彩色色块给毛玻璃提供真正可供模糊的内容——如果这里没有足够的
    // 颜色/对比度，栏和卡片就会显得是一片平白。保持色彩丰富，但
    // 通过重度模糊柔化成梦幻般的色晕。
    val a = if (dark) 0.24f else 0.18f
    val blue = IOSColors.Blue.copy(alpha = a)
    val purple = IOSColors.Purple.copy(alpha = a)
    val pink = Color(0xFFFF5C7A).copy(alpha = a)
    val teal = Color(0xFF32D6C8).copy(alpha = a)
    val orange = IOSColors.Orange.copy(alpha = a)
    Box(modifier.fillMaxSize().hazeSourceLayer(state)) {
        // 将圆形重度模糊成柔和、梦幻的色晕。
        Canvas(Modifier.fillMaxSize().blur(60.dp, BlurredEdgeTreatment.Rectangle)) {
            drawRect(Brush.verticalGradient(grad))
            fun disc(color: Color, cx: Float, cy: Float, r: Float) {
                drawCircle(color, size.minDimension * r, Offset(size.width * cx, size.height * cy))
            }
            disc(blue, 0.16f, 0.08f, 0.34f)
            disc(purple, 0.88f, 0.12f, 0.36f)
            disc(pink, 0.28f, 0.40f, 0.30f)
            disc(teal, 0.82f, 0.50f, 0.32f)
            disc(orange, 0.14f, 0.66f, 0.30f)
            disc(purple, 0.74f, 0.78f, 0.32f)
            disc(blue, 0.30f, 0.94f, 0.30f)
            disc(pink, 0.90f, 0.95f, 0.28f)
        }
    }
}

package com.iccyuan.hush.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 统一管理的设计令牌：颜色、透明度、以及个别原始字号集中放在一处，避免在调用处硬编码。
 * 颜色见 [IOSColors]；未被 Material 排版/配色方案覆盖的透明度与字号放在这里。
 */

/** 叠加在强调色/文字色之上的语义化透明度。 */
object Alpha {
    /** 激活/选中元素背后的较强着色填充。 */
    const val FillStrong = 0.18f
    /** 选中状态的着色填充。 */
    const val Fill = 0.16f
    /** 浅着色填充（轨道、细微高亮）。 */
    const val FillLight = 0.12f
    /** 极淡着色填充（未选中的胶囊/按钮）。 */
    const val FillFaint = 0.08f
    /** 状态徽标背景。 */
    const val Badge = 0.15f
    /** 强调色边框/描边（如选中外圈）。 */
    const val Border = 0.55f
    /** 弱化的图标、拖拽手柄、淡分隔线。 */
    const val Muted = 0.40f
}

/** 未通过 Material 排版体系表达的原始字号。 */
object FontSizes {
    /** 极小的角标（如节假日日历上的“休/班”标记）。 */
    val Tiny = 8.sp
}

/** 统一的间距刻度（dp）。调用处优先用这些语义档位，避免散落硬编码的数值。 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

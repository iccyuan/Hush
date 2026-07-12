package com.iccyuan.hush.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 数值型设计令牌：间距、透明度、以及个别原始字号——凡是会散落成硬编码数字的量都收在这里，
 * 调用处一律引用语义档位，改版与适配时只动这一处。颜色令牌另见 [IOSColors]（Color.kt）。
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

/** 圆角半径。 */
object Radius {
    /** 气泡、卡片等中等圆角。 */
    val md = 14.dp
    /** 对话框、面板等大圆角。 */
    val lg = 22.dp
}

/** 组件尺寸：浮层的宽高上限与气泡尖角等。集中在此，便于按屏幕尺寸整体调校。 */
object Sizes {
    /** 居中对话框的最大宽度——再宽行长就难读了。 */
    val dialogMaxWidth = 440.dp
    /** 气泡提示的最大宽度。 */
    val bubbleMaxWidth = 320.dp
    /** 对话框最大高度占屏幕的比例：内容再长也不能把按钮顶出屏幕。 */
    const val dialogMaxHeightFraction = 0.8f
    /** 气泡最大高度占屏幕的比例：它只是补充说明，不该铺满整屏。 */
    const val bubbleMaxHeightFraction = 0.5f
    /** 气泡指向锚点的小尖角。 */
    val bubbleArrowHeight = 8.dp
    val bubbleArrowWidth = 16.dp
    /** 「详情」ⓘ 这类跟在标题后的行内小图标：与文字同量级，不能喧宾夺主。 */
    val inlineIcon = 16.dp
    /** 行内小图标的触控区：比图标本身大一圈以便点中，又不至于把行撑高。 */
    val inlineIconTouch = 24.dp
}

package com.iccyuan.hush.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * iOS 系统调色板（近似 Apple 的语义化颜色），用于让
 * 应用呈现原生 iOS 的观感，而非 Material You。
 */
object IOSColors {
    // 强调色（iOS 系统调色板的唯一可信来源）
    val Blue = Color(0xFF007AFF)
    val BlueDark = Color(0xFF0A84FF)
    val Green = Color(0xFF34C759)
    val GreenDark = Color(0xFF30D158)
    val Red = Color(0xFFFF3B30)
    val RedDark = Color(0xFFFF453A)
    val Orange = Color(0xFFFF9500)
    val Purple = Color(0xFFAF52DE)
    val Indigo = Color(0xFF5856D6)
    val Teal = Color(0xFF32ADE6)
    val Pink = Color(0xFFFF2D55)
    val Gray = Color(0xFF8E8E93)

    // 浅色主题背景
    val GroupedBgLight = Color(0xFFF2F2F7)      // systemGroupedBackground
    val SurfaceLight = Color(0xFFFFFFFF)         // secondarySystemGroupedBackground
    val SeparatorLight = Color(0x5C3C3C43)
    val LabelLight = Color(0xFF000000)
    val SecondaryLabelLight = Color(0x993C3C43)

    // 深色主题背景
    val GroupedBgDark = Color(0xFF000000)
    val SurfaceDark = Color(0xFF1C1C1E)          // secondarySystemGroupedBackground
    val ElevatedDark = Color(0xFF2C2C2E)
    val SeparatorDark = Color(0x5C545458)
    val LabelDark = Color(0xFFFFFFFF)
    val SecondaryLabelDark = Color(0x99EBEBF5)

    // 微妙的背景渐变端点
    val GradientTopLight = Color(0xFFEAF0FB)
    val GradientBottomLight = Color(0xFFF2F2F7)
    val GradientTopDark = Color(0xFF15151A)
    val GradientBottomDark = Color(0xFF000000)

    // iOS 控件专用的灰色填充/轨道色（取自原先散落在各控件里的硬编码值）
    val ControlFillLight = Color(0x1F787880)     // 浅色：分段控件轨道 / 描边按钮底
    val ControlFillDark = Color(0x33787880)      // 深色：描边按钮底
    val SegmentTrackDark = Color(0xFF2C2C2E)     // 深色：分段控件轨道
    val SegmentPillDark = Color(0xFF636366)      // 深色：分段控件选中胶囊
    val SwitchTrackOffLight = Color(0xFFE9E9EA)  // 浅色：开关关闭态轨道
    val SwitchTrackOffDark = Color(0xFF39393D)   // 深色：开关关闭态轨道
}

package com.buzzkill.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * iOS system colour palette (approximation of Apple's semantic colors) used to give
 * the app a native-iOS feel rather than Material You.
 */
object IOSColors {
    // Accents (single source of truth for the iOS system palette)
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

    // Light theme backgrounds
    val GroupedBgLight = Color(0xFFF2F2F7)      // systemGroupedBackground
    val SurfaceLight = Color(0xFFFFFFFF)         // secondarySystemGroupedBackground
    val SeparatorLight = Color(0x5C3C3C43)
    val LabelLight = Color(0xFF000000)
    val SecondaryLabelLight = Color(0x993C3C43)

    // Dark theme backgrounds
    val GroupedBgDark = Color(0xFF000000)
    val SurfaceDark = Color(0xFF1C1C1E)          // secondarySystemGroupedBackground
    val ElevatedDark = Color(0xFF2C2C2E)
    val SeparatorDark = Color(0x5C545458)
    val LabelDark = Color(0xFFFFFFFF)
    val SecondaryLabelDark = Color(0x99EBEBF5)

    // Subtle background gradient endpoints
    val GradientTopLight = Color(0xFFEAF0FB)
    val GradientBottomLight = Color(0xFFF2F2F7)
    val GradientTopDark = Color(0xFF15151A)
    val GradientBottomDark = Color(0xFF000000)
}

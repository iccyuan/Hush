package com.iccyuan.hush.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat

/** 向非配色方案的消费者（玻璃色调）暴露当前主题是否为深色。 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val IOSDarkColors = darkColorScheme(
    primary = IOSColors.BlueDark,
    onPrimary = Color.White,
    secondary = IOSColors.GreenDark,
    error = IOSColors.RedDark,
    background = IOSColors.GroupedBgDark,
    onBackground = IOSColors.LabelDark,
    surface = IOSColors.SurfaceDark,
    onSurface = IOSColors.LabelDark,
    surfaceVariant = IOSColors.ElevatedDark,
    onSurfaceVariant = IOSColors.SecondaryLabelDark,
    outline = IOSColors.SeparatorDark,
    // 深色主题下的反相表面（Snackbar 等用到）：浅底深字。
    inverseSurface = IOSColors.GroupedBgLight,
    inverseOnSurface = IOSColors.LabelLight,
    inversePrimary = IOSColors.Blue,
)

private val IOSLightColors = lightColorScheme(
    primary = IOSColors.Blue,
    onPrimary = Color.White,
    secondary = IOSColors.Green,
    error = IOSColors.Red,
    background = IOSColors.GroupedBgLight,
    onBackground = IOSColors.LabelLight,
    surface = IOSColors.SurfaceLight,
    onSurface = IOSColors.LabelLight,
    surfaceVariant = Color(0xFFEFEFF4),
    onSurfaceVariant = IOSColors.SecondaryLabelLight,
    outline = IOSColors.SeparatorLight,
    // 浅色主题下的反相表面（Snackbar 等用到）：深底浅字。
    inverseSurface = IOSColors.SurfaceDark,
    inverseOnSurface = IOSColors.GroupedBgLight,
    inversePrimary = IOSColors.BlueDark,
)

/** 圆角的、近似 iOS 连续圆角的形状。 */
private val IOSShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun HushTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) IOSDarkColors else IOSLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = IOSShapes,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}

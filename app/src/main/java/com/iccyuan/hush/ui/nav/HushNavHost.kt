package com.iccyuan.hush.ui.nav
import com.iccyuan.hush.ui.insights.InsightsScreen

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iccyuan.hush.ui.editor.RuleEditorScreen
import com.iccyuan.hush.ui.settings.SettingsCategory
import com.iccyuan.hush.ui.settings.SettingsDetailScreen

object Routes {
    const val MAIN = "main"
    const val EDITOR = "editor/{ruleId}"
    const val INSIGHTS = "insights"
    const val SETTINGS_DETAIL = "settings/{category}"
    fun editor(ruleId: Long) = "editor/$ruleId"
    fun settingsDetail(category: SettingsCategory) = "settings/${category.name}"
}

@Composable
fun HushNavHost() {
    val nav = rememberNavController()
    val dur = 300
    NavHost(
        navController = nav,
        startDestination = Routes.MAIN,
        // iOS 风格的推入/弹出：新页面从右侧滑入，返回时再向右滑出。
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(dur)) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(dur)) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(dur)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(dur)) },
    ) {
        composable(Routes.MAIN) {
            MainScaffold(
                onOpenRule = { id -> nav.navigate(Routes.editor(id)) },
                onOpenInsights = { nav.navigate(Routes.INSIGHTS) },
                onOpenSettingsCategory = { cat -> nav.navigate(Routes.settingsDetail(cat)) },
            )
        }
        composable(
            Routes.SETTINGS_DETAIL,
            arguments = listOf(navArgument("category") { type = NavType.StringType }),
        ) { backStack ->
            val cat = backStack.arguments?.getString("category")
                ?.let { runCatching { SettingsCategory.valueOf(it) }.getOrNull() }
                ?: SettingsCategory.GENERAL
            SettingsDetailScreen(category = cat, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(navArgument("ruleId") { type = NavType.LongType }),
        ) { backStack ->
            val ruleId = backStack.arguments?.getLong("ruleId") ?: 0L
            RuleEditorScreen(
                ruleId = ruleId,
                onDone = { nav.popBackStack() },
            )
        }
        composable(Routes.INSIGHTS) {
            InsightsScreen(onBack = { nav.popBackStack() })
        }
    }
}

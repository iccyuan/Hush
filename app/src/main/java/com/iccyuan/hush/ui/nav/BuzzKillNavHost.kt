package com.iccyuan.hush.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iccyuan.hush.ui.editor.RuleEditorScreen

object Routes {
    const val MAIN = "main"
    const val EDITOR = "editor/{ruleId}"
    const val INSIGHTS = "insights"
    fun editor(ruleId: Long) = "editor/$ruleId"
}

@Composable
fun BuzzKillNavHost() {
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
            )
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
            com.iccyuan.hush.ui.insights.InsightsScreen(onBack = { nav.popBackStack() })
        }
    }
}

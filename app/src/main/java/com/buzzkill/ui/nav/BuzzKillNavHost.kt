package com.buzzkill.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.buzzkill.ui.editor.RuleEditorScreen

object Routes {
    const val MAIN = "main"
    const val EDITOR = "editor/{ruleId}"
    fun editor(ruleId: Long) = "editor/$ruleId"
}

@Composable
fun BuzzKillNavHost() {
    val nav = rememberNavController()
    val dur = 300
    NavHost(
        navController = nav,
        startDestination = Routes.MAIN,
        // iOS-style push/pop: new screen slides in from the right, back slides it out.
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(dur)) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(dur)) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(dur)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(dur)) },
    ) {
        composable(Routes.MAIN) {
            MainScaffold(
                onOpenRule = { id -> nav.navigate(Routes.editor(id)) },
                onNewRule = { nav.navigate(Routes.editor(0L)) },
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
    }
}

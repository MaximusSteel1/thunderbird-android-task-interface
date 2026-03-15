package net.thunderbird.feature.taskmail.internal.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import net.thunderbird.feature.taskmail.api.TaskMailRoute

@Composable
internal fun TaskMailNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    taskMailNavigation: DefaultTaskMailNavigation = DefaultTaskMailNavigation(),
    deepLinkIntent: Intent? = null,
) {
    LaunchedEffect(navController, deepLinkIntent) {
        if (deepLinkIntent != null) {
            navController.handleDeepLink(deepLinkIntent)
        }
    }

    NavHost(
        navController = navController,
        startDestination = TaskMailRoute.Workspace,
        modifier = modifier,
    ) {
        taskMailNavigation.registerRoutes(
            navGraphBuilder = this,
            onBack = { navController.popBackStack() },
            onFinish = { route -> navController.navigate(route) },
        )
    }
}

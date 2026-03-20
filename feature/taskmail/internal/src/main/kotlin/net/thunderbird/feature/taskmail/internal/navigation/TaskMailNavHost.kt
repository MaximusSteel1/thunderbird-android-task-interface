package net.thunderbird.feature.taskmail.internal.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
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
    onExit: () -> Unit = {},
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
            onBack = taskMailNavHostOnBack(
                popBackStack = navController::popBackStack,
                exitHost = onExit,
            ),
            onFinish = { route -> navController.navigate(route) },
            onRepoSelected = taskMailNavHostOnRepoSelected(
                isReturningToExistingNewTask = {
                    navController.previousBackStackEntry
                        ?.destination
                        ?.hasRoute(TaskMailRoute.NewTask::class) == true
                },
                setSelectedRepoPathOnNewTask = { repoPath ->
                    navController.getBackStackEntry(TaskMailRoute.NewTask)
                        .savedStateHandle
                        .set(TASKMAIL_SELECTED_REPO_PATH_RESULT_KEY, repoPath)
                },
                popToExistingNewTask = {
                    navController.popBackStack(
                        TaskMailRoute.NewTask,
                        inclusive = false,
                        saveState = false,
                    )
                },
                popProjectSync = navController::popBackStack,
                openNewTask = { navController.navigate(TaskMailRoute.NewTask) },
            ),
        )
    }
}

internal fun taskMailNavHostOnBack(
    popBackStack: () -> Boolean,
    exitHost: () -> Unit,
): () -> Unit {
    return {
        if (!popBackStack()) {
            exitHost()
        }
    }
}

internal fun taskMailNavHostOnRepoSelected(
    isReturningToExistingNewTask: () -> Boolean,
    setSelectedRepoPathOnNewTask: (String) -> Unit,
    popToExistingNewTask: () -> Boolean,
    popProjectSync: () -> Boolean,
    openNewTask: () -> Unit,
): (String) -> Unit {
    return { repoPath ->
        if (isReturningToExistingNewTask()) {
            setSelectedRepoPathOnNewTask(repoPath)
            popToExistingNewTask()
        } else {
            popProjectSync()
            openNewTask()
            setSelectedRepoPathOnNewTask(repoPath)
        }
    }
}

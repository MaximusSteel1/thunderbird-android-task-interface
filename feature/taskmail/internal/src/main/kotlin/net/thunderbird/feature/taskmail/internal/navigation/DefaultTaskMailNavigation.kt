package net.thunderbird.feature.taskmail.internal.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import net.thunderbird.core.ui.navigation.deepLinkComposable
import net.thunderbird.feature.taskmail.api.TaskMailNavigation
import net.thunderbird.feature.taskmail.api.TaskMailRoute
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailScreen
import net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskScreen
import net.thunderbird.feature.taskmail.internal.ui.projectsync.TaskProjectSyncScreen
import net.thunderbird.feature.taskmail.internal.ui.settings.TaskMailSettingsScreen
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceScreen

internal class DefaultTaskMailNavigation : TaskMailNavigation {

    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (TaskMailRoute) -> Unit,
    ) {
        registerRoutes(
            navGraphBuilder = navGraphBuilder,
            onBack = onBack,
            onFinish = onFinish,
            onRepoSelected = {},
        )
    }

    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (TaskMailRoute) -> Unit,
        onRepoSelected: (String) -> Unit,
    ) {
        with(navGraphBuilder) {
            deepLinkComposable<TaskMailRoute.Workspace>(TaskMailRoute.Workspace.BASE_PATH) {
                TaskWorkspaceScreen(
                    onOpenSession = { sessionId, threadId ->
                        onFinish(
                            TaskMailRoute.SessionDetail(
                                sessionId = encodeTaskMailSessionId(sessionId),
                                threadId = threadId,
                            ),
                        )
                    },
                    onOpenProjectSync = {
                        onFinish(TaskMailRoute.ProjectSync)
                    },
                    onOpenNewTask = {
                        onFinish(TaskMailRoute.NewTask)
                    },
                )
            }
        }

        with(navGraphBuilder) {
            deepLinkComposable<TaskMailRoute.SessionDetail>(TaskMailRoute.SessionDetail.BASE_PATH) { backStackEntry ->
                val route = backStackEntry.toRoute<TaskMailRoute.SessionDetail>()

                TaskSessionDetailScreen(
                    sessionId = decodeTaskMailSessionId(route.sessionId),
                    threadId = route.threadId,
                    onBack = onBack,
                )
            }

            deepLinkComposable<TaskMailRoute.NewTask>(TaskMailRoute.NewTask.BASE_PATH) { backStackEntry ->
                val selectedRepoPath by backStackEntry.savedStateHandle
                    .getStateFlow<String?>(TASKMAIL_SELECTED_REPO_PATH_RESULT_KEY, null)
                    .collectAsStateWithLifecycle()

                TaskNewTaskScreen(
                    onBack = onBack,
                    onOpenProjectSync = {
                        onFinish(TaskMailRoute.ProjectSync)
                    },
                    selectedRepoPath = selectedRepoPath,
                    onSelectedRepoPathConsumed = {
                        backStackEntry.savedStateHandle.remove<String>(TASKMAIL_SELECTED_REPO_PATH_RESULT_KEY)
                    },
                )
            }

            deepLinkComposable<TaskMailRoute.ProjectSync>(TaskMailRoute.ProjectSync.BASE_PATH) {
                TaskProjectSyncScreen(
                    onBack = onBack,
                    onRepoSelected = onRepoSelected,
                )
            }

            deepLinkComposable<TaskMailRoute.Settings>(TaskMailRoute.Settings.BASE_PATH) {
                TaskMailSettingsScreen(
                    onBack = onBack,
                )
            }
        }
    }
}

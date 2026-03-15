package net.thunderbird.feature.taskmail.internal.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import net.thunderbird.core.ui.navigation.deepLinkComposable
import net.thunderbird.feature.taskmail.api.TaskMailNavigation
import net.thunderbird.feature.taskmail.api.TaskMailRoute
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailScreen
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceScreen

internal class DefaultTaskMailNavigation : TaskMailNavigation {

    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (TaskMailRoute) -> Unit,
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
        }
    }
}

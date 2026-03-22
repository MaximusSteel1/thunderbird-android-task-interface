package net.thunderbird.feature.taskmail.internal.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import net.thunderbird.core.ui.contract.mvi.observe
import net.thunderbird.feature.taskmail.internal.ui.TaskMailForegroundRefreshLifecycleEffect
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun TaskWorkspaceScreen(
    onOpenSession: (workspaceId: String?, sessionId: String?, threadId: String) -> Unit,
    onOpenProjectSync: () -> Unit,
    onOpenNewTask: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskWorkspaceContract.ViewModel = koinViewModel<TaskWorkspaceViewModel>(),
) {
    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            TaskWorkspaceContract.Effect.OpenProjectSync -> onOpenProjectSync()
            TaskWorkspaceContract.Effect.OpenNewTask -> onOpenNewTask()
            is TaskWorkspaceContract.Effect.OpenSessionDetail -> {
                onOpenSession(effect.workspaceId, effect.sessionId, effect.threadId)
            }
        }
    }

    LaunchedEffect(Unit) {
        dispatch(TaskWorkspaceContract.Event.LoadData)
    }

    TaskMailForegroundRefreshLifecycleEffect(
        onStart = { dispatch(TaskWorkspaceContract.Event.ForegroundRefreshStarted) },
        onStop = { dispatch(TaskWorkspaceContract.Event.ForegroundRefreshStopped) },
    )

    TaskWorkspaceContent(
        state = state.value,
        onEvent = dispatch,
        modifier = modifier,
    )
}

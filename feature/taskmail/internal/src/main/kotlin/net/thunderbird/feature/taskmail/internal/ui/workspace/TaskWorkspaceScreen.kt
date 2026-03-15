package net.thunderbird.feature.taskmail.internal.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import net.thunderbird.core.ui.contract.mvi.observe
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun TaskWorkspaceScreen(
    onOpenSession: (sessionId: String?, threadId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskWorkspaceContract.ViewModel = koinViewModel<TaskWorkspaceViewModel>(),
) {
    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            is TaskWorkspaceContract.Effect.OpenSessionDetail -> {
                onOpenSession(effect.sessionId, effect.threadId)
            }
        }
    }

    LaunchedEffect(Unit) {
        dispatch(TaskWorkspaceContract.Event.LoadData)
    }

    TaskWorkspaceContent(
        state = state.value,
        onEvent = dispatch,
        modifier = modifier,
    )
}

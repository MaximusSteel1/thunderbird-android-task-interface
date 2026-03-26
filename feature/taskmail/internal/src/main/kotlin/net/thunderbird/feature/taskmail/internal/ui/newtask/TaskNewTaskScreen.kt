package net.thunderbird.feature.taskmail.internal.ui.newtask

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import net.thunderbird.core.ui.contract.mvi.observe
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun TaskNewTaskScreen(
    onBack: () -> Unit,
    onOpenSession: (workspaceId: String?, sessionId: String) -> Unit,
    onOpenProjectSync: () -> Unit,
    modifier: Modifier = Modifier,
    selectedRepoPath: String? = null,
    onSelectedRepoPathConsumed: () -> Unit = {},
    viewModel: TaskNewTaskContract.ViewModel = koinViewModel<TaskNewTaskViewModel>(),
) {
    val context = LocalContext.current
    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            TaskNewTaskContract.Effect.NavigateBack -> onBack()
            is TaskNewTaskContract.Effect.NavigateToSession -> {
                onOpenSession(effect.workspaceId, effect.sessionId)
            }
            TaskNewTaskContract.Effect.OpenProjectSync -> onOpenProjectSync()
            is TaskNewTaskContract.Effect.ShowMessage -> {
                Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        dispatch(TaskNewTaskContract.Event.LoadData)
    }

    LaunchedEffect(selectedRepoPath) {
        val repoPath = selectedRepoPath ?: return@LaunchedEffect
        dispatch(TaskNewTaskContract.Event.RepoChanged(repoPath))
        onSelectedRepoPathConsumed()
    }

    TaskNewTaskContent(
        state = state.value,
        onEvent = dispatch,
        modifier = modifier,
    )
}

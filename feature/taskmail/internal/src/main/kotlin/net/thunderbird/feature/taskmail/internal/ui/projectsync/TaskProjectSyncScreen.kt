package net.thunderbird.feature.taskmail.internal.ui.projectsync

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import net.thunderbird.core.ui.contract.mvi.observe
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun TaskProjectSyncScreen(
    onBack: () -> Unit,
    onRepoSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskProjectSyncContract.ViewModel = koinViewModel<TaskProjectSyncViewModel>(),
) {
    val context = LocalContext.current
    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            TaskProjectSyncContract.Effect.NavigateBack -> onBack()
            is TaskProjectSyncContract.Effect.ReturnRepo -> onRepoSelected(effect.repoPath)
            is TaskProjectSyncContract.Effect.ShowMessage -> {
                Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        dispatch(TaskProjectSyncContract.Event.LoadData)
    }

    TaskProjectSyncContent(
        state = state.value,
        onEvent = dispatch,
        modifier = modifier,
    )
}

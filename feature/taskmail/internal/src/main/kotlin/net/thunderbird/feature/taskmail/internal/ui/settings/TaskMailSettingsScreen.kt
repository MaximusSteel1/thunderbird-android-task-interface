package net.thunderbird.feature.taskmail.internal.ui.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import net.thunderbird.core.ui.contract.mvi.observe
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun TaskMailSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskMailSettingsContract.ViewModel = koinViewModel<TaskMailSettingsViewModel>(),
) {
    val context = LocalContext.current
    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            TaskMailSettingsContract.Effect.NavigateBack -> onBack()
            is TaskMailSettingsContract.Effect.ShowMessage -> {
                Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        dispatch(TaskMailSettingsContract.Event.LoadData)
    }

    TaskMailSettingsContent(
        state = state.value,
        onEvent = dispatch,
        modifier = modifier,
    )
}

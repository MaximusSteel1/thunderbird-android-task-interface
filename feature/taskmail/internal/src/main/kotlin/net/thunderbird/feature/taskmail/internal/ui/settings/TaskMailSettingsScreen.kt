package net.thunderbird.feature.taskmail.internal.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    val openRelayConfig = remember(context) {
        if (canOpenTaskMailRelayDebug(context)) {
            { launchTaskMailRelayDebug(context) }
        } else {
            null
        }
    }
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
        onOpenRelayConfig = openRelayConfig,
        modifier = modifier,
    )
}

private val TASKMAIL_RELAY_DEBUG_URI: Uri = Uri.parse("app://taskmail/debug/relay")

private fun canOpenTaskMailRelayDebug(context: Context): Boolean {
    return createTaskMailRelayDebugIntent(context).resolveActivity(context.packageManager) != null
}

private fun launchTaskMailRelayDebug(context: Context) {
    runCatching {
        context.startActivity(createTaskMailRelayDebugIntent(context))
    }.onFailure {
        Toast.makeText(context, "Unable to open TaskMail relay config.", Toast.LENGTH_LONG).show()
    }
}

private fun createTaskMailRelayDebugIntent(context: Context): Intent {
    return Intent(Intent.ACTION_VIEW, TASKMAIL_RELAY_DEBUG_URI).apply {
        `package` = context.packageName
    }
}

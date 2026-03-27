package net.thunderbird.feature.taskmail.internal.ui.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.k9mail.core.android.common.activity.CreateDocumentResultContract
import net.thunderbird.core.ui.contract.mvi.observe
import net.thunderbird.feature.taskmail.internal.ui.TaskMailForegroundRefreshLifecycleEffect
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun TaskSessionHistoryScreen(
    workspaceId: String?,
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskSessionDetailContract.ViewModel = koinViewModel<TaskSessionDetailViewModel>(),
) {
    val context = LocalContext.current
    var pendingAttachmentSave by remember { mutableStateOf<String?>(null) }
    val saveAttachmentLauncher = rememberHistorySaveAttachmentLauncher(
        pendingAttachmentId = pendingAttachmentSave,
        onPendingAttachmentConsumed = { pendingAttachmentSave = null },
        onAttachmentSaveSelected = { attachmentId, destinationUriString ->
            viewModel.event(
                TaskSessionDetailContract.Event.AttachmentSaveDestinationSelected(
                    attachmentId = attachmentId,
                    destinationUriString = destinationUriString,
                ),
            )
        },
    )

    val (state, dispatch) = viewModel.observe { effect ->
        pendingAttachmentSave = handleHistoryEffect(
            effect = effect,
            context = context,
            onBack = onBack,
            saveAttachmentLauncher = saveAttachmentLauncher,
            pendingAttachmentSave = pendingAttachmentSave,
        )
    }

    LaunchedEffect(workspaceId, sessionId) {
        dispatch(
            TaskSessionDetailContract.Event.LoadDetail(
                workspaceId = workspaceId,
                sessionId = sessionId,
                preferServerHistoryRounds = true,
            ),
        )
    }

    TaskMailForegroundRefreshLifecycleEffect(
        onStart = { dispatch(TaskSessionDetailContract.Event.ForegroundRefreshStarted) },
        onStop = { dispatch(TaskSessionDetailContract.Event.ForegroundRefreshStopped) },
    )

    TaskSessionHistoryContent(
        state = state.value,
        onBack = onBack,
        onEvent = dispatch,
        modifier = modifier,
    )
}

@Composable
private fun rememberHistorySaveAttachmentLauncher(
    pendingAttachmentId: String?,
    onPendingAttachmentConsumed: () -> Unit,
    onAttachmentSaveSelected: (attachmentId: String, destinationUriString: String) -> Unit,
): ActivityResultLauncher<CreateDocumentResultContract.Input> {
    return rememberLauncherForActivityResult(CreateDocumentResultContract()) { uri ->
        val attachmentId = pendingAttachmentId
        onPendingAttachmentConsumed()

        if (attachmentId != null && uri != null) {
            onAttachmentSaveSelected(
                attachmentId,
                uri.toString(),
            )
        }
    }
}

private fun handleHistoryEffect(
    effect: TaskSessionDetailContract.Effect,
    context: Context,
    onBack: () -> Unit,
    saveAttachmentLauncher: ActivityResultLauncher<CreateDocumentResultContract.Input>,
    pendingAttachmentSave: String?,
): String? {
    return when (effect) {
        TaskSessionDetailContract.Effect.NavigateBack -> {
            onBack()
            pendingAttachmentSave
        }

        is TaskSessionDetailContract.Effect.ShowMessage -> {
            Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
            pendingAttachmentSave
        }

        is TaskSessionDetailContract.Effect.OpenAttachment -> {
            runCatching {
                context.startActivity(effect.intent)
            }.onFailure {
                Toast.makeText(context, "No app can open this attachment.", Toast.LENGTH_LONG).show()
            }
            pendingAttachmentSave
        }

        is TaskSessionDetailContract.Effect.CreateAttachmentDocument -> {
            runCatching {
                saveAttachmentLauncher.launch(
                    CreateDocumentResultContract.Input(
                        title = effect.displayName,
                        mimeType = effect.mimeType,
                    ),
                )
                effect.attachmentId
            }.getOrElse {
                Toast.makeText(context, "Unable to open the save dialog.", Toast.LENGTH_LONG).show()
                null
            }
        }

        is TaskSessionDetailContract.Effect.ShowAttachmentActionError -> {
            Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
            pendingAttachmentSave
        }
    }
}

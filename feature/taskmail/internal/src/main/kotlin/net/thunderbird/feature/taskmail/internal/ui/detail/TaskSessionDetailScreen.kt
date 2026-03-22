package net.thunderbird.feature.taskmail.internal.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun TaskSessionDetailScreen(
    sessionId: String?,
    threadId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskSessionDetailContract.ViewModel = koinViewModel<TaskSessionDetailViewModel>(),
) {
    val context = LocalContext.current
    var pendingAttachmentSave by remember { mutableStateOf<String?>(null) }
    val attachmentPicker = rememberAttachmentPicker(
        context = context,
        onAttachmentsSelected = { uris ->
            viewModel.event(
                TaskSessionDetailContract.Event.AttachmentsSelected(
                    uriStrings = uris.map(Uri::toString),
                ),
            )
        },
    )
    val saveAttachmentLauncher = rememberSaveAttachmentLauncher(
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
        pendingAttachmentSave = handleDetailEffect(
            effect = effect,
            context = context,
            onBack = onBack,
            saveAttachmentLauncher = saveAttachmentLauncher,
            pendingAttachmentSave = pendingAttachmentSave,
        )
    }

    LaunchedEffect(sessionId, threadId) {
        dispatch(
            TaskSessionDetailContract.Event.LoadDetail(
                sessionId = sessionId,
                threadId = threadId,
            ),
        )
    }

    TaskMailForegroundRefreshLifecycleEffect(
        onStart = { dispatch(TaskSessionDetailContract.Event.ForegroundRefreshStarted) },
        onStop = { dispatch(TaskSessionDetailContract.Event.ForegroundRefreshStopped) },
    )

    TaskSessionDetailContent(
        state = state.value,
        onEvent = dispatch,
        onPickAttachments = {
            attachmentPicker.launch(arrayOf("*/*"))
        },
        modifier = modifier,
    )
}

@Composable
private fun rememberAttachmentPicker(
    context: Context,
    onAttachmentsSelected: (List<Uri>) -> Unit,
): ActivityResultLauncher<Array<String>> {
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            persistAttachmentPermissions(context = context, uris = uris)
            onAttachmentsSelected(uris)
        }
    }
}

@Composable
private fun rememberSaveAttachmentLauncher(
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

private fun persistAttachmentPermissions(
    context: Context,
    uris: List<Uri>,
) {
    uris.forEach { uri ->
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}

private fun handleDetailEffect(
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
            openTimelineAttachment(context = context, intent = effect.intent)
            pendingAttachmentSave
        }

        is TaskSessionDetailContract.Effect.CreateAttachmentDocument -> {
            launchSaveDialog(
                context = context,
                effect = effect,
                saveAttachmentLauncher = saveAttachmentLauncher,
            )
        }

        is TaskSessionDetailContract.Effect.ShowAttachmentActionError -> {
            Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
            pendingAttachmentSave
        }
    }
}

private fun openTimelineAttachment(
    context: Context,
    intent: Intent,
) {
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "No app can open this attachment.", Toast.LENGTH_LONG).show()
    }
}

private fun launchSaveDialog(
    context: Context,
    effect: TaskSessionDetailContract.Effect.CreateAttachmentDocument,
    saveAttachmentLauncher: ActivityResultLauncher<CreateDocumentResultContract.Input>,
): String? {
    return runCatching {
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

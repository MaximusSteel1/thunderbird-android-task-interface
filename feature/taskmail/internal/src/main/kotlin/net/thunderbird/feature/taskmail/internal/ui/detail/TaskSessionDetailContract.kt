package net.thunderbird.feature.taskmail.internal.ui.detail

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotRound
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission

internal interface TaskSessionDetailContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val refreshError: String? = null,
        val isHistoryVisible: Boolean = false,
        val isGuideComposerVisible: Boolean = false,
        val draftText: String = "",
        val isSending: Boolean = false,
        val sendError: String? = null,
        val historySnapshotError: String? = null,
        val latestDirectSessionActionRecord: TaskMailSessionActionSendRecord? = null,
        val replyAttachments: ImmutableList<TaskReplyAttachment> = persistentListOf(),
        val selectedReplyPermission: TaskMailNewTaskPermission = TaskMailNewTaskPermission.Default,
        val historySnapshotRounds: ImmutableList<TaskSessionHistorySnapshotRound> = persistentListOf(),
        val detail: TaskSessionDetailUiState? = null,
    )

    sealed interface Event {
        data class LoadDetail(
            val workspaceId: String?,
            val sessionId: String,
            val preferServerHistoryRounds: Boolean = false,
        ) : Event

        data class DraftChanged(val text: String) : Event
        data class AttachmentsSelected(val uriStrings: List<String>) : Event
        data class RemoveAttachmentClicked(val attachmentId: String) : Event
        data class OpenTimelineAttachmentClicked(val attachmentId: String) : Event
        data class SaveTimelineAttachmentClicked(val attachmentId: String) : Event
        data class AttachmentSaveDestinationSelected(
            val attachmentId: String,
            val destinationUriString: String,
        ) : Event
        data object ForegroundRefreshStarted : Event
        data object ForegroundRefreshStopped : Event
        data object SendReplyClicked : Event
        data class SendChoiceClicked(val choice: String) : Event
        data object StatusQueryClicked : Event
        data class ReplyPermissionChanged(val permission: TaskMailNewTaskPermission) : Event
        data object GuideClicked : Event
        data object GuideDismissed : Event
        data object ResumeClicked : Event
        data object StopRunningClicked : Event
        data object DeactivateClicked : Event
        data object RefreshClicked : Event
        data object HistoryClicked : Event
        data object HistoryDismissed : Event
        data object DismissSendError : Event
        data object BackClicked : Event
    }

    sealed interface Effect {
        data object NavigateBack : Effect
        data class ShowMessage(val message: String) : Effect
        data class OpenAttachment(val intent: android.content.Intent) : Effect
        data class CreateAttachmentDocument(
            val attachmentId: String,
            val displayName: String,
            val mimeType: String,
        ) : Effect
        data class ShowAttachmentActionError(val message: String) : Effect
    }
}

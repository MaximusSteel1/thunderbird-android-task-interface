package net.thunderbird.feature.taskmail.internal.ui.newtask

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskMode
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission

internal interface TaskNewTaskContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val isLoading: Boolean = false,
        val senderAccountBlockingError: String? = null,
        val senderAccounts: ImmutableList<TaskMailSenderAccount> = persistentListOf(),
        val selectedSenderAccountId: String? = null,
        val pcSelection: TaskNewTaskPcSelectionUiState = TaskNewTaskPcSelectionUiState(),
        val workspaceSelection: TaskNewTaskWorkspaceSelectionUiState = TaskNewTaskWorkspaceSelectionUiState(),
        val taskInput: TaskNewTaskTaskInputUiState = TaskNewTaskTaskInputUiState(),
        val executionPolicyEditor: TaskNewTaskExecutionPolicyUiState = TaskNewTaskExecutionPolicyUiState(),
        val submitState: TaskNewTaskSubmitUiState = TaskNewTaskSubmitUiState(),
        val validationErrors: TaskNewTaskValidationErrors = TaskNewTaskValidationErrors(),
        val lastDirectSendEvidence: TaskMailDirectSendEvidence? = null,
    ) {
        val selectedSenderAccount: TaskMailSenderAccount?
            get() = senderAccounts.firstOrNull { it.accountUuid == selectedSenderAccountId }

        val selectedWorkspaceOption: TaskNewTaskWorkspaceOptionUi?
            get() = workspaceSelection.workspaceOptions.firstOrNull { it.id == workspaceSelection.selectedWorkspaceId }

        val resolvedRepoBridgePath: String?
            get() = workspaceSelection.repoPath.trim().takeIf(String::isNotEmpty)
                ?: selectedWorkspaceOption?.repoPath?.trim()?.takeIf(String::isNotEmpty)

        val resolvedWorkdirBridge: String?
            get() = workspaceSelection.workdir.trim().takeIf(String::isNotEmpty)
                ?: selectedWorkspaceOption?.workdir?.trim()?.takeIf(String::isNotEmpty)

        val hasControlPlaneRouteTarget: Boolean
            get() = pcSelection.selectedPcId.isNotBlank() || workspaceSelection.selectedWorkspaceId.isNotBlank()

        val requiresSenderAccountSelection: Boolean
            get() = senderAccounts.size > 1

        val hasBlockingState: Boolean
            get() = !isLoading && senderAccountBlockingError != null

        val canSend: Boolean
            get() = !isLoading && !submitState.isSending && !hasBlockingState
    }

    sealed interface Event {
        data object LoadData : Event
        data object BackClicked : Event
        data object ChooseRepoClicked : Event
        data class SenderAccountSelected(val accountUuid: String?) : Event
        data class PcChanged(val value: String) : Event
        data class WorkspaceChanged(val value: String) : Event
        data class BackendSelected(val backend: TaskMailBackend) : Event
        data class RepoChanged(val value: String) : Event
        data class TaskChanged(val value: String) : Event
        data class SubjectTitleChanged(val value: String) : Event
        data object AdvancedToggleClicked : Event
        data class WorkdirChanged(val value: String) : Event
        data class ModeChanged(val mode: TaskMailNewTaskMode) : Event
        data class TimeoutChanged(val value: String) : Event
        data class PermissionChanged(val permission: TaskMailNewTaskPermission) : Event
        data class ProfileChanged(val value: String) : Event
        data class AcceptanceChanged(val value: String) : Event
        data object SendClicked : Event
        data object DismissSendError : Event
    }

    sealed interface Effect {
        data object NavigateBack : Effect
        data object OpenProjectSync : Effect
        data class ShowMessage(val message: String) : Effect
    }
}

@Immutable
internal data class TaskNewTaskPcSelectionUiState(
    val selectedPcId: String = "",
    val pcOptions: ImmutableList<TaskNewTaskPcOptionUi> = persistentListOf(),
)

@Immutable
internal data class TaskNewTaskPcOptionUi(
    val id: String,
    val title: String = id,
    val supportingText: String? = null,
)

@Immutable
internal data class TaskNewTaskWorkspaceSelectionUiState(
    val selectedWorkspaceId: String = "",
    val workspaceOptions: ImmutableList<TaskNewTaskWorkspaceOptionUi> = persistentListOf(),
    val repoPath: String = "",
    val workdir: String = "",
)

@Immutable
internal data class TaskNewTaskWorkspaceOptionUi(
    val id: String,
    val title: String = id,
    val supportingText: String? = null,
    val repoPath: String? = null,
    val workdir: String? = null,
)

@Immutable
internal data class TaskNewTaskTaskInputUiState(
    val taskText: String = "",
    val subjectTitle: String = "",
    val isSubjectTitleEdited: Boolean = false,
)

@Immutable
internal data class TaskNewTaskExecutionPolicyUiState(
    val backend: TaskMailBackend? = null,
    val mode: TaskMailNewTaskMode = TaskMailNewTaskMode.Modify,
    val timeoutText: String = "",
    val permission: TaskMailNewTaskPermission = TaskMailNewTaskPermission.Default,
    val profile: String = "",
    val backendTransport: String = "",
    val acceptanceText: String = "",
    val isExpanded: Boolean = false,
)

@Immutable
internal data class TaskNewTaskSubmitUiState(
    val isSending: Boolean = false,
    val sendError: String? = null,
)

@Immutable
internal data class TaskNewTaskValidationErrors(
    val senderAccountError: String? = null,
    val backendError: String? = null,
    val repoError: String? = null,
    val taskError: String? = null,
    val titleError: String? = null,
    val timeoutError: String? = null,
)

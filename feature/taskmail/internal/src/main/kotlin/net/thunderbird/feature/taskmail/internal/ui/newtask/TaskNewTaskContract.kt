package net.thunderbird.feature.taskmail.internal.ui.newtask

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
        val selectedBackend: TaskMailBackend? = null,
        val repoPath: String = "",
        val taskText: String = "",
        val subjectTitle: String = "",
        val isSubjectTitleEdited: Boolean = false,
        val workdir: String = "",
        val mode: TaskMailNewTaskMode = TaskMailNewTaskMode.Modify,
        val timeoutText: String = "",
        val permission: TaskMailNewTaskPermission = TaskMailNewTaskPermission.Default,
        val profile: String = "",
        val acceptanceText: String = "",
        val isAdvancedExpanded: Boolean = false,
        val isSending: Boolean = false,
        val sendError: String? = null,
        val lastDirectSendEvidence: TaskMailDirectSendEvidence? = null,
        val senderAccountError: String? = null,
        val backendError: String? = null,
        val repoError: String? = null,
        val taskError: String? = null,
        val titleError: String? = null,
        val timeoutError: String? = null,
    ) {
        val selectedSenderAccount: TaskMailSenderAccount?
            get() = senderAccounts.firstOrNull { it.accountUuid == selectedSenderAccountId }

        val requiresSenderAccountSelection: Boolean
            get() = senderAccounts.size > 1

        val hasBlockingState: Boolean
            get() = !isLoading && senderAccountBlockingError != null

        val canSend: Boolean
            get() = !isLoading && !isSending && !hasBlockingState
    }

    sealed interface Event {
        data object LoadData : Event
        data object BackClicked : Event
        data object ChooseRepoClicked : Event
        data class SenderAccountSelected(val accountUuid: String?) : Event
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

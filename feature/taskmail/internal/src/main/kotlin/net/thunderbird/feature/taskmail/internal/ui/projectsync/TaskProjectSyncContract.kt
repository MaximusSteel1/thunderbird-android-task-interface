package net.thunderbird.feature.taskmail.internal.ui.projectsync

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount

internal interface TaskProjectSyncContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val isLoading: Boolean = false,
        val isSyncing: Boolean = false,
        val isLoadingResult: Boolean = false,
        val pendingSyncRequestStartedAt: Long? = null,
        val canRetryWithMail: Boolean = false,
        val senderAccountBlockingError: String? = null,
        val senderAccounts: ImmutableList<TaskMailSenderAccount> = persistentListOf(),
        val selectedSenderAccountId: String? = null,
        val senderAccountError: String? = null,
        val syncError: String? = null,
        val resultError: String? = null,
        val latestResult: TaskMailProjectSyncResult? = null,
    ) {
        val selectedSenderAccount: TaskMailSenderAccount?
            get() = senderAccounts.firstOrNull { it.accountUuid == selectedSenderAccountId }

        val requiresSenderAccountSelection: Boolean
            get() = senderAccounts.size > 1

        val hasBlockingState: Boolean
            get() = !isLoading && senderAccountBlockingError != null

        val isAwaitingFreshResult: Boolean
            get() = pendingSyncRequestStartedAt != null

        val canRequestSync: Boolean
            get() = !isLoading && !isSyncing && !hasBlockingState && !isAwaitingFreshResult
    }

    sealed interface Event {
        data object LoadData : Event
        data object BackClicked : Event
        data class SenderAccountSelected(val accountUuid: String?) : Event
        data object SyncRequested : Event
        data object MailRetryRequested : Event
        data class UseRepoClicked(val repoPath: String) : Event
        data object DismissSyncError : Event
    }

    sealed interface Effect {
        data object NavigateBack : Effect
        data class ReturnRepo(val repoPath: String) : Effect
        data class ShowMessage(val message: String) : Effect
    }
}

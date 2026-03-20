package net.thunderbird.feature.taskmail.internal.ui.projectsync

import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetLatestTaskMailProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailSenderAccounts
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailStoreChanges
import net.thunderbird.feature.taskmail.internal.domain.usecase.RefreshTaskMail
import net.thunderbird.feature.taskmail.internal.domain.usecase.RequestTaskMailProjectSync
import net.thunderbird.feature.taskmail.internal.ui.projectsync.TaskProjectSyncContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.projectsync.TaskProjectSyncContract.Event
import net.thunderbird.feature.taskmail.internal.ui.projectsync.TaskProjectSyncContract.State

private const val NO_SENDER_ACCOUNT_MESSAGE =
    "Set up a mailbox account before requesting the TaskMail project list."
private const val LOAD_SENDER_ACCOUNTS_ERROR =
    "Unable to load mailbox accounts for TaskMail project sync."
private const val SENDER_ACCOUNT_REQUIRED_ERROR = "Select the mailbox account to sync."
private const val RESULT_LOAD_ERROR = "Unable to load the latest TaskMail project list."
private const val SYNC_REQUEST_FAILURE = "Failed to send TaskMail project sync request."
private const val SYNC_REQUEST_SUCCESS =
    "Project list sync requested. The repo list updates when the [SYNC] reply arrives."

internal class TaskProjectSyncViewModel(
    private val getTaskMailSenderAccounts: GetTaskMailSenderAccounts,
    private val getLatestTaskMailProjectSyncResult: GetLatestTaskMailProjectSyncResult,
    private val requestTaskMailProjectSync: RequestTaskMailProjectSync,
    private val refreshTaskMail: RefreshTaskMail,
    private val observeTaskMailStoreChanges: ObserveTaskMailStoreChanges,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskProjectSyncContract.ViewModel {

    private var hasLoadedAccounts = false

    init {
        observeLocalMailChanges()
    }

    override fun event(event: Event) {
        when (event) {
            Event.LoadData -> handleOneTimeEvent(event, ::loadSenderAccounts)
            Event.BackClicked -> emitEffect(Effect.NavigateBack)
            Event.SyncRequested -> requestSync()
            Event.DismissSyncError -> updateState { it.copy(syncError = null) }
            is Event.SenderAccountSelected -> handleSenderAccountSelected(event.accountUuid)
            is Event.UseRepoClicked -> emitEffect(Effect.ReturnRepo(event.repoPath))
        }
    }

    private fun observeLocalMailChanges() {
        viewModelScope.launch {
            observeTaskMailStoreChanges()
                .conflate()
                .collect {
                    val selectedAccountId = state.value.selectedSenderAccountId ?: return@collect
                    if (hasLoadedAccounts && !state.value.isLoading && !state.value.isSyncing) {
                        loadLatestResult(accountUuid = selectedAccountId, clearCurrentResult = false)
                    }
                }
        }
    }

    private fun loadSenderAccounts() {
        updateState {
            it.copy(
                isLoading = true,
                senderAccountBlockingError = null,
                senderAccountError = null,
                syncError = null,
                resultError = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                getTaskMailSenderAccounts()
            }.onSuccess { accounts ->
                hasLoadedAccounts = true
                val selectedAccountId = resolveSelectedSenderAccountId(
                    existingSelection = state.value.selectedSenderAccountId,
                    accounts = accounts,
                )
                updateState { current ->
                    current.copy(
                        isLoading = false,
                        senderAccountBlockingError = accounts.blockingErrorOrNull(),
                        senderAccounts = accounts.toImmutableList(),
                        selectedSenderAccountId = selectedAccountId,
                        senderAccountError = null,
                    )
                }
                if (selectedAccountId != null) {
                    loadLatestResult(accountUuid = selectedAccountId, clearCurrentResult = true)
                }
            }.onFailure {
                updateState {
                    it.copy(
                        isLoading = false,
                        senderAccountBlockingError = LOAD_SENDER_ACCOUNTS_ERROR,
                        senderAccounts = persistentListOf(),
                        selectedSenderAccountId = null,
                        senderAccountError = null,
                        latestResult = null,
                    )
                }
            }
        }
    }

    private fun handleSenderAccountSelected(accountUuid: String?) {
        updateState {
            it.copy(
                selectedSenderAccountId = accountUuid,
                senderAccountError = null,
                syncError = null,
                resultError = null,
            )
        }

        if (accountUuid != null) {
            loadLatestResult(accountUuid = accountUuid, clearCurrentResult = true)
        } else {
            updateState {
                it.copy(
                    latestResult = null,
                    resultError = null,
                )
            }
        }
    }

    private fun loadLatestResult(
        accountUuid: String,
        clearCurrentResult: Boolean,
    ) {
        updateState {
            it.copy(
                isLoadingResult = true,
                resultError = null,
                latestResult = if (clearCurrentResult) null else it.latestResult,
            )
        }

        viewModelScope.launch {
            runCatching {
                getLatestTaskMailProjectSyncResult(accountUuid)
            }.onSuccess { result ->
                updateState {
                    it.copy(
                        isLoadingResult = false,
                        resultError = null,
                        latestResult = result,
                    )
                }
            }.onFailure {
                updateState {
                    it.copy(
                        isLoadingResult = false,
                        resultError = RESULT_LOAD_ERROR,
                        latestResult = null,
                    )
                }
            }
        }
    }

    private fun requestSync() {
        val currentState = state.value
        if (!currentState.canRequestSync) return

        val accountUuid = resolveSelectedAccountIdForSync(currentState) ?: return

        updateState {
            it.copy(
                isSyncing = true,
                syncError = null,
                senderAccountError = null,
            )
        }

        viewModelScope.launch {
            val requestResult = requestTaskMailProjectSync(accountUuid)
            if (requestResult.isFailure) {
                updateState {
                    it.copy(
                        isSyncing = false,
                        syncError = requestResult.exceptionOrNull()?.message
                            ?.takeIf(String::isNotBlank)
                            ?: SYNC_REQUEST_FAILURE,
                    )
                }
            } else {
                val refreshError = refreshTaskMail()
                    .exceptionOrNull()
                    ?.message
                    ?.takeIf(String::isNotBlank)
                loadLatestResult(accountUuid = accountUuid, clearCurrentResult = false)
                updateState {
                    it.copy(
                        isSyncing = false,
                        syncError = refreshError,
                    )
                }
                emitEffect(Effect.ShowMessage(SYNC_REQUEST_SUCCESS))
            }
        }
    }

    private fun resolveSelectedAccountIdForSync(state: State): String? {
        val selectedAccountId = state.selectedSenderAccountId
            ?.takeIf { accountUuid ->
                state.senderAccounts.any { it.accountUuid == accountUuid }
            }

        return if (state.requiresSenderAccountSelection && selectedAccountId == null) {
            updateState { it.copy(senderAccountError = SENDER_ACCOUNT_REQUIRED_ERROR) }
            null
        } else {
            selectedAccountId
        }
    }

    private fun resolveSelectedSenderAccountId(
        existingSelection: String?,
        accounts: List<TaskMailSenderAccount>,
    ): String? {
        return when {
            accounts.isEmpty() -> null
            accounts.size == 1 -> accounts.single().accountUuid
            existingSelection != null && accounts.any { it.accountUuid == existingSelection } -> existingSelection
            else -> null
        }
    }
}

private fun List<TaskMailSenderAccount>.blockingErrorOrNull(): String? {
    return if (isEmpty()) {
        NO_SENDER_ACCOUNT_MESSAGE
    } else {
        null
    }
}

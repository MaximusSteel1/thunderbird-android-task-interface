package net.thunderbird.feature.taskmail.internal.ui.projectsync

import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.data.debug.NoOpTaskMailProjectSyncDebugRecorder
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailProjectSyncDebugRecorder
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncResult
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
private const val MAIL_RETRY_REQUEST_FAILURE =
    "Failed to send TaskMail project sync compatibility mail retry."
private const val MAIL_RETRY_REQUEST_SUCCESS =
    "Compatibility mail retry requested. The repo list updates when the next [SYNC] reply arrives."
private const val FIRST_POST_SYNC_FOLLOW_UP_DELAY_MILLIS = 30_000L
private const val SECOND_POST_SYNC_FOLLOW_UP_DELAY_MILLIS = 60_000L
private val POST_SYNC_FOLLOW_UP_DELAYS_MILLIS = listOf(
    FIRST_POST_SYNC_FOLLOW_UP_DELAY_MILLIS,
    SECOND_POST_SYNC_FOLLOW_UP_DELAY_MILLIS,
)

@Suppress("TooManyFunctions")
internal class TaskProjectSyncViewModel(
    private val getTaskMailSenderAccounts: GetTaskMailSenderAccounts,
    private val getLatestTaskMailProjectSyncResult: GetLatestTaskMailProjectSyncResult,
    private val requestTaskMailProjectSync: RequestTaskMailProjectSync,
    private val refreshTaskMail: RefreshTaskMail,
    private val observeTaskMailStoreChanges: ObserveTaskMailStoreChanges,
    private val enablePostSyncFollowUpRefresh: Boolean = false,
    private val postSyncFollowUpDelaysMillis: List<Long> = POST_SYNC_FOLLOW_UP_DELAYS_MILLIS,
    private val currentTimeProvider: () -> Long = System::currentTimeMillis,
    private val debugRecorder: TaskMailProjectSyncDebugRecorder = NoOpTaskMailProjectSyncDebugRecorder,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskProjectSyncContract.ViewModel {

    private var hasLoadedAccounts = false
    private var pendingFollowUpRefreshJob: Job? = null

    init {
        observeLocalMailChanges()
    }

    override fun event(event: Event) {
        when (event) {
            Event.LoadData -> handleOneTimeEvent(event, ::loadSenderAccounts)
            Event.BackClicked -> emitEffect(Effect.NavigateBack)
            Event.SyncRequested -> requestSync()
            Event.MailRetryRequested -> requestSyncViaMail()
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
                pendingSyncRequestStartedAt = null,
                canRetryWithMail = false,
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
                        pendingSyncRequestStartedAt = null,
                        canRetryWithMail = false,
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
                        pendingSyncRequestStartedAt = null,
                        canRetryWithMail = false,
                        senderAccountError = null,
                        latestResult = null,
                    )
                }
            }
        }
    }

    private fun handleSenderAccountSelected(accountUuid: String?) {
        pendingFollowUpRefreshJob?.cancel()
        updateState {
            it.copy(
                selectedSenderAccountId = accountUuid,
                pendingSyncRequestStartedAt = null,
                canRetryWithMail = false,
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
                    pendingSyncRequestStartedAt = null,
                    canRetryWithMail = false,
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
                    val pendingSyncRequestStartedAt = it.pendingSyncRequestStartedAt
                        .clearIfFreshResult(result)
                    it.copy(
                        isLoadingResult = false,
                        resultError = null,
                        latestResult = result,
                        pendingSyncRequestStartedAt = pendingSyncRequestStartedAt,
                        canRetryWithMail = it.canRetryWithMail && pendingSyncRequestStartedAt != null,
                    )
                }
                if (state.value.pendingSyncRequestStartedAt == null && result != null) {
                    debugRecorder.record(
                        event = "project_sync_fresh_result_observed",
                        "receivedAt" to result.receivedAt,
                        "scannedAt" to result.scannedAt,
                    )
                }
            }.onFailure {
                updateState {
                    it.copy(
                        isLoadingResult = false,
                        resultError = RESULT_LOAD_ERROR,
                        latestResult = if (clearCurrentResult) null else it.latestResult,
                    )
                }
            }
        }
    }

    private fun requestSync() {
        val currentState = state.value
        if (!currentState.canRequestSync) return

        val accountUuid = resolveSelectedAccountIdForSync(currentState) ?: return
        val requestStartedAt = currentTimeProvider()
        pendingFollowUpRefreshJob?.cancel()
        debugRecorder.record(
            event = "project_sync_ui_request_started",
            "requestStartedAt" to requestStartedAt,
        )

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
                handleRequestSyncFailure(
                    requestResult = requestResult,
                    requestStartedAt = requestStartedAt,
                )
            } else {
                handleRequestSyncSuccess(
                    accountUuid = accountUuid,
                    requestStartedAt = requestStartedAt,
                )
            }
        }
    }

    private fun requestSyncViaMail() {
        val mailRetryRequest = resolveMailRetryRequest(state.value) ?: return
        pendingFollowUpRefreshJob?.cancel()
        debugRecorder.record(
            event = "project_sync_manual_mail_retry_clicked",
            "requestStartedAt" to mailRetryRequest.requestStartedAt,
        )

        updateState {
            it.copy(
                isSyncing = true,
                canRetryWithMail = false,
                syncError = null,
                senderAccountError = null,
            )
        }

        viewModelScope.launch {
            val mailRetryResult = requestTaskMailProjectSync.viaMail(mailRetryRequest.accountUuid)
            if (mailRetryResult.isFailure) {
                debugRecorder.record(
                    event = "project_sync_manual_mail_retry_failed",
                    "requestStartedAt" to mailRetryRequest.requestStartedAt,
                    "errorMessage" to mailRetryResult.exceptionOrNull()?.message,
                )
                updateState {
                    it.copy(
                        isSyncing = false,
                        canRetryWithMail = it.pendingSyncRequestStartedAt != null,
                        syncError = mailRetryResult.exceptionOrNull()?.message
                            ?.takeIf(String::isNotBlank)
                            ?: MAIL_RETRY_REQUEST_FAILURE,
                    )
                }
            } else {
                refreshTaskMail()
                updateState {
                    it.copy(
                        isSyncing = false,
                        canRetryWithMail = false,
                    )
                }
                debugRecorder.record(
                    event = "project_sync_manual_mail_retry_waiting",
                    "requestStartedAt" to mailRetryRequest.requestStartedAt,
                )
                loadLatestResult(
                    accountUuid = mailRetryRequest.accountUuid,
                    clearCurrentResult = false,
                )
                schedulePostSyncFollowUpRefresh(
                    accountUuid = mailRetryRequest.accountUuid,
                    requestStartedAt = mailRetryRequest.requestStartedAt,
                    showMailRetryAction = false,
                )
                emitEffect(Effect.ShowMessage(MAIL_RETRY_REQUEST_SUCCESS))
            }
        }
    }

    private suspend fun handleRequestSyncFailure(
        requestResult: Result<Unit>,
        requestStartedAt: Long,
    ) {
        debugRecorder.record(
            event = "project_sync_ui_request_failed",
            "requestStartedAt" to requestStartedAt,
            "errorMessage" to requestResult.exceptionOrNull()?.message,
        )
        updateState {
            it.copy(
                isSyncing = false,
                pendingSyncRequestStartedAt = null,
                canRetryWithMail = false,
                syncError = requestResult.exceptionOrNull()?.message
                    ?.takeIf(String::isNotBlank)
                    ?: SYNC_REQUEST_FAILURE,
            )
        }
    }

    private suspend fun handleRequestSyncSuccess(
        accountUuid: String,
        requestStartedAt: Long,
    ) {
        refreshTaskMail()
        updateState {
            it.copy(
                isSyncing = false,
                pendingSyncRequestStartedAt = requestStartedAt,
                canRetryWithMail = false,
            )
        }
        debugRecorder.record(
            event = "project_sync_waiting_for_result_started",
            "requestStartedAt" to requestStartedAt,
        )
        loadLatestResult(
            accountUuid = accountUuid,
            clearCurrentResult = false,
        )
        schedulePostSyncFollowUpRefresh(
            accountUuid = accountUuid,
            requestStartedAt = requestStartedAt,
            showMailRetryAction = true,
        )
        emitEffect(Effect.ShowMessage(SYNC_REQUEST_SUCCESS))
    }

    private fun schedulePostSyncFollowUpRefresh(
        accountUuid: String,
        requestStartedAt: Long,
        showMailRetryAction: Boolean,
    ) {
        if (!enablePostSyncFollowUpRefresh || postSyncFollowUpDelaysMillis.isEmpty()) return

        pendingFollowUpRefreshJob = viewModelScope.launch {
            postSyncFollowUpDelaysMillis.forEachIndexed { index, delayMillis ->
                delay(delayMillis)

                val currentState = state.value
                if (currentState.selectedSenderAccountId != accountUuid) return@launch
                if (currentState.pendingSyncRequestStartedAt != requestStartedAt) return@launch

                val latestResult = currentState.latestResult
                if (latestResult != null && latestResult.receivedAt >= requestStartedAt) {
                    return@launch
                }

                if (showMailRetryAction && index == 0) {
                    debugRecorder.record(
                        event = "project_sync_manual_mail_retry_available",
                        "requestStartedAt" to requestStartedAt,
                    )
                    updateState { it.copy(canRetryWithMail = true) }
                }

                debugRecorder.record(
                    event = "project_sync_follow_up_refresh_triggered",
                    "requestStartedAt" to requestStartedAt,
                    "followUpIndex" to index,
                    "delayMillis" to delayMillis,
                )
                refreshTaskMail()
                loadLatestResult(accountUuid = accountUuid, clearCurrentResult = false)
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

    private fun resolveMailRetryRequest(state: State): MailRetryRequest? {
        if (!state.canRetryWithMail) return null

        val accountUuid = resolveSelectedAccountIdForSync(state)
        val requestStartedAt = state.pendingSyncRequestStartedAt

        return if (accountUuid != null && requestStartedAt != null) {
            MailRetryRequest(
                accountUuid = accountUuid,
                requestStartedAt = requestStartedAt,
            )
        } else {
            null
        }
    }
}

private data class MailRetryRequest(
    val accountUuid: String,
    val requestStartedAt: Long,
)

private fun Long?.clearIfFreshResult(result: TaskMailProjectSyncResult?): Long? {
    return if (this != null && result != null && result.receivedAt >= this) {
        null
    } else {
        this
    }
}

private fun List<TaskMailSenderAccount>.blockingErrorOrNull(): String? {
    return if (isEmpty()) {
        NO_SENDER_ACCOUNT_MESSAGE
    } else {
        null
    }
}

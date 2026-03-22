package net.thunderbird.feature.taskmail.internal.ui.workspace

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.data.TaskMailForegroundRefreshTickerFactory
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionSummary
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailSenderAccounts
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskWorkspaceSummaries
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailStoreChanges
import net.thunderbird.feature.taskmail.internal.domain.usecase.RefreshTaskMail
import net.thunderbird.feature.taskmail.internal.domain.usecase.SyncTaskMailCache
import net.thunderbird.feature.taskmail.internal.ui.toDisplayWorkdir
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceContract.Event
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceContract.State

internal class TaskWorkspaceViewModel(
    repository: TaskMailRepository,
    private val getTaskWorkspaceSummaries: GetTaskWorkspaceSummaries = GetTaskWorkspaceSummaries(repository),
    private val getTaskMailSenderAccounts: GetTaskMailSenderAccounts,
    private val refreshTaskMail: RefreshTaskMail,
    private val observeTaskMailStoreChanges: ObserveTaskMailStoreChanges,
    private val foregroundRefreshTickerFactory: TaskMailForegroundRefreshTickerFactory,
    private val syncTaskMailCache: SyncTaskMailCache? = null,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskWorkspaceContract.ViewModel {

    private var hasLoadedData = false
    private val loadMutex = Mutex()
    private val syncMutex = Mutex()
    private var foregroundRefreshJob: Job? = null

    init {
        observeLocalMailChanges()
    }

    override fun event(event: Event) {
        when (event) {
            Event.LoadData -> loadData(
                force = false,
                refreshTransportBeforeLoad = false,
                syncCacheBeforeLoad = !hasLoadedData,
            )

            Event.RefreshRequested -> loadData(
                force = true,
                refreshTransportBeforeLoad = true,
                syncCacheBeforeLoad = true,
            )

            Event.ForegroundRefreshStarted -> {
                if (hasLoadedData) {
                    loadData(
                        force = true,
                        refreshTransportBeforeLoad = false,
                        syncCacheBeforeLoad = false,
                    )
                }
                setForegroundRefreshEnabled(enabled = true)
            }
            Event.ForegroundRefreshStopped -> setForegroundRefreshEnabled(enabled = false)
            Event.RetryClicked -> loadData(
                force = true,
                refreshTransportBeforeLoad = false,
                syncCacheBeforeLoad = true,
            )

            Event.ProjectListClicked -> emitEffect(Effect.OpenProjectSync)
            Event.NewTaskClicked -> emitEffect(Effect.OpenNewTask)
            is Event.SessionClicked -> {
                emitEffect(
                    Effect.OpenSessionDetail(
                        workspaceId = event.workspaceId,
                        sessionId = event.sessionId,
                        threadId = event.threadId,
                    ),
                )
            }
        }
    }

    private fun observeLocalMailChanges() {
        viewModelScope.launch {
            observeTaskMailStoreChanges()
                .conflate()
                .collect {
                    if (hasLoadedData || state.value.isLoading || state.value.workspaces.isNotEmpty()) {
                        loadData(
                            force = true,
                            refreshTransportBeforeLoad = false,
                            syncCacheBeforeLoad = true,
                        )
                    }
                }
        }
    }

    private fun loadData(
        force: Boolean,
        refreshTransportBeforeLoad: Boolean,
        syncCacheBeforeLoad: Boolean,
    ) {
        if (shouldSkipLoad(force)) return

        viewModelScope.launch {
            loadMutex.withLock {
                if (shouldSkipLoad(force)) return@withLock

                val cachedWorkspaces = runCatching {
                    getTaskWorkspaceSummaries()
                }.getOrNull().orEmpty()
                if (
                    shouldRenderCachedWorkspacesBeforeSync(
                        force = force,
                        refreshTransportBeforeLoad = refreshTransportBeforeLoad,
                        syncCacheBeforeLoad = syncCacheBeforeLoad,
                        cachedWorkspaces = cachedWorkspaces,
                    )
                ) {
                    handleLoadSuccess(cachedWorkspaces, syncError = null)
                    launchBackgroundSyncAndReload()
                    return@withLock
                }

                val isRefresh = hasLoadedData
                updateState { it.toLoadingState(isRefresh = isRefresh) }
                val syncError = syncBeforeLoad(
                    refreshTransportBeforeLoad = refreshTransportBeforeLoad,
                    syncCacheBeforeLoad = syncCacheBeforeLoad,
                )

                runCatching {
                    getTaskWorkspaceSummaries()
                }
                    .onSuccess { workspaces ->
                        if (!hasLoadedData && workspaces.isEmpty() && syncError != null) {
                            handleLoadFailure(syncError)
                        } else {
                            handleLoadSuccess(workspaces, syncError)
                        }
                    }
                    .onFailure { handleLoadFailure(syncError) }
            }
        }
    }

    private fun shouldSkipLoad(force: Boolean): Boolean {
        return hasLoadedData && !force
    }

    private suspend fun syncBeforeLoad(
        refreshTransportBeforeLoad: Boolean,
        syncCacheBeforeLoad: Boolean,
    ): String? {
        if (!refreshTransportBeforeLoad && !syncCacheBeforeLoad) return null

        return syncMutex.withLock {
            val result = when {
                refreshTransportBeforeLoad -> refreshTaskMail()
                syncCacheBeforeLoad -> syncTaskMailCache?.invoke() ?: Result.success(Unit)
                else -> Result.success(Unit)
            }

            result
                .exceptionOrNull()
                ?.let { throwable ->
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: "Failed to refresh TaskMail workspaces."
                }
        }
    }

    private fun launchBackgroundSyncAndReload() {
        viewModelScope.launch {
            loadMutex.withLock {
                updateState { it.toRefreshingState() }
                val syncError = syncBeforeLoad(
                    refreshTransportBeforeLoad = false,
                    syncCacheBeforeLoad = true,
                )

                runCatching {
                    getTaskWorkspaceSummaries()
                }
                    .onSuccess { workspaces ->
                        handleLoadSuccess(workspaces, syncError)
                    }
                    .onFailure { handleLoadFailure(syncError) }
            }
        }
    }

    private fun setForegroundRefreshEnabled(enabled: Boolean) {
        if (!enabled) {
            foregroundRefreshJob?.cancel()
            foregroundRefreshJob = null
            return
        }

        if (foregroundRefreshJob != null) return

        foregroundRefreshJob = viewModelScope.launch {
            requestForegroundRefresh()
            foregroundRefreshTickerFactory.createTicker(FOREGROUND_REFRESH_INTERVAL_MS).collect {
                requestForegroundRefresh()
            }
        }
    }

    private suspend fun requestForegroundRefresh() {
        val accountUuid = getTaskMailSenderAccounts()
            .singleOrNull()
            ?.accountUuid
            ?: return

        syncMutex.withLock {
            refreshTaskMail(accountUuid)
        }
    }

    private fun handleLoadSuccess(
        workspaces: List<TaskWorkspaceSummary>,
        syncError: String?,
    ) {
        hasLoadedData = true
        updateState {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                error = null,
                refreshError = syncError,
                workspaces = workspaces.map(TaskWorkspaceSummary::toUiState),
            )
        }
    }

    private fun handleLoadFailure(syncError: String?) {
        updateState { currentState ->
            if (hasLoadedData) {
                currentState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    refreshError = syncError ?: "Failed to refresh TaskMail workspaces.",
                )
            } else {
                currentState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = syncError ?: "Failed to load TaskMail workspaces.",
                    workspaces = emptyList(),
                )
            }
        }
    }
}

private const val FOREGROUND_REFRESH_INTERVAL_MS = 10_000L

private fun shouldRenderCachedWorkspacesBeforeSync(
    force: Boolean,
    refreshTransportBeforeLoad: Boolean,
    syncCacheBeforeLoad: Boolean,
    cachedWorkspaces: List<TaskWorkspaceSummary>,
): Boolean {
    return !force &&
        !refreshTransportBeforeLoad &&
        syncCacheBeforeLoad &&
        cachedWorkspaces.isNotEmpty()
}

private fun State.toLoadingState(isRefresh: Boolean): State {
    return if (isRefresh) {
        copy(
            isRefreshing = true,
            refreshError = null,
        )
    } else {
        copy(
            isLoading = true,
            error = null,
            refreshError = null,
        )
    }
}

private fun State.toRefreshingState(): State {
    return copy(
        isLoading = false,
        isRefreshing = true,
        error = null,
        refreshError = null,
    )
}

private fun TaskWorkspaceSummary.toUiState(): TaskWorkspaceItemUi {
    return TaskWorkspaceItemUi(
        title = title,
        subtitle = subtitle.toDisplayWorkdir(),
        sessionCountLabel = "$sessionCount session${if (sessionCount == 1) "" else "s"}",
        sessions = sessions.map { session ->
            session.toUiState(workspaceId = key.workspaceId)
        },
    )
}

private fun TaskSessionSummary.toUiState(workspaceId: String?): TaskSessionItemUi {
    return TaskSessionItemUi(
        workspaceId = key.workspaceId ?: workspaceId,
        sessionId = key.sessionId,
        threadId = key.threadId,
        sessionName = sessionName,
        status = status.name,
        backend = backend.name,
        lastSummary = lastSummary,
        pendingQuestion = pendingQuestion,
    )
}

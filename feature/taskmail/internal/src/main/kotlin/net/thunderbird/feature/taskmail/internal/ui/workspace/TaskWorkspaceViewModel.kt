package net.thunderbird.feature.taskmail.internal.ui.workspace

import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.data.TaskMailForegroundRefreshTickerFactory
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceKey
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailSenderAccounts
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetails
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailStoreChanges
import net.thunderbird.feature.taskmail.internal.domain.usecase.RefreshTaskMail
import net.thunderbird.feature.taskmail.internal.domain.usecase.SyncTaskMailCache
import net.thunderbird.feature.taskmail.internal.ui.toDisplayWorkdir
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceContract.Event
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceContract.State

internal class TaskWorkspaceViewModel(
    private val getTaskSessionDetails: GetTaskSessionDetails,
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
                    if (hasLoadedData || state.value.isLoading || state.value.hasContent) {
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

                val cachedSessionDetails = runCatching {
                    getTaskSessionDetails()
                }.getOrNull().orEmpty()
                if (
                    shouldRenderCachedSessionsBeforeSync(
                        force = force,
                        refreshTransportBeforeLoad = refreshTransportBeforeLoad,
                        syncCacheBeforeLoad = syncCacheBeforeLoad,
                        cachedSessionDetails = cachedSessionDetails,
                    )
                ) {
                    handleLoadSuccess(cachedSessionDetails, syncError = null)
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
                    getTaskSessionDetails()
                }
                    .onSuccess { sessionDetails ->
                        if (!hasLoadedData && sessionDetails.isEmpty() && syncError != null) {
                            handleLoadFailure(syncError)
                        } else {
                            handleLoadSuccess(sessionDetails, syncError)
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
                        ?: "Failed to refresh TaskMail workbench."
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
                    getTaskSessionDetails()
                }
                    .onSuccess { sessionDetails ->
                        handleLoadSuccess(sessionDetails, syncError)
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
        sessionDetails: List<TaskSessionDetail>,
        syncError: String?,
    ) {
        hasLoadedData = true
        val workbench = sessionDetails.toWorkbenchUiState()
        updateState {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                error = null,
                refreshError = syncError,
                attentionSessions = workbench.attentionSessions,
                activeSessions = workbench.activeSessions,
                recentSessions = workbench.recentSessions,
                pcSummaries = workbench.pcSummaries,
                workspaceSummaries = workbench.workspaceSummaries,
            )
        }
    }

    private fun handleLoadFailure(syncError: String?) {
        updateState { currentState ->
            if (hasLoadedData) {
                currentState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    refreshError = syncError ?: "Failed to refresh TaskMail workbench.",
                )
            } else {
                currentState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = syncError ?: "Failed to load TaskMail workbench.",
                    attentionSessions = emptyList(),
                    activeSessions = emptyList(),
                    recentSessions = emptyList(),
                    pcSummaries = emptyList(),
                    workspaceSummaries = emptyList(),
                )
            }
        }
    }
}

private const val FOREGROUND_REFRESH_INTERVAL_MS = 10_000L

private data class TaskWorkspaceWorkbenchUiState(
    val attentionSessions: List<TaskSessionItemUi>,
    val activeSessions: List<TaskSessionItemUi>,
    val recentSessions: List<TaskSessionItemUi>,
    val pcSummaries: List<TaskPcSummaryItemUi>,
    val workspaceSummaries: List<TaskWorkspaceItemUi>,
)

private fun shouldRenderCachedSessionsBeforeSync(
    force: Boolean,
    refreshTransportBeforeLoad: Boolean,
    syncCacheBeforeLoad: Boolean,
    cachedSessionDetails: List<TaskSessionDetail>,
): Boolean {
    return !force &&
        !refreshTransportBeforeLoad &&
        syncCacheBeforeLoad &&
        cachedSessionDetails.isNotEmpty()
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

private fun List<TaskSessionDetail>.toWorkbenchUiState(): TaskWorkspaceWorkbenchUiState {
    val workspaceSummaries = groupBy(TaskSessionDetail::workspace)
        .values
        .sortedByDescending { details ->
            details.maxOfOrNull(TaskSessionDetail::lastUpdatedAt) ?: Long.MIN_VALUE
        }
        .map(::toWorkspaceUiState)
    val sessions = workspaceSummaries
        .flatMap(TaskWorkspaceItemUi::sessions)
        .sortedByDescending(TaskSessionItemUi::lastUpdatedAt)
    val attentionSessions = sessions.filter(TaskSessionItemUi::requiresAttention)
    val activeSessions = sessions.filter(TaskSessionItemUi::isActiveSession)
    val attentionOrActiveIds = buildSet {
        addAll(attentionSessions.map(TaskSessionItemUi::identity))
        addAll(activeSessions.map(TaskSessionItemUi::identity))
    }
    val recentSessions = sessions
        .filterNot { session -> session.identity in attentionOrActiveIds }
        .take(5)
        .ifEmpty { sessions.take(5) }

    return TaskWorkspaceWorkbenchUiState(
        attentionSessions = attentionSessions,
        activeSessions = activeSessions,
        recentSessions = recentSessions,
        pcSummaries = workspaceSummaries.toPcSummaries(),
        workspaceSummaries = workspaceSummaries,
    )
}

private fun toWorkspaceUiState(details: List<TaskSessionDetail>): TaskWorkspaceItemUi {
    val primaryDetail = details.maxByOrNull(TaskSessionDetail::lastUpdatedAt) ?: details.first()
    val workspace = primaryDetail.workspace
    val title = resolveWorkspaceTitle(workspace)
    val subtitle = primaryDetail.workdir.toDisplayWorkdir()
    val sessions = details
        .sortedByDescending(TaskSessionDetail::lastUpdatedAt)
        .map { detail ->
            detail.toUiState(
                workspaceTitle = title,
                workspaceSubtitle = subtitle,
                workspaceIdFallback = workspace.workspaceId,
            )
        }

    return TaskWorkspaceItemUi(
        title = title,
        subtitle = subtitle,
        sessionCountLabel = "${sessions.size} session${if (sessions.size == 1) "" else "s"}",
        sessions = sessions,
    )
}

private fun TaskSessionDetail.toUiState(
    workspaceTitle: String = resolveWorkspaceTitle(workspace),
    workspaceSubtitle: String? = workdir.toDisplayWorkdir(),
    workspaceIdFallback: String? = workspace.workspaceId,
): TaskSessionItemUi {
    val routeLabel = listOfNotNull(
        workspaceTitle,
        workspaceSubtitle,
    ).joinToString(" · ").takeIf(String::isNotBlank)

    return TaskSessionItemUi(
        workspaceId = key.workspaceId ?: workspaceIdFallback,
        sessionId = key.sessionId,
        stableId = buildTaskSessionStableId(
            workspaceId = key.workspaceId ?: workspaceIdFallback,
            sessionId = key.sessionId,
            threadId = key.threadId,
        ),
        sessionName = sessionName,
        status = status.name,
        backend = backend.name,
        lastSummary = lastSummary,
        pendingQuestion = pendingQuestions.isNotEmpty(),
        routeLabel = routeLabel,
        lastUpdatedAt = lastUpdatedAt(),
    )
}

private fun List<TaskWorkspaceItemUi>.toPcSummaries(): List<TaskPcSummaryItemUi> {
    if (isEmpty()) return emptyList()

    val workspaceCountLabel = "${size} workspace summary${if (size == 1) "" else "ies"}"

    return listOf(
        TaskPcSummaryItemUi(
            title = "PC routing pending",
            supportingText = "VPS-first PC inventory is not wired yet. Current home derives route context from cached session details.",
            workspaceCountLabel = workspaceCountLabel,
        ),
    )
}

private fun TaskSessionDetail.lastUpdatedAt(): Long {
    return timeline.lastOrNull()?.timestamp ?: 0L
}

private fun deriveWorkspaceTitle(repoPath: String): String {
    return File(repoPath).name.takeIf { it.isNotBlank() } ?: repoPath
}

private fun resolveWorkspaceTitle(workspace: TaskWorkspaceKey): String {
    return workspace.repoPath
        .takeIf { it.isNotBlank() }
        ?.let(::deriveWorkspaceTitle)
        ?: workspace.workspaceId
        ?: "Task workspace"
}

private val TaskSessionItemUi.identity: String
    get() = stableId

private fun buildTaskSessionStableId(
    workspaceId: String?,
    sessionId: String?,
    threadId: String?,
): String {
    val normalizedWorkspaceId = workspaceId?.trim()?.takeIf(String::isNotBlank)
    val normalizedSessionId = sessionId?.trim()?.takeIf(String::isNotBlank)
    if (normalizedSessionId != null) {
        return listOfNotNull(normalizedWorkspaceId, normalizedSessionId)
            .joinToString(separator = "::")
    }

    val normalizedThreadId = threadId?.trim()?.takeIf(String::isNotBlank)
        ?: "unknown_thread"
    return listOf("compat", normalizedWorkspaceId ?: "workspace_unknown", normalizedThreadId)
        .joinToString(separator = "::")
}

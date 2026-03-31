@file:Suppress("TooManyFunctions")

package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.thunderbird.core.logging.Logger
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.data.TaskMailForegroundRefreshTickerFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.TaskMailTimelineAttachmentHandler
import net.thunderbird.feature.taskmail.internal.data.facade.SessionSnapshotRequestException
import net.thunderbird.feature.taskmail.internal.data.facade.toTaskSessionDetail as toSnapshotTaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.model.TaskAttachmentActionTarget
import net.thunderbird.feature.taskmail.internal.domain.model.LegacyMailAttachmentTarget
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotRound
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotLocator
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionPendingSubmission
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProcessItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionSubscriptionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.withPendingSubmissionsRemoved
import net.thunderbird.feature.taskmail.internal.domain.model.withPendingSubmission
import net.thunderbird.feature.taskmail.internal.domain.model.isCompatibleWith
import net.thunderbird.feature.taskmail.internal.domain.model.merge
import net.thunderbird.feature.taskmail.internal.domain.model.prefersVpsProjection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionRequest
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionResult
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionTarget
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionAckStatus
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionQuestionAnswer
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.toTargetIdentity
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetLatestTaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionHistorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailSessionUpdates
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailStoreChanges
import net.thunderbird.feature.taskmail.internal.domain.usecase.RecordTaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.RefreshTaskMail
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailDirectSessionAction
import net.thunderbird.feature.taskmail.internal.domain.usecase.SyncTaskMailCache
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract.Event
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract.State
import net.thunderbird.feature.taskmail.internal.ui.toDisplayWorkdir

private const val SEND_FAILURE_MESSAGE = "Failed to send TaskMail reply."
private const val REPLY_SENT_VIA_DIRECT_MESSAGE =
    "[VPS] Reply submitted. Detail will keep following VPS-native session updates."
private const val QUICK_ANSWER_SENT_VIA_DIRECT_MESSAGE =
    "[VPS] Quick answer submitted. Detail will keep following VPS-native session updates."
private const val STATUS_QUERY_SENT_VIA_DIRECT_MESSAGE =
    "[VPS] /status submitted. Detail will keep following VPS-native session updates."
private const val GUIDE_SENT_VIA_DIRECT_MESSAGE =
    "[VPS] Guide submitted. Detail will keep following VPS-native session updates."
private const val RESUME_SENT_VIA_DIRECT_MESSAGE =
    "[VPS] Resume submitted. Detail will keep following VPS-native session updates."
private const val STOP_RUNNING_SENT_VIA_DIRECT_MESSAGE =
    "[VPS] /kill submitted. Detail will keep following VPS-native session updates."
private const val DEACTIVATE_SENT_VIA_DIRECT_MESSAGE =
    "[VPS] /end submitted. Detail will keep following VPS-native session updates."
private const val DIRECT_SESSION_ACTION_UNAVAILABLE_MESSAGE =
    "Session actions are unavailable until this session is backed by VPS session-action data."
private const val DIRECT_REPLY_PAUSED_MESSAGE =
    "Plain-text reply is unavailable while the session is paused."
private const val STRUCTURED_REPLY_INCOMPLETE_MESSAGE =
    "Complete every required answer before sending this structured reply."
private const val GUIDE_UNAVAILABLE_MESSAGE =
    "Guide is unavailable until this session can accept a plain-text reply."
private const val RESUME_FAILURE_MESSAGE = "Failed to submit /resume."
private const val STOP_RUNNING_FAILURE_MESSAGE = "Failed to submit /kill."
private const val DEACTIVATE_FAILURE_MESSAGE = "Failed to submit /end."

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class TaskSessionDetailViewModel(
    private val detailRepository: TaskSessionDetailRepository,
    private val getTaskSessionDetail: GetTaskSessionDetail = GetTaskSessionDetail(detailRepository),
    private val refreshTaskMail: RefreshTaskMail,
    private val observeTaskMailStoreChanges: ObserveTaskMailStoreChanges,
    private val foregroundRefreshTickerFactory: TaskMailForegroundRefreshTickerFactory,
    private val sendTaskMailDirectSessionAction: SendTaskMailDirectSessionAction? = null,
    private val getLatestTaskMailSessionActionSendRecord: GetLatestTaskMailSessionActionSendRecord? = null,
    private val recordTaskMailSessionActionSendRecord: RecordTaskMailSessionActionSendRecord? = null,
    private val replyAttachmentResolver: TaskMailReplyAttachmentResolver,
    private val timelineAttachmentHandler: TaskMailTimelineAttachmentHandler,
    private val logger: Logger,
    private val syncTaskMailCache: SyncTaskMailCache? = null,
    private val observeTaskMailSessionUpdates: ObserveTaskMailSessionUpdates? = null,
    private val getTaskSessionHistorySnapshot: GetTaskSessionHistorySnapshot? = null,
    private val sessionSnapshotRecoveryMaxAttempts: Int = SESSION_SNAPSHOT_RECOVERY_MAX_ATTEMPTS,
    private val sessionSnapshotRecoveryDelayMs: Long = SESSION_SNAPSHOT_RECOVERY_DELAY_MS,
    private val currentTimeProvider: () -> Long = System::currentTimeMillis,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskSessionDetailContract.ViewModel {

    private var currentKey: TaskSessionKey? = null
    private var currentDetail: TaskSessionDetail? = null
    private val loadMutex = Mutex()
    private val syncMutex = Mutex()
    private var foregroundRefreshJob: Job? = null
    private var vpsObservationJob: Job? = null
    private var sessionSnapshotRecoveryJob: Job? = null
    private var historySnapshotJob: Job? = null
    private var pendingSubmissionContinuityJob: Job? = null
    private var vpsObservationKey: TaskSessionKey? = null
    private var preferServerHistoryRounds: Boolean = false

    init {
        observeLocalMailChanges()
    }

    override fun onCleared() {
        stopVpsObservation()
        historySnapshotJob?.cancel()
        pendingSubmissionContinuityJob?.cancel()
        super.onCleared()
    }

    override fun event(event: Event) {
        when (event) {
            is Event.LoadDetail -> loadDetail(
                key = TaskSessionKey(
                    workspaceId = event.workspaceId?.trim()?.takeIf(String::isNotBlank),
                    sessionId = event.sessionId.trim().takeIf(String::isNotBlank),
                ),
                preferHistorySnapshot = event.preferServerHistoryRounds,
                syncCacheBeforeLoad = true,
            )

            is Event.DraftChanged -> updateState { it.copy(draftText = event.text) }
            is Event.AttachmentsSelected,
            is Event.RemoveAttachmentClicked,
            is Event.OpenTimelineAttachmentClicked,
            is Event.SaveTimelineAttachmentClicked,
            is Event.AttachmentSaveDestinationSelected,
            -> handleAttachmentEvent(event)
            Event.ForegroundRefreshStarted -> startForegroundRefresh()
            Event.ForegroundRefreshStopped -> stopForegroundRefresh()
            Event.SendReplyClicked -> sendReply()
            is Event.SendChoiceClicked -> sendChoice(event.choice)
            Event.StatusQueryClicked -> sendStatusQuery()
            is Event.ReplyPermissionChanged -> {
                updateState { it.copy(selectedReplyPermission = event.permission) }
            }
            Event.GuideClicked -> openGuideComposer()
            Event.GuideDismissed -> {
                updateState { it.copy(isGuideComposerVisible = false, sendError = null) }
            }
            Event.ResumeClicked -> sendResume()
            Event.StopRunningClicked -> sendStopRunning()
            Event.DeactivateClicked -> sendDeactivate()
            Event.RefreshClicked -> refreshDetail(
                refreshTransportBeforeLoad = true,
                syncCacheBeforeLoad = true,
            )
            Event.HistoryClicked -> updateState { it.copy(isHistoryVisible = true) }
            Event.HistoryDismissed -> updateState { it.copy(isHistoryVisible = false) }
            Event.DismissSendError -> updateState { it.copy(sendError = null) }
            Event.BackClicked -> emitEffect(Effect.NavigateBack)
        }
    }

    private fun handleAttachmentEvent(event: Event) {
        when (event) {
            is Event.AttachmentsSelected -> addReplyAttachments(event.uriStrings)
            is Event.RemoveAttachmentClicked -> removeReplyAttachment(event.attachmentId)
            is Event.OpenTimelineAttachmentClicked -> openTimelineAttachment(event.attachmentId)
            is Event.SaveTimelineAttachmentClicked -> requestSaveForTimelineAttachment(event.attachmentId)
            is Event.AttachmentSaveDestinationSelected -> {
                saveTimelineAttachment(
                    attachmentId = event.attachmentId,
                    destinationUriString = event.destinationUriString,
                )
            }

            else -> Unit
        }
    }

    private fun observeLocalMailChanges() {
        viewModelScope.launch {
            observeTaskMailStoreChanges()
                .conflate()
                .collect {
                    currentKey?.let { key ->
                        logger.debug(TAG) {
                            "Observed local TaskMail store change while detail is visible."
                        }
                        loadDetail(
                            key = key,
                            forceRefresh = true,
                            refreshTransportBeforeLoad = false,
                            syncCacheBeforeLoad = true,
                        )
                    }
                }
        }
    }

    private fun loadDetail(
        key: TaskSessionKey,
        preferHistorySnapshot: Boolean = preferServerHistoryRounds,
        forceRefresh: Boolean = false,
        refreshTransportBeforeLoad: Boolean = false,
        syncCacheBeforeLoad: Boolean = false,
    ) {
        val previousKey = currentKey
        if (shouldSkipDetailLoad(key = key, forceRefresh = forceRefresh, comparisonKey = currentKey)) {
            preferServerHistoryRounds = preferHistorySnapshot
            if (preferHistorySnapshot && state.value.historySnapshotRounds.isEmpty()) {
                currentDetail?.let(::loadHistorySnapshot)
            } else if (!preferHistorySnapshot) {
                clearHistorySnapshotState()
            }
            return
        }

        currentKey = key
        preferServerHistoryRounds = preferHistorySnapshot
        if (key != previousKey) {
            stopVpsObservation()
            clearHistorySnapshotState()
            loadLatestDirectSessionActionRecord(key)
        } else if (!preferHistorySnapshot) {
            clearHistorySnapshotState()
        }
        viewModelScope.launch {
            loadMutex.withLock {
                if (shouldSkipDetailLoad(key = key, forceRefresh = forceRefresh, comparisonKey = previousKey)) {
                    return@withLock
                }

                val loadContext = DetailLoadContext(
                    isSameKey = key == previousKey,
                    canKeepCurrentContent = key == previousKey && state.value.detail != null,
                )
                val cachedDetail = if (
                    shouldAttemptCachedDetailBeforeSync(
                        forceRefresh = forceRefresh,
                        refreshTransportBeforeLoad = refreshTransportBeforeLoad,
                        syncCacheBeforeLoad = syncCacheBeforeLoad,
                    )
                ) {
                    runCatching { getTaskSessionDetail(key) }.getOrNull()
                } else {
                    null
                }

                if (
                    shouldRenderCachedDetailBeforeSync(
                        forceRefresh = forceRefresh,
                        refreshTransportBeforeLoad = refreshTransportBeforeLoad,
                        syncCacheBeforeLoad = syncCacheBeforeLoad,
                        cachedDetail = cachedDetail,
                    )
                ) {
                    handleDetailLoadSuccess(cachedDetail, loadContext, syncError = null)
                    if (cachedDetail?.prefersVpsProjection() != true) {
                        launchBackgroundSyncAndReload(key)
                    }
                    return@withLock
                }

                updateState { it.toLoadingState(loadContext) }
                val syncError = syncBeforeLoad(
                    refreshTransportBeforeLoad = refreshTransportBeforeLoad,
                    syncCacheBeforeLoad = syncCacheBeforeLoad,
                )

                runCatching {
                    getTaskSessionDetail(key)
                }.onSuccess { detail ->
                    if (detail == null && !loadContext.canKeepCurrentContent && syncError != null) {
                        handleDetailLoadFailure(loadContext, syncError)
                    } else {
                        handleDetailLoadSuccess(detail, loadContext, syncError)
                    }
                }
                    .onFailure { handleDetailLoadFailure(loadContext, syncError) }
            }
        }
    }

    private fun launchBackgroundSyncAndReload(key: TaskSessionKey) {
        viewModelScope.launch {
            loadMutex.withLock {
                if (currentKey != key) return@withLock

                val loadContext = DetailLoadContext(
                    isSameKey = true,
                    canKeepCurrentContent = state.value.detail != null,
                )
                updateState { it.toRefreshingState() }
                val syncError = syncBeforeLoad(
                    refreshTransportBeforeLoad = false,
                    syncCacheBeforeLoad = true,
                )

                runCatching {
                    getTaskSessionDetail(key)
                }.onSuccess { detail ->
                    if (currentKey != key) return@onSuccess

                    if (detail == null && !loadContext.canKeepCurrentContent && syncError != null) {
                        handleDetailLoadFailure(loadContext, syncError)
                    } else {
                        handleDetailLoadSuccess(detail, loadContext, syncError)
                    }
                }.onFailure {
                    if (currentKey != key) return@onFailure
                    handleDetailLoadFailure(loadContext, syncError)
                }
            }
        }
    }

    private fun shouldSkipDetailLoad(
        key: TaskSessionKey,
        forceRefresh: Boolean,
        comparisonKey: TaskSessionKey?,
    ): Boolean {
        return !forceRefresh && key == comparisonKey && state.value.detail != null
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
                        ?: "Failed to refresh TaskMail session detail."
                }
        }
    }

    private fun startForegroundRefresh() {
        if (foregroundRefreshJob != null) return

        foregroundRefreshJob = viewModelScope.launch {
            requestForegroundRefresh()
            foregroundRefreshTickerFactory.createTicker(FOREGROUND_REFRESH_INTERVAL_MS).collect {
                requestForegroundRefresh()
            }
        }
    }

    private fun stopForegroundRefresh() {
        foregroundRefreshJob?.cancel()
        foregroundRefreshJob = null
    }

    private suspend fun requestForegroundRefresh() {
        val accountUuid = currentDetail?.replyContext?.accountUuid ?: return

        syncMutex.withLock {
            refreshTaskMail(accountUuid)
        }
    }

    private fun State.toLoadingState(loadContext: DetailLoadContext): State {
        return if (loadContext.canKeepCurrentContent) {
            copy(
                isRefreshing = true,
                refreshError = null,
            )
        } else {
            copy(
                isLoading = true,
                error = null,
                refreshError = null,
                sendError = if (loadContext.isSameKey) sendError else null,
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

    private fun handleDetailLoadSuccess(
        detail: TaskSessionDetail?,
        loadContext: DetailLoadContext,
        syncError: String?,
    ) {
        currentDetail = detail

        if (detail == null) {
            stopVpsObservation()
            clearHistorySnapshotState()
            handleMissingDetail(loadContext)
            return
        }

        startOrRefreshVpsObservation(detail)
        logger.debug(TAG) {
            "Rendered repository detail " +
                "status=${detail.status.name} " +
                "pendingQuestionCount=${detail.pendingQuestions.size} " +
                "mailTimelineCount=${detail.timeline.size}"
        }
        val uiDetail = detail.toUiState(
            historySnapshotRounds = state.value.historySnapshotRounds,
        )
        updateState {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                error = null,
                refreshError = syncError,
                sendError = if (loadContext.isSameKey) it.sendError else null,
                detail = uiDetail,
                draftText = if (loadContext.isSameKey) {
                    it.resolveDraftText(uiDetail)
                } else {
                    uiDetail.structuredReplyTemplate.orEmpty()
                },
                isGuideComposerVisible = if (loadContext.isSameKey && uiDetail.isCurrentInputLedMode()) {
                    it.isGuideComposerVisible
                } else {
                    false
                },
                selectedReplyPermission = if (loadContext.isSameKey) {
                    it.selectedReplyPermission
                } else {
                    TaskMailNewTaskPermission.Default
                },
                replyAttachments = if (loadContext.isSameKey) it.replyAttachments else persistentListOf(),
            )
        }
        if (detail.pendingSubmissions.isNotEmpty()) {
            refreshPendingSubmissionContinuity(detail)
        }
        if (preferServerHistoryRounds && state.value.historySnapshotRounds.isEmpty()) {
            loadHistorySnapshot(detail)
        }
    }

    private fun handleMissingDetail(loadContext: DetailLoadContext) {
        updateState {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                error = "Task session detail was not found.",
                refreshError = null,
                isGuideComposerVisible = false,
                draftText = if (loadContext.isSameKey) it.draftText else "",
                replyAttachments = if (loadContext.isSameKey) it.replyAttachments else persistentListOf(),
                historySnapshotRounds = persistentListOf(),
                historySnapshotError = null,
                detail = null,
            )
        }
    }

    private fun handleDetailLoadFailure(
        loadContext: DetailLoadContext,
        syncError: String?,
    ) {
        if (!loadContext.canKeepCurrentContent) {
            currentDetail = null
            stopVpsObservation()
            clearHistorySnapshotState()
        }

        updateState { currentState ->
            if (loadContext.canKeepCurrentContent) {
                currentState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    refreshError = syncError ?: "Failed to refresh TaskMail session detail.",
                )
            } else {
                currentState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = syncError ?: "Failed to load TaskMail session detail.",
                    isGuideComposerVisible = false,
                    draftText = "",
                    replyAttachments = persistentListOf(),
                    historySnapshotRounds = persistentListOf(),
                    historySnapshotError = null,
                    detail = null,
                )
            }
        }
    }

    private fun clearHistorySnapshotState() {
        historySnapshotJob?.cancel()
        historySnapshotJob = null
        updateState {
            it.copy(
                historySnapshotRounds = persistentListOf(),
                historySnapshotError = null,
            )
        }
    }

    private fun loadHistorySnapshot(detail: TaskSessionDetail) {
        val getHistorySnapshot = getTaskSessionHistorySnapshot ?: return
        val key = currentKey ?: return
        historySnapshotJob?.cancel()
        historySnapshotJob = viewModelScope.launch {
            val result = getHistorySnapshot(buildHistorySnapshotLocator(key, detail))
            if (!matchesCurrentHistoryTarget(key) || !preferServerHistoryRounds) return@launch

            result.onSuccess { snapshot ->
                clearPendingSubmissionsFromSnapshot(snapshot)
                updateState {
                    it.copy(
                        historySnapshotRounds = snapshot.rounds,
                        historySnapshotError = null,
                    )
                }
                currentDetail?.let { renderCurrentDetail() }
            }.onFailure { error ->
                logger.warn(TAG, error) { "Failed to load TaskMail history snapshot." }
                updateState { currentState ->
                    currentState.copy(
                        historySnapshotError = error.message
                            ?.takeIf(String::isNotBlank)
                            ?: "Failed to load round-by-round history.",
                    )
                }
            }
        }
    }

    private fun refreshPendingSubmissionContinuity(detail: TaskSessionDetail) {
        val getHistorySnapshot = getTaskSessionHistorySnapshot ?: return
        if (detail.pendingSubmissions.isEmpty()) return
        val key = currentKey ?: return

        pendingSubmissionContinuityJob?.cancel()
        pendingSubmissionContinuityJob = viewModelScope.launch {
            val result = getHistorySnapshot(buildHistorySnapshotLocator(key, detail))
            if (!matchesCurrentHistoryTarget(key)) return@launch
            result.onSuccess(::clearPendingSubmissionsFromSnapshot)
        }
    }

    private fun buildHistorySnapshotLocator(
        key: TaskSessionKey,
        detail: TaskSessionDetail,
    ): TaskSessionHistorySnapshotLocator {
        return TaskSessionHistorySnapshotLocator(
            workspaceId = key.workspaceId ?: detail.workspace.workspaceId,
            sessionId = requireNotNull(key.sessionId ?: detail.key.sessionId),
            threadId = key.threadId ?: detail.key.threadId,
            repoPath = detail.repoPath,
            workdir = detail.workdir,
        )
    }

    private fun matchesCurrentHistoryTarget(key: TaskSessionKey): Boolean {
        val latestKey = currentKey
        val matchesSameSession = latestKey?.sessionId != null &&
            key.sessionId != null &&
            latestKey.sessionId == key.sessionId
        val matchesSameThread = latestKey?.threadId != null &&
            key.threadId != null &&
            latestKey.threadId == key.threadId
        return matchesSameSession || matchesSameThread
    }

    private fun startOrRefreshVpsObservation(detail: TaskSessionDetail) {
        if (!detail.prefersVpsProjection()) {
            stopVpsObservation()
            return
        }

        val updatesObserver = observeTaskMailSessionUpdates
        if (vpsObservationJob != null && vpsObservationKey == detail.key && sessionSnapshotRecoveryJob != null) {
            return
        }

        stopVpsObservation()
        vpsObservationKey = detail.key
        startSessionSnapshotRecovery(detail)
        if (updatesObserver == null) return

        logger.debug(TAG) { "Starting VPS-native detail observation from session-updates facade." }
        vpsObservationJob = viewModelScope.launch {
            try {
                updatesObserver(detail).collect { snapshot ->
                    val activeObservationKey = vpsObservationKey ?: detail.key
                    if (!activeObservationKey.matchesObservationKey(detail.key)) return@collect
                    logger.debug(TAG) {
                        "Applying Android session-updates snapshot " +
                            "snapshotId=${snapshot.snapshotId} " +
                            "generatedAt=${snapshot.generatedAt}"
                    }

                    sessionSnapshotRecoveryJob?.cancel()
                    applySessionSnapshot(
                        snapshot = snapshot,
                        fallbackDetail = detail,
                        subscriptionStatus = TaskSessionProjectionSubscriptionStatus.Active,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.warn(TAG, error) {
                    "Android session-updates observation ended unexpectedly."
                }
            }
        }
    }

    private fun startSessionSnapshotRecovery(detail: TaskSessionDetail) {
        if (!detail.prefersVpsProjection()) return
        val getHistorySnapshot = getTaskSessionHistorySnapshot ?: return
        val key = currentKey ?: return
        if (!key.matchesObservationKey(detail.key)) return

        sessionSnapshotRecoveryJob?.cancel()
        sessionSnapshotRecoveryJob = viewModelScope.launch {
            recoverDetailViaSessionSnapshot(
                detail = detail,
                getHistorySnapshot = getHistorySnapshot,
            )
        }
    }

    private suspend fun recoverDetailViaSessionSnapshot(
        detail: TaskSessionDetail,
        getHistorySnapshot: GetTaskSessionHistorySnapshot? = this.getTaskSessionHistorySnapshot,
    ) {
        val snapshotFetcher = getHistorySnapshot ?: return
        val key = currentKey ?: return
        if (!key.matchesObservationKey(detail.key)) return

        logger.debug(TAG) {
            "Starting Android session-snapshot recovery for VPS-native detail."
        }
        val locator = buildHistorySnapshotLocator(key, detail)
        var attempt = 1
        while (attempt <= sessionSnapshotRecoveryMaxAttempts) {
            val result = snapshotFetcher(locator)
            result
                .onSuccess { snapshot ->
                    if (!matchesCurrentHistoryTarget(key)) return

                    applySessionSnapshot(
                        snapshot = snapshot,
                        fallbackDetail = detail,
                        subscriptionStatus = TaskSessionProjectionSubscriptionStatus.Idle,
                    )
                }
                .onFailure { error ->
                    val shouldRetry = error.isRetryableSessionSnapshotMiss() &&
                        attempt < sessionSnapshotRecoveryMaxAttempts &&
                        matchesCurrentHistoryTarget(key)
                    if (!shouldRetry) {
                        logger.warn(TAG, error) {
                            "Failed to recover TaskMail detail via session snapshot fallback."
                        }
                        return
                    }

                    logger.debug(TAG) {
                        "Session snapshot fallback is not materialized yet. " +
                            "Scheduling retry attempt=$attempt/$sessionSnapshotRecoveryMaxAttempts."
                    }
                    delay(sessionSnapshotRecoveryDelayMs)
                    attempt += 1
                }
            if (result.isSuccess) {
                return
            }
        }
    }

    private fun stopVpsObservation() {
        vpsObservationJob?.cancel()
        vpsObservationJob = null
        sessionSnapshotRecoveryJob?.cancel()
        sessionSnapshotRecoveryJob = null
        vpsObservationKey = null
    }

    private fun renderCurrentDetail() {
        val detail = currentDetail ?: return
        val uiDetail = detail.toUiState(
            historySnapshotRounds = state.value.historySnapshotRounds,
        )

        updateState { currentState ->
            currentState.copy(
                detail = uiDetail,
                draftText = currentState.resolveDraftText(uiDetail),
            )
        }
    }

    private fun refreshDetail(
        refreshTransportBeforeLoad: Boolean,
        syncCacheBeforeLoad: Boolean,
    ) {
        val key = currentKey ?: return
        if (currentDetail?.prefersVpsProjection() == true) {
            stopVpsObservation()
            loadDetail(
                key = key,
                forceRefresh = true,
                refreshTransportBeforeLoad = false,
                syncCacheBeforeLoad = false,
            )
            return
        }
        loadDetail(
            key = key,
            forceRefresh = true,
            refreshTransportBeforeLoad = refreshTransportBeforeLoad,
            syncCacheBeforeLoad = syncCacheBeforeLoad,
        )
    }

    private fun addReplyAttachments(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return

        viewModelScope.launch {
            val attachments = replyAttachmentResolver.resolveSelectedAttachments(uriStrings)
            updateState { currentState ->
                val mergedAttachments = (currentState.replyAttachments + attachments)
                    .distinctBy(TaskReplyAttachment::id)
                    .toImmutableList()

                currentState.copy(
                    replyAttachments = mergedAttachments,
                    sendError = null,
                )
            }
        }
    }

    private fun removeReplyAttachment(attachmentId: String) {
        updateState { currentState ->
            currentState.copy(
                replyAttachments = currentState.replyAttachments
                    .filterNot { attachment -> attachment.id == attachmentId }
                    .toImmutableList(),
            )
        }
    }

    private fun openGuideComposer() {
        val detail = state.value.detail ?: return
        val unavailableReason = sessionActionReplyUnavailableReason(
            detail = detail,
            attachments = emptyList(),
        ) ?: detail.replyUnavailableReason
        if (unavailableReason != null) {
            emitEffect(
                Effect.ShowMessage(
                    unavailableReason.ifBlank { GUIDE_UNAVAILABLE_MESSAGE },
                ),
            )
            return
        }

        updateState {
            it.copy(
                isGuideComposerVisible = true,
                sendError = null,
            )
        }
    }

    @Suppress("ReturnCount")
    private fun sendReply() {
        val currentState = state.value
        val detail = currentState.detail ?: return
        currentDetail ?: return
        val draftText = currentState.draftText
        if (!detail.requiresStructuredReply && draftText.isBlank() && currentState.replyAttachments.isEmpty()) {
            return
        }

        val directReplyUnavailableReason = sessionActionReplyUnavailableReason(
            detail = detail,
            attachments = currentState.replyAttachments,
        )
        if (directReplyUnavailableReason != null) {
            updateState { it.copy(sendError = directReplyUnavailableReason) }
            return
        }

        if (detail.requiresStructuredReply && !detail.canSendReply(draftText = draftText, attachmentCount = currentState.replyAttachments.size)) {
            updateState { it.copy(sendError = STRUCTURED_REPLY_INCOMPLETE_MESSAGE) }
            return
        }

        val successMessage = if (currentState.isGuideComposerVisible) {
            GUIDE_SENT_VIA_DIRECT_MESSAGE
        } else {
            REPLY_SENT_VIA_DIRECT_MESSAGE
        }

        performSend(
            sendFailureMessage = SEND_FAILURE_MESSAGE,
            hideGuideComposerOnSuccess = currentState.isGuideComposerVisible,
        ) {
            submitSessionAction(
                request = buildReplySessionActionRequest(
                    detail = detail,
                    draftText = draftText,
                    attachments = currentState.replyAttachments,
                    permission = currentState.selectedReplyPermission,
                ),
                directSuccessMessage = successMessage,
            )
        }
    }

    private fun sendChoice(choice: String) {
        val currentState = state.value
        val detail = currentState.detail ?: return
        if (!detail.canUseQuickAnswer(choice)) {
            updateState {
                it.copy(sendError = "Quick answers are only available when exactly one pending question is active.")
            }
            return
        }

        val directReplyUnavailableReason = sessionActionReplyUnavailableReason(
            detail = detail,
            attachments = currentState.replyAttachments,
        )
        if (directReplyUnavailableReason != null) {
            updateState { it.copy(sendError = directReplyUnavailableReason) }
            return
        }

        performSend(sendFailureMessage = SEND_FAILURE_MESSAGE) {
            submitSessionAction(
                request = buildQuickAnswerSessionActionRequest(
                    choice = choice,
                    attachments = currentState.replyAttachments,
                    permission = currentState.selectedReplyPermission,
                ),
                directSuccessMessage = QUICK_ANSWER_SENT_VIA_DIRECT_MESSAGE,
            )
        }
    }

    @Suppress("ReturnCount")
    private fun sendStatusQuery() {
        val detail = state.value.detail ?: return
        currentDetail ?: return
        if (state.value.replyAttachments.isNotEmpty()) {
            updateState {
                it.copy(sendError = "Remove selected attachments before sending /status.")
            }
            return
        }

        val directStatusUnavailableReason = sessionActionStatusUnavailableReason()
        if (directStatusUnavailableReason != null) {
            updateState {
                it.copy(sendError = directStatusUnavailableReason)
            }
            return
        }

        if (!detail.canQueryStatus) {
            updateState {
                it.copy(sendError = detail.replyUnavailableReason ?: DIRECT_SESSION_ACTION_UNAVAILABLE_MESSAGE)
            }
            return
        }

        performSend(sendFailureMessage = SEND_FAILURE_MESSAGE) {
            submitSessionAction(
                request = TaskMailDirectSessionActionRequest.Status(
                    target = requireNotNull(currentSessionActionTargetOrNull()),
                ),
                directSuccessMessage = STATUS_QUERY_SENT_VIA_DIRECT_MESSAGE,
            )
        }
    }

    private fun sendStopRunning() {
        val unavailableReason = sessionActionStatusUnavailableReason()
        if (unavailableReason != null) {
            updateState { it.copy(sendError = unavailableReason) }
            return
        }

        performSend(
            sendFailureMessage = STOP_RUNNING_FAILURE_MESSAGE,
            hideGuideComposerOnSuccess = true,
        ) {
            submitSessionAction(
                request = TaskMailDirectSessionActionRequest.Kill(
                    target = requireNotNull(currentSessionActionTargetOrNull()),
                ),
                directSuccessMessage = STOP_RUNNING_SENT_VIA_DIRECT_MESSAGE,
            )
        }
    }

    private fun sendResume() {
        val unavailableReason = sessionActionStatusUnavailableReason()
        if (unavailableReason != null) {
            updateState { it.copy(sendError = unavailableReason) }
            return
        }

        performSend(
            sendFailureMessage = RESUME_FAILURE_MESSAGE,
            clearReplyDraftOnSuccess = false,
        ) {
            submitSessionAction(
                request = TaskMailDirectSessionActionRequest.Resume(
                    target = requireNotNull(currentSessionActionTargetOrNull()),
                ),
                directSuccessMessage = RESUME_SENT_VIA_DIRECT_MESSAGE,
            )
        }
    }

    private fun sendDeactivate() {
        val unavailableReason = sessionActionStatusUnavailableReason()
        if (unavailableReason != null) {
            updateState { it.copy(sendError = unavailableReason) }
            return
        }

        performSend(sendFailureMessage = DEACTIVATE_FAILURE_MESSAGE) {
            submitSessionAction(
                request = TaskMailDirectSessionActionRequest.End(
                    target = requireNotNull(currentSessionActionTargetOrNull()),
                ),
                directSuccessMessage = DEACTIVATE_SENT_VIA_DIRECT_MESSAGE,
            )
        }
    }

    private fun sessionActionReplyUnavailableReason(
        detail: TaskSessionDetailUiState,
        attachments: List<TaskReplyAttachment>,
    ): String? {
        return when {
            !detail.canReply -> detail.replyUnavailableReason ?: DIRECT_SESSION_ACTION_UNAVAILABLE_MESSAGE
            !hasSessionActionDispatcher() -> DIRECT_SESSION_ACTION_UNAVAILABLE_MESSAGE
            currentSessionActionTargetOrNull() == null -> DIRECT_SESSION_ACTION_UNAVAILABLE_MESSAGE
            detail.requiresResumeBeforeReply -> DIRECT_REPLY_PAUSED_MESSAGE
            else -> null
        }
    }

    private fun sessionActionStatusUnavailableReason(): String? {
        val detail = state.value.detail
        return when {
            detail != null && !detail.canQueryStatus ->
                detail.replyUnavailableReason ?: DIRECT_SESSION_ACTION_UNAVAILABLE_MESSAGE
            !hasSessionActionDispatcher() -> DIRECT_SESSION_ACTION_UNAVAILABLE_MESSAGE
            currentSessionActionTargetOrNull() == null -> DIRECT_SESSION_ACTION_UNAVAILABLE_MESSAGE
            else -> null
        }
    }

    private fun hasSessionActionDispatcher(): Boolean {
        return sendTaskMailDirectSessionAction != null
    }

    private fun buildReplySessionActionRequest(
        detail: TaskSessionDetailUiState,
        draftText: String,
        attachments: List<TaskReplyAttachment>,
        permission: TaskMailNewTaskPermission,
    ): TaskMailDirectSessionActionRequest {
        val target = requireNotNull(currentSessionActionTargetOrNull())
        return when {
            attachments.isNotEmpty() -> TaskMailDirectSessionActionRequest.AttachmentContinuation(
                target = target,
                replyText = draftText,
                attachments = attachments,
                permission = permission,
            )

            detail.requiresStructuredReply -> TaskMailDirectSessionActionRequest.Answers(
                target = target,
                questionAnswers = parseStructuredReplyAnswers(
                    draftText = draftText,
                    pendingQuestions = detail.pendingQuestions,
                ).getOrElse {
                    throw IllegalArgumentException(STRUCTURED_REPLY_INCOMPLETE_MESSAGE, it)
                },
                permission = permission,
            )

            else -> TaskMailDirectSessionActionRequest.Reply(
                target = target,
                replyText = draftText,
                permission = permission,
            )
        }
    }

    private fun buildQuickAnswerSessionActionRequest(
        choice: String,
        attachments: List<TaskReplyAttachment>,
        permission: TaskMailNewTaskPermission,
    ): TaskMailDirectSessionActionRequest {
        val target = requireNotNull(currentSessionActionTargetOrNull())
        return if (attachments.isNotEmpty()) {
            TaskMailDirectSessionActionRequest.AttachmentContinuation(
                target = target,
                replyText = choice,
                attachments = attachments,
                permission = permission,
            )
        } else {
            TaskMailDirectSessionActionRequest.Reply(
                target = target,
                replyText = choice,
                permission = permission,
            )
        }
    }

    private suspend fun submitSessionAction(
        request: TaskMailDirectSessionActionRequest,
        directSuccessMessage: String,
    ): TaskSessionSendUiResult {
        val directSessionActionSender = requireNotNull(sendTaskMailDirectSessionAction)
        val result = directSessionActionSender(request)
        val latestRecord = runCatching {
            recordTaskMailSessionActionSendRecord?.invoke(
                request = request,
                evidence = result.toDirectSendEvidence(),
            )
        }.getOrNull()

        return when (result) {
            is TaskMailDirectSessionActionResult.Accepted ->
                TaskSessionSendUiResult.Success(
                    message = directSuccessMessage,
                    latestDirectSessionActionRecord = latestRecord,
                    directControlPlaneSnapshot = result.controlPlaneSnapshot,
                    pendingSubmission = request.toPendingSubmission(result, currentTimeProvider()),
                )
            is TaskMailDirectSessionActionResult.FallbackToMail ->
                TaskSessionSendUiResult.Failure(
                    errorMessage = result.detailMessage.orFailureMessage(SEND_FAILURE_MESSAGE),
                    latestDirectSessionActionRecord = latestRecord,
                )
            is TaskMailDirectSessionActionResult.Rejected ->
                TaskSessionSendUiResult.Failure(
                    errorMessage = result.errorMessage.ifBlank { SEND_FAILURE_MESSAGE },
                    latestDirectSessionActionRecord = latestRecord,
                )
        }
    }

    private fun openTimelineAttachment(attachmentId: String) {
        val attachment = findTimelineAttachment(attachmentId)
            ?: return emitEffect(
                Effect.ShowAttachmentActionError("Attachment details are unavailable for this message."),
            )

        viewModelScope.launch {
            timelineAttachmentHandler.createOpenIntent(attachment)
                .onSuccess { intent ->
                    emitEffect(Effect.OpenAttachment(intent))
                }
                .onFailure {
                    emitEffect(Effect.ShowAttachmentActionError("Failed to open this attachment."))
                }
        }
    }

    private fun requestSaveForTimelineAttachment(attachmentId: String) {
        val attachment = findTimelineAttachment(attachmentId)
            ?: return emitEffect(
                Effect.ShowAttachmentActionError("Attachment details are unavailable for this message."),
            )
        val mimeType = attachment.contentType
            ?.takeIf { it.isNotBlank() }
            ?: "application/octet-stream"

        emitEffect(
            Effect.CreateAttachmentDocument(
                attachmentId = attachment.attachmentId,
                displayName = attachment.displayName,
                mimeType = mimeType,
            ),
        )
    }

    private fun saveTimelineAttachment(
        attachmentId: String,
        destinationUriString: String,
    ) {
        val attachment = findTimelineAttachment(attachmentId)
            ?: return emitEffect(
                Effect.ShowAttachmentActionError("Attachment details are unavailable for this message."),
            )

        viewModelScope.launch {
            timelineAttachmentHandler.saveAttachmentTo(
                attachment = attachment,
                destinationUriString = destinationUriString,
            ).onFailure {
                emitEffect(Effect.ShowAttachmentActionError("Failed to save this attachment."))
            }
        }
    }

    private fun performSend(
        sendFailureMessage: String,
        clearReplyDraftOnSuccess: Boolean = true,
        refreshAfterSuccess: Boolean = true,
        hideGuideComposerOnSuccess: Boolean = false,
        action: suspend () -> TaskSessionSendUiResult,
    ) {
        viewModelScope.launch {
            updateState {
                it.copy(
                    isSending = true,
                    sendError = null,
                    refreshError = null,
                )
            }

            val result = runCatching { action() }
                .getOrElse {
                    TaskSessionSendUiResult.Failure(sendFailureMessage)
                }

            when (result) {
                is TaskSessionSendUiResult.Success -> {
                    updateState {
                        it.copy(
                            isSending = false,
                            draftText = if (clearReplyDraftOnSuccess) "" else it.draftText,
                            isGuideComposerVisible = if (hideGuideComposerOnSuccess) {
                                false
                            } else {
                                it.isGuideComposerVisible
                            },
                            latestDirectSessionActionRecord = result.latestDirectSessionActionRecord
                                ?: it.latestDirectSessionActionRecord,
                            replyAttachments = if (clearReplyDraftOnSuccess) {
                                persistentListOf()
                            } else {
                                it.replyAttachments
                            },
                            sendError = null,
                        )
                    }
                    result.pendingSubmission?.let { submission ->
                        persistPendingSubmission(submission)
                        currentDetail?.let(::refreshPendingSubmissionContinuity)
                    }
                    result.directControlPlaneSnapshot?.let(::applyDirectControlPlaneSnapshot)
                    emitEffect(Effect.ShowMessage(result.message))
                    if (refreshAfterSuccess) {
                        val key = currentKey ?: return@launch
                        refreshAfterSuccessfulDirectSend(key)
                    }
                }

                is TaskSessionSendUiResult.Failure -> {
                    updateState {
                        it.copy(
                            isSending = false,
                            latestDirectSessionActionRecord = result.latestDirectSessionActionRecord
                                ?: it.latestDirectSessionActionRecord,
                            sendError = result.errorMessage.ifBlank { sendFailureMessage },
                        )
                    }
                }
            }
        }
    }

    private fun applyDirectControlPlaneSnapshot(snapshot: TaskSessionControlPlaneSnapshot) {
        val existingDetail = currentDetail ?: return
        val mergedDetail = existingDetail.mergeControlPlaneSnapshot(snapshot).let { detail ->
            val resultCommandId = snapshot.result?.commandId?.trim()?.takeIf(String::isNotEmpty)
            if (resultCommandId != null) {
                detail.withPendingSubmissionsRemoved(setOf(resultCommandId))
            } else {
                detail
            }
        }
        currentDetail = mergedDetail
        if (mergedDetail != existingDetail) {
            persistDirectControlPlaneSnapshot(mergedDetail)
        }
        renderCurrentDetail()
    }

    private fun loadLatestDirectSessionActionRecord(key: TaskSessionKey) {
        val getLatestRecord = getLatestTaskMailSessionActionSendRecord ?: run {
            updateState { it.copy(latestDirectSessionActionRecord = null) }
            return
        }
        val target = key.toDirectSessionActionTargetOrNull()
        if (target == null) {
            updateState { it.copy(latestDirectSessionActionRecord = null) }
            return
        }

        viewModelScope.launch {
            val latestRecord = runCatching {
                getLatestRecord(target)
            }.getOrNull()

            updateState { currentState ->
                if (currentKey == key) {
                    currentState.copy(
                        latestDirectSessionActionRecord = latestRecord,
                    )
                } else {
                    currentState
                }
            }
        }
    }

    @Suppress("ReturnCount")
    private fun currentSessionActionTargetOrNull(): TaskMailDirectSessionActionTarget? {
        val key = currentKey ?: return null
        return key.toDirectSessionActionTargetOrNull()
    }

    private fun persistDirectControlPlaneSnapshot(detail: TaskSessionDetail) {
        persistProjectedDetail(detail)
    }

    private fun persistProjectedDetail(detail: TaskSessionDetail) {
        viewModelScope.launch {
            runCatching {
                detailRepository.upsertSessionDetails(listOf(detail))
            }.onFailure { error ->
                logger.warn(TAG, error) { "Failed to persist TaskMail session projection." }
            }
        }
    }

    private fun persistPendingSubmission(submission: TaskSessionPendingSubmission) {
        val existingDetail = currentDetail ?: return
        val updatedDetail = existingDetail.withPendingSubmission(submission)
        currentDetail = updatedDetail
        persistProjectedDetail(updatedDetail)
        renderCurrentDetail()
    }

    private fun clearPendingSubmissionsFromSnapshot(snapshot: TaskSessionHistorySnapshot) {
        val continuityCommandIds = setOfNotNull(
            snapshot.latestSessionAction?.commandId?.trim()?.takeIf(String::isNotEmpty),
        )
        clearPendingSubmissions(continuityCommandIds)
    }

    private fun clearPendingSubmissions(commandIds: Set<String>) {
        if (commandIds.isEmpty()) return

        val existingDetail = currentDetail ?: return
        val updatedDetail = existingDetail.withPendingSubmissionsRemoved(commandIds)
        if (updatedDetail == existingDetail) return

        currentDetail = updatedDetail
        persistProjectedDetail(updatedDetail)
        renderCurrentDetail()
    }

    private fun applySessionSnapshot(
        snapshot: TaskSessionHistorySnapshot,
        fallbackDetail: TaskSessionDetail,
        subscriptionStatus: TaskSessionProjectionSubscriptionStatus,
    ) {
        val key = currentKey ?: return
        if (!key.matchesObservationKey(fallbackDetail.key)) return

        clearPendingSubmissionsFromSnapshot(snapshot)
        val locator = buildHistorySnapshotLocator(
            key = key,
            detail = currentDetail ?: fallbackDetail,
        )
        val existingDetail = currentDetail ?: fallbackDetail
        val updatedDetail = snapshot.toSnapshotTaskSessionDetail(
            locator = locator,
            existingDetail = existingDetail,
            subscriptionStatus = subscriptionStatus,
        ) ?: return

        currentKey = currentKey?.copy(
            workspaceId = updatedDetail.key.workspaceId ?: currentKey?.workspaceId,
            sessionId = updatedDetail.key.sessionId ?: currentKey?.sessionId,
            threadId = updatedDetail.key.threadId ?: currentKey?.threadId,
        ) ?: updatedDetail.key
        vpsObservationKey = updatedDetail.key
        currentDetail = updatedDetail
        persistProjectedDetail(updatedDetail)
        updateState {
            it.copy(
                historySnapshotRounds = snapshot.rounds,
                historySnapshotError = null,
            )
        }
        renderCurrentDetail()
    }

    private fun refreshAfterSuccessfulDirectSend(key: TaskSessionKey) {
        if (currentDetail?.prefersVpsProjection() == true) {
            loadDetail(
                key = key,
                forceRefresh = true,
                refreshTransportBeforeLoad = false,
                syncCacheBeforeLoad = false,
            )
        } else {
            loadDetail(
                key = key,
                forceRefresh = true,
                refreshTransportBeforeLoad = true,
                syncCacheBeforeLoad = true,
            )
        }
    }

    private fun findTimelineAttachment(attachmentId: String): TaskAttachmentActionTarget? {
        val detailAttachment = state.value.detail
            ?.allAttachments()
            ?.firstOrNull { attachment -> attachment.id == attachmentId }
            ?.actionTarget
        if (detailAttachment != null) {
            return detailAttachment
        }

        return state.value.historySnapshotRounds
            .asSequence()
            .flatMap { round -> round.inputAttachments.asSequence() + round.resultAttachments.asSequence() }
            .firstOrNull { attachment -> attachment.attachmentId == attachmentId }
            ?.actionTarget
    }

    private data class DetailLoadContext(
        val isSameKey: Boolean,
        val canKeepCurrentContent: Boolean,
    )
}

private sealed interface TaskSessionSendUiResult {
    data class Success(
        val message: String,
        val latestDirectSessionActionRecord: TaskMailSessionActionSendRecord? = null,
        val directControlPlaneSnapshot: TaskSessionControlPlaneSnapshot? = null,
        val pendingSubmission: TaskSessionPendingSubmission? = null,
    ) : TaskSessionSendUiResult

    data class Failure(
        val errorMessage: String,
        val latestDirectSessionActionRecord: TaskMailSessionActionSendRecord? = null,
    ) : TaskSessionSendUiResult
}

private const val FOREGROUND_REFRESH_INTERVAL_MS = 10_000L
private const val SESSION_NOT_FOUND_ERROR_CODE = "session_not_found"
private const val SESSION_SNAPSHOT_RECOVERY_MAX_ATTEMPTS = 18
private const val SESSION_SNAPSHOT_RECOVERY_DELAY_MS = 10_000L
private const val TAG = "TaskSessionDetailViewModel"

private fun TaskSessionKey.toDirectSessionActionTargetOrNull(): TaskMailDirectSessionActionTarget? {
    val normalizedWorkspaceId = workspaceId?.trim()?.takeIf(String::isNotBlank)
    val normalizedSessionId = sessionId?.trim()?.takeIf(String::isNotBlank)
    val normalizedThreadId = threadId?.trim()?.takeIf(String::isNotBlank)

    return if (normalizedSessionId != null) {
        TaskMailDirectSessionActionTarget(
            workspaceId = normalizedWorkspaceId,
            sessionId = normalizedSessionId,
            threadId = normalizedThreadId,
        )
    } else {
        null
    }
}

private fun shouldAttemptCachedDetailBeforeSync(
    forceRefresh: Boolean,
    refreshTransportBeforeLoad: Boolean,
    syncCacheBeforeLoad: Boolean,
): Boolean {
    return !forceRefresh &&
        !refreshTransportBeforeLoad &&
        syncCacheBeforeLoad
}

private fun shouldRenderCachedDetailBeforeSync(
    forceRefresh: Boolean,
    refreshTransportBeforeLoad: Boolean,
    syncCacheBeforeLoad: Boolean,
    cachedDetail: TaskSessionDetail?,
): Boolean {
    return shouldAttemptCachedDetailBeforeSync(
        forceRefresh = forceRefresh,
        refreshTransportBeforeLoad = refreshTransportBeforeLoad,
        syncCacheBeforeLoad = syncCacheBeforeLoad,
    ) &&
        cachedDetail != null
}

private fun TaskSessionDetail.toUiState(
    historySnapshotRounds: ImmutableList<TaskSessionHistorySnapshotRound> = persistentListOf(),
): TaskSessionDetailUiState {
    val effectiveStatus = status
    val effectiveSummary = lastSummary
    val isVpsBackedSession = prefersVpsProjection()
    val directActionTarget = TaskSessionKey(
        workspaceId = key.workspaceId ?: workspace.workspaceId,
        sessionId = key.sessionId,
        threadId = key.threadId,
    ).toDirectSessionActionTargetOrNull()
    val pendingQuestionItems = pendingQuestions
        .map(TaskQuestionCapsule::toUiState)
        .toImmutableList()
    val replyUiState = pendingQuestionItems.toReplyUiState(effectiveStatus)
    val canUseSessionActions = isVpsBackedSession && directActionTarget != null
    val replyUnavailableReason = when {
        !isVpsBackedSession -> DIRECT_SESSION_ACTION_UNAVAILABLE_MESSAGE
        directActionTarget == null -> DIRECT_SESSION_ACTION_UNAVAILABLE_MESSAGE
        replyUiState.requiresResumeBeforeReply -> DIRECT_REPLY_PAUSED_MESSAGE
        else -> null
    }
    val canReply = replyUnavailableReason == null
    val pageMode = effectiveStatus.toPageMode()
    val mergedTimeline = timeline
        .asReversed()
        .map(TaskTimelineItem::toUiState)
        .toImmutableList()
    val timelineResultBody = mergedTimeline.findLatestResultBodyCandidate(
        summary = effectiveSummary,
        statusLabel = effectiveStatus.name,
    )
    val preferredResultBody = historySnapshotRounds.findLatestResultBodyCandidate(
        fallbackItem = timelineResultBody,
        statusLabel = effectiveStatus.name,
    ) ?: timelineResultBody

    val uiState = TaskSessionDetailUiState(
        sessionId = key.sessionId,
        workspaceId = key.workspaceId ?: workspace.workspaceId,
        sessionName = sessionName,
        backend = backend.name,
        status = effectiveStatus.name,
        pageMode = pageMode,
        repoPath = repoPath,
        workdir = workdir.toDisplayWorkdir(),
        lastSummary = effectiveSummary,
        lastActiveAt = lastActiveAt,
        lastProgressAt = lastProgressAt,
        processSection = buildProcessSection(
            pageMode = pageMode,
            historySnapshotRounds = historySnapshotRounds,
        ),
        pendingSubmission = pendingSubmissions.firstOrNull()?.toUiState(),
        recentContext = buildRecentContext(
            timeline = mergedTimeline,
            summary = effectiveSummary,
            pendingQuestions = pendingQuestionItems,
        ),
        resultSummary = buildResultSummary(
            status = effectiveStatus,
            summary = effectiveSummary,
        ),
        resultBody = preferredResultBody,
        artifacts = persistentListOf(),
        historyPreview = mergedTimeline
            .take(HISTORY_PREVIEW_COUNT)
            .map(TaskTimelineItemUi::toHistoryRoundUi)
            .toImmutableList(),
        pendingQuestions = pendingQuestionItems,
        quickAnswerChoices = replyUiState.quickAnswerChoices,
        requiresStructuredReply = replyUiState.requiresStructuredReply,
        requiresResumeBeforeReply = replyUiState.requiresResumeBeforeReply,
        structuredReplyTemplate = replyUiState.structuredReplyTemplate,
        replyLabel = replyUiState.replyLabel,
        replySupportingText = replyUiState.replySupportingText,
        replyContext = replyContext,
        canReply = canReply,
        canQueryStatus = canUseSessionActions,
        replyUnavailableReason = replyUnavailableReason,
        timeline = mergedTimeline,
    )

    return if (controlPlaneSnapshot != null) {
        uiState.withControlPlaneSnapshot(controlPlaneSnapshot)
    } else {
        uiState
    }
}

private fun TaskSessionDetail.buildProcessSection(
    pageMode: TaskSessionPageMode,
    historySnapshotRounds: ImmutableList<TaskSessionHistorySnapshotRound>,
): TaskProcessSectionUi? {
    return when (pageMode) {
        TaskSessionPageMode.ActiveRun -> liveProcess?.toProcessSectionUi(
            title = processSectionTitle(pageMode),
            supportingText = processSectionSupportingText(pageMode),
            defaultExpanded = true,
        )

        TaskSessionPageMode.AwaitingReply,
        TaskSessionPageMode.Terminal,
        -> historySnapshotRounds
            .latestStableRoundProcessItems()
            ?.toProcessSectionUi(
                title = processSectionTitle(pageMode),
                supportingText = processSectionSupportingText(pageMode),
                defaultExpanded = false,
            )
    }
}

private fun TaskTimelineItem.toUiState(): TaskTimelineItemUi {
    return TaskTimelineItemUi(
        id = id,
        timestamp = timestamp,
        direction = direction.name,
        statusLabel = statusLabel?.name,
        summary = summary,
        plainText = body.plainText,
        renderMode = body.renderMode,
        richDocument = body.richDocument,
        attachments = attachments.map(TaskMessageAttachment::toUiState).toImmutableList(),
    )
}

private fun buildRecentContext(
    timeline: ImmutableList<TaskTimelineItemUi>,
    summary: String?,
    pendingQuestions: ImmutableList<TaskPendingQuestionUi>,
): TaskRecentContextUi? {
    val latestUserMessage = timeline
        .firstOrNull { item -> item.direction.equals("Outgoing", ignoreCase = true) }
        ?.plainText
        ?.takeIf(String::isNotBlank)
    val latestAssistantMessage = timeline
        .firstOrNull { item ->
            item.direction.equals("Incoming", ignoreCase = true) ||
                item.direction.equals("System", ignoreCase = true)
        }
        ?.plainText
        ?.takeIf(String::isNotBlank)
    val waitingForUserText = pendingQuestions.firstOrNull()?.questionText
        ?: summary?.takeIf(String::isNotBlank)

    if (latestUserMessage == null && latestAssistantMessage == null && waitingForUserText == null) {
        return null
    }

    return TaskRecentContextUi(
        latestUserMessage = latestUserMessage,
        latestAssistantMessage = latestAssistantMessage,
        waitingForUserText = waitingForUserText,
    )
}

private fun buildResultSummary(
    status: TaskMailSessionStatus,
    summary: String?,
): TaskResultSummaryUi {
    val headline = when (status) {
        TaskMailSessionStatus.Done -> "Latest run completed"
        TaskMailSessionStatus.Failed -> "Latest run failed"
        TaskMailSessionStatus.Running -> "Run in progress"
        TaskMailSessionStatus.WaitingUser -> "Waiting for your reply"
        TaskMailSessionStatus.Paused -> "Session paused"
        else -> "Latest session result"
    }
    val supportingText = summary?.takeIf(String::isNotBlank)

    return TaskResultSummaryUi(
        headline = headline,
        supportingText = supportingText,
        statusLabel = status.name,
    )
}

private fun processSectionTitle(pageMode: TaskSessionPageMode): String {
    return "Process"
}

private fun processSectionSupportingText(pageMode: TaskSessionPageMode): String {
    return if (pageMode == TaskSessionPageMode.ActiveRun) {
        "Assistant output is shown in order while the current run continues."
    } else {
        "Open the preserved assistant process behind the latest stable result when you need more detail."
    }
}

private fun ImmutableList<TaskSessionHistorySnapshotRound>.latestStableRoundProcessItems(): Iterable<TaskSessionProcessItem>? {
    return maxByOrNull { round ->
        if (round.status.isStableProcessRoundStatus()) round.roundNumber else Int.MIN_VALUE
    }
        ?.takeIf { round -> round.status.isStableProcessRoundStatus() }
        ?.processItems
}

private fun String.isStableProcessRoundStatus(): Boolean {
    return when (trim().lowercase()) {
        "queued",
        "running",
        -> false

        else -> true
    }
}

private fun TaskSessionKey.matchesObservationKey(other: TaskSessionKey): Boolean {
    return this == other || isCompatibleWith(other) || other.isCompatibleWith(this)
}

private fun TaskTimelineItemUi.toHistoryRoundUi(): TaskHistoryRoundUi {
    return TaskHistoryRoundUi(
        id = id,
        title = summary ?: statusLabel ?: direction,
        summary = statusLabel,
        statusLabel = statusLabel,
        messagePreview = plainText.takeIf(String::isNotBlank),
    )
}

private fun TaskQuestionCapsule.toUiState(): TaskPendingQuestionUi {
    return TaskPendingQuestionUi(
        questionId = questionId,
        questionText = questionText,
        choices = choices.map { choice ->
            TaskPendingQuestionChoiceUi(
                value = choice,
                label = choiceLabels[choice] ?: choice,
            )
        }.toImmutableList(),
        isRequired = required,
    )
}

private fun TaskSessionPendingSubmission.toUiState(): TaskPendingSubmissionUi {
    val actionLabel = when (actionType.wireValue) {
        "reply" -> "Reply"
        "answers" -> "Answers"
        "status" -> "/status"
        "pause" -> "Pause"
        "resume" -> "Resume"
        "kill" -> "Stop running"
        "end" -> "Deactivate"
        "attachment_continuation" -> "Attachments"
        else -> "Command"
    }
    val message = when (ackStatus) {
        TaskMailSessionActionAckStatus.Accepted -> "$actionLabel accepted by the PC."
        TaskMailSessionActionAckStatus.AcceptedButQueued -> "$actionLabel accepted and queued on the PC."
        TaskMailSessionActionAckStatus.Rejected -> "$actionLabel was rejected by the PC."
    }

    return TaskPendingSubmissionUi(
        message = message,
        submittedAt = submittedAt,
    )
}

private data class ReplyUiStateComponents(
    val quickAnswerChoices: ImmutableList<TaskPendingQuestionChoiceUi>,
    val requiresStructuredReply: Boolean,
    val requiresResumeBeforeReply: Boolean,
    val structuredReplyTemplate: String?,
    val replyLabel: String,
    val replySupportingText: String,
)

private fun List<TaskPendingQuestionUi>.toReplyUiState(
    status: TaskMailSessionStatus,
): ReplyUiStateComponents {
    val requiresStructuredReply = size > 1
    val quickAnswerChoices = when (size) {
        1 -> single().choices
        else -> persistentListOf()
    }
    val requiresResumeBeforeReply = status == TaskMailSessionStatus.Paused

    return ReplyUiStateComponents(
        quickAnswerChoices = quickAnswerChoices,
        requiresStructuredReply = requiresStructuredReply,
        requiresResumeBeforeReply = requiresResumeBeforeReply,
        structuredReplyTemplate = if (requiresStructuredReply) {
            buildStructuredReplyTemplate(this)
        } else {
            null
        },
        replyLabel = if (requiresStructuredReply) "Answers" else "Reply to this task",
        replySupportingText = replySupportingText(
            requiresResumeBeforeReply = requiresResumeBeforeReply,
            requiresStructuredReply = requiresStructuredReply,
            hasQuickAnswers = quickAnswerChoices.isNotEmpty(),
        ),
    )
}

private fun TaskMailSessionStatus.toPageMode(): TaskSessionPageMode {
    return when (this) {
        TaskMailSessionStatus.Queued,
        TaskMailSessionStatus.Running,
        -> TaskSessionPageMode.ActiveRun

        TaskMailSessionStatus.WaitingUser,
        TaskMailSessionStatus.Paused,
        -> TaskSessionPageMode.AwaitingReply

        TaskMailSessionStatus.Done,
        TaskMailSessionStatus.Failed,
        TaskMailSessionStatus.Killed,
        TaskMailSessionStatus.Unknown,
        -> TaskSessionPageMode.Terminal
    }
}

private fun replySupportingText(
    requiresResumeBeforeReply: Boolean,
    requiresStructuredReply: Boolean,
    hasQuickAnswers: Boolean,
): String {
    return when {
        requiresResumeBeforeReply && requiresStructuredReply -> {
            "This session is paused. Structured answers are currently unavailable in this screen."
        }

        requiresResumeBeforeReply && hasQuickAnswers -> {
            "This session is paused. Quick answers are currently unavailable in this screen."
        }

        requiresResumeBeforeReply -> "This session is paused. Plain-text reply is currently unavailable in this screen."
        requiresStructuredReply -> {
            "Answer each pending question below. Your answers will be sent as a structured TaskMail payload."
        }

        hasQuickAnswers -> {
            "Answer the pending question with plain text, attachments, or a quick TaskMail action."
        }

        else -> "Send a plain-text reply to continue this task."
    }
}

private fun TaskSessionDetail.mergeControlPlaneSnapshot(
    snapshot: TaskSessionControlPlaneSnapshot?,
): TaskSessionDetail {
    val mergedSnapshot = controlPlaneSnapshot.merge(snapshot)
    return if (mergedSnapshot == controlPlaneSnapshot) {
        this
    } else {
        copy(controlPlaneSnapshot = mergedSnapshot)
    }
}

private fun TaskMessageAttachment.toUiState(): TaskTimelineAttachmentUi {
    val actionTarget = toActionTarget()
    return TaskTimelineAttachmentUi(
        id = id,
        displayName = displayName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        isInline = isInline,
        isImage = isImage,
        internalUriString = internalUriString,
        actionTarget = actionTarget,
    )
}

private fun TaskMessageAttachment.toActionTarget(): TaskAttachmentActionTarget? {
    val normalizedInternalUri = internalUriString?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return TaskAttachmentActionTarget(
        attachmentId = id,
        displayName = displayName,
        contentType = contentType,
        legacyMail = LegacyMailAttachmentTarget(
            internalUriString = normalizedInternalUri,
            accountUuid = accountUuid,
            folderId = folderId,
            messageServerId = messageServerId,
            partId = partId,
            isContentAvailable = isContentAvailable,
        ),
    )
}

private fun TaskSessionDetailUiState.allAttachments(): Sequence<TaskTimelineAttachmentUi> {
    val resultBodyAttachments = resultBody?.attachments.orEmpty()
    return sequenceOf(
        timeline.asSequence().flatMap { item -> item.attachments.asSequence() },
        artifacts.asSequence(),
        resultBodyAttachments.asSequence(),
    ).flatten()
}

private fun buildStructuredReplyTemplate(pendingQuestions: List<TaskPendingQuestionUi>): String {
    return buildString {
        appendLine("Answers:")
        pendingQuestions.forEach { question ->
            append(question.questionId)
            appendLine(":")
        }
    }.trimEnd()
}

private fun List<TaskMailSessionQuestionAnswer>.toStructuredReplyBody(): String {
    return buildString {
        appendLine("Answers:")
        this@toStructuredReplyBody.forEach { answer ->
            append(answer.questionId)
            append(": ")
            appendLine(answer.value)
        }
    }.trimEnd()
}

private fun TaskMailDirectSessionActionRequest.toPendingSubmission(
    acceptedResult: TaskMailDirectSessionActionResult.Accepted,
    submittedAt: Long,
): TaskSessionPendingSubmission? {
    val targetIdentity = acceptedResult.targetIdentity
        ?: target.workspaceId?.let { target.toTargetIdentity() }
        ?: return null

    return TaskSessionPendingSubmission(
        commandId = acceptedResult.commandId ?: acceptedResult.receiptId,
        requestId = acceptedResult.requestId,
        actionType = actionType,
        submittedAt = submittedAt,
        ackStatus = acceptedResult.ackStatus,
        targetIdentity = targetIdentity,
    )
}

private fun State.resolveDraftText(detail: TaskSessionDetailUiState): String {
    val currentDraft = draftText
    val previousTemplate = this.detail?.structuredReplyTemplate
    val nextTemplate = detail.structuredReplyTemplate

    return when {
        nextTemplate != null && currentDraft.isBlank() -> nextTemplate
        nextTemplate != null && previousTemplate != null && currentDraft == previousTemplate -> nextTemplate
        nextTemplate == null && previousTemplate != null && currentDraft == previousTemplate -> ""
        else -> currentDraft
    }
}

private fun TaskSessionDetailUiState.isCurrentInputLedMode(): Boolean {
    return status.equals("Queued", ignoreCase = true) ||
        status.equals("Running", ignoreCase = true)
}

private const val HISTORY_PREVIEW_COUNT = 5

private fun TaskMailDirectSessionActionResult.toDirectSendEvidence(): TaskMailDirectSendEvidence {
    return when (this) {
        is TaskMailDirectSessionActionResult.Accepted -> {
            TaskMailDirectSendEvidence(
                bootstrapStatus = RelayBootstrapStatus.HelloAck,
                outcome = TaskMailDirectOutcome.DirectAccepted,
                switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                requestId = requestId,
                receiptId = receiptId,
                transportMessageId = transportMessageId,
            )
        }

        is TaskMailDirectSessionActionResult.FallbackToMail -> {
            TaskMailDirectSendEvidence(
                bootstrapStatus = RelayBootstrapStatus.HelloAck,
                outcome = TaskMailDirectOutcome.DirectRejected,
                switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                requestId = requestId,
                receiptId = receiptId,
                transportMessageId = transportMessageId,
                fallbackReason = detailMessage?.trim()?.takeIf(String::isNotEmpty),
                errorMessage = detailMessage?.trim()?.takeIf(String::isNotEmpty),
            )
        }

        is TaskMailDirectSessionActionResult.Rejected -> {
            TaskMailDirectSendEvidence(
                bootstrapStatus = RelayBootstrapStatus.HelloAck,
                outcome = TaskMailDirectOutcome.DirectRejected,
                switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                requestId = requestId,
                receiptId = receiptId,
                transportMessageId = transportMessageId,
                errorMessage = errorMessage.trim().takeIf(String::isNotEmpty),
            )
        }
    }
}

private fun String?.orFailureMessage(defaultMessage: String): String {
    return this?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: defaultMessage
}

private fun Throwable.isRetryableSessionSnapshotMiss(): Boolean {
    if (this is SessionSnapshotRequestException && errorCode == SESSION_NOT_FOUND_ERROR_CODE) {
        return true
    }

    return message?.contains(
        "could not resolve a session for the requested session_id",
        ignoreCase = true,
    ) == true
}

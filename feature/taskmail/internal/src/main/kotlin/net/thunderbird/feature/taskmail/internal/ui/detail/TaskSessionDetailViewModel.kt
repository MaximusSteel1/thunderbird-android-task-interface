@file:Suppress("TooManyFunctions")

package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.thunderbird.core.logging.Logger
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.data.TaskMailForegroundRefreshTickerFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.TaskMailTimelineAttachmentHandler
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectAcceptedEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyResult
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionRequest
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionResult
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionTarget
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetLatestTaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailDirectSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailStoreChanges
import net.thunderbird.feature.taskmail.internal.domain.usecase.RecordTaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.RefreshTaskMail
import net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectOrFallback
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailDirectSessionAction
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailReply
import net.thunderbird.feature.taskmail.internal.domain.usecase.SyncTaskMailCache
import net.thunderbird.feature.taskmail.internal.domain.usecase.TaskMailDirectAttemptResult
import net.thunderbird.feature.taskmail.internal.domain.usecase.TaskMailDirectOrFallbackResult
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract.Event
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract.State
import net.thunderbird.feature.taskmail.internal.ui.toDisplayWorkdir

private const val SEND_FAILURE_MESSAGE = "Failed to send TaskMail reply."
private const val REPLY_SENT_VIA_MAIL_MESSAGE = "[Mail] Reply sent."
private const val REPLY_SENT_VIA_DIRECT_MESSAGE =
    "[Relay] Reply sent. Final state will refresh after the canonical TaskMail mail arrives."
private const val REPLY_SENT_VIA_MAIL_FALLBACK_MESSAGE = "[Mail fallback] Reply sent."
private const val QUICK_ANSWER_SENT_VIA_MAIL_MESSAGE = "[Mail] Quick answer sent."
private const val STATUS_QUERY_SENT_VIA_MAIL_MESSAGE = "[Mail] /status sent."
private const val STATUS_QUERY_SENT_VIA_DIRECT_MESSAGE =
    "[Relay] /status sent. Final state will refresh after the canonical [STATUS] mail arrives."
private const val STATUS_QUERY_SENT_VIA_MAIL_FALLBACK_MESSAGE = "[Mail fallback] /status sent."

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class TaskSessionDetailViewModel(
    detailRepository: TaskSessionDetailRepository,
    private val getTaskSessionDetail: GetTaskSessionDetail = GetTaskSessionDetail(detailRepository),
    private val refreshTaskMail: RefreshTaskMail,
    private val observeTaskMailStoreChanges: ObserveTaskMailStoreChanges,
    private val foregroundRefreshTickerFactory: TaskMailForegroundRefreshTickerFactory,
    private val sendTaskMailReply: SendTaskMailReply,
    private val sendTaskMailDirectSessionAction: SendTaskMailDirectSessionAction? = null,
    private val getLatestTaskMailSessionActionSendRecord: GetLatestTaskMailSessionActionSendRecord? = null,
    private val recordTaskMailSessionActionSendRecord: RecordTaskMailSessionActionSendRecord? = null,
    private val replyAttachmentResolver: TaskMailReplyAttachmentResolver,
    private val timelineAttachmentHandler: TaskMailTimelineAttachmentHandler,
    private val logger: Logger,
    private val runTaskMailDirectOrFallback: RunTaskMailDirectOrFallback? = null,
    private val syncTaskMailCache: SyncTaskMailCache? = null,
    private val observeTaskMailDirectSessionDetail: ObserveTaskMailDirectSessionDetail? = null,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskSessionDetailContract.ViewModel {

    private var currentKey: TaskSessionKey? = null
    private var currentDetail: TaskSessionDetail? = null
    private val loadMutex = Mutex()
    private val syncMutex = Mutex()
    private var foregroundRefreshJob: Job? = null
    private var directObservationJob: Job? = null
    private var directProjectionKey: TaskSessionKey? = null
    private var currentDirectProjectionStatus: TaskMailSessionStatus? = null
    private var currentDirectProjectionSummary: String? = null
    private var currentDirectProjectionQuestions: List<TaskQuestionCapsule>? = null
    private var currentDirectProjectionTimeline: List<TaskTimelineItem>? = null

    init {
        observeLocalMailChanges()
    }

    override fun onCleared() {
        stopDirectObservation(clearProjection = true)
        super.onCleared()
    }

    override fun event(event: Event) {
        when (event) {
            is Event.LoadDetail -> loadDetail(
                key = TaskSessionKey(
                    workspaceId = event.workspaceId?.trim()?.takeIf(String::isNotBlank),
                    sessionId = event.sessionId,
                    threadId = event.threadId,
                ),
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
        forceRefresh: Boolean = false,
        refreshTransportBeforeLoad: Boolean = false,
        syncCacheBeforeLoad: Boolean = false,
    ) {
        val previousKey = currentKey
        if (shouldSkipDetailLoad(key = key, forceRefresh = forceRefresh, comparisonKey = currentKey)) return

        currentKey = key
        if (key != previousKey) {
            stopDirectObservation(clearProjection = true)
            loadLatestDirectSessionActionRecord(key)
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
                    launchBackgroundSyncAndReload(key)
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
            stopDirectObservation(clearProjection = true)
            handleMissingDetail(loadContext)
            return
        }

        startOrRefreshDirectObservation(detail)
        logger.debug(TAG) {
            "Rendered repository detail " +
                "status=${detail.status.name} " +
                "pendingQuestionCount=${detail.pendingQuestions.size} " +
                "mailTimelineCount=${detail.timeline.size} " +
                "directOverlayActive=${hasActiveDirectOverlay()}"
        }
        val uiDetail = detail.toUiState(
            directStatus = currentDirectProjectionStatus,
            directSummary = currentDirectProjectionSummary,
            directQuestions = currentDirectProjectionQuestions,
            directTimeline = currentDirectProjectionTimeline,
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
                replyAttachments = if (loadContext.isSameKey) it.replyAttachments else persistentListOf(),
            )
        }
    }

    private fun handleMissingDetail(loadContext: DetailLoadContext) {
        updateState {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                error = "Task session detail was not found.",
                refreshError = null,
                draftText = if (loadContext.isSameKey) it.draftText else "",
                replyAttachments = if (loadContext.isSameKey) it.replyAttachments else persistentListOf(),
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
            stopDirectObservation(clearProjection = true)
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
                    draftText = "",
                    replyAttachments = persistentListOf(),
                    detail = null,
                )
            }
        }
    }

    private fun startOrRefreshDirectObservation(detail: TaskSessionDetail) {
        val observer = observeTaskMailDirectSessionDetail ?: return
        if (directObservationJob != null && directProjectionKey == detail.key) {
            return
        }

        stopDirectObservation(clearProjection = true)
        directProjectionKey = detail.key
        logger.debug(TAG) { "Starting direct detail observation from detail view model." }
        directObservationJob = viewModelScope.launch {
            observer(detail).collect { projection ->
                if (currentDetail?.key != detail.key) return@collect

                logger.debug(TAG) {
                    "Applying direct detail projection " +
                        "status=${projection.headerStatus.name} " +
                        "pendingQuestionCount=${projection.pendingQuestions.size} " +
                        "provisionalTimelineCount=${projection.provisionalTimeline.size} " +
                        "lastSequence=${projection.lastSequence}"
                }
                currentDirectProjectionStatus = projection.headerStatus
                currentDirectProjectionSummary = projection.lastSummary
                currentDirectProjectionQuestions = projection.pendingQuestions
                currentDirectProjectionTimeline = projection.provisionalTimeline
                renderDirectProjection()
            }
        }
    }

    private fun stopDirectObservation(clearProjection: Boolean) {
        directObservationJob?.cancel()
        directObservationJob = null
        directProjectionKey = null

        if (clearProjection) {
            currentDirectProjectionStatus = null
            currentDirectProjectionSummary = null
            currentDirectProjectionQuestions = null
            currentDirectProjectionTimeline = null
        }
    }

    private fun hasActiveDirectOverlay(): Boolean {
        return currentDirectProjectionStatus != null ||
            currentDirectProjectionSummary != null ||
            currentDirectProjectionQuestions != null ||
            currentDirectProjectionTimeline != null
    }

    private fun renderDirectProjection() {
        val detail = currentDetail ?: return
        val uiDetail = detail.toUiState(
            directStatus = currentDirectProjectionStatus,
            directSummary = currentDirectProjectionSummary,
            directQuestions = currentDirectProjectionQuestions,
            directTimeline = currentDirectProjectionTimeline,
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

    @Suppress("ReturnCount")
    private fun sendReply() {
        val currentState = state.value
        val detail = currentState.detail ?: return
        currentDetail ?: return
        val draftText = currentState.draftText
        if (!detail.requiresStructuredReply && draftText.isBlank() && currentState.replyAttachments.isEmpty()) {
            return
        }

        withReplyContext { context ->
            if (detail.requiresStructuredReply && !detail.canSendReply(draftText, currentState.replyAttachments.size)) {
                updateState {
                    it.copy(sendError = "Complete all required answers using valid question_id values before sending.")
                }
                return@withReplyContext
            }

            if (canUseDirectPlainReply(detail = detail, attachments = currentState.replyAttachments)) {
                performSend(sendFailureMessage = SEND_FAILURE_MESSAGE) {
                    sendDirectSessionActionOrFallback(
                        request = TaskMailDirectSessionActionRequest.Reply(
                            target = requireNotNull(currentDirectSessionActionTargetOrNull()),
                            replyText = draftText,
                        ),
                        mailFallback = {
                            sendTaskMailReply.sendFreeText(
                                context = context,
                                draftText = draftText,
                                attachments = currentState.replyAttachments,
                            )
                        },
                        directSuccessMessage = REPLY_SENT_VIA_DIRECT_MESSAGE,
                        fallbackSuccessMessage = REPLY_SENT_VIA_MAIL_FALLBACK_MESSAGE,
                    )
                }
            } else {
                performMailSend(REPLY_SENT_VIA_MAIL_MESSAGE) {
                    if (detail.requiresResumeBeforeReply) {
                        sendTaskMailReply.sendResumeSession(
                            context = context,
                            draftText = draftText,
                            attachments = currentState.replyAttachments,
                        )
                    } else if (detail.requiresStructuredReply) {
                        sendTaskMailReply.sendStructuredAnswers(
                            context = context,
                            draftText = draftText,
                            attachments = currentState.replyAttachments,
                        )
                    } else {
                        sendTaskMailReply.sendFreeText(
                            context = context,
                            draftText = draftText,
                            attachments = currentState.replyAttachments,
                        )
                    }
                }
            }
        }
    }

    private fun sendChoice(choice: String) {
        val detail = state.value.detail ?: return
        if (!detail.canUseQuickAnswer(choice)) {
            updateState {
                it.copy(sendError = "Quick answers are only available when exactly one pending question is active.")
            }
            return
        }

        withReplyContext { context ->
            performMailSend(QUICK_ANSWER_SENT_VIA_MAIL_MESSAGE) {
                if (detail.requiresResumeBeforeReply) {
                    sendTaskMailReply.sendResumeSession(
                        context = context,
                        draftText = choice,
                        attachments = state.value.replyAttachments,
                    )
                } else {
                    sendTaskMailReply.sendQuestionChoice(
                        context = context,
                        choice = choice,
                        attachments = state.value.replyAttachments,
                    )
                }
            }
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

        withReplyContext { context ->
            if (canUseDirectStatusQuery(detail = detail)) {
                performSend(sendFailureMessage = SEND_FAILURE_MESSAGE) {
                    sendDirectSessionActionOrFallback(
                        request = TaskMailDirectSessionActionRequest.Status(
                            target = requireNotNull(currentDirectSessionActionTargetOrNull()),
                        ),
                        mailFallback = {
                            sendTaskMailReply.sendStatusQuery(context)
                        },
                        directSuccessMessage = STATUS_QUERY_SENT_VIA_DIRECT_MESSAGE,
                        fallbackSuccessMessage = STATUS_QUERY_SENT_VIA_MAIL_FALLBACK_MESSAGE,
                    )
                }
            } else {
                performMailSend(STATUS_QUERY_SENT_VIA_MAIL_MESSAGE) {
                    sendTaskMailReply.sendStatusQuery(context)
                }
            }
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
                attachmentId = attachment.id,
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

    private fun withReplyContext(action: (TaskSessionReplyContext) -> Unit) {
        val detail = state.value.detail ?: return
        val replyContext = detail.replyContext

        if (replyContext == null) {
            updateState {
                it.copy(
                    sendError = detail.replyUnavailableReason ?: "Reply unavailable for this session.",
                )
            }
            return
        }
        if (!state.value.isSending) {
            action(replyContext)
        }
    }

    private fun performMailSend(
        successMessage: String,
        action: suspend () -> TaskMailReplyResult,
    ) {
        performSend(sendFailureMessage = SEND_FAILURE_MESSAGE) {
            action().toUiSendResult(successMessage)
        }
    }

    private fun performSend(
        sendFailureMessage: String,
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
                            draftText = "",
                            latestDirectSessionActionRecord = result.latestDirectSessionActionRecord
                                ?: it.latestDirectSessionActionRecord,
                            replyAttachments = persistentListOf(),
                            sendError = null,
                        )
                    }
                    emitEffect(Effect.ShowMessage(result.message))
                    val key = currentKey ?: return@launch
                    loadDetail(
                        key = key,
                        forceRefresh = true,
                        refreshTransportBeforeLoad = false,
                        syncCacheBeforeLoad = true,
                    )
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

    private suspend fun sendDirectSessionActionOrFallback(
        request: TaskMailDirectSessionActionRequest,
        mailFallback: suspend () -> TaskMailReplyResult,
        directSuccessMessage: String,
        fallbackSuccessMessage: String,
    ): TaskSessionSendUiResult {
        val directSessionActionSender = requireNotNull(sendTaskMailDirectSessionAction)
        val directOrFallbackRunner = requireNotNull(runTaskMailDirectOrFallback)
        val result = directOrFallbackRunner.execute(
            directSend = {
                directSessionActionSender(request).toDirectAttemptResult()
            },
            mailFallback = {
                mailFallback().toMailFallbackResult()
            },
        )
        val latestRecord = runCatching {
            recordTaskMailSessionActionSendRecord?.invoke(
                request = request,
                evidence = result.evidence,
            )
        }.getOrNull()

        return when (result) {
            is TaskMailDirectOrFallbackResult.DirectAccepted ->
                TaskSessionSendUiResult.Success(
                    message = directSuccessMessage,
                    latestDirectSessionActionRecord = latestRecord,
                )
            is TaskMailDirectOrFallbackResult.MailFallbackSucceeded ->
                TaskSessionSendUiResult.Success(
                    message = fallbackSuccessMessage,
                    latestDirectSessionActionRecord = latestRecord,
                )
            is TaskMailDirectOrFallbackResult.MailFallbackFailed ->
                TaskSessionSendUiResult.Failure(
                    errorMessage = result.errorMessage ?: SEND_FAILURE_MESSAGE,
                    latestDirectSessionActionRecord = latestRecord,
                )
            is TaskMailDirectOrFallbackResult.DirectRejected ->
                TaskSessionSendUiResult.Failure(
                    errorMessage = result.errorMessage.ifBlank { SEND_FAILURE_MESSAGE },
                    latestDirectSessionActionRecord = latestRecord,
                )
        }
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

    private fun canUseDirectPlainReply(
        detail: TaskSessionDetailUiState,
        attachments: List<TaskReplyAttachment>,
    ): Boolean {
        return sendTaskMailDirectSessionAction != null &&
            runTaskMailDirectOrFallback != null &&
            currentDirectSessionActionTargetOrNull() != null &&
            !detail.requiresStructuredReply &&
            !detail.requiresResumeBeforeReply &&
            attachments.isEmpty()
    }

    private fun canUseDirectStatusQuery(
        detail: TaskSessionDetailUiState,
    ): Boolean {
        return sendTaskMailDirectSessionAction != null &&
            runTaskMailDirectOrFallback != null &&
            currentDirectSessionActionTargetOrNull() != null &&
            detail.canQueryStatus
    }

    @Suppress("ReturnCount")
    private fun currentDirectSessionActionTargetOrNull(): TaskMailDirectSessionActionTarget? {
        return currentKey?.toDirectSessionActionTargetOrNull()
    }

    private fun findTimelineAttachment(attachmentId: String): TaskMessageAttachment? {
        return currentDetail?.timeline
            ?.asSequence()
            ?.flatMap { item -> item.attachments.asSequence() }
            ?.firstOrNull { attachment -> attachment.id == attachmentId }
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
    ) : TaskSessionSendUiResult

    data class Failure(
        val errorMessage: String,
        val latestDirectSessionActionRecord: TaskMailSessionActionSendRecord? = null,
    ) : TaskSessionSendUiResult
}

private const val FOREGROUND_REFRESH_INTERVAL_MS = 10_000L
private const val TAG = "TaskSessionDetailViewModel"

private fun TaskSessionKey.toDirectSessionActionTargetOrNull(): TaskMailDirectSessionActionTarget? {
    val normalizedWorkspaceId = workspaceId?.trim()?.takeIf(String::isNotBlank)
    val normalizedSessionId = sessionId?.trim()?.takeIf(String::isNotBlank)

    return if (normalizedWorkspaceId != null && normalizedSessionId != null) {
        TaskMailDirectSessionActionTarget(
            workspaceId = normalizedWorkspaceId,
            sessionId = normalizedSessionId,
            threadId = threadId,
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
    directStatus: TaskMailSessionStatus? = null,
    directSummary: String? = null,
    directQuestions: List<TaskQuestionCapsule>? = null,
    directTimeline: List<TaskTimelineItem>? = null,
): TaskSessionDetailUiState {
    val effectiveStatus = directStatus ?: status
    val effectiveSummary = directSummary ?: lastSummary
    val canReply = replyContext != null
    val pendingQuestionItems = (directQuestions ?: pendingQuestions)
        .map(TaskQuestionCapsule::toUiState)
        .toImmutableList()
    val replyUiState = pendingQuestionItems.toReplyUiState(effectiveStatus)
    val mergedTimeline = mergeTimeline(
        mailTimeline = timeline,
        directTimeline = directTimeline,
    )
        .asReversed()
        .map(TaskTimelineItem::toUiState)
        .toImmutableList()
    val artifactItems = mergedTimeline
        .flatMap(TaskTimelineItemUi::attachments)
        .distinctBy(TaskTimelineAttachmentUi::id)
        .map(TaskTimelineAttachmentUi::toArtifactUi)
        .toImmutableList()

    return TaskSessionDetailUiState(
        sessionName = sessionName,
        backend = backend.name,
        status = effectiveStatus.name,
        repoPath = repoPath,
        workdir = workdir.toDisplayWorkdir(),
        lastSummary = effectiveSummary,
        recentContext = buildRecentContext(
            timeline = mergedTimeline,
            summary = effectiveSummary,
            pendingQuestions = pendingQuestionItems,
        ),
        resultSummary = buildResultSummary(
            status = effectiveStatus,
            summary = effectiveSummary,
            artifactCount = artifactItems.size,
        ),
        artifacts = artifactItems,
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
        canQueryStatus = canReply,
        replyUnavailableReason = if (canReply) null else "Reply unavailable for this session.",
        timeline = mergedTimeline,
    )
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
    artifactCount: Int,
): TaskResultSummaryUi {
    val headline = when (status) {
        TaskMailSessionStatus.Done -> "Latest run completed"
        TaskMailSessionStatus.Failed -> "Latest run failed"
        TaskMailSessionStatus.Running -> "Run in progress"
        TaskMailSessionStatus.WaitingUser -> "Waiting for your reply"
        TaskMailSessionStatus.Paused -> "Session paused"
        else -> "Latest session result"
    }
    val supportingText = buildList {
        summary?.takeIf(String::isNotBlank)?.let(::add)
        if (artifactCount > 0) {
            add("$artifactCount file" + if (artifactCount == 1) "" else "s")
        }
    }
        .joinToString(separator = " · ")
        .ifBlank { null }

    return TaskResultSummaryUi(
        headline = headline,
        supportingText = supportingText,
        statusLabel = status.name,
    )
}

private fun TaskTimelineAttachmentUi.toArtifactUi(): TaskSessionArtifactUi {
    val supportingText = buildList {
        contentType?.takeIf(String::isNotBlank)?.let(::add)
        sizeBytes?.takeIf { it > 0 }?.let { add("${it} B") }
    }
        .joinToString(separator = " · ")
        .ifBlank { null }

    return TaskSessionArtifactUi(
        id = id,
        title = displayName,
        supportingText = supportingText,
    )
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

private fun replySupportingText(
    requiresResumeBeforeReply: Boolean,
    requiresStructuredReply: Boolean,
    hasQuickAnswers: Boolean,
): String {
    return when {
        requiresResumeBeforeReply && requiresStructuredReply -> {
            "This session is paused. Sending will prepend /resume, then use one line per question in the form " +
                "question_id: value."
        }

        requiresResumeBeforeReply && hasQuickAnswers -> {
            "This session is paused. Quick answers and manual replies will resume it with /resume first."
        }

        requiresResumeBeforeReply -> "This session is paused. Sending will prepend /resume before continuing."
        requiresStructuredReply -> {
            "Use one line per question in the form question_id: value. " +
                "The draft is prefilled with a copy-ready template, and you can attach files if needed."
        }

        hasQuickAnswers -> {
            "Answer the pending question with plain text, attachments, or a quick TaskMail action."
        }

        else -> "Send a plain-text reply, attach files, or both to continue this task."
    }
}

private fun TaskMessageAttachment.toUiState(): TaskTimelineAttachmentUi {
    return TaskTimelineAttachmentUi(
        id = id,
        displayName = displayName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        isInline = isInline,
        isImage = isImage,
        internalUriString = internalUriString,
        isActionAvailable = internalUriString != null,
    )
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

private const val HISTORY_PREVIEW_COUNT = 5

private fun TaskMailReplyResult.toUiSendResult(
    successMessage: String,
): TaskSessionSendUiResult {
    return if (isSuccess) {
        TaskSessionSendUiResult.Success(successMessage)
    } else {
        TaskSessionSendUiResult.Failure(errorMessage ?: SEND_FAILURE_MESSAGE)
    }
}

private fun TaskMailReplyResult.toMailFallbackResult(): Result<Unit> {
    return if (isSuccess) {
        Result.success(Unit)
    } else {
        Result.failure(
            IllegalStateException(
                errorMessage ?: SEND_FAILURE_MESSAGE,
            ),
        )
    }
}

private fun TaskMailDirectSessionActionResult.toDirectAttemptResult():
    TaskMailDirectAttemptResult<TaskMailDirectSessionActionResult.Accepted> {
    return when (this) {
        is TaskMailDirectSessionActionResult.Accepted -> {
            TaskMailDirectAttemptResult.Accepted(
                payload = this,
                acceptedEvidence = TaskMailDirectAcceptedEvidence(
                    requestId = requestId,
                    receiptId = receiptId,
                    transportMessageId = transportMessageId,
                ),
            )
        }

        is TaskMailDirectSessionActionResult.FallbackToMail ->
            TaskMailDirectAttemptResult.FallbackToMail(
                detailMessage = detailMessage,
                requestId = requestId,
                receiptId = receiptId,
                transportMessageId = transportMessageId,
            )
        is TaskMailDirectSessionActionResult.Rejected ->
            TaskMailDirectAttemptResult.Rejected(
                errorMessage = errorMessage,
                requestId = requestId,
                receiptId = receiptId,
                transportMessageId = transportMessageId,
            )
    }
}

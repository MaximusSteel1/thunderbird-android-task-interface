package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.data.TaskMailForegroundRefreshTickerFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.TaskMailTimelineAttachmentHandler
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
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailStoreChanges
import net.thunderbird.feature.taskmail.internal.domain.usecase.RefreshTaskMail
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailReply
import net.thunderbird.feature.taskmail.internal.domain.usecase.SyncTaskMailCache
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract.Event
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract.State
import net.thunderbird.feature.taskmail.internal.ui.toDisplayWorkdir

@Suppress("TooManyFunctions")
internal class TaskSessionDetailViewModel(
    detailRepository: TaskSessionDetailRepository,
    private val getTaskSessionDetail: GetTaskSessionDetail = GetTaskSessionDetail(detailRepository),
    private val refreshTaskMail: RefreshTaskMail,
    private val observeTaskMailStoreChanges: ObserveTaskMailStoreChanges,
    private val foregroundRefreshTickerFactory: TaskMailForegroundRefreshTickerFactory,
    private val sendTaskMailReply: SendTaskMailReply,
    private val replyAttachmentResolver: TaskMailReplyAttachmentResolver,
    private val timelineAttachmentHandler: TaskMailTimelineAttachmentHandler,
    private val syncTaskMailCache: SyncTaskMailCache? = null,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskSessionDetailContract.ViewModel {

    private var currentKey: TaskSessionKey? = null
    private var currentDetail: TaskSessionDetail? = null
    private val loadMutex = Mutex()
    private val syncMutex = Mutex()
    private var foregroundRefreshJob: Job? = null

    init {
        observeLocalMailChanges()
    }

    override fun event(event: Event) {
        when (event) {
            is Event.LoadDetail -> loadDetail(
                key = TaskSessionKey(
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
            handleMissingDetail(loadContext)
            return
        }

        val uiDetail = detail.toUiState()
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

    private fun sendReply() {
        val currentState = state.value
        val detail = currentState.detail ?: return
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

            performSend {
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

    private fun sendChoice(choice: String) {
        val detail = state.value.detail ?: return
        if (!detail.canUseQuickAnswer(choice)) {
            updateState {
                it.copy(sendError = "Quick answers are only available when exactly one pending question is active.")
            }
            return
        }

        withReplyContext { context ->
            performSend {
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

    private fun sendStatusQuery() {
        if (state.value.replyAttachments.isNotEmpty()) {
            updateState {
                it.copy(sendError = "Remove selected attachments before sending /status.")
            }
            return
        }

        withReplyContext { context ->
            performSend {
                sendTaskMailReply.sendStatusQuery(context)
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

    private fun performSend(action: suspend () -> TaskMailReplyResult) {
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
                    TaskMailReplyResult.failure("Failed to send TaskMail reply.")
                }

            if (result.isSuccess) {
                updateState {
                    it.copy(
                        isSending = false,
                        draftText = "",
                        replyAttachments = persistentListOf(),
                        sendError = null,
                    )
                }
                val key = currentKey ?: return@launch
                loadDetail(
                    key = key,
                    forceRefresh = true,
                    refreshTransportBeforeLoad = false,
                    syncCacheBeforeLoad = true,
                )
            } else {
                updateState {
                    it.copy(
                        isSending = false,
                        sendError = result.errorMessage ?: "Failed to send TaskMail reply.",
                    )
                }
            }
        }
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

private const val FOREGROUND_REFRESH_INTERVAL_MS = 10_000L

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

private fun TaskSessionDetail.toUiState(): TaskSessionDetailUiState {
    val canReply = replyContext != null
    val pendingQuestionItems = pendingQuestions
        .map(TaskQuestionCapsule::toUiState)
        .toImmutableList()
    val requiresStructuredReply = pendingQuestionItems.size > 1
    val quickAnswerChoices = if (pendingQuestionItems.size == 1) {
        pendingQuestionItems.single().choices
    } else {
        persistentListOf()
    }
    val requiresResumeBeforeReply = status == TaskMailSessionStatus.Paused
    val replySupportingText = when {
        requiresResumeBeforeReply && requiresStructuredReply -> {
            "This session is paused. Sending will prepend /resume, then use one line per question in the form " +
                "question_id: value."
        }

        requiresResumeBeforeReply && quickAnswerChoices.isNotEmpty() -> {
            "This session is paused. Quick answers and manual replies will resume it with /resume first."
        }

        requiresResumeBeforeReply -> "This session is paused. Sending will prepend /resume before continuing."
        requiresStructuredReply -> {
            "Use one line per question in the form question_id: value. " +
                "The draft is prefilled with a copy-ready template, and you can attach files if needed."
        }

        quickAnswerChoices.isNotEmpty() -> {
            "Answer the pending question with plain text, attachments, or a quick TaskMail action."
        }
        else -> "Send a plain-text reply, attach files, or both to continue this task."
    }
    val structuredReplyTemplate = if (requiresStructuredReply) {
        buildStructuredReplyTemplate(pendingQuestionItems)
    } else {
        null
    }

    return TaskSessionDetailUiState(
        sessionName = sessionName,
        backend = backend.name,
        status = status.name,
        repoPath = repoPath,
        workdir = workdir.toDisplayWorkdir(),
        lastSummary = lastSummary,
        pendingQuestions = pendingQuestionItems,
        quickAnswerChoices = quickAnswerChoices,
        requiresStructuredReply = requiresStructuredReply,
        requiresResumeBeforeReply = requiresResumeBeforeReply,
        structuredReplyTemplate = structuredReplyTemplate,
        replyLabel = if (requiresStructuredReply) "Answers" else "Reply to this task",
        replySupportingText = replySupportingText,
        replyContext = replyContext,
        canReply = canReply,
        canQueryStatus = canReply,
        replyUnavailableReason = if (canReply) null else "Reply unavailable for this session.",
        timeline = timeline
            .asReversed()
            .map(TaskTimelineItem::toUiState)
            .toImmutableList(),
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

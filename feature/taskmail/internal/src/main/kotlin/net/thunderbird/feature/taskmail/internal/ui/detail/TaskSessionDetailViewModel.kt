package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.TaskMailTimelineAttachmentHandler
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyResult
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailReply
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract.Event
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract.State

@Suppress("TooManyFunctions")
internal class TaskSessionDetailViewModel(
    repository: TaskMailRepository,
    private val getTaskSessionDetail: GetTaskSessionDetail = GetTaskSessionDetail(repository),
    private val sendTaskMailReply: SendTaskMailReply,
    private val replyAttachmentResolver: TaskMailReplyAttachmentResolver,
    private val timelineAttachmentHandler: TaskMailTimelineAttachmentHandler,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskSessionDetailContract.ViewModel {

    private var currentKey: TaskSessionKey? = null
    private var currentDetail: TaskSessionDetail? = null

    override fun event(event: Event) {
        when (event) {
            is Event.LoadDetail -> loadDetail(
                key = TaskSessionKey(
                    sessionId = event.sessionId,
                    threadId = event.threadId,
                ),
            )

            is Event.DraftChanged -> updateState { it.copy(draftText = event.text) }
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
            Event.SendReplyClicked -> sendReply()
            is Event.SendChoiceClicked -> sendChoice(event.choice)
            Event.StatusQueryClicked -> sendStatusQuery()
            Event.RefreshClicked -> refreshDetail()
            Event.DismissSendError -> updateState { it.copy(sendError = null) }
            Event.BackClicked -> emitEffect(Effect.NavigateBack)
        }
    }

    private fun loadDetail(key: TaskSessionKey, forceRefresh: Boolean = false) {
        val previousKey = currentKey
        if (!forceRefresh && key == currentKey && state.value.detail != null) return

        currentKey = key
        viewModelScope.launch {
            updateState {
                it.copy(
                    isLoading = true,
                    error = null,
                )
            }

            runCatching {
                getTaskSessionDetail(key)
            }.onSuccess { detail ->
                currentDetail = detail
                if (detail == null) {
                    updateState {
                        it.copy(
                            isLoading = false,
                            error = "Task session detail was not found.",
                            replyAttachments = if (key == previousKey) it.replyAttachments else persistentListOf(),
                            detail = null,
                        )
                    }
                } else {
                    val uiDetail = detail.toUiState()
                    updateState {
                        it.copy(
                            isLoading = false,
                            error = null,
                            sendError = null,
                            detail = uiDetail,
                            replyAttachments = if (key == previousKey) it.replyAttachments else persistentListOf(),
                            draftText = it.resolveDraftText(uiDetail),
                        )
                    }
                }
            }.onFailure {
                if (state.value.detail == null) {
                    currentDetail = null
                }
                updateState { currentState ->
                    if (currentState.detail != null) {
                        currentState.copy(
                            isLoading = false,
                            sendError = "Failed to refresh TaskMail session detail.",
                        )
                    } else {
                        currentState.copy(
                            isLoading = false,
                            error = "Failed to load TaskMail session detail.",
                            detail = null,
                        )
                    }
                }
            }
        }
    }

    private fun refreshDetail() {
        val key = currentKey ?: return
        loadDetail(key = key, forceRefresh = true)
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
                    it.copy(sendError = "Add at least one structured answer before sending.")
                }
                return@withReplyContext
            }

            performSend {
                if (detail.requiresStructuredReply) {
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
                sendTaskMailReply.sendQuestionChoice(
                    context = context,
                    choice = choice,
                    attachments = state.value.replyAttachments,
                )
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
                refreshDetail()
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
}

private fun TaskSessionDetail.toUiState(): TaskSessionDetailUiState {
    val canReply = replyContext != null
    val pendingQuestionItems = pendingQuestions
        .map(TaskQuestionCapsule::toUiState)
        .toImmutableList()
    val requiresStructuredReply = pendingQuestionItems.size > 1
    val quickAnswerChoices = if (pendingQuestions.size == 1) {
        pendingQuestions.single().choices.toImmutableList()
    } else {
        persistentListOf()
    }
    val replySupportingText = when {
        requiresStructuredReply -> {
            "Use one line per question in the form question_id: value. " +
                "The draft is prefilled with a copy-ready template, and you can attach files if needed."
        }

        quickAnswerChoices.isNotEmpty() -> "Send a plain-text reply, attach files, or use a quick TaskMail action."
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
        workdir = workdir,
        lastSummary = lastSummary,
        pendingQuestions = pendingQuestionItems,
        quickAnswerChoices = quickAnswerChoices,
        requiresStructuredReply = requiresStructuredReply,
        structuredReplyTemplate = structuredReplyTemplate,
        replyLabel = if (requiresStructuredReply) "Answers" else "Reply to this task",
        replySupportingText = replySupportingText,
        replyContext = replyContext,
        canReply = canReply,
        canQueryStatus = canReply,
        replyUnavailableReason = if (canReply) null else "Reply unavailable for this session.",
        timeline = timeline.map(TaskTimelineItem::toUiState).toImmutableList(),
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

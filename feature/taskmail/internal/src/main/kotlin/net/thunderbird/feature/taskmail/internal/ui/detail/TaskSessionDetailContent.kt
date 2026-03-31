package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonOutlined
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.card.CardElevated
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.atom.textfield.TextFieldOutlined
import app.k9mail.core.ui.compose.designsystem.molecule.ErrorView
import app.k9mail.core.ui.compose.designsystem.molecule.PullToRefreshBox
import app.k9mail.core.ui.compose.designsystem.organism.SubtitleTopAppBarWithBackButton
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.ErrorBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.WarningBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.detail.component.CurrentRoundInputCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.HistoryContextSheet
import net.thunderbird.feature.taskmail.internal.ui.detail.component.PendingQuestionCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.ResultSummaryCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskProcessSection
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskReplyComposer
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskReplyComposerState
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskSecondaryActionsCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskSessionMetaCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskSessionStatusCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskStructuredReplyInputUi

@Composable
internal fun TaskSessionDetailContent(
    state: TaskSessionDetailContract.State,
    onEvent: (TaskSessionDetailContract.Event) -> Unit,
    onOpenHistory: () -> Unit = {
        onEvent(TaskSessionDetailContract.Event.HistoryClicked)
    },
    onPickAttachments: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTimelineAttachment: (String) -> Unit = {
        onEvent(TaskSessionDetailContract.Event.OpenTimelineAttachmentClicked(it))
    },
    onSaveTimelineAttachment: (String) -> Unit = {
        onEvent(TaskSessionDetailContract.Event.SaveTimelineAttachmentClicked(it))
    },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SubtitleTopAppBarWithBackButton(
                title = state.detail?.sessionName ?: "Task session",
                subtitle = state.detail?.topBarSubtitle() ?: "TaskMail",
                onBackClick = { onEvent(TaskSessionDetailContract.Event.BackClicked) },
                actions = {
                    ButtonText(
                        text = "History",
                        onClick = onOpenHistory,
                        modifier = Modifier.testTag("TaskSessionDetailHistoryButton"),
                        enabled = state.detail?.timeline?.isNotEmpty() == true,
                    )
                },
            )
        },
    ) { innerPadding ->
        TaskSessionDetailBody(
            state = state,
            onEvent = onEvent,
            onPickAttachments = onPickAttachments,
            onOpenTimelineAttachment = onOpenTimelineAttachment,
            onSaveTimelineAttachment = onSaveTimelineAttachment,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun TaskSessionDetailBody(
    state: TaskSessionDetailContract.State,
    onEvent: (TaskSessionDetailContract.Event) -> Unit,
    onPickAttachments: () -> Unit,
    onOpenTimelineAttachment: (String) -> Unit,
    onSaveTimelineAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> {
            DetailCenteredMessage(
                title = "Loading session",
                message = "Building the timeline and current task state.",
                modifier = modifier,
            )
        }

        state.error != null -> {
            ErrorView(
                title = "Unable to load session",
                message = state.error,
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            )
        }

        state.detail != null -> {
            TaskSessionDetailLoadedContent(
                state = state,
                onEvent = onEvent,
                onPickAttachments = onPickAttachments,
                onOpenTimelineAttachment = onOpenTimelineAttachment,
                onSaveTimelineAttachment = onSaveTimelineAttachment,
                modifier = modifier,
            )
        }

        else -> {
            DetailCenteredMessage(
                title = "No detail available",
                message = "The selected session has no timeline data yet.",
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TaskSessionDetailLoadedContent(
    state: TaskSessionDetailContract.State,
    onEvent: (TaskSessionDetailContract.Event) -> Unit,
    onPickAttachments: () -> Unit,
    onOpenTimelineAttachment: (String) -> Unit,
    onSaveTimelineAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail ?: return
    val replyComposerState = state.toReplyComposerState(detail)
    var isProcessExpanded by rememberSaveable(
        detail.sessionId,
        detail.pageMode,
        detail.processSection?.defaultExpanded,
    ) {
        mutableStateOf(detail.processSection?.defaultExpanded == true)
    }

    PullToRefreshBox(
        modifier = modifier.fillMaxSize(),
        isRefreshing = state.isRefreshing,
        onRefresh = { onEvent(TaskSessionDetailContract.Event.RefreshClicked) },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("TaskSessionDetailList"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            refreshErrorItem(refreshError = state.refreshError)
            item {
                TaskSessionStatusCard(
                    status = detail.status,
                    headline = detail.statusHeadline(),
                    actorHint = detail.statusActorHint(),
                    submissionMessage = detail.pendingSubmission?.message,
                    supportingText = detail.statusSupportingText(),
                    timingRows = detail.statusTimingRows(),
                )
            }

            when (detail.pageMode) {
                TaskSessionPageMode.ActiveRun -> {
                    item {
                        CurrentRoundInputCard(
                            status = detail.status,
                            body = detail.currentInputBodyText(state),
                            permissionLabel = state.selectedReplyPermission.displayLabel(),
                            attachments = state.replyAttachments,
                        )
                    }
                    item {
                        TaskSecondaryActionsCard(
                            canQueryStatus = replyComposerState.canQueryStatus,
                            showGuide = true,
                            showResume = false,
                            showStopRunning = true,
                            showDeactivate = false,
                            isActionEnabled = !state.isSending,
                            onStatusQuery = {
                                onEvent(TaskSessionDetailContract.Event.StatusQueryClicked)
                            },
                            onGuide = { onEvent(TaskSessionDetailContract.Event.GuideClicked) },
                            onResume = {},
                            onStopRunning = {
                                onEvent(TaskSessionDetailContract.Event.StopRunningClicked)
                            },
                            onDeactivate = {},
                        )
                    }
                    if (state.isGuideComposerVisible) {
                        item {
                            GuideComposerCard(
                                state = replyComposerState,
                                onDraftChanged = {
                                    onEvent(TaskSessionDetailContract.Event.DraftChanged(it))
                                },
                                onSendReply = {
                                    onEvent(TaskSessionDetailContract.Event.SendReplyClicked)
                                },
                                onDismiss = {
                                    onEvent(TaskSessionDetailContract.Event.GuideDismissed)
                                },
                            )
                        }
                    }
                    if (detail.resultSummary != null && detail.resultBody != null) {
                        resultSummaryItem(
                            detail = detail,
                            title = "Previous result",
                            supportingText = "Latest stable result before the current round started.",
                            resultBody = detail.resultBody,
                            onOpenTimelineAttachment = onOpenTimelineAttachment,
                            onSaveTimelineAttachment = onSaveTimelineAttachment,
                        )
                    }
                }

                TaskSessionPageMode.AwaitingReply,
                TaskSessionPageMode.Terminal,
                -> {
                    resultSummaryItem(
                        detail = detail,
                        title = "Latest result",
                        supportingText = "Read the latest stable output before deciding what to do next.",
                        resultBody = detail.resultBody,
                        onOpenTimelineAttachment = onOpenTimelineAttachment,
                        onSaveTimelineAttachment = onSaveTimelineAttachment,
                    )
                    if (detail.pendingQuestions.isNotEmpty()) {
                        item {
                            PendingQuestionCard(questions = detail.pendingQuestions)
                        }
                    }
                    if (shouldShowReplyComposer(detail = detail, state = state)) {
                        replyItem(
                            state = replyComposerState,
                            detail = detail,
                            onEvent = onEvent,
                            onPickAttachments = onPickAttachments,
                        )
                    }
                    item {
                        TaskSecondaryActionsCard(
                            canQueryStatus = replyComposerState.canQueryStatus,
                            showGuide = false,
                            showResume = detail.requiresResumeBeforeReply,
                            showStopRunning = false,
                            showDeactivate = detail.pageMode != TaskSessionPageMode.ActiveRun,
                            isActionEnabled = !state.isSending,
                            onStatusQuery = {
                                onEvent(TaskSessionDetailContract.Event.StatusQueryClicked)
                            },
                            onGuide = {},
                            onResume = {
                                onEvent(TaskSessionDetailContract.Event.ResumeClicked)
                            },
                            onStopRunning = {},
                            onDeactivate = {
                                onEvent(TaskSessionDetailContract.Event.DeactivateClicked)
                            },
                        )
                    }
                }
            }

            detail.processSection?.let { processSection ->
                processSectionItem(
                    section = processSection,
                    isExpanded = isProcessExpanded,
                    onToggle = { isProcessExpanded = !isProcessExpanded },
                    onOpenTimelineAttachment = onOpenTimelineAttachment,
                    onSaveTimelineAttachment = onSaveTimelineAttachment,
                )
            }

            item {
                TaskSessionMetaCard(
                    headline = detail.sessionMetaHeadline(),
                    lines = detail.metadataLines(),
                )
            }
        }
    }

    if (state.isHistoryVisible) {
        HistoryContextSheet(
            historyPreview = detail.historyPreview,
            onDismissRequest = {
                onEvent(TaskSessionDetailContract.Event.HistoryDismissed)
            },
        )
    }
}

private fun LazyListScope.refreshErrorItem(refreshError: String?) {
    refreshError ?: return

    item {
        WarningBannerInlineNotificationCard(
            title = "TaskMail update failed",
            supportingText = refreshError,
            actions = {},
        )
    }
}

private fun LazyListScope.replyItem(
    state: TaskReplyComposerState,
    detail: TaskSessionDetailUiState,
    onEvent: (TaskSessionDetailContract.Event) -> Unit,
    onPickAttachments: () -> Unit,
) {
    item {
        TaskReplyComposer(
            state = state,
            onDraftChanged = {
                onEvent(TaskSessionDetailContract.Event.DraftChanged(it))
            },
            onStructuredAnswerChanged = { questionId, answerText ->
                val nextAnswers = state.structuredQuestions
                    .associate { structuredQuestion ->
                        structuredQuestion.questionId to if (structuredQuestion.questionId == questionId) {
                            answerText
                        } else {
                            structuredQuestion.answerText
                        }
                    }
                onEvent(
                    TaskSessionDetailContract.Event.DraftChanged(
                        buildStructuredReplyDraft(
                            questionIdsInOrder = detail.pendingQuestions.map(TaskPendingQuestionUi::questionId),
                            answersByQuestionId = nextAnswers,
                        ),
                    ),
                )
            },
            onPickAttachments = onPickAttachments,
            onSendReply = {
                onEvent(TaskSessionDetailContract.Event.SendReplyClicked)
            },
            onResume = {
                onEvent(TaskSessionDetailContract.Event.ResumeClicked)
            },
            onSendChoice = {
                onEvent(TaskSessionDetailContract.Event.SendChoiceClicked(it))
            },
            onDismissSendError = {
                onEvent(TaskSessionDetailContract.Event.DismissSendError)
            },
            onPermissionSelected = {
                onEvent(TaskSessionDetailContract.Event.ReplyPermissionChanged(it))
            },
            onRemoveAttachment = {
                onEvent(TaskSessionDetailContract.Event.RemoveAttachmentClicked(it))
            },
        )
    }
}

@Composable
private fun GuideComposerCard(
    state: TaskReplyComposerState,
    onDraftChanged: (String) -> Unit,
    onSendReply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardElevated(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = "Guide this task",
                supportingText = if (state.canReply) {
                    state.replySupportingText
                } else {
                    "Guide is currently unavailable for this session."
                },
            )

            state.sendError?.let { error ->
                ErrorBannerInlineNotificationCard(
                    title = "TaskMail reply failed",
                    supportingText = error,
                    actions = {
                        ButtonText(
                            text = "Dismiss",
                            onClick = onDismiss,
                        )
                    },
                )
            }

            if (!state.canReply) {
                WarningBannerInlineNotificationCard(
                    title = "Guide unavailable",
                    supportingText = state.replyUnavailableReason ?: "Guide unavailable for this session.",
                    actions = {},
                )
            } else {
                TextFieldOutlined(
                    value = state.draftText,
                    onValueChange = onDraftChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("TaskGuideComposerInput"),
                    label = state.replyLabel,
                    isEnabled = !state.isSending,
                    isSingleLine = false,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ButtonFilled(
                    text = state.sendButtonText,
                    onClick = onSendReply,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("TaskGuideComposerSendButton"),
                    enabled = state.canReply && state.canSendReply && !state.isSending,
                )
                ButtonOutlined(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("TaskGuideComposerCancelButton"),
                    enabled = !state.isSending,
                )
            }
        }
    }
}

private fun LazyListScope.processSectionItem(
    section: TaskProcessSectionUi,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onOpenTimelineAttachment: (String) -> Unit,
    onSaveTimelineAttachment: (String) -> Unit,
) {
    item {
        TaskProcessSection(
            section = section,
            isExpanded = isExpanded,
            onToggle = onToggle,
            onOpenAttachment = onOpenTimelineAttachment,
            onSaveAttachment = onSaveTimelineAttachment,
        )
    }
}

private fun LazyListScope.resultSummaryItem(
    detail: TaskSessionDetailUiState,
    title: String,
    supportingText: String,
    resultBody: TaskTimelineItemUi? = null,
    onOpenTimelineAttachment: (String) -> Unit,
    onSaveTimelineAttachment: (String) -> Unit,
) {
    detail.resultSummary?.let { result ->
        item {
            ResultSummaryCard(
                result = result,
                title = title,
                supportingText = supportingText,
                speakerLabel = detail.backend,
                resultBody = resultBody,
                supplementalAttachments = detail.artifacts,
                timestampText = resultBody?.timestampText(prefix = "Updated"),
                onOpenAttachment = onOpenTimelineAttachment,
                onSaveAttachment = onSaveTimelineAttachment,
            )
        }
    }
}

private fun TaskSessionDetailContract.State.toReplyComposerState(
    detail: TaskSessionDetailUiState,
): TaskReplyComposerState {
    val structuredAnswers = if (detail.requiresStructuredReply) {
        extractStructuredReplyDraftValues(
            draftText = draftText,
            pendingQuestions = detail.pendingQuestions,
        )
    } else {
        emptyMap()
    }

    return TaskReplyComposerState(
        draftText = draftText,
        isSending = isSending,
        sendError = sendError,
        canReply = detail.canReply,
        canSendReply = detail.canSendReply(
            draftText = draftText,
            attachmentCount = replyAttachments.size,
        ),
        canQueryStatus = detail.canQueryStatus && replyAttachments.isEmpty(),
        replyUnavailableReason = detail.replyUnavailableReason,
        quickAnswerChoices = detail.quickAnswerChoices,
        replyAttachments = replyAttachments,
        requiresStructuredReply = detail.requiresStructuredReply,
        structuredQuestions = detail.pendingQuestions
            .map { question ->
                TaskStructuredReplyInputUi(
                    questionId = question.questionId,
                    questionText = question.questionText,
                    answerText = structuredAnswers[question.questionId].orEmpty(),
                    choices = question.choices,
                    isRequired = question.isRequired,
                )
            }
            .toImmutableList(),
        requiresResumeBeforeReply = detail.requiresResumeBeforeReply,
        canResume = detail.requiresResumeBeforeReply,
        selectedPermission = selectedReplyPermission,
        isQuestionReply = detail.pendingQuestions.isNotEmpty(),
        replyLabel = detail.replyLabel,
        replySupportingText = detail.replySupportingText,
    )
}

private fun shouldShowReplyComposer(
    detail: TaskSessionDetailUiState,
    state: TaskSessionDetailContract.State,
): Boolean {
    return detail.canReply ||
        detail.requiresResumeBeforeReply ||
        detail.pendingQuestions.isNotEmpty() ||
        state.replyAttachments.isNotEmpty() ||
        state.sendError != null
}

private fun TaskSessionDetailUiState.topBarSubtitle(): String {
    return workdir
        ?.takeIf(String::isNotBlank)
        ?: backend
}

private fun TaskSessionDetailUiState.currentInputBodyText(
    state: TaskSessionDetailContract.State,
): String {
    return if (state.isSending && state.isGuideComposerVisible && state.draftText.isNotBlank()) {
        state.draftText
    } else {
        recentContext?.latestUserMessage
            ?.takeIf(String::isNotBlank)
            ?: "Latest outgoing input is not available yet."
    }
}

private fun TaskSessionDetailUiState.statusHeadline(): String {
    return when (pageMode) {
        TaskSessionPageMode.ActiveRun -> when {
            status.equals("Queued", ignoreCase = true) -> "Task is queued"
            else -> "Task is running"
        }

        TaskSessionPageMode.AwaitingReply -> when {
            status.equals("Paused", ignoreCase = true) -> "Session is paused"
            else -> "System is waiting for your reply"
        }

        TaskSessionPageMode.Terminal -> when {
            status.equals("Done", ignoreCase = true) -> "Latest run completed"
            status.equals("Failed", ignoreCase = true) -> "Latest run failed"
            status.equals("Killed", ignoreCase = true) -> "Run stopped"
            else -> "Latest session state"
        }
    }
}

private fun TaskSessionDetailUiState.statusActorHint(): String {
    return when (pageMode) {
        TaskSessionPageMode.ActiveRun -> "The PC is still working on the current round."
        TaskSessionPageMode.AwaitingReply -> {
            if (requiresResumeBeforeReply) {
                "Resume the session first, then continue with your reply."
            } else {
                "It is your turn to reply."
            }
        }

        TaskSessionPageMode.Terminal -> "Review the latest result and decide whether to continue."
    }
}

private fun TaskSessionDetailUiState.statusSupportingText(): String? {
    return when (pageMode) {
        TaskSessionPageMode.ActiveRun -> null

        TaskSessionPageMode.AwaitingReply -> pendingQuestions
            .takeIf { it.isNotEmpty() }
            ?.let { questions ->
                if (questions.size == 1) {
                    "Review the pending question below before replying."
                } else {
                    "${questions.size} pending questions are ready below."
                }
            }
            ?: recentContext?.waitingForUserText?.takeIf(String::isNotBlank)
            ?: lastSummary?.takeIf(String::isNotBlank)

        TaskSessionPageMode.Terminal -> lastSummary?.takeIf(String::isNotBlank)
            ?: recentContext?.latestAssistantMessage?.takeIf(String::isNotBlank)
    }
}

private fun TaskSessionDetailUiState.statusTimingRows(): List<Pair<String, String>> {
    return buildList {
        pendingSubmission
            ?.submittedAt
            ?.takeIf { it > 0L }
            ?.let { submittedAt ->
                add("Submitted" to submittedAt.timestampText())
            }
        lastProgressAt
            ?.toTimestampLabelOrNull()
            ?.let { add("Last progress" to it) }
        lastActiveAt
            ?.toTimestampLabelOrNull()
            ?.takeIf { lastActive ->
                none { (label, value) -> label == "Last progress" && value == lastActive }
            }
            ?.let { add("Last active" to it) }
    }
}

private fun TaskSessionDetailUiState.sessionMetaHeadline(): String {
    return workdir
        ?.takeIf(String::isNotBlank)
        ?: repoPath.substringAfterLast('/').substringAfterLast('\\').ifBlank { repoPath }
}

private fun TaskSessionDetailUiState.metadataLines(): List<Pair<String, String>> {
    return buildList {
        add("Repository" to repoPath)
        add("Backend" to backend)
        workdir?.takeIf(String::isNotBlank)?.let { add("Workdir" to it) }
        workspaceId?.takeIf(String::isNotBlank)?.let { add("Workspace ID" to it) }
        sessionId?.takeIf(String::isNotBlank)?.let { add("Session ID" to it) }
    }
}

private fun String.toTimestampLabelOrNull(): String? {
    return toEpochMillis()
        .takeIf { it > 0L }
        ?.timestampText()
}

private fun String.toEpochMillis(): Long {
    return runCatching {
        OffsetDateTime.parse(this).toInstant().toEpochMilli()
    }.recoverCatching {
        LocalDateTime.parse(this, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}

private fun Long.timestampText(prefix: String? = null): String {
    if (this <= 0L) return prefix?.let { "$it · Unknown time" } ?: "Unknown time"

    val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(this))
    return prefix?.let { "$it · $formatted" } ?: formatted
}

private fun TaskTimelineItemUi.timestampText(prefix: String? = null): String {
    return timestamp.timestampText(prefix = prefix)
}

private fun TaskMailNewTaskPermission.displayLabel(): String {
    return if (name.equals("Highest", ignoreCase = true)) {
        "Highest"
    } else {
        "Default"
    }
}

@Composable
private fun DetailCenteredMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextHeadlineSmall(text = title)
        TextBodyLarge(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

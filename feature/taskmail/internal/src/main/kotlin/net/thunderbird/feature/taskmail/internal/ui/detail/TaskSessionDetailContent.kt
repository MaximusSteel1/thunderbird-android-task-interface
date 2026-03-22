package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.molecule.ErrorView
import app.k9mail.core.ui.compose.designsystem.molecule.PullToRefreshBox
import app.k9mail.core.ui.compose.designsystem.organism.SubtitleTopAppBarWithBackButton
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.WarningBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.detail.component.PendingQuestionCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.SessionHeader
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskReplyComposer
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskReplyComposerState
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskStateCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TimelineMessageCard

@Composable
internal fun TaskSessionDetailContent(
    state: TaskSessionDetailContract.State,
    onEvent: (TaskSessionDetailContract.Event) -> Unit,
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
                subtitle = state.detail?.backend ?: "TaskMail",
                onBackClick = { onEvent(TaskSessionDetailContract.Event.BackClicked) },
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
            overviewItems(detail = detail)
            refreshErrorItem(refreshError = state.refreshError)
            latestDirectSessionActionItem(state = state)
            replyItem(
                state = replyComposerState,
                onEvent = onEvent,
                onPickAttachments = onPickAttachments,
            )
            timelineItems(
                timeline = detail.timeline,
                onOpenTimelineAttachment = onOpenTimelineAttachment,
                onSaveTimelineAttachment = onSaveTimelineAttachment,
            )
        }
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

private fun LazyListScope.overviewItems(detail: TaskSessionDetailUiState) {
    item {
        SessionHeader(
            sessionName = detail.sessionName,
            status = detail.status,
            backend = detail.backend,
        )
    }
    item {
        TaskStateCard(
            repoPath = detail.repoPath,
            workdir = detail.workdir,
            lastSummary = detail.lastSummary,
        )
    }

    if (detail.pendingQuestions.isNotEmpty()) {
        item {
            PendingQuestionCard(questions = detail.pendingQuestions)
        }
    }
}

private fun LazyListScope.replyItem(
    state: TaskReplyComposerState,
    onEvent: (TaskSessionDetailContract.Event) -> Unit,
    onPickAttachments: () -> Unit,
) {
    item {
        TaskReplyComposer(
            state = state,
            onDraftChanged = {
                onEvent(TaskSessionDetailContract.Event.DraftChanged(it))
            },
            onPickAttachments = onPickAttachments,
            onSendReply = {
                onEvent(TaskSessionDetailContract.Event.SendReplyClicked)
            },
            onStatusQuery = {
                onEvent(TaskSessionDetailContract.Event.StatusQueryClicked)
            },
            onSendChoice = {
                onEvent(TaskSessionDetailContract.Event.SendChoiceClicked(it))
            },
            onRemoveAttachment = {
                onEvent(TaskSessionDetailContract.Event.RemoveAttachmentClicked(it))
            },
            onDismissSendError = {
                onEvent(TaskSessionDetailContract.Event.DismissSendError)
            },
        )
    }
}

private fun LazyListScope.timelineItems(
    timeline: kotlinx.collections.immutable.ImmutableList<TaskTimelineItemUi>,
    onOpenTimelineAttachment: (String) -> Unit,
    onSaveTimelineAttachment: (String) -> Unit,
) {
    item {
        TaskSectionHeader(
            title = "Timeline",
            supportingText = "Newest messages are shown first.",
        )
    }

    items(timeline, key = TaskTimelineItemUi::id) { item ->
        TimelineMessageCard(
            item = item,
            onOpenAttachment = onOpenTimelineAttachment,
            onSaveAttachment = onSaveTimelineAttachment,
        )
    }
}

private fun TaskSessionDetailContract.State.toReplyComposerState(
    detail: TaskSessionDetailUiState,
): TaskReplyComposerState {
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
        requiresResumeBeforeReply = detail.requiresResumeBeforeReply,
        isQuestionReply = detail.pendingQuestions.isNotEmpty(),
        replyLabel = detail.replyLabel,
        replySupportingText = detail.replySupportingText,
    )
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

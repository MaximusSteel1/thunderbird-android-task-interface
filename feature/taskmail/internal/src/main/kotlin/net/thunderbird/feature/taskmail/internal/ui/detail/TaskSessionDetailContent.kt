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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilledTonal
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonOutlined
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonSegmentedSingleChoice
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.card.CardElevated
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.textfield.TextFieldOutlined
import app.k9mail.core.ui.compose.designsystem.molecule.ErrorView
import app.k9mail.core.ui.compose.designsystem.molecule.PullToRefreshBox
import app.k9mail.core.ui.compose.designsystem.organism.SubtitleTopAppBarWithBackButton
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.ErrorBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.WarningBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.detail.component.ArtifactSection
import net.thunderbird.feature.taskmail.internal.ui.detail.component.CurrentRoundInputCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.HistoryContextSheet
import net.thunderbird.feature.taskmail.internal.ui.detail.component.PendingQuestionCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.ProcessFoldCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.ResultSummaryCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.SessionControlRail
import net.thunderbird.feature.taskmail.internal.ui.detail.component.SessionEnvironmentCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.SessionMetadataCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskReplyComposer
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskReplyComposerState
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TimelineMessageCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TimelineMessageCardStyle

private val replyPermissionOptions = TaskMailNewTaskPermission.entries.toImmutableList()
private val replyPermissionLabel: (TaskMailNewTaskPermission) -> String = { permission ->
    when (permission) {
        TaskMailNewTaskPermission.Default -> "Default"
        TaskMailNewTaskPermission.Highest -> "Highest"
    }
}

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
    val isCurrentInputLedMode = detail.isCurrentInputLedMode()
    var isProcessExpanded by rememberSaveable(detail.sessionId, detail.status) {
        mutableStateOf(false)
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
                SessionEnvironmentCard(
                    headline = detail.environmentHeadline(),
                    workspaceLabel = detail.workspaceLabel(),
                )
            }

            if (isCurrentInputLedMode) {
                item {
                    CurrentRoundInputCard(
                        status = detail.status,
                        body = detail.currentInputBodyText(state),
                        permissionLabel = replyPermissionLabel(state.selectedReplyPermission),
                        attachments = state.replyAttachments,
                    )
                }
            } else {
                resultSummaryItem(
                    detail = detail,
                    title = "Latest result",
                    supportingText = "Read the last stable output before deciding how to continue.",
                )
            }

            processItems(
                timeline = detail.timeline,
                isExpanded = isProcessExpanded,
                onToggle = { isProcessExpanded = !isProcessExpanded },
                onOpenTimelineAttachment = onOpenTimelineAttachment,
                onSaveTimelineAttachment = onSaveTimelineAttachment,
                processSectionTitle = processSectionTitle(isCurrentInputLedMode = isCurrentInputLedMode),
                processSectionSupportingText = processSectionSupportingText(
                    isCurrentInputLedMode = isCurrentInputLedMode,
                ),
            )

            if (isCurrentInputLedMode) {
                item {
                    SessionControlRail(
                        canQueryStatus = replyComposerState.canQueryStatus,
                        isActionEnabled = !state.isSending,
                        onStatusQuery = {
                            onEvent(TaskSessionDetailContract.Event.StatusQueryClicked)
                        },
                        onGuide = { onEvent(TaskSessionDetailContract.Event.GuideClicked) },
                        onStopRunning = {
                            onEvent(TaskSessionDetailContract.Event.StopRunningClicked)
                        },
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
                resultSummaryItem(
                    detail = detail,
                    title = "Previous result",
                    supportingText = "This was the last stable output before the current round started.",
                )
                artifactItem(detail = detail)
            } else {
                artifactItem(detail = detail)
                if (detail.pendingQuestions.isNotEmpty()) {
                    item {
                        PendingQuestionCard(questions = detail.pendingQuestions)
                    }
                }
                item {
                    ReplyPermissionCard(
                        selectedPermission = state.selectedReplyPermission,
                        onPermissionSelected = {
                            onEvent(TaskSessionDetailContract.Event.ReplyPermissionChanged(it))
                        },
                        enabled = !state.isSending && replyComposerState.canReply,
                    )
                }
                replyItem(
                    state = replyComposerState,
                    onEvent = onEvent,
                    onPickAttachments = onPickAttachments,
                )
                item {
                    DeactivateCard(
                        enabled = !state.isSending,
                        onDeactivate = {
                            onEvent(TaskSessionDetailContract.Event.DeactivateClicked)
                        },
                    )
                }
            }

            item {
                SessionMetadataCard(
                    lines = detail.metadataLines(),
                    canQueryStatus = replyComposerState.canQueryStatus && !state.isSending,
                    onStatusQuery = {
                        onEvent(TaskSessionDetailContract.Event.StatusQueryClicked)
                    },
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

private fun LazyListScope.processItems(
    timeline: kotlinx.collections.immutable.ImmutableList<TaskTimelineItemUi>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onOpenTimelineAttachment: (String) -> Unit,
    onSaveTimelineAttachment: (String) -> Unit,
    processSectionTitle: String,
    processSectionSupportingText: String,
) {
    item {
        ProcessFoldCard(
            timelineCount = timeline.size,
            isExpanded = isExpanded,
            onToggle = onToggle,
            title = processSectionTitle,
            supportingText = processSectionSupportingText,
        )
    }

    if (!isExpanded) return

    items(timeline, key = TaskTimelineItemUi::id) { item ->
        TimelineMessageCard(
            item = item,
            style = TimelineMessageCardStyle.ProcessRecord,
            onOpenAttachment = onOpenTimelineAttachment,
            onSaveAttachment = onSaveTimelineAttachment,
        )
    }
}

private fun LazyListScope.resultSummaryItem(
    detail: TaskSessionDetailUiState,
    title: String,
    supportingText: String,
) {
    detail.resultSummary?.let { result ->
        item {
            ResultSummaryCard(
                result = result,
                title = title,
                supportingText = supportingText,
                speakerLabel = detail.backend,
            )
        }
    }
}

private fun LazyListScope.artifactItem(detail: TaskSessionDetailUiState) {
    if (detail.artifacts.isEmpty()) return

    item {
        ArtifactSection(artifacts = detail.artifacts)
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
private fun ReplyPermissionCard(
    selectedPermission: TaskMailNewTaskPermission,
    onPermissionSelected: (TaskMailNewTaskPermission) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextLabelMedium(
                text = "Permission",
                color = MainTheme.colors.onSurfaceVariant,
            )
            ButtonSegmentedSingleChoice(
                onClick = {
                    if (enabled) {
                        onPermissionSelected(it)
                    }
                },
                options = replyPermissionOptions,
                optionTitle = replyPermissionLabel,
                selectedOption = selectedPermission,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DeactivateCard(
    enabled: Boolean,
    onDeactivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextLabelMedium(
                text = "Session controls",
                color = MainTheme.colors.onSurfaceVariant,
            )
            ButtonFilledTonal(
                text = "Deactivate",
                onClick = onDeactivate,
                enabled = enabled,
            )
        }
    }
}

private fun TaskSessionDetailUiState.isCurrentInputLedMode(): Boolean {
    return status.equals("Queued", ignoreCase = true) ||
        status.equals("Running", ignoreCase = true)
}

private fun TaskSessionDetailUiState.topBarSubtitle(): String {
    return workdir
        ?.takeIf(String::isNotBlank)
        ?: backend
}

private fun TaskSessionDetailUiState.environmentHeadline(): String {
    val workdirLabel = workdir
        ?.takeIf(String::isNotBlank)
        ?: repoPath.substringAfterLast('/').substringAfterLast('\\')
            .ifBlank { repoPath }
    return "PC pending · $workdirLabel"
}

private fun TaskSessionDetailUiState.workspaceLabel(): String {
    return workspaceId
        ?.takeIf(String::isNotBlank)
        ?.let { "Workspace · $it" }
        ?: "Workspace · missing binding"
}

private fun processSectionTitle(isCurrentInputLedMode: Boolean): String {
    return if (isCurrentInputLedMode) {
        "Run activity"
    } else {
        "Run records"
    }
}

private fun processSectionSupportingText(isCurrentInputLedMode: Boolean): String {
    return if (isCurrentInputLedMode) {
        "Preserved activity records explain what the current run has emitted so far."
    } else {
        "Open the preserved records behind the latest stable result when you need detail."
    }
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

private fun TaskSessionDetailUiState.metadataLines(): List<Pair<String, String>> {
    return buildList {
        add("Status" to status)
        add("Backend" to backend)
        add("Repository" to repoPath)
        workdir?.takeIf(String::isNotBlank)?.let { add("Workdir" to it) }
        workspaceId?.takeIf(String::isNotBlank)?.let { add("Workspace ID" to it) }
        sessionId?.takeIf(String::isNotBlank)?.let { add("Session ID" to it) }
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

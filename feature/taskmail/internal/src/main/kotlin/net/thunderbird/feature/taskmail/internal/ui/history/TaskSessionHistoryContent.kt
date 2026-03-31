package net.thunderbird.feature.taskmail.internal.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.DividerHorizontal
import app.k9mail.core.ui.compose.designsystem.atom.Surface
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import app.k9mail.core.ui.compose.designsystem.molecule.ErrorView
import app.k9mail.core.ui.compose.designsystem.molecule.PullToRefreshBox
import app.k9mail.core.ui.compose.designsystem.organism.SubtitleTopAppBarWithBackButton
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.WarningBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskCodeLocatorText
import net.thunderbird.feature.taskmail.internal.ui.component.TaskCodeLocatorTextStyle
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.component.TaskStatusBadge
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailUiState
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineAttachmentUi
import net.thunderbird.feature.taskmail.internal.ui.detail.component.SessionEnvironmentCard
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TaskProcessSection
import net.thunderbird.feature.taskmail.internal.ui.detail.component.TimelineAttachments

@Composable
internal fun TaskSessionHistoryContent(
    state: TaskSessionDetailContract.State,
    onBack: () -> Unit,
    onEvent: (TaskSessionDetailContract.Event) -> Unit,
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
                title = "History review",
                subtitle = state.detail?.sessionName ?: "TaskMail",
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        TaskSessionHistoryBody(
            state = state,
            onEvent = onEvent,
            onOpenTimelineAttachment = onOpenTimelineAttachment,
            onSaveTimelineAttachment = onSaveTimelineAttachment,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun TaskSessionHistoryBody(
    state: TaskSessionDetailContract.State,
    onEvent: (TaskSessionDetailContract.Event) -> Unit,
    onOpenTimelineAttachment: (String) -> Unit,
    onSaveTimelineAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> {
            HistoryCenteredMessage(
                title = "Loading history",
                message = "Building round-by-round context from the session timeline.",
                modifier = modifier,
            )
        }

        state.error != null -> {
            ErrorView(
                title = "Unable to load history",
                message = state.error,
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            )
        }

        state.detail != null -> {
            TaskSessionHistoryLoadedContent(
                state = state,
                onEvent = onEvent,
                onOpenTimelineAttachment = onOpenTimelineAttachment,
                onSaveTimelineAttachment = onSaveTimelineAttachment,
                modifier = modifier,
            )
        }

        else -> {
            HistoryCenteredMessage(
                title = "No history available",
                message = "The selected session has no timeline data yet.",
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun TaskSessionHistoryLoadedContent(
    state: TaskSessionDetailContract.State,
    onEvent: (TaskSessionDetailContract.Event) -> Unit,
    onOpenTimelineAttachment: (String) -> Unit,
    onSaveTimelineAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail ?: return
    val localRounds = remember(detail.timeline, detail.backend) {
        TaskSessionHistoryRoundProjector.project(detail)
    }
    val rounds = remember(state.historySnapshotRounds, localRounds, detail.backend) {
        TaskSessionHistorySnapshotRoundMapper.merge(
            snapshotRounds = state.historySnapshotRounds,
            localRounds = localRounds,
            fallbackSpeakerLabel = detail.backend,
        )
    }
    var expandedRoundIds by rememberSaveable(detail.sessionId) {
        mutableStateOf(listOfNotNull(rounds.firstOrNull()?.id))
    }
    var expandedProcessRoundIds by rememberSaveable(
        detail.sessionId,
        rounds.map { round -> "${round.id}:${round.processSection?.defaultExpanded}" },
    ) {
        mutableStateOf(
            rounds
                .filter { round -> round.processSection?.defaultExpanded == true }
                .map(TaskSessionHistoryRoundUi::id),
        )
    }

    PullToRefreshBox(
        modifier = modifier.fillMaxSize(),
        isRefreshing = state.isRefreshing,
        onRefresh = { onEvent(TaskSessionDetailContract.Event.RefreshClicked) },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("TaskSessionHistoryList"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.refreshError?.let { refreshError ->
                item {
                    WarningBannerInlineNotificationCard(
                        title = "TaskMail update failed",
                        supportingText = refreshError,
                        actions = {},
                    )
                }
            }

            state.historySnapshotError?.let { historySnapshotError ->
                item {
                    WarningBannerInlineNotificationCard(
                        title = "Round snapshot unavailable",
                        supportingText = historySnapshotError,
                        actions = {},
                    )
                }
            }

            item {
                SessionEnvironmentCard(
                    headline = detail.historyEnvironmentHeadline(),
                    workspaceLabel = detail.historyWorkspaceLabel(),
                )
            }

            item {
                TaskSectionHeader(
                    title = "Rounds",
                    supportingText = "Tap a round to inspect its input, records, result, and attachments. Multiple rounds can stay open for comparison.",
                )
            }

            if (rounds.isEmpty()) {
                item {
                    CardOutlined(modifier = Modifier.fillMaxWidth()) {
                        TextBodyMedium(
                            text = "No rounds could be projected from this session yet.",
                            modifier = Modifier.padding(16.dp),
                            color = MainTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(rounds, key = TaskSessionHistoryRoundUi::id) { round ->
                    val isExpanded = round.id in expandedRoundIds
                    val isProcessExpanded = round.id in expandedProcessRoundIds

                    HistoryReviewRoundCard(
                        round = round,
                        isExpanded = isExpanded,
                        isProcessExpanded = isProcessExpanded,
                        onToggleExpanded = {
                            expandedRoundIds = expandedRoundIds.toggle(round.id)
                        },
                        onToggleProcess = {
                            expandedProcessRoundIds = expandedProcessRoundIds.toggle(round.id)
                        },
                        onOpenAttachment = onOpenTimelineAttachment,
                        onSaveAttachment = onSaveTimelineAttachment,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryReviewRoundCard(
    round: TaskSessionHistoryRoundUi,
    isExpanded: Boolean,
    isProcessExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleProcess: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    onSaveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CardOutlined(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .testTag("TaskSessionHistoryRoundCard:${round.id}"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextTitleMedium(text = "#${round.roundNumber}")
                        TextLabelMedium(
                            text = round.timestampLabel,
                            color = MainTheme.colors.onSurfaceVariant,
                        )
                    }
                    TaskStatusBadge(text = round.statusLabel)
                }

                HistoryRoundPreview(
                    inputPreview = round.inputPreview,
                    resultPreview = round.resultPreview,
                )

                if (round.previewAttachments.isNotEmpty()) {
                    HistoryAttachmentPreviewRow(
                        attachments = round.previewAttachments,
                        hiddenCount = round.totalAttachmentCount - round.previewAttachments.size,
                        onOpenAttachment = onOpenAttachment,
                    )
                }
            }

            if (isExpanded) {
                DividerHorizontal()
                HistoryRoundSection(
                    title = "Input",
                    body = round.inputText ?: "No preserved input is available for this round.",
                    enableLocatorCollapse = false,
                )
                round.processSection?.let { processSection ->
                    TaskProcessSection(
                        section = processSection,
                        isExpanded = isProcessExpanded,
                        onToggle = onToggleProcess,
                        onOpenAttachment = onOpenAttachment,
                        onSaveAttachment = onSaveAttachment,
                    )
                }
                HistoryRoundSection(
                    title = "${round.speakerLabel} result",
                    body = round.resultText,
                    enableLocatorCollapse = true,
                )
                HistoryRoundAttachmentsSection(
                    inputAttachments = round.inputAttachments,
                    resultAttachments = round.resultAttachments,
                    onOpenAttachment = onOpenAttachment,
                    onSaveAttachment = onSaveAttachment,
                )
            }
        }
    }
}

@Composable
private fun HistoryRoundPreview(
    inputPreview: String,
    resultPreview: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextBodyMedium(
            text = inputPreview,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        DividerHorizontal()
        TextBodyMedium(
            text = resultPreview,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HistoryRoundSection(
    title: String,
    body: String,
    enableLocatorCollapse: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TaskSectionHeader(title = title)
        if (enableLocatorCollapse) {
            TaskCodeLocatorText(
                text = body,
                style = TaskCodeLocatorTextStyle.BodyMedium,
            )
        } else {
            TextBodyMedium(text = body)
        }
    }
}

@Composable
private fun HistoryRoundAttachmentsSection(
    inputAttachments: ImmutableList<TaskTimelineAttachmentUi>,
    resultAttachments: ImmutableList<TaskTimelineAttachmentUi>,
    onOpenAttachment: (String) -> Unit,
    onSaveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TaskSectionHeader(
            title = "Attachments",
            supportingText = "Input and result files remain grouped by origin inside this round.",
        )

        if (inputAttachments.isEmpty() && resultAttachments.isEmpty()) {
            TextBodyMedium(
                text = "No attachments captured for this round.",
                color = MainTheme.colors.onSurfaceVariant,
            )
            return@Column
        }

        if (inputAttachments.isNotEmpty()) {
            TextLabelMedium(
                text = "Input attachments",
                color = MainTheme.colors.onSurfaceVariant,
            )
            TimelineAttachments(
                attachments = inputAttachments,
                onOpenAttachment = onOpenAttachment,
                onSaveAttachment = onSaveAttachment,
            )
        }

        if (resultAttachments.isNotEmpty()) {
            TextLabelMedium(
                text = "Result attachments",
                color = MainTheme.colors.onSurfaceVariant,
            )
            TimelineAttachments(
                attachments = resultAttachments,
                onOpenAttachment = onOpenAttachment,
                onSaveAttachment = onSaveAttachment,
            )
        }
    }
}

@Composable
private fun HistoryAttachmentPreviewRow(
    attachments: ImmutableList<TaskSessionHistoryAttachmentPreviewUi>,
    hiddenCount: Int,
    onOpenAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            HistoryAttachmentPreviewChip(
                attachment = attachment,
                onOpenAttachment = onOpenAttachment,
            )
        }
        if (hiddenCount > 0) {
            HistoryAttachmentOverflowChip(hiddenCount = hiddenCount)
        }
    }
}

@Composable
private fun HistoryAttachmentPreviewChip(
    attachment: TaskSessionHistoryAttachmentPreviewUi,
    onOpenAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .testTag("TaskSessionHistoryAttachmentPreview:${attachment.attachmentId}")
            .clickable(
                enabled = attachment.isActionAvailable,
                onClick = { onOpenAttachment(attachment.attachmentId) },
            ),
        shape = MainTheme.shapes.small,
        color = MainTheme.colors.surfaceContainerHigh,
        contentColor = MainTheme.colors.onSurface,
        tonalElevation = MainTheme.elevations.level0,
    ) {
        TextLabelMedium(
            text = attachment.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            color = if (attachment.isActionAvailable) {
                MainTheme.colors.onSurface
            } else {
                MainTheme.colors.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun HistoryAttachmentOverflowChip(
    hiddenCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MainTheme.shapes.small,
        color = MainTheme.colors.surfaceContainer,
        contentColor = MainTheme.colors.onSurfaceVariant,
        tonalElevation = MainTheme.elevations.level0,
    ) {
        TextLabelMedium(
            text = "+$hiddenCount",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            color = MainTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryCenteredMessage(
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

private fun List<String>.toggle(value: String): List<String> {
    return if (value in this) {
        filterNot { it == value }
    } else {
        this + value
    }
}

private fun TaskSessionDetailUiState.historyEnvironmentHeadline(): String {
    val workdirLabel = workdir
        ?.takeIf(String::isNotBlank)
        ?: repoPath.substringAfterLast('/').substringAfterLast('\\').ifBlank { repoPath }
    return "History · $workdirLabel"
}

private fun TaskSessionDetailUiState.historyWorkspaceLabel(): String {
    return workspaceId
        ?.takeIf(String::isNotBlank)
        ?.let { "Workspace · $it" }
        ?: "Workspace · missing binding"
}

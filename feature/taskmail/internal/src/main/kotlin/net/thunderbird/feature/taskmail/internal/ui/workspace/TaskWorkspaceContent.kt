package net.thunderbird.feature.taskmail.internal.ui.workspace

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilledTonal
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonIcon
import app.k9mail.core.ui.compose.designsystem.atom.card.CardElevated
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.molecule.ErrorView
import app.k9mail.core.ui.compose.designsystem.molecule.PullToRefreshBox
import app.k9mail.core.ui.compose.designsystem.organism.TopAppBar
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.WarningBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import net.thunderbird.core.ui.compose.common.modifier.testTagAsResourceId
import net.thunderbird.core.ui.compose.designsystem.atom.icon.Icons
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.workspace.component.SessionRow
import net.thunderbird.feature.taskmail.internal.ui.workspace.component.WorkspaceCard

@Composable
internal fun TaskWorkspaceContent(
    state: TaskWorkspaceContract.State,
    onEvent: (TaskWorkspaceContract.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = "TaskMail",
                actions = {
                    ButtonIcon(
                        onClick = { onEvent(TaskWorkspaceContract.Event.ProjectListClicked) },
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "Project list",
                        modifier = Modifier.testTagAsResourceId("TaskWorkspaceProjectListButton"),
                    )
                    ButtonIcon(
                        onClick = { onEvent(TaskWorkspaceContract.Event.NewTaskClicked) },
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "New task",
                        modifier = Modifier.testTagAsResourceId("TaskWorkspaceNewTaskButton"),
                    )
                },
            )
        },
    ) { innerPadding ->
        TaskWorkspaceBody(
            state = state,
            onEvent = onEvent,
            innerPadding = innerPadding,
        )
    }
}

@Composable
private fun TaskWorkspaceBody(
    state: TaskWorkspaceContract.State,
    onEvent: (TaskWorkspaceContract.Event) -> Unit,
    innerPadding: PaddingValues,
) {
    when {
        state.isLoading && !state.hasContent -> {
            WorkspaceCenteredMessage(
                title = "Loading TaskMail",
                message = "Reading sessions and workspace context.",
                modifier = Modifier.padding(innerPadding),
            )
        }

        state.error != null && !state.hasContent -> {
            ErrorView(
                title = "Unable to load TaskMail",
                message = state.error,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                onRetry = { onEvent(TaskWorkspaceContract.Event.RetryClicked) },
                contentAlignment = Alignment.Center,
            )
        }

        else -> {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onEvent(TaskWorkspaceContract.Event.RefreshRequested) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (state.isEmpty) {
                    WorkspaceEmptyState(
                        refreshError = state.refreshError,
                        onProjectList = { onEvent(TaskWorkspaceContract.Event.ProjectListClicked) },
                        onNewTask = { onEvent(TaskWorkspaceContract.Event.NewTaskClicked) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    WorkbenchHome(
                        state = state,
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkbenchHome(
    state: TaskWorkspaceContract.State,
    onEvent: (TaskWorkspaceContract.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTagAsResourceId("TaskWorkspaceHomeList"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
        TaskSectionHeader(
            title = "Session workbench",
            supportingText = "Sessions are the primary object. PC and workspace stay as route context until control-plane inventory is wired.",
        )
    }

        state.refreshError?.let { refreshError ->
            item {
                WarningBannerInlineNotificationCard(
                    title = "TaskMail refresh failed",
                    supportingText = refreshError,
                    actions = {},
                )
            }
        }

        sessionSection(
            title = "Needs attention",
            supportingText = "Questions, paused sessions, and failed runs that may need action.",
            sessions = state.attentionSessions,
            emptyText = "No sessions need attention right now.",
            onEvent = onEvent,
        )

        sessionSection(
            title = "Active sessions",
            supportingText = "Queued and running work that is still in flight.",
            sessions = state.activeSessions,
            emptyText = "No active sessions right now.",
            onEvent = onEvent,
        )

        sessionSection(
            title = "Recent sessions",
            supportingText = "Most recently updated sessions from the current cached projection.",
            sessions = state.recentSessions,
            emptyText = "Recent history will appear here after sessions start syncing.",
            onEvent = onEvent,
        )

        pcSummarySection(
            pcSummaries = state.pcSummaries,
        )

        workspaceSummarySection(
            state = state,
            onEvent = onEvent,
        )
    }
}

private fun LazyListScope.sessionSection(
    title: String,
    supportingText: String,
    sessions: List<TaskSessionItemUi>,
    emptyText: String,
    onEvent: (TaskWorkspaceContract.Event) -> Unit,
) {
    item {
        TaskSectionHeader(
            title = title,
            supportingText = supportingText,
        )
    }

    if (sessions.isEmpty()) {
        item {
            SectionPlaceholder(text = emptyText)
        }
        return
    }

    items(
        items = sessions,
        key = { session -> session.stableId },
    ) { session ->
        SessionRow(
            session = session,
            onClick = {
                session.sessionId?.let { sessionId ->
                    onEvent(
                        TaskWorkspaceContract.Event.SessionClicked(
                            workspaceId = session.workspaceId,
                            sessionId = sessionId,
                        ),
                    )
                }
            },
        )
    }
}

private fun LazyListScope.pcSummarySection(
    pcSummaries: List<TaskPcSummaryItemUi>,
) {
    item {
        TaskSectionHeader(
            title = "PC summaries",
            supportingText = "Control-plane PC inventory will land here once the VPS-side list API is wired.",
        )
    }

    if (pcSummaries.isEmpty()) {
        item {
            SectionPlaceholder(
                text = "PC inventory is not wired yet. The current home derives route context from cached session details.",
            )
        }
        return
    }

    items(pcSummaries) { summary ->
        PcSummaryCard(summary = summary)
    }
}

private fun LazyListScope.workspaceSummarySection(
    state: TaskWorkspaceContract.State,
    onEvent: (TaskWorkspaceContract.Event) -> Unit,
) {
    item {
        TaskSectionHeader(
            title = "Workspace summaries",
            supportingText = "Current workspace route context derived from cached session details while control-plane inventory is still being wired.",
        )
    }

    if (state.workspaceSummaries.isEmpty()) {
        item {
            SectionPlaceholder(
                text = "Workspace summaries will appear here once cached session details are available.",
            )
        }
        return
    }

    items(state.workspaceSummaries) { workspace ->
        WorkspaceCard(workspace = workspace) {
            workspace.sessions.forEach { session ->
                SessionRow(
                    session = session,
                    onClick = {
                        session.sessionId?.let { sessionId ->
                            onEvent(
                                TaskWorkspaceContract.Event.SessionClicked(
                                    workspaceId = session.workspaceId,
                                    sessionId = sessionId,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
) {
    TextBodySmall(
        text = text,
        color = MainTheme.colors.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun PcSummaryCard(
    summary: TaskPcSummaryItemUi,
    modifier: Modifier = Modifier,
) {
    CardElevated(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextHeadlineSmall(text = summary.title)
            summary.supportingText?.let { supportingText ->
                TextBodyMedium(
                    text = supportingText,
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }
            TextLabelMedium(
                text = summary.workspaceCountLabel,
                color = MainTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkspaceEmptyState(
    refreshError: String?,
    onProjectList: () -> Unit,
    onNewTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        refreshError?.let {
            WarningBannerInlineNotificationCard(
                title = "TaskMail refresh failed",
                supportingText = it,
                actions = {},
            )
        }
        TextHeadlineSmall(text = "No TaskMail sessions yet")
        TextBodyLarge(
            text = "When TaskMail sessions arrive, the workbench will show session sections first, with workspace context kept below.",
        )
        ButtonFilled(
            text = "New task",
            onClick = onNewTask,
            modifier = Modifier
                .fillMaxWidth()
                .testTagAsResourceId("TaskWorkspaceEmptyStateNewTaskButton"),
        )
        ButtonFilledTonal(
            text = "Project list",
            onClick = onProjectList,
            modifier = Modifier
                .fillMaxWidth()
                .testTagAsResourceId("TaskWorkspaceEmptyStateProjectListButton"),
        )
    }
}

@Composable
private fun WorkspaceCenteredMessage(
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

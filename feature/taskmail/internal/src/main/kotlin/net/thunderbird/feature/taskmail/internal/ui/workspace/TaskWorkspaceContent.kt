package net.thunderbird.feature.taskmail.internal.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilledTonal
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonIcon
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.molecule.ErrorView
import app.k9mail.core.ui.compose.designsystem.molecule.PullToRefreshBox
import app.k9mail.core.ui.compose.designsystem.organism.TopAppBar
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.WarningBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import net.thunderbird.core.ui.compose.common.modifier.testTagAsResourceId
import net.thunderbird.core.ui.compose.designsystem.atom.icon.Icons
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
        state.isLoading && state.workspaces.isEmpty() -> {
            WorkspaceCenteredMessage(
                title = "Loading TaskMail",
                message = "Reading workspaces and sessions from local mail.",
                modifier = Modifier.padding(innerPadding),
            )
        }

        state.error != null && state.workspaces.isEmpty() -> {
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
                    WorkspaceList(
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
private fun WorkspaceList(
    state: TaskWorkspaceContract.State,
    onEvent: (TaskWorkspaceContract.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TaskSectionHeader(
                title = "Workspaces",
                supportingText = "Browse active task sessions grouped by repo and workdir.",
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

        items(state.workspaces) { workspace ->
            WorkspaceCard(workspace = workspace) {
                workspace.sessions.forEach { session ->
                    SessionRow(
                        session = session,
                        onClick = {
                            onEvent(
                                TaskWorkspaceContract.Event.SessionClicked(
                                    workspaceId = session.workspaceId,
                                    sessionId = session.sessionId,
                                    threadId = session.threadId,
                                ),
                            )
                        },
                    )
                }
            }
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
            text = "When TaskMail threads arrive, they will appear grouped by workspace here.",
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

package net.thunderbird.feature.taskmail.internal.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilledTonal
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonIcon
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.card.CardElevated
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
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
import net.thunderbird.feature.taskmail.internal.ui.component.TaskBadgeRow
import net.thunderbird.feature.taskmail.internal.ui.component.TaskStatusBadge
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.workspace.component.SessionRow

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
    val pcNodes = state.pcTreeNodes.ifEmpty { state.toPlaceholderPcTree() }

    LazyColumn(
        modifier = modifier.testTagAsResourceId("TaskWorkspaceHomeList"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TaskSectionHeader(
                title = "Session workbench",
                supportingText = "Sessions are grouped as PC -> workspace -> session. When live environment inventory is available, online/offline and missing workspace state come directly from VPS.",
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

        if (pcNodes.isEmpty()) {
            item {
                SectionPlaceholder(
                    text = "PC/workspace trees will appear here once cached session projections are available.",
                )
            }
        } else {
            items(pcNodes, key = TaskWorkspacePcNodeUi::id) { pc ->
                PcTreeCard(
                    pc = pc,
                    onSessionClick = { session ->
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
            text = "When TaskMail sessions arrive, the workbench will show active session sections first, with routed workspace context kept below.",
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
private fun PcTreeCard(
    pc: TaskWorkspacePcNodeUi,
    onSessionClick: (TaskSessionItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable(pc.id) { mutableStateOf(pc.isInitiallyExpanded) }

    CardElevated(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextHeadlineSmall(text = pc.title)
                    TextBodyMedium(
                        text = pc.supportingText,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
                ButtonText(
                    text = if (isExpanded) "Collapse" else "Expand",
                    onClick = { isExpanded = !isExpanded },
                )
            }

            TaskBadgeRow {
                TaskStatusBadge(text = pc.connectionStatus)
                TaskStatusBadge(text = pc.summaryLabel)
            }

            if (isExpanded) {
                pc.workspaces.forEach { workspace ->
                    WorkspaceTreeCard(
                        workspace = workspace,
                        onSessionClick = onSessionClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTreeCard(
    workspace: TaskWorkspaceTreeNodeUi,
    onSessionClick: (TaskSessionItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable(workspace.id) { mutableStateOf(workspace.isInitiallyExpanded) }

    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextLabelMedium(text = workspace.title)
                    TextBodySmall(
                        text = workspace.supportingText,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
                ButtonText(
                    text = if (isExpanded) "Collapse" else "Expand",
                    onClick = { isExpanded = !isExpanded },
                )
            }

            TaskBadgeRow {
                TaskStatusBadge(text = workspace.summaryLabel)
                workspace.presenceLabel?.let { presenceLabel ->
                    TaskStatusBadge(text = presenceLabel)
                }
            }

            if (isExpanded) {
                workspace.sessions.forEach { session ->
                    SessionRow(
                        session = session,
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { onSessionClick(session) },
                    )
                }
            }
        }
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

private fun TaskWorkspaceContract.State.toPlaceholderPcTree(): List<TaskWorkspacePcNodeUi> {
    val groupedWorkspaces = workspaceSummaries.map { workspace ->
        val runningCount = workspace.sessions.count(TaskSessionItemUi::isActiveSession)
        val waitingCount = workspace.sessions.count(TaskSessionItemUi::requiresAttention)
        TaskWorkspaceTreeNodeUi(
            id = workspace.routeTargetLabel ?: workspace.title,
            title = workspace.title,
            supportingText = buildWorkspaceSupportingText(workspace),
            summaryLabel = when {
                waitingCount > 0 -> "$waitingCount needs attention"
                runningCount > 0 -> "$runningCount active"
                else -> workspace.sessionCountLabel
            },
            presenceLabel = if (workspace.routeTargetLabel == null) "Missing" else null,
            isMissingBinding = workspace.routeTargetLabel == null,
            isInitiallyExpanded = waitingCount > 0 || runningCount > 0,
            sessions = workspace.sessions,
        )
    }

    val groupedSessionIds = groupedWorkspaces
        .flatMap(TaskWorkspaceTreeNodeUi::sessions)
        .map(TaskSessionItemUi::stableId)
        .toSet()
    val looseSessions = (attentionSessions + activeSessions + recentSessions)
        .distinctBy(TaskSessionItemUi::stableId)
        .filterNot { session -> session.stableId in groupedSessionIds }

    val workspaces = buildList {
        addAll(groupedWorkspaces)
        if (looseSessions.isNotEmpty()) {
            add(
                TaskWorkspaceTreeNodeUi(
                    id = "workspace_missing",
                    title = "Missing workspace",
                    supportingText = "Session is still visible even though a stable workspace binding is missing.",
                    summaryLabel = "${looseSessions.size} session(s)",
                    presenceLabel = "Missing",
                    isMissingBinding = true,
                    isInitiallyExpanded = true,
                    sessions = looseSessions,
                ),
            )
        }
    }

    if (workspaces.isEmpty()) return emptyList()

    return listOf(
        TaskWorkspacePcNodeUi(
            id = "placeholder_pc",
            title = "Routed PC",
            supportingText = "Placeholder PC grouping derived from current session bindings.",
            connectionStatus = if (activeSessions.isNotEmpty()) "Online" else "Offline",
            summaryLabel = "${workspaces.size} workspace(s)",
            isInitiallyExpanded = true,
            workspaces = workspaces,
        ),
    )
}

private fun buildWorkspaceSupportingText(workspace: TaskWorkspaceItemUi): String {
    return when {
        workspace.routeTargetLabel != null && workspace.subtitle != null -> {
            "${workspace.routeTargetLabel} · ${workspace.subtitle}"
        }

        workspace.routeTargetLabel != null -> workspace.routeTargetLabel
        workspace.subtitle != null -> "Workspace binding missing · ${workspace.subtitle}"
        else -> "Workspace binding missing"
    }
}

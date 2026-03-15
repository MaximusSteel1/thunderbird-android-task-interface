package net.thunderbird.feature.taskmail.internal.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.molecule.ErrorView
import app.k9mail.core.ui.compose.designsystem.organism.TopAppBar
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
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
            TopAppBar(title = "TaskMail")
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
        state.isLoading -> {
            WorkspaceCenteredMessage(
                title = "Loading TaskMail",
                message = "Reading workspaces and sessions from local mail.",
                modifier = Modifier.padding(innerPadding),
            )
        }

        state.error != null -> {
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

        state.isEmpty -> {
            WorkspaceCenteredMessage(
                title = "No TaskMail sessions yet",
                message = "When TaskMail threads arrive, they will appear grouped by workspace here.",
                modifier = Modifier.padding(innerPadding),
            )
        }

        else -> {
            WorkspaceList(
                state = state,
                onEvent = onEvent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
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

        items(state.workspaces) { workspace ->
            WorkspaceCard(workspace = workspace) {
                workspace.sessions.forEach { session ->
                    SessionRow(
                        session = session,
                        onClick = {
                            onEvent(
                                TaskWorkspaceContract.Event.SessionClicked(
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

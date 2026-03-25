package net.thunderbird.feature.taskmail.internal.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData

@Composable
@Preview(showBackground = true)
internal fun TaskWorkspacePreview() {
    TaskWorkspaceContent(
        state = TaskWorkspaceContract.State(
            workspaceSummaries = listOf(
                TaskWorkspaceItemUi(
                    title = TaskMailPreviewData.workspaceSummaries.first().title,
                    subtitle = TaskMailPreviewData.workspaceSummaries.first().subtitle,
                    sessionCountLabel = "1 session",
                    sessions = listOf(
                        TaskSessionItemUi(
                            sessionId = "session_001",
                            stableId = "workspace_001::session_001",
                            sessionName = "Build TaskMail Phase 1",
                            status = "WaitingUser",
                            backend = "Codex",
                            lastSummary = "Parser layer is complete. Waiting to continue UI work.",
                            pendingQuestion = true,
                        ),
                    ),
                ),
            ),
        ),
        onEvent = {},
    )
}

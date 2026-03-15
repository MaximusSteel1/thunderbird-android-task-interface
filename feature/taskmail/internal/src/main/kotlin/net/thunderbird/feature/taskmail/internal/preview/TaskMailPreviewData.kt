package net.thunderbird.feature.taskmail.internal.preview

import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageBody
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionSummary
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule

internal object TaskMailPreviewData {
    val workspaceSummaries: List<TaskWorkspaceSummary> = listOf(
        TaskWorkspaceSummary(
            key = TaskWorkspaceKey(
                workspaceId = "workspace_001",
                repoPath = "E:/projects/android_task_manager",
                workdir = "feature/taskmail",
            ),
            title = "android_task_manager",
            subtitle = "feature/taskmail",
            backendSet = setOf(TaskMailBackend.Codex),
            activeSessionId = "session_001",
            sessionCount = 1,
            sessions = listOf(
                TaskSessionSummary(
                    key = TaskSessionKey(
                        sessionId = "session_001",
                        threadId = "thread_001",
                    ),
                    sessionName = "Build TaskMail Phase 1",
                    status = TaskMailSessionStatus.WaitingUser,
                    backend = TaskMailBackend.Codex,
                    lastSummary = "Parser layer is complete. Waiting to continue UI work.",
                    lastUpdatedAt = 1_742_000_000_000,
                    pendingQuestion = true,
                ),
            ),
        ),
    )

    val sessionDetails: List<TaskSessionDetail> = listOf(
        TaskSessionDetail(
            key = TaskSessionKey(
                sessionId = "session_001",
                threadId = "thread_001",
            ),
            workspace = TaskWorkspaceKey(
                workspaceId = "workspace_001",
                repoPath = "E:/projects/android_task_manager",
                workdir = "feature/taskmail",
            ),
            sessionName = "Build TaskMail Phase 1",
            backend = TaskMailBackend.Codex,
            status = TaskMailSessionStatus.WaitingUser,
            repoPath = "E:/projects/android_task_manager",
            workdir = "feature/taskmail",
            lastSummary = "Parser layer is complete. Waiting to continue UI work.",
            question = TaskQuestionCapsule(
                questionId = "question_001",
                questionText = "Should I proceed with the workspace and detail screens next?",
                choices = listOf("yes", "no"),
            ),
            replyContext = TaskSessionReplyContext(
                accountUuid = "account-1",
                folderId = 1L,
                messageServerId = "msg-system-001",
                threadRootId = 100L,
                anchorTimestamp = 1_742_000_000_000,
            ),
            timeline = listOf(
                TaskTimelineItem(
                    id = "timeline_001",
                    timestamp = 1_742_000_000_000,
                    direction = TaskTimelineDirection.System,
                    statusLabel = TaskMailStatusLabel.Question,
                    summary = "Need confirmation before continuing the UI layer.",
                    body = TaskMessageBody(
                        plainText = "Parser layer is complete. Waiting for the next implementation step.",
                        markdownCandidate = false,
                    ),
                ),
            ),
        ),
    )
}

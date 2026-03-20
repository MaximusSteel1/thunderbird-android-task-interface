package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionLifecycle
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailParsedSubject
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskStateCapsule
import org.junit.Test

internal class DefaultTaskMailRepositoryLifecycleCompatibilityTest {

    @Test
    fun `getTaskSessionDetail preserves lifecycle and progress timestamps from the latest state capsule`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = LifecycleCompatibilityMessageSource(
                messages = listOf(
                    lifecycleSystemMessage(
                        threadRootId = 101L,
                        messageServerId = "msg-lifecycle",
                        timestamp = 101L,
                        sessionId = "session-lifecycle",
                        workspaceId = "workspace-1",
                        sessionName = "Lifecycle slice",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        status = TaskMailSessionStatus.Done,
                        lifecycle = TaskMailSessionLifecycle.Ended,
                        lastActiveAt = "2026-03-18T16:12:00Z",
                        lastProgressAt = "2026-03-18T16:10:00Z",
                        lastSummary = "Ended after the latest completed run.",
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskSessionDetail(
            TaskSessionKey(sessionId = "session-lifecycle", threadId = "thread-101"),
        )

        assertThat(result).isNotNull()
        val detail = result!!
        assertThat(detail.lifecycle).isEqualTo(TaskMailSessionLifecycle.Ended)
        assertThat(detail.lastActiveAt).isEqualTo("2026-03-18T16:12:00Z")
        assertThat(detail.lastProgressAt).isEqualTo("2026-03-18T16:10:00Z")
    }

    @Test
    fun `getTaskWorkspaceSummaries preserves lifecycle and progress timestamps on session summaries`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = LifecycleCompatibilityMessageSource(
                messages = listOf(
                    lifecycleSystemMessage(
                        threadRootId = 102L,
                        messageServerId = "msg-workspace-lifecycle",
                        timestamp = 102L,
                        sessionId = "session-workspace-lifecycle",
                        workspaceId = "workspace-1",
                        sessionName = "Workspace lifecycle slice",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        status = TaskMailSessionStatus.Running,
                        lifecycle = TaskMailSessionLifecycle.Active,
                        lastActiveAt = "2026-03-18T16:15:00Z",
                        lastProgressAt = "2026-03-18T16:14:30Z",
                        lastSummary = "Still active.",
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskWorkspaceSummaries()

        val session = result.single().sessions.single()
        assertThat(session.lifecycle).isEqualTo(TaskMailSessionLifecycle.Active)
        assertThat(session.lastActiveAt).isEqualTo("2026-03-18T16:15:00Z")
        assertThat(session.lastProgressAt).isEqualTo("2026-03-18T16:14:30Z")
    }
}

private class LifecycleCompatibilityMessageSource(
    private val messages: List<TaskMailMessage>,
) : TaskMailMessageSource {
    override suspend fun getMessages(): List<TaskMailMessage> = messages
}

@Suppress("LongParameterList")
private fun lifecycleSystemMessage(
    threadRootId: Long,
    messageServerId: String,
    timestamp: Long,
    sessionId: String,
    workspaceId: String,
    sessionName: String,
    repoPath: String,
    workdir: String,
    status: TaskMailSessionStatus,
    lifecycle: TaskMailSessionLifecycle,
    lastActiveAt: String,
    lastProgressAt: String,
    lastSummary: String,
): TaskMailMessage {
    return TaskMailMessage(
        accountUuid = "account-1",
        folderId = 1L,
        messageServerId = messageServerId,
        threadRootId = threadRootId,
        timestamp = timestamp,
        subject = "[RUNNING] [CX] [$sessionId] $sessionName",
        rawBodyText = """
            Summary: $lastSummary

            ---TASK-STATE-BEGIN---
            thread_id: thread-$threadRootId
            workspace_id: $workspaceId
            session_id: $sessionId
            session_name: $sessionName
            repo_path: $repoPath
            workdir: $workdir
            backend: codex
            status: ${status.name.lowercase()}
            lifecycle: ${lifecycle.name.lowercase()}
            last_active_at: $lastActiveAt
            last_progress_at: $lastProgressAt
            last_summary: $lastSummary
            ---TASK-STATE-END---
        """.trimIndent(),
        detection = TaskMailDetection(
            isTaskMail = true,
            isSystemMessage = true,
            parsedSubject = TaskMailParsedSubject(
                backend = TaskMailBackend.Codex,
                statusLabel = null,
                sessionIdFromSubject = sessionId,
                subjectText = sessionName,
                isReplyLike = false,
            ),
            stateCapsule = TaskStateCapsule(
                threadId = "thread-$threadRootId",
                workspaceId = workspaceId,
                sessionId = sessionId,
                sessionName = sessionName,
                backend = TaskMailBackend.Codex,
                repoPath = repoPath,
                workdir = workdir,
                status = status,
                lifecycle = lifecycle,
                lastActiveAt = lastActiveAt,
                lastProgressAt = lastProgressAt,
                lastSummary = lastSummary,
            ),
        ),
        isFromCurrentUser = false,
    )
}

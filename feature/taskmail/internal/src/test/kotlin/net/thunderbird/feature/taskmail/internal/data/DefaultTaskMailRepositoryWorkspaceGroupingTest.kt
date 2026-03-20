package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailParsedSubject
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskStateCapsule
import org.junit.Test

internal class DefaultTaskMailRepositoryWorkspaceGroupingTest {

    @Test
    fun `getTaskWorkspaceSummaries merges repo workspaces even when workspace ids differ`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = WorkspaceGroupingMessageSource(
                messages = listOf(
                    workspaceSystemMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-1",
                        timestamp = 100L,
                        sessionId = "session-1",
                        workspaceId = "workspace-a",
                        sessionName = "Implement parser",
                        repoPath = "E:\\projects\\android_task_manager\\",
                        workdir = "feature\\taskmail",
                        status = TaskMailSessionStatus.Running,
                        lastSummary = "Parser is running.",
                    ),
                    workspaceSystemMessage(
                        threadRootId = 200L,
                        messageServerId = "msg-2",
                        timestamp = 200L,
                        sessionId = "session-2",
                        workspaceId = "workspace-b",
                        sessionName = "Wire repository",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        status = TaskMailSessionStatus.WaitingUser,
                        lastSummary = "Need confirmation from user.",
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskWorkspaceSummaries()

        assertThat(result).hasSize(1)
        assertThat(result.single().key.workspaceId).isNull()
        assertThat(result.single().key.repoPath).isEqualTo("E:/projects/android_task_manager")
        assertThat(result.single().title).isEqualTo("android_task_manager")
        assertThat(result.single().subtitle).isEqualTo("feature/taskmail")
        assertThat(result.single().sessionCount).isEqualTo(2)
    }

    @Test
    fun `getTaskWorkspaceSummaries groups missing repo path sessions by workspace id`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = WorkspaceGroupingMessageSource(
                messages = listOf(
                    workspaceSystemMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-1",
                        timestamp = 100L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "",
                        workdir = "feature/taskmail",
                        status = TaskMailSessionStatus.Running,
                        lastSummary = "Parser is running.",
                    ),
                    workspaceSystemMessage(
                        threadRootId = 200L,
                        messageServerId = "msg-2",
                        timestamp = 200L,
                        sessionId = "session-2",
                        workspaceId = "workspace-1",
                        sessionName = "Wire repository",
                        repoPath = "",
                        workdir = "another/workdir",
                        status = TaskMailSessionStatus.WaitingUser,
                        lastSummary = "Need confirmation from user.",
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskWorkspaceSummaries()

        assertThat(result).hasSize(1)
        assertThat(result.single().key.workspaceId).isEqualTo("workspace-1")
        assertThat(result.single().title).isEqualTo("workspace-1")
        assertThat(result.single().sessionCount).isEqualTo(2)
    }

    @Test
    fun `getTaskWorkspaceSummaries treats dot workdir as repo root`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = WorkspaceGroupingMessageSource(
                messages = listOf(
                    workspaceSystemMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-1",
                        timestamp = 100L,
                        sessionId = "session-1",
                        workspaceId = "workspace-root-empty",
                        sessionName = "Root empty",
                        repoPath = "E:/projects/mail_based_task_manager",
                        workdir = "",
                        status = TaskMailSessionStatus.Done,
                        lastSummary = "Repo root with empty workdir.",
                    ),
                    workspaceSystemMessage(
                        threadRootId = 200L,
                        messageServerId = "msg-2",
                        timestamp = 200L,
                        sessionId = "session-2",
                        workspaceId = "workspace-root-dot",
                        sessionName = "Root dot",
                        repoPath = "E:/projects/mail_based_task_manager",
                        workdir = ".",
                        status = TaskMailSessionStatus.Running,
                        lastSummary = "Repo root with dot workdir.",
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskWorkspaceSummaries()

        assertThat(result).hasSize(1)
        assertThat(result.single().key.repoPath).isEqualTo("E:/projects/mail_based_task_manager")
        assertThat(result.single().key.workdir).isNull()
        assertThat(result.single().sessionCount).isEqualTo(2)
    }
}

private class WorkspaceGroupingMessageSource(
    private val messages: List<TaskMailMessage>,
) : TaskMailMessageSource {
    override suspend fun getMessages(): List<TaskMailMessage> = messages
}

@Suppress("LongParameterList")
private fun workspaceSystemMessage(
    threadRootId: Long,
    messageServerId: String,
    timestamp: Long,
    sessionId: String,
    workspaceId: String,
    sessionName: String,
    repoPath: String,
    workdir: String,
    status: TaskMailSessionStatus,
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
                lastSummary = lastSummary,
            ),
        ),
        isFromCurrentUser = false,
    )
}

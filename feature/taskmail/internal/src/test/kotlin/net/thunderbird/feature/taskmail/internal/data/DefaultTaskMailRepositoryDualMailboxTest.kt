package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailEnvelope
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailMessageDetector
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailParsedSubject
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskStateCapsule
import org.junit.Test

internal class DefaultTaskMailRepositoryDualMailboxTest {

    @Test
    fun `getTaskSessionDetail prefers user mailbox when bot mailbox copy is also present`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = DualMailboxFakeTaskMailMessageSource(
                messages = listOf(
                    userMailboxQuestionMessage(),
                    botMailboxSystemCopy(),
                ),
            ),
        )

        val result = testSubject.getTaskSessionDetail(
            TaskSessionKey(sessionId = "session-1", threadId = "thread-200"),
        )

        assertThat(result).isNotNull()
        val detail = result!!
        val replyContext = detail.replyContext
        assertThat(replyContext).isNotNull()
        assertThat(replyContext!!.accountUuid).isEqualTo("user-account")
        assertThat(replyContext.messageServerId).isEqualTo("msg-user-mailbox")
        assertThat(detail.timeline).hasSize(1)
    }

    @Test
    fun `getTaskSessionDetail keeps legacy single mailbox fallback when only service-side copies exist`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = DualMailboxFakeTaskMailMessageSource(
                messages = listOf(botMailboxSystemCopy(accountUuid = "single-account")),
            ),
        )

        val result = testSubject.getTaskSessionDetail(
            TaskSessionKey(sessionId = "session-1", threadId = "thread-300"),
        )

        assertThat(result).isNotNull()
        val detail = result!!
        val replyContext = detail.replyContext
        assertThat(replyContext).isNotNull()
        assertThat(replyContext!!.accountUuid).isEqualTo("single-account")
    }

    @Test
    fun `workspace summaries ignore inbound bot mailbox task requests when user mailbox data exists`() = runTest {
        val detector = TaskMailMessageDetector()
        val botMailboxRequest = TaskMailMessage(
            accountUuid = "bot-account",
            folderId = 1L,
            messageServerId = "msg-bot-inbox",
            threadRootId = 900L,
            timestamp = 50L,
            subject = "[CX] Analyze floor_shear",
            rawBodyText = """
                Repo: E:/projects/android_task_manager
                Task:
                Analyze floor_shear.
            """.trimIndent(),
            detection = detector.detect(
                TaskMailEnvelope(
                    messageId = "msg-bot-inbox",
                    subject = "[CX] Analyze floor_shear",
                    fromAddress = "user@example.com",
                    timestamp = 50L,
                    plainTextBody = """
                        Repo: E:/projects/android_task_manager
                        Task:
                        Analyze floor_shear.
                    """.trimIndent(),
                ),
            ),
            isFromCurrentUser = false,
        )
        val testSubject = DefaultTaskMailRepository(
            messageSource = DualMailboxFakeTaskMailMessageSource(
                messages = listOf(
                    botMailboxRequest,
                    userMailboxQuestionMessage(),
                ),
            ),
        )

        val result = testSubject.getTaskWorkspaceSummaries()

        assertThat(result).hasSize(1)
        assertThat(result.single().sessions).hasSize(1)
        assertThat(result.single().sessions.single().key.sessionId).isEqualTo("session-1")
    }
}

private class DualMailboxFakeTaskMailMessageSource(
    private val messages: List<TaskMailMessage>,
) : TaskMailMessageSource {
    override suspend fun getMessages(): List<TaskMailMessage> = messages
}

private fun userMailboxQuestionMessage(): TaskMailMessage {
    return TaskMailMessage(
        accountUuid = "user-account",
        folderId = 1L,
        messageServerId = "msg-user-mailbox",
        threadRootId = 200L,
        timestamp = 200L,
        subject = "[QUESTION] [CX] [S:session-1] Implement parser",
        rawBodyText = """
            Summary: Need confirmation from user.

            ---TASK-QUESTION-BEGIN---
            question_id: question-session-1
            question_text: Proceed?
            choices: yes | no
            ---TASK-QUESTION-END---

            ---TASK-STATE-BEGIN---
            thread_id: thread-200
            workspace_id: workspace-1
            session_id: session-1
            session_name: Implement parser
            repo_path: E:/projects/android_task_manager
            workdir: feature/taskmail
            backend: codex
            status: waiting_user
            last_summary: Need confirmation from user.
            ---TASK-STATE-END---
        """.trimIndent(),
        detection = TaskMailDetection(
            isTaskMail = true,
            isSystemMessage = true,
            parsedSubject = TaskMailParsedSubject(
                backend = TaskMailBackend.Codex,
                statusLabel = null,
                sessionIdFromSubject = "session-1",
                subjectText = "Implement parser",
                isReplyLike = false,
            ),
            stateCapsule = TaskStateCapsule(
                threadId = "thread-200",
                workspaceId = "workspace-1",
                sessionId = "session-1",
                sessionName = "Implement parser",
                backend = TaskMailBackend.Codex,
                repoPath = "E:/projects/android_task_manager",
                workdir = "feature/taskmail",
                status = TaskMailSessionStatus.WaitingUser,
                lastSummary = "Need confirmation from user.",
            ),
            questionCapsule = TaskQuestionCapsule(
                questionId = "question-session-1",
                questionText = "Proceed?",
                choices = listOf("yes", "no"),
            ),
        ),
        isFromCurrentUser = false,
    )
}

private fun botMailboxSystemCopy(accountUuid: String = "bot-account"): TaskMailMessage {
    return userMailboxQuestionMessage().copy(
        accountUuid = accountUuid,
        messageServerId = "msg-bot-mailbox",
        threadRootId = 300L,
        timestamp = 250L,
        rawBodyText = """
            Summary: Need confirmation from user.

            ---TASK-STATE-BEGIN---
            thread_id: thread-300
            workspace_id: workspace-1
            session_id: session-1
            session_name: Implement parser
            repo_path: E:/projects/android_task_manager
            workdir: feature/taskmail
            backend: codex
            status: waiting_user
            last_summary: Need confirmation from user.
            ---TASK-STATE-END---
        """.trimIndent(),
        detection = TaskMailDetection(
            isTaskMail = true,
            isSystemMessage = true,
            parsedSubject = TaskMailParsedSubject(
                backend = TaskMailBackend.Codex,
                statusLabel = null,
                sessionIdFromSubject = "session-1",
                subjectText = "Implement parser",
                isReplyLike = false,
            ),
            stateCapsule = TaskStateCapsule(
                threadId = "thread-300",
                workspaceId = "workspace-1",
                sessionId = "session-1",
                sessionName = "Implement parser",
                backend = TaskMailBackend.Codex,
                repoPath = "E:/projects/android_task_manager",
                workdir = "feature/taskmail",
                status = TaskMailSessionStatus.WaitingUser,
                lastSummary = "Need confirmation from user.",
            ),
        ),
        isFromCurrentUser = true,
    )
}

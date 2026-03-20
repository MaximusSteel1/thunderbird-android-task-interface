package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailParsedSubject
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskStateCapsule
import org.junit.Test

internal class DefaultTaskMailRepositoryPendingQuestionsTest {

    @Test
    fun `getTaskSessionDetail clears pending questions after newer non-question system status arrives`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = PendingQuestionsMessageSource(
                messages = listOf(
                    pendingQuestionMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-question",
                        timestamp = 100L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        lastSummary = "Need confirmation from user.",
                        questionText = "Proceed with repository wiring?",
                        choices = listOf("yes", "no"),
                    ),
                    pendingAnswerMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-answer",
                        timestamp = 150L,
                        subject = "Re: [QUESTION] [CX] [S:session-1] Implement parser",
                        rawBodyText = "yes",
                    ),
                    doneSystemMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-done",
                        timestamp = 200L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        lastSummary = "Repository wiring completed.",
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskSessionDetail(
            TaskSessionKey(
                sessionId = "session-1",
                threadId = "thread-100",
            ),
        )

        assertThat(result).isNotNull()
        val detail = result!!
        assertThat(detail.status).isEqualTo(TaskMailSessionStatus.Done)
        assertThat(detail.question).isNull()
        assertThat(detail.pendingQuestions).isEqualTo(emptyList())
    }
}

private class PendingQuestionsMessageSource(
    private val messages: List<TaskMailMessage>,
) : TaskMailMessageSource {
    override suspend fun getMessages(): List<TaskMailMessage> = messages
}

@Suppress("LongParameterList")
private fun pendingQuestionMessage(
    threadRootId: Long,
    messageServerId: String,
    timestamp: Long,
    sessionId: String,
    workspaceId: String,
    sessionName: String,
    repoPath: String,
    workdir: String,
    lastSummary: String,
    questionText: String,
    choices: List<String>,
    accountUuid: String = "account-1",
): TaskMailMessage {
    return TaskMailMessage(
        accountUuid = accountUuid,
        folderId = 1L,
        messageServerId = messageServerId,
        threadRootId = threadRootId,
        timestamp = timestamp,
        subject = "[QUESTION] [CX] [S:$sessionId] $sessionName",
        rawBodyText = """
            Summary: $lastSummary

            ---TASK-QUESTION-BEGIN---
            question_id: question-$sessionId
            question_text: $questionText
            choices: ${choices.joinToString("|")}
            ---TASK-QUESTION-END---

            ---TASK-STATE-BEGIN---
            thread_id: thread-$threadRootId
            workspace_id: $workspaceId
            session_id: $sessionId
            session_name: $sessionName
            repo_path: $repoPath
            workdir: $workdir
            backend: codex
            status: waiting_user
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
                status = TaskMailSessionStatus.WaitingUser,
                lastSummary = lastSummary,
            ),
            questionCapsule = TaskQuestionCapsule(
                questionId = "question-$sessionId",
                questionText = questionText,
                choices = choices,
            ),
        ),
        isFromCurrentUser = false,
    )
}

private fun pendingAnswerMessage(
    threadRootId: Long,
    messageServerId: String,
    timestamp: Long,
    subject: String,
    rawBodyText: String,
    accountUuid: String = "account-1",
): TaskMailMessage {
    return TaskMailMessage(
        accountUuid = accountUuid,
        folderId = 1L,
        messageServerId = messageServerId,
        threadRootId = threadRootId,
        timestamp = timestamp,
        subject = subject,
        rawBodyText = rawBodyText,
        detection = TaskMailDetection(
            isTaskMail = true,
            isSystemMessage = false,
            parsedSubject = TaskMailParsedSubject(
                backend = TaskMailBackend.Codex,
                statusLabel = null,
                sessionIdFromSubject = "session-1",
                subjectText = subject.substringAfterLast(']').trim(),
                isReplyLike = true,
            ),
        ),
        isFromCurrentUser = true,
    )
}

@Suppress("LongParameterList")
private fun doneSystemMessage(
    threadRootId: Long,
    messageServerId: String,
    timestamp: Long,
    sessionId: String,
    workspaceId: String,
    sessionName: String,
    repoPath: String,
    workdir: String,
    lastSummary: String,
    accountUuid: String = "account-1",
): TaskMailMessage {
    return TaskMailMessage(
        accountUuid = accountUuid,
        folderId = 1L,
        messageServerId = messageServerId,
        threadRootId = threadRootId,
        timestamp = timestamp,
        subject = "[DONE] [CX] [S:$sessionId] $sessionName",
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
            status: done
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
                status = TaskMailSessionStatus.Done,
                lastSummary = lastSummary,
            ),
        ),
        isFromCurrentUser = false,
    )
}

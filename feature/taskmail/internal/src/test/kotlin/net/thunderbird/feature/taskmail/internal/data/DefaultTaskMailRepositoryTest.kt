package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailParsedSubject
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskStateCapsule
import org.junit.Test

internal class DefaultTaskMailRepositoryTest {

    @Test
    fun `getTaskWorkspaceSummaries groups sessions by workspace and sorts by latest update`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    systemMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-1",
                        timestamp = 100L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        status = TaskMailSessionStatus.Running,
                        lastSummary = "Parser is running.",
                    ),
                    systemMessage(
                        threadRootId = 200L,
                        messageServerId = "msg-2",
                        timestamp = 200L,
                        sessionId = "session-2",
                        workspaceId = "workspace-1",
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
        assertThat(result.single().sessionCount).isEqualTo(2)
        assertThat(result.single().activeSessionId).isEqualTo("session-2")
        assertThat(result.single().sessions.map { it.sessionName }).containsExactly(
            "Wire repository",
            "Implement parser",
        )
    }

    @Test
    fun `getTaskSessionDetail uses capsule metadata and extracted bodies`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    userMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-user",
                        timestamp = 100L,
                        subject = "Re: [CX] [S:session-1] Implement parser",
                        rawBodyText = """
                            Looks good.
                            
                            On Tue, someone wrote:
                            > previous
                        """.trimIndent(),
                    ),
                    questionMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-system",
                        timestamp = 150L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        lastSummary = "Need confirmation from user.",
                        questionText = "Proceed with repository wiring?",
                        choices = listOf("yes", "no"),
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
        assertThat(detail.status).isEqualTo(TaskMailSessionStatus.WaitingUser)
        assertThat(detail.question?.questionText).isEqualTo("Proceed with repository wiring?")
        assertThat(detail.pendingQuestions).hasSize(1)
        assertThat(detail.timeline.map { it.direction }).containsExactly(
            TaskTimelineDirection.Outgoing,
            TaskTimelineDirection.System,
        )
        assertThat(detail.timeline.first().body.plainText).isEqualTo("Looks good.")
        assertThat(detail.timeline.last().body.plainText).isEqualTo("Need confirmation from user.")
        assertThat(detail.replyContext).isNotNull()
        assertThat(detail.replyContext!!.messageServerId).isEqualTo("msg-system")
    }

    @Test
    fun `getTaskSessionDetail preserves attachment metadata on timeline items`() = runTest {
        val attachment = TaskMessageAttachment(
            id = "content://taskmail/result-chart",
            displayName = "result_chart.png",
            contentType = "image/png",
            sizeBytes = 2_048L,
            isInline = true,
            isImage = true,
            contentId = "chart-preview",
            internalUriString = "content://taskmail/result-chart",
            accountUuid = "account_001",
            folderId = 1L,
            messageServerId = "msg-system",
            partId = 99L,
            isContentAvailable = true,
        )
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    systemMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-system",
                        timestamp = 100L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        status = TaskMailSessionStatus.Done,
                        lastSummary = "Attached the generated chart.",
                        attachments = listOf(attachment),
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
        assertThat(result!!.timeline.single().attachments).containsExactly(attachment)
    }

    @Test
    fun `getTaskSessionDetail falls back to thread id when session id is unavailable`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    userMessage(
                        threadRootId = 321L,
                        messageServerId = "msg-user",
                        timestamp = 10L,
                        subject = "[CX] Do a thing",
                        rawBodyText = "Please do the thing.",
                        sessionIdFromSubject = null,
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskSessionDetail(
            TaskSessionKey(
                sessionId = null,
                threadId = "321",
            ),
        )

        assertThat(result).isNotNull()
        val detail = result!!
        assertThat(detail.key.sessionId).isNull()
        assertThat(detail.workspace.repoPath).isEqualTo("321")
        assertThat(detail.sessionName).isEqualTo("Do a thing")
        assertThat(detail.replyContext).isNotNull()
        assertThat(detail.replyContext!!.messageServerId).isEqualTo("msg-user")
    }

    @Test
    @Suppress("MaxLineLength")
    fun `getTaskSessionDetail selects newest logical-session message as reply anchor across physical threads`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    systemMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-old",
                        timestamp = 100L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        status = TaskMailSessionStatus.Running,
                        lastSummary = "Parser is running.",
                    ),
                    questionMessage(
                        threadRootId = 200L,
                        messageServerId = "msg-new",
                        timestamp = 200L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        lastSummary = "Need confirmation from user.",
                        questionText = "Proceed?",
                        choices = listOf("yes", "no"),
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskSessionDetail(TaskSessionKey(sessionId = "session-1", threadId = "thread-200"))

        assertThat(result).isNotNull()
        val detail = result!!
        assertThat(detail.replyContext).isNotNull()
        val replyContext = detail.replyContext!!
        assertThat(replyContext.messageServerId).isEqualTo("msg-new")
        assertThat(replyContext.threadRootId).isEqualTo(200L)
    }

    @Test
    fun `getTaskSessionDetail disables reply for mixed-account logical sessions`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    systemMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-1",
                        timestamp = 100L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        status = TaskMailSessionStatus.Running,
                        lastSummary = "Parser is running.",
                        accountUuid = "account-1",
                    ),
                    questionMessage(
                        threadRootId = 200L,
                        messageServerId = "msg-2",
                        timestamp = 200L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        lastSummary = "Need confirmation from user.",
                        questionText = "Proceed?",
                        choices = listOf("yes", "no"),
                        accountUuid = "account-2",
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskSessionDetail(TaskSessionKey(sessionId = "session-1", threadId = "thread-200"))

        assertThat(result).isNotNull()
        assertThat(result!!.replyContext).isNull()
    }

    @Test
    fun `getTaskSessionDetail keeps null reply context when newest anchor has blank server id`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    systemMessage(
                        threadRootId = 100L,
                        messageServerId = "",
                        timestamp = 100L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        status = TaskMailSessionStatus.Running,
                        lastSummary = "Parser is running.",
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskSessionDetail(TaskSessionKey(sessionId = "session-1", threadId = "thread-100"))

        assertThat(result).isNotNull()
        assertThat(result!!.replyContext).isNull()
    }

    @Test
    fun `getTaskWorkspaceSummaries merges physical mail threads that share a logical task thread`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    systemMessage(
                        threadRootId = 100L,
                        messageServerId = "msg-1",
                        timestamp = 100L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        status = TaskMailSessionStatus.Running,
                        lastSummary = "Parser is running.",
                    ),
                    systemMessage(
                        threadRootId = 200L,
                        messageServerId = "msg-2",
                        timestamp = 200L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        status = TaskMailSessionStatus.Done,
                        lastSummary = "Parser is done.",
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskWorkspaceSummaries()

        assertThat(result).hasSize(1)
        assertThat(result.single().sessions).hasSize(1)
        assertThat(result.single().sessions.single().key.sessionId).isEqualTo("session-1")
        assertThat(result.single().sessions.single().status).isEqualTo(TaskMailSessionStatus.Done)
        assertThat(result.single().sessions.single().lastSummary).isEqualTo("Parser is done.")
    }

    @Test
    fun `getTaskSessionDetail preserves the newest multi-question set`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    multiQuestionMessage(
                        threadRootId = 200L,
                        messageServerId = "msg-question",
                        timestamp = 200L,
                        sessionId = "session-1",
                        workspaceId = "workspace-1",
                        sessionName = "Implement parser",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        lastSummary = "Need more details before continuing.",
                        questions = listOf(
                            TaskQuestionCapsule(
                                questionId = "phase2_entry_position",
                                questionText = "Where should the Tasks drawer entry be placed?",
                                choices = listOf("top", "below", "section"),
                                questionSetId = "phase2_clarifications",
                                questionType = "single_choice",
                                required = true,
                            ),
                            TaskQuestionCapsule(
                                questionId = "phase2_icon_strings",
                                questionText = "Who provides icon and string resources?",
                                choices = listOf("provide", "reuse", "placeholder"),
                                questionSetId = "phase2_clarifications",
                                questionType = "single_choice",
                                required = false,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskSessionDetail(TaskSessionKey(sessionId = "session-1", threadId = "thread-200"))

        assertThat(result).isNotNull()
        val detail = result!!
        assertThat(detail.pendingQuestions.map { it.questionId }).containsExactly(
            "phase2_entry_position",
            "phase2_icon_strings",
        )
        assertThat(detail.pendingQuestions.last().choiceLabels).isEqualTo(emptyMap())
        assertThat(detail.question?.questionId).isEqualTo("phase2_icon_strings")
    }

    private class FakeTaskMailMessageSource(
        private val messages: List<TaskMailMessage>,
    ) : TaskMailMessageSource {
        override suspend fun getMessages(): List<TaskMailMessage> = messages
    }
}

@Suppress("LongParameterList")
private fun systemMessage(
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
    attachments: List<TaskMessageAttachment> = emptyList(),
    accountUuid: String = "account-1",
): TaskMailMessage {
    return TaskMailMessage(
        accountUuid = accountUuid,
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
        attachments = attachments,
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

@Suppress("LongParameterList")
private fun questionMessage(
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

@Suppress("LongMethod", "LongParameterList")
private fun multiQuestionMessage(
    threadRootId: Long,
    messageServerId: String,
    timestamp: Long,
    sessionId: String,
    workspaceId: String,
    sessionName: String,
    repoPath: String,
    workdir: String,
    lastSummary: String,
    questions: List<TaskQuestionCapsule>,
    accountUuid: String = "account-1",
): TaskMailMessage {
    val renderedQuestionBlocks = questions.joinToString(separator = "\n") { question ->
        buildString {
            appendLine("---TASK-QUESTION-BEGIN---")
            question.questionSetId?.let { appendLine("question_set_id: $it") }
            question.questionType?.let { appendLine("question_type: $it") }
            appendLine("question_id: ${question.questionId}")
            appendLine("required: ${question.required}")
            appendLine("question_text: ${question.questionText}")
            if (question.choices.isNotEmpty()) {
                appendLine("choices: ${question.choices.joinToString(" | ")}")
            }
            if (question.choiceLabels.isNotEmpty()) {
                appendLine(
                    "choice_labels: ${
                        question.choiceLabels.entries.joinToString(" | ") { (key, value) -> "$key=$value" }
                    }",
                )
            }
            append("---TASK-QUESTION-END---")
        }
    }

    return TaskMailMessage(
        accountUuid = accountUuid,
        folderId = 1L,
        messageServerId = messageServerId,
        threadRootId = threadRootId,
        timestamp = timestamp,
        subject = "[QUESTION] [CX] [S:$sessionId] $sessionName",
        rawBodyText = """
            Summary: $lastSummary
            
            $renderedQuestionBlocks
            
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
            questionCapsule = questions.lastOrNull(),
            questionCapsules = questions,
        ),
        isFromCurrentUser = false,
    )
}

private fun userMessage(
    threadRootId: Long,
    messageServerId: String,
    timestamp: Long,
    subject: String,
    rawBodyText: String,
    sessionIdFromSubject: String? = "session-1",
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
                sessionIdFromSubject = sessionIdFromSubject,
                subjectText = subject.substringAfterLast(']').trim(),
                isReplyLike = true,
            ),
        ),
        isFromCurrentUser = true,
    )
}

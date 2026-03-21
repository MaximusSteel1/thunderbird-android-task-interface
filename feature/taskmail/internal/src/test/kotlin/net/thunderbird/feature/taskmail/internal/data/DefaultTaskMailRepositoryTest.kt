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
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailEnvelope
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailMessageDetector
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailParsedSubject
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskStateCapsule
import org.junit.Test

@Suppress("LargeClass")
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
    @Suppress("MaxLineLength")
    fun `getTaskWorkspaceSummaries should ignore outgoing new task request placeholders without canonical session identity`() = runTest {
        val detector = TaskMailMessageDetector()
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    detectedMessage(
                        detector = detector,
                        threadRootId = 201L,
                        messageServerId = "msg-local",
                        timestamp = 100L,
                        subject = "[CX] phase3-workspace-refresh-A73D2C9F11",
                        rawBodyText = """
                            Repo: E:\projects\android_task_manager

                            Task:
                            Ask exactly one question that requires me to reply with the exact token A73D2C9F11.
                        """.trimIndent(),
                        isFromCurrentUser = true,
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskWorkspaceSummaries()

        assertThat(result).hasSize(0)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `getTaskWorkspaceSummaries should keep only canonical session when placeholder request and status mail coexist`() = runTest {
        val detector = TaskMailMessageDetector()
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    detectedMessage(
                        detector = detector,
                        threadRootId = 201L,
                        messageServerId = "msg-local",
                        timestamp = 100L,
                        subject = "[CX] phase3-workspace-refresh-A73D2C9F11",
                        rawBodyText = """
                            Repo: E:\projects\android_task_manager

                            Task:
                            Ask exactly one question that requires me to reply with the exact token A73D2C9F11.
                        """.trimIndent(),
                        isFromCurrentUser = true,
                    ),
                    questionMessage(
                        threadRootId = 301L,
                        messageServerId = "msg-system",
                        timestamp = 200L,
                        sessionId = "thread_089",
                        workspaceId = "workspace_cb2404bf828c",
                        sessionName = "phase3-workspace-refresh-A73D2C9F11",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "",
                        lastSummary = "Please reply with the exact token A73D2C9F11.",
                        questionText = "Please reply with the exact token A73D2C9F11.",
                        choices = emptyList(),
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskWorkspaceSummaries()

        assertThat(result).hasSize(1)
        assertThat(result.single().sessionCount).isEqualTo(1)
        assertThat(result.single().sessions.single().key.threadId).isEqualTo("thread-301")
        assertThat(result.single().sessions.single().status).isEqualTo(TaskMailSessionStatus.WaitingUser)
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

    @Test
    fun `getTaskSessionDetail falls back to clean summary when capsule summary is machine generated`() = runTest {
        val pollutedSummary = listOf(
            "Status: DONE",
            "Session ID: thread-100",
            "Thread ID: thread-100",
            "Task ID: task-001",
            "Backend: codex",
        ).joinToString(" ")
        val cleanSummary = "Please review the current session first, then continue the regression pass."
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(machineSummaryMessage(pollutedSummary, cleanSummary)),
            ),
        )

        val result = testSubject.getTaskSessionDetail(TaskSessionKey(sessionId = "session-1", threadId = "thread-100"))

        assertThat(result).isNotNull()
        val detail = result!!
        assertThat(detail.lastSummary).isEqualTo(cleanSummary)
        assertThat(detail.timeline.single().summary).isEqualTo(cleanSummary)
        assertThat(detail.timeline.single().body.plainText).isEqualTo(cleanSummary)
    }

    @Test
    fun `getTaskSessionDetail keeps paused state metadata from the latest state capsule`() = runTest {
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
                        status = TaskMailSessionStatus.Paused,
                        pausedFromStatus = TaskMailSessionStatus.Done,
                        lastSummary = "Session paused after the last completed run.",
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskSessionDetail(TaskSessionKey(sessionId = "session-1", threadId = "thread-100"))

        assertThat(result).isNotNull()
        val detail = result!!
        assertThat(detail.status).isEqualTo(TaskMailSessionStatus.Paused)
        assertThat(detail.pausedFromStatus).isEqualTo(TaskMailSessionStatus.Done)
    }

    @Test
    fun `getTaskSessionDetail should keep reply like done mails outgoing and preserve latest done reply`() = runTest {
        val detector = TaskMailMessageDetector()
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = thread026TimelineMessages(detector),
            ),
        )

        val result = testSubject.getTaskSessionDetail(TaskSessionKey(sessionId = "thread_026", threadId = "thread_026"))

        assertThat(result).isNotNull()
        val detail = result!!
        assertThat(detail.status).isEqualTo(TaskMailSessionStatus.Done)
        assertThat(detail.repoPath).isEqualTo("E:\\projects\\test_folder_for_task_manager")
        assertThat(detail.lastSummary).isEqualTo("1. Hi")
        assertThat(detail.timeline.map { it.direction }).containsExactly(
            TaskTimelineDirection.Outgoing,
            TaskTimelineDirection.System,
        )
        assertThat(detail.timeline.first().statusLabel).isNull()
        assertThat(detail.timeline.first().body.plainText).isEqualTo("Say Ho")
        assertThat(detail.timeline.last().statusLabel).isEqualTo(TaskMailStatusLabel.Done)
        assertThat(detail.timeline.last().body.plainText).isEqualTo("1. Hi 2. Ho 3. Aha 4. Oho")
    }

    @Test
    fun `getTaskSessionDetail should collapse duplicate timeline copies and keep fuller body`() = runTest {
        val detector = TaskMailMessageDetector()
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = duplicateThread026Messages(detector),
            ),
        )

        val result = testSubject.getTaskSessionDetail(TaskSessionKey(sessionId = "thread_026", threadId = "thread_026"))

        assertThat(result).isNotNull()
        val detail = result!!
        assertThat(detail.timeline.map { it.direction }).containsExactly(
            TaskTimelineDirection.Outgoing,
            TaskTimelineDirection.System,
        )
        assertThat(detail.timeline.first().body.plainText).isEqualTo(
            "把你的工作目录输出给我看，顺便把工作目录下的文件输出出来，做成列表",
        )
        assertThat(detail.timeline.last().body.plainText).isEqualTo(
            """
            **工作目录：** `E:\projects\test_folder_for_task_manager`

            **文件列表：**
            1. `_mailin_20260314_164903_001.png`
            2. `attachment_check.md`
            3. `codex_outgoing_note_1.md`
            """.trimIndent(),
        )
    }

    @Test
    fun `getTaskSessionDetail should collapse copies sharing internet message id`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    systemMessage(
                        threadRootId = 300L,
                        messageServerId = "msg-system",
                        timestamp = 1_763_573_040_000L,
                        sessionId = "session-message-id",
                        workspaceId = "workspace-message-id",
                        sessionName = "Message id smoke",
                        repoPath = "E:/projects/test_folder_for_task_manager",
                        workdir = "",
                        status = TaskMailSessionStatus.Done,
                        lastSummary = "Message-ID dedupe completed successfully.",
                    ),
                    userMessage(
                        threadRootId = 300L,
                        messageServerId = "msg-user-inbox-copy",
                        timestamp = 1_763_573_000_000L,
                        subject = "Re: [DONE][S:session-message-id] Message id smoke",
                        rawBodyText = "现在我想看这些文件的文件大小",
                        sessionIdFromSubject = "session-message-id",
                        internetMessageId = "<message-id-smoke@example.com>",
                    ),
                    userMessage(
                        threadRootId = 301L,
                        messageServerId = "msg-user-sent-copy",
                        timestamp = 1_763_573_000_000L,
                        subject = "Re: [DONE] [S:session-message-id] [OC] Message id smoke",
                        rawBodyText = "现在我想看这些文件的文件大小",
                        sessionIdFromSubject = "session-message-id",
                        internetMessageId = "<message-id-smoke@example.com>",
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskSessionDetail(
            TaskSessionKey(sessionId = "session-message-id", threadId = "thread-300"),
        )

        assertThat(result).isNotNull()
        val detail = result!!
        assertThat(detail.timeline.map { it.direction }).containsExactly(
            TaskTimelineDirection.Outgoing,
            TaskTimelineDirection.System,
        )
        assertThat(detail.timeline.first().body.plainText).isEqualTo("现在我想看这些文件的文件大小")
    }

    @Test
    fun `getTaskSessionDetail should collapse closely timed duplicate outgoing attachments`() = runTest {
        val attachment = TaskMessageAttachment(
            id = "attachment-1",
            displayName = "05_two_full_clf_top_shap.csv",
            contentType = "text/comma-separated-values",
            sizeBytes = 295,
        )
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    systemMessage(
                        threadRootId = 200L,
                        messageServerId = "msg-system",
                        timestamp = 1_763_573_000_000L,
                        sessionId = "session-attachments",
                        workspaceId = "workspace-attachments",
                        sessionName = "Attachment smoke",
                        repoPath = "E:/projects/test_folder_for_task_manager",
                        workdir = "",
                        status = TaskMailSessionStatus.Done,
                        lastSummary = "Attachment processed successfully.",
                    ),
                    userMessage(
                        threadRootId = 200L,
                        messageServerId = "msg-user-preview",
                        timestamp = 1_763_572_160_000L,
                        subject = "Re: [DONE][S:session-attachments] Attachment smoke",
                        rawBodyText = "可以看到新增的这个文件吗？",
                        sessionIdFromSubject = "session-attachments",
                        attachments = listOf(attachment),
                    ),
                    userMessage(
                        threadRootId = 201L,
                        messageServerId = "msg-user-full",
                        timestamp = 1_763_572_168_000L,
                        subject = "Re: [DONE][S:session-attachments] Attachment smoke",
                        rawBodyText = "可以看到新增的这个文件吗？",
                        sessionIdFromSubject = "session-attachments",
                        attachments = listOf(attachment),
                    ),
                ),
            ),
        )

        val result = testSubject.getTaskSessionDetail(
            TaskSessionKey(sessionId = "session-attachments", threadId = "thread-200"),
        )

        assertThat(result).isNotNull()
        val detail = result!!
        assertThat(detail.timeline.map { it.direction }).containsExactly(
            TaskTimelineDirection.Outgoing,
            TaskTimelineDirection.System,
        )
        assertThat(detail.timeline.first().attachments).hasSize(1)
        assertThat(detail.timeline.first().attachments.single().displayName).isEqualTo("05_two_full_clf_top_shap.csv")
    }

    private class FakeTaskMailMessageSource(
        private val messages: List<TaskMailMessage>,
    ) : TaskMailMessageSource {
        override suspend fun getMessages(): List<TaskMailMessage> = messages
    }
}

@Suppress("LongParameterList")
private fun detectedMessage(
    detector: TaskMailMessageDetector,
    threadRootId: Long,
    messageServerId: String,
    timestamp: Long,
    subject: String,
    rawBodyText: String,
    isFromCurrentUser: Boolean,
    internetMessageId: String? = null,
    accountUuid: String = "account-1",
): TaskMailMessage {
    val detection = detector.detect(
        TaskMailEnvelope(
            messageId = messageServerId,
            subject = subject,
            fromAddress = if (isFromCurrentUser) "user@example.com" else "assistant@example.com",
            timestamp = timestamp,
            plainTextBody = rawBodyText,
        ),
    )

    return TaskMailMessage(
        accountUuid = accountUuid,
        folderId = 1L,
        messageServerId = messageServerId,
        threadRootId = threadRootId,
        timestamp = timestamp,
        subject = subject,
        rawBodyText = rawBodyText,
        internetMessageId = internetMessageId,
        detection = detection,
        isFromCurrentUser = isFromCurrentUser,
    )
}

private fun thread026TimelineMessages(detector: TaskMailMessageDetector): List<TaskMailMessage> {
    return listOf(
        detectedMessage(
            detector = detector,
            threadRootId = 26L,
            messageServerId = "msg-95",
            timestamp = 1_763_018_181_000L,
            subject = "Re: [DONE][S:thread_026] 时间线测试",
            rawBodyText = """
                Say Ho

                -----Original Message-----
                From: Task_runner <assistant@example.com>
                Sent: 2026-03-15 14:15:50
                To: user@example.com
                Subject: [DONE][S:thread_026] 时间线测试

                Reply:
                1. Hi

                ---TASK-STATE-BEGIN---
                thread_id: thread_026
                session_id: thread_026
                backend: opencode
                status: done
                ---TASK-STATE-END---
            """.trimIndent(),
            isFromCurrentUser = true,
        ),
        detectedMessage(
            detector = detector,
            threadRootId = 26L,
            messageServerId = "msg-99",
            timestamp = 1_763_018_558_000L,
            subject = "[DONE][S:thread_026] 时间线测试",
            rawBodyText = """
                Status: DONE Reply: 1. Hi 2. Ho 3. Aha 4. Oho

                ---TASK-STATE-BEGIN--- thread_id: thread_026 workspace_id: workspace_1ed1d100687a session_id: thread_026 session_name: 时间线测试 task_id: 20260315_142209_86ff backend: opencode repo_path: E:\projects\test_folder_for_task_manager workdir: mode: modify status: done last_summary: 1. Hi ---TASK-STATE-END---
            """.trimIndent(),
            isFromCurrentUser = false,
        ),
    )
}

private fun duplicateThread026Messages(detector: TaskMailMessageDetector): List<TaskMailMessage> {
    return duplicateThread026UserMessages(detector) + duplicateThread026SystemMessages(detector)
}

private fun duplicateThread026UserMessages(detector: TaskMailMessageDetector): List<TaskMailMessage> {
    return listOf(
        detectedMessage(
            detector = detector,
            threadRootId = 26L,
            messageServerId = "msg-96-preview",
            timestamp = 1_763_572_306_000L,
            subject = "Re: [DONE][S:thread_026] 时间线测试",
            rawBodyText = THREAD_026_DUPLICATE_REQUEST_BODY,
            isFromCurrentUser = true,
        ),
        detectedMessage(
            detector = detector,
            threadRootId = 27L,
            messageServerId = "msg-96-full",
            timestamp = 1_763_572_306_000L,
            subject = "Re: [DONE][S:thread_026] 时间线测试",
            rawBodyText = THREAD_026_DUPLICATE_REQUEST_BODY,
            isFromCurrentUser = true,
        ),
    )
}

private fun duplicateThread026SystemMessages(detector: TaskMailMessageDetector): List<TaskMailMessage> {
    return listOf(
        detectedMessage(
            detector = detector,
            threadRootId = 26L,
            messageServerId = "msg-97-preview",
            timestamp = 1_763_572_344_000L,
            subject = "[DONE][S:thread_026] 时间线测试",
            rawBodyText = duplicateThread026PreviewSystemBody(),
            isFromCurrentUser = false,
        ),
        detectedMessage(
            detector = detector,
            threadRootId = 27L,
            messageServerId = "msg-97-full",
            timestamp = 1_763_572_344_000L,
            subject = "[DONE][S:thread_026] 时间线测试",
            rawBodyText = duplicateThread026FullSystemBody(),
            isFromCurrentUser = false,
        ),
    )
}

private fun duplicateThread026PreviewSystemBody(): String {
    return """
        Status: DONE
        Session ID: thread_026
        Thread ID: thread_026
        Task ID: 20260315_183156_62b3
        Backend: opencode
        Repo: E:\projects\test_folder_for_task_manager
        Workdir:
        Exit Code: 0

        Reply:
        **工作目录：** `E:\projects\test_folder_for_task_manager`

        **文件列表：**
        1. `_mailin_20260314_164903_001.png`
        2. `attachment_check.md`
        3...
    """.trimIndent()
}

private fun duplicateThread026FullSystemBody(): String {
    return """
        Status: DONE
        Session ID: thread_026
        Thread ID: thread_026
        Task ID: 20260315_183156_62b3
        Backend: opencode
        Repo: E:\projects\test_folder_for_task_manager
        Workdir:
        Exit Code: 0

        Reply:
        **工作目录：** `E:\projects\test_folder_for_task_manager`

        **文件列表：**
        1. `_mailin_20260314_164903_001.png`
        2. `attachment_check.md`
        3. `codex_outgoing_note_1.md`

        ---TASK-STATE-BEGIN---
        thread_id: thread_026
        workspace_id: workspace_1ed1d100687a
        session_id: thread_026
        session_name: 时间线测试
        task_id: 20260315_183156_62b3
        backend: opencode
        repo_path: E:\projects\test_folder_for_task_manager
        workdir:
        mode: modify
        status: done
        last_summary: **文件列表：**
        ---TASK-STATE-END---
    """.trimIndent()
}

private const val THREAD_026_DUPLICATE_REQUEST_BODY = "把你的工作目录输出给我看，顺便把工作目录下的文件输出出来，做成列表"

private fun machineSummaryMessage(
    pollutedSummary: String,
    cleanSummary: String,
): TaskMailMessage {
    return TaskMailMessage(
        accountUuid = "account-1",
        folderId = 1L,
        messageServerId = "msg-system",
        threadRootId = 100L,
        timestamp = 100L,
        subject = "[DONE] [CX] [S:session-1] Implement parser",
        rawBodyText = """
            Status: DONE Session ID: thread-100 Thread ID: thread-100 Task ID: task-001 Backend: codex Repo: E:\projects\android_task_manager Workdir: feature\taskmail

            $cleanSummary

            ---TASK-STATE-BEGIN--- thread_id: thread-100 workspace_id: workspace-1 session_id: session-1 session_name: Implement parser task_id: task-001 backend: codex repo_path: E:\projects\android_task_manager workdir: feature\taskmail mode: modify status: done last_summary: $pollutedSummary ---TASK-STATE-END---
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
                threadId = "thread-100",
                workspaceId = "workspace-1",
                sessionId = "session-1",
                sessionName = "Implement parser",
                taskId = "task-001",
                backend = TaskMailBackend.Codex,
                repoPath = "E:/projects/android_task_manager",
                workdir = "feature/taskmail",
                mode = "modify",
                status = TaskMailSessionStatus.Done,
                lastSummary = pollutedSummary,
            ),
        ),
        isFromCurrentUser = false,
    )
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
    pausedFromStatus: TaskMailSessionStatus? = null,
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
            ${pausedFromStatus?.let { "paused_from_status: ${it.name.lowercase()}" } ?: ""}
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
                pausedFromStatus = pausedFromStatus,
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
    htmlBody: String? = null,
    sessionIdFromSubject: String? = "session-1",
    internetMessageId: String? = null,
    attachments: List<TaskMessageAttachment> = emptyList(),
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
        htmlBody = htmlBody,
        internetMessageId = internetMessageId,
        attachments = attachments,
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

package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailEnvelope
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailMessageDetector

class DefaultTaskMailRepositoryBusinessEventKeyTest {
    private val detector = TaskMailMessageDetector()

    @Test
    fun `terminal mail should expose reply and terminal business keys`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    detectedSystemMessage(
                        messageServerId = "msg-terminal",
                        timestamp = 1_763_596_560_000L,
                        subject = "[DONE][S:session_001] Phase 3 detail bridge",
                        rawBodyText = """
                            Status: DONE
                            Summary: Completed successfully.
                            Reply:
                            Completed successfully.

                            ---TASK-STATE-BEGIN---
                            thread_id: thread_001
                            workspace_id: workspace_001
                            session_id: session_001
                            session_name: Phase 3 detail bridge
                            task_id: task_001
                            backend: codex
                            repo_path: E:\projects\android_task_manager
                            workdir: feature/taskmail/internal
                            status: done
                            last_progress_at: 2026-03-21T22:46:03
                            last_summary: Completed successfully.
                            ---TASK-STATE-END---
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        val detail = testSubject.getTaskSessionDetail(
            key = TaskSessionKey(
                sessionId = "session_001",
                threadId = "thread_001",
            ),
        )

        val businessEventKeys = detail?.timeline?.single()?.businessEventKeys.orEmpty()
        assertThat(businessEventKeys).contains("reply/2026-03-21T22:46:03")
        assertThat(businessEventKeys).contains("terminal/done/2026-03-21T22:46:03")
    }

    @Test
    @Suppress("LongMethod")
    fun `question and paused mail should expose business keys`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    detectedSystemMessage(
                        messageServerId = "msg-question",
                        timestamp = 1_763_596_620_000L,
                        subject = "[QUESTION][S:session_001] Phase 3 detail bridge",
                        rawBodyText = """
                            Status: QUESTION
                            Summary: Need one answer before continuing.
                            Question: Which branch should I use?
                            Choices: main | release

                            ---TASK-STATE-BEGIN---
                            thread_id: thread_001
                            workspace_id: workspace_001
                            session_id: session_001
                            session_name: Phase 3 detail bridge
                            task_id: task_001
                            backend: codex
                            repo_path: E:\projects\android_task_manager
                            workdir: feature/taskmail/internal
                            status: awaiting_user_input
                            last_progress_at: 2026-03-21T22:47:03
                            last_summary: Need one answer before continuing.
                            ---TASK-STATE-END---

                            ---TASK-QUESTION-BEGIN---
                            question_set_id: qset_branch_choice
                            question_id: q_branch
                            question_type: single_choice
                            required: true
                            question_text: Which branch should I use?
                            choices: main | release
                            choice_labels: main=Main branch | release=Release branch
                            ---TASK-QUESTION-END---
                        """.trimIndent(),
                    ),
                    detectedSystemMessage(
                        messageServerId = "msg-paused",
                        timestamp = 1_763_596_680_000L,
                        subject = "[PAUSED][S:session_001] Phase 3 detail bridge",
                        rawBodyText = """
                            Status: PAUSED
                            Summary: Waiting for your answer before continuing.
                            Paused From: awaiting_user_input

                            ---TASK-STATE-BEGIN---
                            thread_id: thread_001
                            workspace_id: workspace_001
                            session_id: session_001
                            session_name: Phase 3 detail bridge
                            task_id: task_001
                            backend: codex
                            repo_path: E:\projects\android_task_manager
                            workdir: feature/taskmail/internal
                            status: paused
                            paused_from_status: awaiting_user_input
                            last_progress_at: 2026-03-21T22:48:03
                            last_summary: Waiting for your answer before continuing.
                            ---TASK-STATE-END---
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        val detail = testSubject.getTaskSessionDetail(
            key = TaskSessionKey(
                sessionId = "session_001",
                threadId = "thread_001",
            ),
        )

        assertThat(detail?.workspace?.workspaceId).isEqualTo("workspace_001")
        assertThat(detail?.workspace?.repoPath).isEqualTo("E:/projects/android_task_manager")
        assertThat(detail?.workspace?.workdir).isEqualTo("feature/taskmail/internal")
        val businessEventKeys = detail?.timeline.orEmpty().flatMap { item -> item.businessEventKeys }
        assertThat(businessEventKeys).contains("question/qset_branch_choice/2026-03-21T22:47:03")
        assertThat(businessEventKeys).contains("paused/awaiting_user_input/2026-03-21T22:48:03")
        assertThat(businessEventKeys).contains("status/awaiting_user_input/2026-03-21T22:47:03")
        assertThat(businessEventKeys).contains("status/paused/2026-03-21T22:48:03")
    }

    private fun detectedSystemMessage(
        messageServerId: String,
        timestamp: Long,
        subject: String,
        rawBodyText: String,
    ): TaskMailMessage {
        val detection = detector.detect(
            TaskMailEnvelope(
                messageId = messageServerId,
                subject = subject,
                fromAddress = "assistant@example.com",
                timestamp = timestamp,
                plainTextBody = rawBodyText,
            ),
        )

        return TaskMailMessage(
            accountUuid = "account-1",
            folderId = 1L,
            messageServerId = messageServerId,
            threadRootId = 1L,
            timestamp = timestamp,
            subject = subject,
            rawBodyText = rawBodyText,
            detection = detection,
            isFromCurrentUser = false,
        )
    }

    private class FakeTaskMailMessageSource(
        private val messages: List<TaskMailMessage>,
    ) : TaskMailMessageSource {
        override suspend fun getMessages(): List<TaskMailMessage> = messages
    }
}

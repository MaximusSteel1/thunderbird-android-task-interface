package net.thunderbird.feature.taskmail.internal.domain.parser

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel

class TaskMailMessageDetectorTest {

    private val testSubject = TaskMailMessageDetector()

    @Test
    fun `detect should mark new task subject as task mail user message`() {
        // Arrange
        val envelope = TaskMailEnvelope(
            messageId = "message-1",
            subject = "[OC] Refactor floor_shear",
            fromAddress = "user@example.com",
            timestamp = 1L,
            plainTextBody = "Please refactor floor_shear.",
        )

        // Act
        val result = testSubject.detect(envelope)

        // Assert
        assertThat(result).isEqualTo(
            TaskMailDetection(
                isTaskMail = true,
                isSystemMessage = false,
                parsedSubject = TaskMailParsedSubject(
                    backend = TaskMailBackend.OpenCode,
                    statusLabel = null,
                    sessionIdFromSubject = null,
                    subjectText = "Refactor floor_shear",
                    isReplyLike = false,
                ),
                stateCapsule = null,
                questionCapsule = null,
            ),
        )
    }

    @Test
    fun `detect should keep sync bootstrap mail outside task session projection`() {
        // Arrange
        val envelope = TaskMailEnvelope(
            messageId = "message-sync",
            subject = "[SYNC] Project Folder List",
            fromAddress = "assistant@example.com",
            timestamp = 1L,
            plainTextBody = """
                D:\projects
                - android_task_manager
                - mail_based_task_manager
            """.trimIndent(),
        )

        // Act
        val result = testSubject.detect(envelope)

        // Assert
        assertThat(result).isEqualTo(
            TaskMailDetection(
                isTaskMail = false,
                isSystemMessage = false,
                parsedSubject = TaskMailParsedSubject(
                    backend = null,
                    statusLabel = null,
                    sessionIdFromSubject = null,
                    subjectText = "[SYNC] Project Folder List",
                    isReplyLike = false,
                ),
                stateCapsule = null,
                questionCapsule = null,
            ),
        )
    }

    @Test
    fun `detect should mark state capsule message as task mail system message`() {
        // Arrange
        val envelope = TaskMailEnvelope(
            messageId = "message-2",
            subject = "Build update",
            fromAddress = "assistant@example.com",
            timestamp = 2L,
            plainTextBody = """
                Status: done
                
                ---TASK-STATE-BEGIN---
                thread_id: thread_001
                session_id: session_001
                backend: codex
                status: done
                ---TASK-STATE-END---
            """.trimIndent(),
        )

        // Act
        val result = testSubject.detect(envelope)

        // Assert
        assertThat(result).isEqualTo(
            TaskMailDetection(
                isTaskMail = true,
                isSystemMessage = true,
                parsedSubject = TaskMailParsedSubject(
                    backend = null,
                    statusLabel = null,
                    sessionIdFromSubject = null,
                    subjectText = "Build update",
                    isReplyLike = false,
                ),
                stateCapsule = TaskStateCapsule(
                    threadId = "thread_001",
                    sessionId = "session_001",
                    backend = TaskMailBackend.Codex,
                    status = TaskMailSessionStatus.Done,
                ),
                questionCapsule = null,
            ),
        )
    }

    @Test
    fun `detect should preserve question capsule from system message`() {
        // Arrange
        val envelope = TaskMailEnvelope(
            messageId = "message-3",
            subject = "[QUESTION][S:session_001] [CX] Analyze floor_shear",
            fromAddress = "assistant@example.com",
            timestamp = 3L,
            plainTextBody = """
                ---TASK-QUESTION-BEGIN---
                question_id: question_001
                question_text: Should I update both files?
                choices: yes | no
                ---TASK-QUESTION-END---
            """.trimIndent(),
        )

        // Act
        val result = testSubject.detect(envelope)

        // Assert
        assertThat(result).isEqualTo(
            TaskMailDetection(
                isTaskMail = true,
                isSystemMessage = true,
                parsedSubject = TaskMailParsedSubject(
                    backend = TaskMailBackend.Codex,
                    statusLabel = TaskMailStatusLabel.Question,
                    sessionIdFromSubject = "session_001",
                    subjectText = "Analyze floor_shear",
                    isReplyLike = false,
                ),
                stateCapsule = null,
                questionCapsule = TaskQuestionCapsule(
                    questionId = "question_001",
                    questionText = "Should I update both files?",
                    choices = listOf("yes", "no"),
                    questionType = "single_choice",
                ),
            ),
        )
    }

    @Test
    fun `detect should preserve all question capsules from a multi-question system message`() {
        // Arrange
        val envelope = TaskMailEnvelope(
            messageId = "message-5",
            subject = "[QUESTION][S:session_001] [CX] Analyze floor_shear",
            fromAddress = "assistant@example.com",
            timestamp = 5L,
            plainTextBody = """
                ---TASK-QUESTION-BEGIN---
                question_set_id: phase2_clarifications
                question_id: phase2_entry_position
                question_type: single_choice
                required: true
                question_text: Where should the Tasks drawer entry be placed?
                choices: top | below | section
                ---TASK-QUESTION-END---
                ---TASK-QUESTION-BEGIN---
                question_set_id: phase2_clarifications
                question_id: phase2_icon_strings
                question_type: single_choice
                required: false
                question_text: Who provides icon and string resources?
                choices: provide | reuse | placeholder
                ---TASK-QUESTION-END---
            """.trimIndent(),
        )

        // Act
        val result = testSubject.detect(envelope)

        // Assert
        assertThat(result.questionCapsules).isEqualTo(
            listOf(
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
        )
        assertThat(result.questionCapsule?.questionId).isEqualTo("phase2_icon_strings")
    }

    @Test
    fun `detect should keep reply like done mail as user message even when quoted capsules are present`() {
        // Arrange
        val envelope = TaskMailEnvelope(
            messageId = "message-6",
            subject = "Re: [DONE][S:thread_026] Timeline test",
            fromAddress = "user@example.com",
            timestamp = 6L,
            plainTextBody = """
                Say Ho

                -----Original Message-----
                From: Task_runner <assistant@example.com>
                Sent: 2026-03-15 14:15:50
                To: user@example.com
                Subject: [DONE][S:thread_026] Timeline test

                Reply:
                1. Hi

                ---TASK-STATE-BEGIN---
                thread_id: thread_026
                session_id: thread_026
                backend: opencode
                status: done
                ---TASK-STATE-END---
            """.trimIndent(),
        )

        // Act
        val result = testSubject.detect(envelope)

        // Assert
        assertThat(result).isEqualTo(
            TaskMailDetection(
                isTaskMail = true,
                isSystemMessage = false,
                parsedSubject = TaskMailParsedSubject(
                    backend = null,
                    statusLabel = TaskMailStatusLabel.Done,
                    sessionIdFromSubject = "thread_026",
                    subjectText = "Timeline test",
                    isReplyLike = true,
                ),
                stateCapsule = null,
                questionCapsule = null,
            ),
        )
        assertThat(result.questionCapsules).isEqualTo(emptyList())
    }

    @Test
    fun `detect should ignore ordinary mail without task markers`() {
        // Arrange
        val envelope = TaskMailEnvelope(
            messageId = "message-4",
            subject = "Weekly planning notes",
            fromAddress = "user@example.com",
            timestamp = 4L,
            plainTextBody = "Agenda for next week.",
        )

        // Act
        val result = testSubject.detect(envelope)

        // Assert
        assertThat(result).isEqualTo(
            TaskMailDetection(
                isTaskMail = false,
                isSystemMessage = false,
                parsedSubject = TaskMailParsedSubject(
                    backend = null,
                    statusLabel = null,
                    sessionIdFromSubject = null,
                    subjectText = "Weekly planning notes",
                    isReplyLike = false,
                ),
                stateCapsule = null,
                questionCapsule = null,
            ),
        )
    }
}

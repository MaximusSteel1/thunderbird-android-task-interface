package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isNotNull
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailDetection
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailParsedSubject
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskStateCapsule
import org.junit.Test

internal class DefaultTaskMailRepositoryAttachmentProjectionTest {

    @Test
    fun `getTaskSessionDetail deduplicates inline attachments before building timeline`() = runTest {
        val testSubject = DefaultTaskMailRepository(
            messageSource = FakeTaskMailMessageSource(
                messages = listOf(
                    systemMessageWithAttachments(
                        attachments = duplicatedTimelineAttachments(),
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
        assertThat(result!!.timeline.single().attachments).containsExactly(expectedDeduplicatedAttachment())
    }

    private fun duplicatedTimelineAttachments(): List<TaskMessageAttachment> {
        return listOf(
            TaskMessageAttachment(
                id = "attachment-fallback",
                displayName = "taskmail-image-smoke.png",
                contentType = "image/png",
                sizeBytes = 2_048L,
                isImage = true,
                contentId = "chart-preview",
                partId = 42L,
            ),
            expectedDeduplicatedAttachment(),
        )
    }

    private fun expectedDeduplicatedAttachment(): TaskMessageAttachment {
        return TaskMessageAttachment(
            id = "content://taskmail/chart-preview",
            displayName = "taskmail-image-smoke.png",
            contentType = "image/png",
            sizeBytes = 2_048L,
            isInline = true,
            isImage = true,
            contentId = "chart-preview",
            internalUriString = "content://taskmail/chart-preview",
            partId = 42L,
            isContentAvailable = true,
        )
    }

    private fun systemMessageWithAttachments(
        attachments: List<TaskMessageAttachment>,
    ): TaskMailMessage {
        return TaskMailMessage(
            accountUuid = "account-1",
            folderId = 1L,
            messageServerId = "msg-system",
            threadRootId = 100L,
            timestamp = 100L,
            subject = "[DONE] [CX] [S:session-1] Implement parser",
            rawBodyText = "Attached the generated chart.",
            attachments = attachments,
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
                    backend = TaskMailBackend.Codex,
                    repoPath = "E:/projects/android_task_manager",
                    workdir = "feature/taskmail",
                    status = TaskMailSessionStatus.Done,
                    lastSummary = "Attached the generated chart.",
                ),
            ),
            isFromCurrentUser = false,
        )
    }

    private class FakeTaskMailMessageSource(
        private val messages: List<TaskMailMessage>,
    ) : TaskMailMessageSource {
        override suspend fun getMessages(): List<TaskMailMessage> = messages
    }
}

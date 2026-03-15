package net.thunderbird.feature.taskmail.internal.domain.usecase

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyKind
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyResult
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplySender

class SendTaskMailReplyTest {

    @Test
    fun `sendFreeText should send raw draft text only`() = runTest {
        // Arrange
        val sender = FakeTaskMailReplySender()
        val testSubject = SendTaskMailReply(sender)

        // Act
        val result = testSubject.sendFreeText(
            context = replyContext(),
            draftText = "  keep whitespace  ",
        )

        // Assert
        assertThat(result).isEqualTo(TaskMailReplyResult.success())
        assertThat(sender.requests).hasSize(1)
        assertThat(sender.requests.single()).isEqualTo(
            TaskMailReplyRequest(
                context = replyContext(),
                body = "  keep whitespace  ",
                kind = TaskMailReplyKind.FreeText,
            ),
        )
    }

    @Test
    fun `sendQuestionChoice should send selected choice text only`() = runTest {
        // Arrange
        val sender = FakeTaskMailReplySender()
        val testSubject = SendTaskMailReply(sender)

        // Act
        val result = testSubject.sendQuestionChoice(
            context = replyContext(),
            choice = "yes",
        )

        // Assert
        assertThat(result).isEqualTo(TaskMailReplyResult.success())
        assertThat(sender.requests).hasSize(1)
        assertThat(sender.requests.single()).isEqualTo(
            TaskMailReplyRequest(
                context = replyContext(),
                body = "yes",
                kind = TaskMailReplyKind.QuestionChoice,
            ),
        )
    }

    @Test
    fun `sendStructuredAnswers should send structured draft text only`() = runTest {
        // Arrange
        val sender = FakeTaskMailReplySender()
        val testSubject = SendTaskMailReply(sender)

        // Act
        val result = testSubject.sendStructuredAnswers(
            context = replyContext(),
            draftText = "Answers:\nphase2_entry_position: below",
        )

        // Assert
        assertThat(result).isEqualTo(TaskMailReplyResult.success())
        assertThat(sender.requests).hasSize(1)
        assertThat(sender.requests.single()).isEqualTo(
            TaskMailReplyRequest(
                context = replyContext(),
                body = "Answers:\nphase2_entry_position: below",
                kind = TaskMailReplyKind.StructuredAnswers,
            ),
        )
    }

    @Test
    fun `sendStatusQuery should send slash status body`() = runTest {
        // Arrange
        val sender = FakeTaskMailReplySender()
        val testSubject = SendTaskMailReply(sender)

        // Act
        val result = testSubject.sendStatusQuery(replyContext())

        // Assert
        assertThat(result).isEqualTo(TaskMailReplyResult.success())
        assertThat(sender.requests).hasSize(1)
        assertThat(sender.requests.single()).isEqualTo(
            TaskMailReplyRequest(
                context = replyContext(),
                body = "/status",
                kind = TaskMailReplyKind.StatusQuery,
            ),
        )
    }

    @Test
    fun `sendFreeText should return sender failure result`() = runTest {
        // Arrange
        val sender = FakeTaskMailReplySender(
            result = TaskMailReplyResult.failure("send failed"),
        )
        val testSubject = SendTaskMailReply(sender)

        // Act
        val result = testSubject.sendFreeText(
            context = replyContext(),
            draftText = "hello",
        )

        // Assert
        assertThat(result).isEqualTo(TaskMailReplyResult.failure("send failed"))
    }

    @Test
    fun `sendFreeText should include selected attachments`() = runTest {
        // Arrange
        val sender = FakeTaskMailReplySender()
        val testSubject = SendTaskMailReply(sender)
        val attachments = listOf(
            TaskReplyAttachment(
                id = "content://taskmail/report",
                uriString = "content://taskmail/report",
                displayName = "report.md",
                contentType = "text/markdown",
                sizeBytes = 512L,
            ),
        )

        // Act
        val result = testSubject.sendFreeText(
            context = replyContext(),
            draftText = "",
            attachments = attachments,
        )

        // Assert
        assertThat(result).isEqualTo(TaskMailReplyResult.success())
        assertThat(sender.requests.single().attachments).isEqualTo(attachments)
    }
}

private class FakeTaskMailReplySender(
    private val result: TaskMailReplyResult = TaskMailReplyResult.success(),
) : TaskMailReplySender {
    val requests = mutableListOf<TaskMailReplyRequest>()

    override suspend fun send(request: TaskMailReplyRequest): TaskMailReplyResult {
        requests += request
        return result
    }
}

private fun replyContext(): TaskSessionReplyContext {
    return TaskSessionReplyContext(
        accountUuid = "account-1",
        folderId = 1L,
        messageServerId = "msg-1",
        threadRootId = 100L,
        anchorTimestamp = 123L,
    )
}

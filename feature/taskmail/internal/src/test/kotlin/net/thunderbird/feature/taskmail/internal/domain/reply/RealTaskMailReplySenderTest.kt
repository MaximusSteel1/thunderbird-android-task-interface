package net.thunderbird.feature.taskmail.internal.domain.reply

import app.k9mail.legacy.message.controller.MessageReference
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.mail.internet.MimeMessage
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.logging.LogTag
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.TaskMailMimeMessageFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailMimeMessageSender
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplySourceMessage
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplySourceMessageLoader
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext

class RealTaskMailReplySenderTest {

    @Test
    fun `send should fail when source message is unavailable`() = runTest {
        val testSubject = RealTaskMailReplySender(
            sourceMessageLoader = FakeTaskMailReplySourceMessageLoader(sourceMessage = null),
            mimeMessageFactory = FakeTaskMailMimeMessageFactory(),
            mimeMessageSender = FakeTaskMailMimeMessageSender(),
            logger = FakeLogger(),
        )

        val result = testSubject.send(replyRequest())

        assertThat(result).isEqualTo(TaskMailReplyResult.failure("TaskMail reply is no longer available."))
    }

    @Test
    fun `send should fail when mime message creation fails`() = runTest {
        val testSubject = RealTaskMailReplySender(
            sourceMessageLoader = FakeTaskMailReplySourceMessageLoader(sourceMessage = sourceMessage()),
            mimeMessageFactory = FakeTaskMailMimeMessageFactory(
                result = Result.failure(IllegalStateException("boom")),
            ),
            mimeMessageSender = FakeTaskMailMimeMessageSender(),
            logger = FakeLogger(),
        )

        val result = testSubject.send(replyRequest())

        assertThat(result).isEqualTo(TaskMailReplyResult.failure("Failed to prepare TaskMail reply."))
    }

    @Test
    fun `send should pass built message to sender on success`() = runTest {
        val sourceMessage = sourceMessage()
        val fakeSender = FakeTaskMailMimeMessageSender()
        val mimeMessage = MimeMessage.create().apply { subject = "Re: Task" }
        val testSubject = RealTaskMailReplySender(
            sourceMessageLoader = FakeTaskMailReplySourceMessageLoader(sourceMessage = sourceMessage),
            mimeMessageFactory = FakeTaskMailMimeMessageFactory(result = Result.success(mimeMessage)),
            mimeMessageSender = fakeSender,
            logger = FakeLogger(),
        )

        val result = testSubject.send(replyRequest())

        assertThat(result).isEqualTo(TaskMailReplyResult.success())
        assertThat(fakeSender.sentAccount).isEqualTo(sourceMessage.account)
        assertThat(fakeSender.sentMessage).isEqualTo(mimeMessage)
        assertThat(fakeSender.sentPlaintextSubject).isEqualTo("Re: Task")
    }

    @Test
    fun `send should fail when sender throws`() = runTest {
        val testSubject = RealTaskMailReplySender(
            sourceMessageLoader = FakeTaskMailReplySourceMessageLoader(sourceMessage = sourceMessage()),
            mimeMessageFactory = FakeTaskMailMimeMessageFactory(
                result = Result.success(MimeMessage.create().apply { subject = "Re: Task" }),
            ),
            mimeMessageSender = FakeTaskMailMimeMessageSender(
                exception = IllegalStateException("send failed"),
            ),
            logger = FakeLogger(),
        )

        val result = testSubject.send(replyRequest())

        assertThat(result).isEqualTo(TaskMailReplyResult.failure("Failed to send TaskMail reply."))
    }
}

private class FakeTaskMailReplySourceMessageLoader(
    private val sourceMessage: TaskMailReplySourceMessage?,
) : TaskMailReplySourceMessageLoader {
    override suspend fun load(context: TaskSessionReplyContext): TaskMailReplySourceMessage? = sourceMessage
}

private class FakeTaskMailMimeMessageFactory(
    private val result: Result<MimeMessage> = Result.success(MimeMessage.create()),
) : TaskMailMimeMessageFactory {
    override suspend fun create(
        request: TaskMailReplyRequest,
        sourceMessage: TaskMailReplySourceMessage,
    ): Result<MimeMessage> = result
}

private class FakeTaskMailMimeMessageSender(
    private val exception: Throwable? = null,
) : TaskMailMimeMessageSender {
    var sentAccount: LegacyAccountDto? = null
    var sentMessage: MimeMessage? = null
    var sentPlaintextSubject: String? = null

    override suspend fun send(account: LegacyAccountDto, message: MimeMessage, plaintextSubject: String) {
        exception?.let { throw it }
        sentAccount = account
        sentMessage = message
        sentPlaintextSubject = plaintextSubject
    }
}

private class FakeLogger : Logger {
    override fun verbose(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun debug(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun info(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun warn(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun error(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
}

private fun replyRequest(): TaskMailReplyRequest {
    return TaskMailReplyRequest(
        context = TaskSessionReplyContext(
            accountUuid = TEST_ACCOUNT_UUID,
            folderId = 1L,
            messageServerId = "msg-1",
        ),
        body = "done",
        kind = TaskMailReplyKind.FreeText,
    )
}

private fun sourceMessage(): TaskMailReplySourceMessage {
    val account = LegacyAccountDto(uuid = TEST_ACCOUNT_UUID).apply {
        identities = mutableListOf(
            Identity(
                email = "user@example.org",
                signature = "sig",
            ),
        )
    }

    return TaskMailReplySourceMessage(
        account = account,
        message = MimeMessage.create().apply {
            subject = "Task"
        },
        messageReference = MessageReference(TEST_ACCOUNT_UUID, 1L, "msg-1"),
    )
}

private const val TEST_ACCOUNT_UUID = "11111111-1111-1111-1111-111111111111"

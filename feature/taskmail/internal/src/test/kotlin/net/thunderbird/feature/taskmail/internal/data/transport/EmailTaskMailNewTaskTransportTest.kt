package net.thunderbird.feature.taskmail.internal.data.transport

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.mail.internet.MimeMessage
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.logging.LogTag
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.TaskMailMimeMessageSender
import net.thunderbird.feature.taskmail.internal.data.TaskMailNewTaskMimeMessageFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailSenderAccountSource
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest

class EmailTaskMailNewTaskTransportTest {

    @Test
    fun `send should fail when sender account is unavailable`() = runTest {
        val testSubject = EmailTaskMailNewTaskTransport(
            senderAccountSource = NewTaskFakeTaskMailSenderAccountSource(account = null),
            mimeMessageFactory = NewTaskFakeTaskMailNewTaskMimeMessageFactory(),
            mimeMessageSender = NewTaskFakeTaskMailMimeMessageSender(),
            logger = NewTaskFakeLogger(),
        )

        val result = testSubject.send(newTaskRequest())

        assertThat(result.exceptionOrNull()?.message).isEqualTo("TaskMail sending account is no longer available.")
    }

    @Test
    fun `send should surface specific mime message creation failures`() = runTest {
        val testSubject = EmailTaskMailNewTaskTransport(
            senderAccountSource = NewTaskFakeTaskMailSenderAccountSource(account = senderAccount()),
            mimeMessageFactory = NewTaskFakeTaskMailNewTaskMimeMessageFactory(
                result = Result.failure(IllegalStateException("TaskMail bot mailbox is not configured.")),
            ),
            mimeMessageSender = NewTaskFakeTaskMailMimeMessageSender(),
            logger = NewTaskFakeLogger(),
        )

        val result = testSubject.send(newTaskRequest())

        assertThat(result.exceptionOrNull()?.message).isEqualTo("TaskMail bot mailbox is not configured.")
    }

    @Test
    fun `send should fall back to generic preparation failure when exception has no message`() = runTest {
        val testSubject = EmailTaskMailNewTaskTransport(
            senderAccountSource = NewTaskFakeTaskMailSenderAccountSource(account = senderAccount()),
            mimeMessageFactory = NewTaskFakeTaskMailNewTaskMimeMessageFactory(
                result = Result.failure(IllegalStateException("")),
            ),
            mimeMessageSender = NewTaskFakeTaskMailMimeMessageSender(),
            logger = NewTaskFakeLogger(),
        )

        val result = testSubject.send(newTaskRequest())

        assertThat(result.exceptionOrNull()?.message).isEqualTo("Failed to prepare TaskMail new-task message.")
    }

    @Test
    fun `send should pass built message to sender on success`() = runTest {
        val account = senderAccount()
        val fakeSender = NewTaskFakeTaskMailMimeMessageSender()
        val mimeMessage = MimeMessage.create().apply { subject = "[CX] Audit TaskMail" }
        val testSubject = EmailTaskMailNewTaskTransport(
            senderAccountSource = NewTaskFakeTaskMailSenderAccountSource(account = account),
            mimeMessageFactory = NewTaskFakeTaskMailNewTaskMimeMessageFactory(result = Result.success(mimeMessage)),
            mimeMessageSender = fakeSender,
            logger = NewTaskFakeLogger(),
        )

        val result = testSubject.send(newTaskRequest())

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(fakeSender.sentAccount).isEqualTo(account)
        assertThat(fakeSender.sentMessage).isEqualTo(mimeMessage)
        assertThat(fakeSender.sentPlaintextSubject).isEqualTo("[CX] Audit TaskMail")
    }

    @Test
    fun `send should fail when sender throws`() = runTest {
        val testSubject = EmailTaskMailNewTaskTransport(
            senderAccountSource = NewTaskFakeTaskMailSenderAccountSource(account = senderAccount()),
            mimeMessageFactory = NewTaskFakeTaskMailNewTaskMimeMessageFactory(
                result = Result.success(MimeMessage.create().apply { subject = "[CX] Audit TaskMail" }),
            ),
            mimeMessageSender = NewTaskFakeTaskMailMimeMessageSender(
                exception = IllegalStateException("send failed"),
            ),
            logger = NewTaskFakeLogger(),
        )

        val result = testSubject.send(newTaskRequest())

        assertThat(result.exceptionOrNull()?.message).isEqualTo("Failed to send TaskMail task request.")
    }
}

private class NewTaskFakeTaskMailSenderAccountSource(
    private val account: LegacyAccountDto?,
) : TaskMailSenderAccountSource {
    override fun getSenderAccounts(): List<TaskMailSenderAccount> = emptyList()

    override fun getAccount(accountUuid: String): LegacyAccountDto? = account
}

private class NewTaskFakeTaskMailNewTaskMimeMessageFactory(
    private val result: Result<MimeMessage> = Result.success(MimeMessage.create()),
) : TaskMailNewTaskMimeMessageFactory {
    override suspend fun create(
        request: TaskMailNewTaskRequest,
        account: LegacyAccountDto,
    ): Result<MimeMessage> = result
}

private class NewTaskFakeTaskMailMimeMessageSender(
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

private class NewTaskFakeLogger : Logger {
    override fun verbose(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun debug(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun info(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun warn(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun error(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
}

private fun senderAccount(): LegacyAccountDto {
    return LegacyAccountDto(uuid = TEST_ACCOUNT_UUID).apply {
        isFinishedSetup = true
        identities = mutableListOf(
            Identity(
                email = "user@example.org",
                replyTo = "user@example.org",
            ),
        )
    }
}

private fun newTaskRequest(): TaskMailNewTaskRequest {
    return TaskMailNewTaskRequest(
        accountUuid = TEST_ACCOUNT_UUID,
        subject = "[CX] Audit TaskMail",
        body = "Repo: repo",
    )
}

private const val TEST_ACCOUNT_UUID = "11111111-1111-1111-1111-111111111111"

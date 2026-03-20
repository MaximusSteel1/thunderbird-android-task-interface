package net.thunderbird.feature.taskmail.internal.domain.reply

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.transport.TaskMailReplyTransport
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext

class RealTaskMailReplySenderTest {

    @Test
    fun `send should return success when transport succeeds`() = runTest {
        val testSubject = RealTaskMailReplySender(
            transport = FakeTaskMailReplyTransport(Result.success(Unit)),
        )

        val result = testSubject.send(replyRequest())

        assertThat(result).isEqualTo(TaskMailReplyResult.success())
    }

    @Test
    fun `send should surface transport failure message`() = runTest {
        val testSubject = RealTaskMailReplySender(
            transport = FakeTaskMailReplyTransport(
                Result.failure(IllegalStateException("TaskMail reply is no longer available.")),
            ),
        )

        val result = testSubject.send(replyRequest())

        assertThat(result).isEqualTo(TaskMailReplyResult.failure("TaskMail reply is no longer available."))
    }
}

private class FakeTaskMailReplyTransport(
    private val result: Result<Unit>,
) : TaskMailReplyTransport {
    override suspend fun send(request: TaskMailReplyRequest): Result<Unit> = result
}

private fun replyRequest(): TaskMailReplyRequest {
    return TaskMailReplyRequest(
        context = TaskSessionReplyContext(
            accountUuid = TEST_ACCOUNT_UUID,
            folderId = 1L,
            messageServerId = "msg-1",
        ),
        body = "done",
        mode = TaskMailReplyMode.ContinueSession,
    )
}

private const val TEST_ACCOUNT_UUID = "11111111-1111-1111-1111-111111111111"

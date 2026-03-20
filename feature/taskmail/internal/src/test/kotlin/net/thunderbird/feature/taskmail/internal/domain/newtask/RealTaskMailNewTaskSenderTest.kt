package net.thunderbird.feature.taskmail.internal.domain.newtask

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.transport.TaskMailNewTaskTransport

class RealTaskMailNewTaskSenderTest {

    @Test
    fun `send should return success when transport succeeds`() = runTest {
        val testSubject = RealTaskMailNewTaskSender(
            transport = FakeTaskMailNewTaskTransport(Result.success(Unit)),
        )

        val result = testSubject.send(newTaskRequest())

        assertThat(result).isEqualTo(TaskMailNewTaskResult.success())
    }

    @Test
    fun `send should surface transport failure message`() = runTest {
        val testSubject = RealTaskMailNewTaskSender(
            transport = FakeTaskMailNewTaskTransport(
                Result.failure(IllegalStateException("TaskMail bot mailbox is not configured.")),
            ),
        )

        val result = testSubject.send(newTaskRequest())

        assertThat(result).isEqualTo(
            TaskMailNewTaskResult.failure("TaskMail bot mailbox is not configured."),
        )
    }
}

private class FakeTaskMailNewTaskTransport(
    private val result: Result<Unit>,
) : TaskMailNewTaskTransport {
    override suspend fun send(request: TaskMailNewTaskRequest): Result<Unit> = result
}

private fun newTaskRequest(): TaskMailNewTaskRequest {
    return TaskMailNewTaskRequest(
        accountUuid = TEST_ACCOUNT_UUID,
        subject = "[CX] Audit TaskMail",
        body = "Repo: repo",
    )
}

private const val TEST_ACCOUNT_UUID = "11111111-1111-1111-1111-111111111111"

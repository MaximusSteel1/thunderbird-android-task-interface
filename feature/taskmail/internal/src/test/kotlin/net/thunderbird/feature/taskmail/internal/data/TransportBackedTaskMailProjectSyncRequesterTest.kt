package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.transport.TaskMailNewTaskTransport
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest

class TransportBackedTaskMailProjectSyncRequesterTest {

    @Test
    fun `request sync should send canonical sync mail request`() = runTest {
        val transport = RecordingTaskMailNewTaskTransport()
        val testSubject = TransportBackedTaskMailProjectSyncRequester(
            transport = transport,
        )

        val result = testSubject.requestSync("account-1")

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(transport.requests).isEqualTo(
            listOf(
                TaskMailNewTaskRequest(
                    accountUuid = "account-1",
                    subject = "[SYNC]",
                    body = "",
                ),
            ),
        )
    }
}

private class RecordingTaskMailNewTaskTransport : TaskMailNewTaskTransport {
    val requests = mutableListOf<TaskMailNewTaskRequest>()

    override suspend fun send(request: TaskMailNewTaskRequest): Result<Unit> {
        requests += request
        return Result.success(Unit)
    }
}

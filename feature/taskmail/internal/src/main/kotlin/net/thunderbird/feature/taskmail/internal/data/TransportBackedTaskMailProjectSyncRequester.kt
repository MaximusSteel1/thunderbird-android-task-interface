package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.data.transport.TaskMailNewTaskTransport
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest

private const val SYNC_SUBJECT = "[SYNC]"

internal class TransportBackedTaskMailProjectSyncRequester(
    private val transport: TaskMailNewTaskTransport,
) : TaskMailProjectSyncRequester {
    override suspend fun requestSync(accountUuid: String): Result<Unit> {
        return transport.send(
            TaskMailNewTaskRequest(
                accountUuid = accountUuid,
                subject = SYNC_SUBJECT,
                body = "",
            ),
        )
    }
}

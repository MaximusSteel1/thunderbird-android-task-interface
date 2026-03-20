package net.thunderbird.feature.taskmail.internal.domain.newtask

import net.thunderbird.feature.taskmail.internal.data.transport.TaskMailNewTaskTransport

internal class RealTaskMailNewTaskSender(
    private val transport: TaskMailNewTaskTransport,
) : TaskMailNewTaskSender {

    override suspend fun send(request: TaskMailNewTaskRequest): TaskMailNewTaskResult {
        return transport.send(request).fold(
            onSuccess = {
                TaskMailNewTaskResult.success()
            },
            onFailure = { error ->
                TaskMailNewTaskResult.failure(
                    error.message?.takeIf(String::isNotBlank) ?: "Failed to send TaskMail task request.",
                )
            },
        )
    }
}

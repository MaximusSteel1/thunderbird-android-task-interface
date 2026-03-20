package net.thunderbird.feature.taskmail.internal.data.transport

import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest

internal interface TaskMailNewTaskTransport {
    suspend fun send(request: TaskMailNewTaskRequest): Result<Unit>
}

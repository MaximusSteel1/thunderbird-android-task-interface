package net.thunderbird.feature.taskmail.internal.domain.newtask

internal interface TaskMailNewTaskSender {
    suspend fun send(request: TaskMailNewTaskRequest): TaskMailNewTaskResult
}

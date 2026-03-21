package net.thunderbird.feature.taskmail.internal.domain.newtask

internal interface TaskMailDirectNewTaskSender {
    suspend fun send(draft: TaskMailNewTaskDraft): TaskMailDirectNewTaskResult
}

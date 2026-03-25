package net.thunderbird.feature.taskmail.internal.domain.newtask

internal interface TaskMailCreateSessionClient {
    suspend fun createSession(draft: TaskMailNewTaskDraft): TaskMailCreateSessionResult
}

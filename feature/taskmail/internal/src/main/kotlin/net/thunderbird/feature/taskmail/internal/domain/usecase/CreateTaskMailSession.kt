package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionClient
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft

internal class CreateTaskMailSession(
    private val createSessionClient: TaskMailCreateSessionClient,
) {
    suspend operator fun invoke(draft: TaskMailNewTaskDraft): TaskMailCreateSessionResult {
        return createSessionClient.createSession(draft)
    }
}

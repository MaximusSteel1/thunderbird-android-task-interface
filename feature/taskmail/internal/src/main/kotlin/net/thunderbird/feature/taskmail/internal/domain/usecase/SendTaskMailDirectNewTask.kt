package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskSender
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft

internal class SendTaskMailDirectNewTask(
    private val directNewTaskSender: TaskMailDirectNewTaskSender,
) {
    suspend operator fun invoke(draft: TaskMailNewTaskDraft): TaskMailDirectNewTaskResult {
        return directNewTaskSender.send(draft)
    }
}

package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskBodySerializer
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskSender
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskSubjectBuilder

internal class SendTaskMailNewTask(
    private val newTaskSender: TaskMailNewTaskSender,
    private val bodySerializer: TaskMailNewTaskBodySerializer = TaskMailNewTaskBodySerializer(),
    private val subjectBuilder: TaskMailNewTaskSubjectBuilder = TaskMailNewTaskSubjectBuilder(),
) {
    suspend operator fun invoke(draft: TaskMailNewTaskDraft): TaskMailNewTaskResult {
        val senderAccountId = draft.senderAccountId
            ?: return TaskMailNewTaskResult.failure(
                errorMessage = "TaskMail sending account is unavailable.",
            )
        return newTaskSender.send(
            TaskMailNewTaskRequest(
                accountUuid = senderAccountId,
                subject = subjectBuilder.build(
                    backend = draft.backend,
                    subjectTitle = draft.subjectTitle,
                ),
                body = bodySerializer.serialize(draft),
            ),
        )
    }
}

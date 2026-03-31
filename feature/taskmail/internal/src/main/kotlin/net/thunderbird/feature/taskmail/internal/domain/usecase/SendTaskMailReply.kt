package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyBodyInput
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyBodySerializer
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyMode
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyResult
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplySender

internal class SendTaskMailReply(
    private val replySender: TaskMailReplySender,
    private val bodySerializer: TaskMailReplyBodySerializer = TaskMailReplyBodySerializer(),
) {
    suspend fun sendFreeText(
        context: TaskSessionReplyContext,
        draftText: String,
        attachments: List<TaskReplyAttachment> = emptyList(),
        permission: TaskMailNewTaskPermission? = null,
    ): TaskMailReplyResult {
        return send(
            context = context,
            input = TaskMailReplyBodyInput(
                mode = TaskMailReplyMode.ContinueSession,
                userText = draftText,
                permission = permission,
            ),
            attachments = attachments,
        )
    }

    suspend fun sendQuestionChoice(
        context: TaskSessionReplyContext,
        choice: String,
        attachments: List<TaskReplyAttachment> = emptyList(),
        permission: TaskMailNewTaskPermission? = null,
    ): TaskMailReplyResult {
        return send(
            context = context,
            input = TaskMailReplyBodyInput(
                mode = TaskMailReplyMode.AnswerSingleQuestion,
                userText = choice,
                permission = permission,
            ),
            attachments = attachments,
        )
    }

    suspend fun sendResumeSession(
        context: TaskSessionReplyContext,
        draftText: String,
        attachments: List<TaskReplyAttachment> = emptyList(),
        permission: TaskMailNewTaskPermission? = null,
    ): TaskMailReplyResult {
        return send(
            context = context,
            input = TaskMailReplyBodyInput(
                mode = TaskMailReplyMode.ResumeSession,
                userText = draftText,
                permission = permission,
            ),
            attachments = attachments,
        )
    }

    suspend fun sendStructuredAnswers(
        context: TaskSessionReplyContext,
        draftText: String,
        attachments: List<TaskReplyAttachment> = emptyList(),
        permission: TaskMailNewTaskPermission? = null,
    ): TaskMailReplyResult {
        return send(
            context = context,
            input = TaskMailReplyBodyInput(
                mode = TaskMailReplyMode.AnswerMultiQuestion,
                userText = draftText,
                permission = permission,
            ),
            attachments = attachments,
        )
    }

    suspend fun sendStatusQuery(context: TaskSessionReplyContext): TaskMailReplyResult {
        return send(
            context = context,
            input = TaskMailReplyBodyInput(mode = TaskMailReplyMode.StatusQuery),
        )
    }

    private suspend fun send(
        context: TaskSessionReplyContext,
        input: TaskMailReplyBodyInput,
        attachments: List<TaskReplyAttachment> = emptyList(),
    ): TaskMailReplyResult {
        return replySender.send(
            TaskMailReplyRequest(
                context = context,
                body = bodySerializer.serialize(input),
                mode = input.mode,
                attachments = attachments,
            ),
        )
    }
}

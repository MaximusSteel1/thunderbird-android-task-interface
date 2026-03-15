package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyKind
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyResult
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplySender

internal class SendTaskMailReply(
    private val replySender: TaskMailReplySender,
) {
    suspend fun sendFreeText(
        context: TaskSessionReplyContext,
        draftText: String,
        attachments: List<TaskReplyAttachment> = emptyList(),
    ): TaskMailReplyResult {
        return replySender.send(
            TaskMailReplyRequest(
                context = context,
                body = draftText,
                kind = TaskMailReplyKind.FreeText,
                attachments = attachments,
            ),
        )
    }

    suspend fun sendQuestionChoice(
        context: TaskSessionReplyContext,
        choice: String,
        attachments: List<TaskReplyAttachment> = emptyList(),
    ): TaskMailReplyResult {
        return replySender.send(
            TaskMailReplyRequest(
                context = context,
                body = choice,
                kind = TaskMailReplyKind.QuestionChoice,
                attachments = attachments,
            ),
        )
    }

    suspend fun sendStructuredAnswers(
        context: TaskSessionReplyContext,
        draftText: String,
        attachments: List<TaskReplyAttachment> = emptyList(),
    ): TaskMailReplyResult {
        return replySender.send(
            TaskMailReplyRequest(
                context = context,
                body = draftText,
                kind = TaskMailReplyKind.StructuredAnswers,
                attachments = attachments,
            ),
        )
    }

    suspend fun sendStatusQuery(context: TaskSessionReplyContext): TaskMailReplyResult {
        return replySender.send(
            TaskMailReplyRequest(
                context = context,
                body = STATUS_QUERY_BODY,
                kind = TaskMailReplyKind.StatusQuery,
            ),
        )
    }

    private companion object {
        const val STATUS_QUERY_BODY = "/status"
    }
}

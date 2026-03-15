package net.thunderbird.feature.taskmail.internal.domain.reply

internal interface TaskMailReplySender {
    suspend fun send(request: TaskMailReplyRequest): TaskMailReplyResult
}

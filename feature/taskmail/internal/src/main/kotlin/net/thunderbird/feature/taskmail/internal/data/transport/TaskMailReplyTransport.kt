package net.thunderbird.feature.taskmail.internal.data.transport

import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest

internal interface TaskMailReplyTransport {
    suspend fun send(request: TaskMailReplyRequest): Result<Unit>
}

package net.thunderbird.feature.taskmail.internal.domain.reply

import net.thunderbird.feature.taskmail.internal.data.transport.TaskMailReplyTransport

internal class RealTaskMailReplySender(
    private val transport: TaskMailReplyTransport,
) : TaskMailReplySender {

    override suspend fun send(request: TaskMailReplyRequest): TaskMailReplyResult {
        return transport.send(request).fold(
            onSuccess = {
                TaskMailReplyResult.success()
            },
            onFailure = { error ->
                TaskMailReplyResult.failure(
                    error.message?.takeIf(String::isNotBlank) ?: "Failed to send TaskMail reply.",
                )
            },
        )
    }
}

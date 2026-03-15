package net.thunderbird.feature.taskmail.internal.domain.reply

internal class NoOpTaskMailReplySender : TaskMailReplySender {
    override suspend fun send(request: TaskMailReplyRequest): TaskMailReplyResult {
        return TaskMailReplyResult.failure("TaskMail reply sending is not wired yet.")
    }
}

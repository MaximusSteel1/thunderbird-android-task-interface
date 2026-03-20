package net.thunderbird.feature.taskmail.internal.data.transport

import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.TaskMailMimeMessageFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailMimeMessageSender
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplySourceMessageLoader
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest

private const val TAG = "EmailTaskMailReplyTransport"

internal class EmailTaskMailReplyTransport(
    private val sourceMessageLoader: TaskMailReplySourceMessageLoader,
    private val mimeMessageFactory: TaskMailMimeMessageFactory,
    private val mimeMessageSender: TaskMailMimeMessageSender,
    private val logger: Logger,
) : TaskMailReplyTransport {

    override suspend fun send(request: TaskMailReplyRequest): Result<Unit> {
        val sourceMessage = sourceMessageLoader.load(request.context)
        return if (sourceMessage == null) {
            Result.failure(IllegalStateException("TaskMail reply is no longer available."))
        } else {
            val mimeMessageResult = mimeMessageFactory.create(request, sourceMessage)
                .onFailure { logger.error(TAG, it) { "Failed to prepare TaskMail reply message" } }
            val mimeMessage = mimeMessageResult.getOrNull()

            if (mimeMessage == null) {
                Result.failure(IllegalStateException("Failed to prepare TaskMail reply."))
            } else {
                runCatching {
                    mimeMessageSender.send(
                        account = sourceMessage.account,
                        message = mimeMessage,
                        plaintextSubject = mimeMessage.subject.orEmpty(),
                    )
                }.onFailure { error ->
                    logger.error(TAG, error) { "Failed to send TaskMail reply message" }
                }
                    .mapFailure("Failed to send TaskMail reply.")
            }
        }
    }
}

private fun <T> Result<T>.mapFailure(message: String): Result<T> {
    return fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(IllegalStateException(message, it)) },
    )
}

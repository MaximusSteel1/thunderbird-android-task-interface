package net.thunderbird.feature.taskmail.internal.domain.reply

import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.TaskMailMimeMessageFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailMimeMessageSender
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplySourceMessageLoader

private const val TAG = "RealTaskMailReplySender"

internal class RealTaskMailReplySender(
    private val sourceMessageLoader: TaskMailReplySourceMessageLoader,
    private val mimeMessageFactory: TaskMailMimeMessageFactory,
    private val mimeMessageSender: TaskMailMimeMessageSender,
    private val logger: Logger,
) : TaskMailReplySender {

    override suspend fun send(request: TaskMailReplyRequest): TaskMailReplyResult {
        val sourceMessage = sourceMessageLoader.load(request.context)
        val replyResult = sourceMessage?.let { preparedSourceMessage ->
            val mimeMessage = mimeMessageFactory.create(request, preparedSourceMessage)
                .onFailure { logger.error(TAG, it) { "Failed to prepare TaskMail reply message" } }
                .getOrNull()

            if (mimeMessage != null) {
                runCatching {
                    mimeMessageSender.send(
                        account = preparedSourceMessage.account,
                        message = mimeMessage,
                        plaintextSubject = mimeMessage.subject.orEmpty(),
                    )
                }.fold(
                    onSuccess = {
                        TaskMailReplyResult.success()
                    },
                    onFailure = { error ->
                        logger.error(TAG, error) { "Failed to send TaskMail reply message" }
                        TaskMailReplyResult.failure("Failed to send TaskMail reply.")
                    },
                )
            } else {
                TaskMailReplyResult.failure("Failed to prepare TaskMail reply.")
            }
        }

        return replyResult ?: TaskMailReplyResult.failure("TaskMail reply is no longer available.")
    }
}

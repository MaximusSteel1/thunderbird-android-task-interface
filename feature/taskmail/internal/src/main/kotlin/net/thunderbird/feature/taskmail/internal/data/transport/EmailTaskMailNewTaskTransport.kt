package net.thunderbird.feature.taskmail.internal.data.transport

import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.TaskMailMimeMessageSender
import net.thunderbird.feature.taskmail.internal.data.TaskMailNewTaskMimeMessageFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailSenderAccountSource
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest

private const val TAG = "EmailTaskMailNewTaskTransport"
private const val PREPARE_FAILURE = "Failed to prepare TaskMail new-task message."

internal class EmailTaskMailNewTaskTransport(
    private val senderAccountSource: TaskMailSenderAccountSource,
    private val mimeMessageFactory: TaskMailNewTaskMimeMessageFactory,
    private val mimeMessageSender: TaskMailMimeMessageSender,
    private val logger: Logger,
) : TaskMailNewTaskTransport {

    override suspend fun send(request: TaskMailNewTaskRequest): Result<Unit> {
        val account = senderAccountSource.getAccount(request.accountUuid)
        return if (account == null) {
            Result.failure(
                IllegalStateException("TaskMail sending account is no longer available."),
            )
        } else {
            val mimeMessageResult = mimeMessageFactory.create(request, account)
                .onFailure { logger.error(TAG, it) { "Failed to prepare TaskMail new-task message" } }
            val mimeMessage = mimeMessageResult.getOrNull()

            if (mimeMessage == null) {
                Result.failure(
                    IllegalStateException(
                        mimeMessageResult.exceptionOrNull()?.message
                            ?.takeIf(String::isNotBlank)
                            ?: PREPARE_FAILURE,
                    ),
                )
            } else {
                runCatching {
                    mimeMessageSender.send(
                        account = account,
                        message = mimeMessage,
                        plaintextSubject = mimeMessage.subject.orEmpty(),
                    )
                }.onFailure { error ->
                    logger.error(TAG, error) { "Failed to send TaskMail new-task message" }
                }
                    .mapFailure("Failed to send TaskMail task request.")
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

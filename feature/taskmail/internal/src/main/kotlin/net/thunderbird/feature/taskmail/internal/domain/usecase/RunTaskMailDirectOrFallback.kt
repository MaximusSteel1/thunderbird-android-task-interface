package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus

internal class RunTaskMailDirectOrFallback(
    private val relayBootstrapManager: RelayBootstrapManager,
) {
    suspend fun <T> execute(
        directSend: suspend () -> TaskMailDirectAttemptResult<T>,
        mailFallback: suspend () -> Result<Unit>,
    ): TaskMailDirectOrFallbackResult<T> {
        val bootstrapStatus = runDirectBootstrapAttempt().status
        if (bootstrapStatus != RelayBootstrapStatus.HelloAck) {
            return sendMailFallback(bootstrapStatus, mailFallback)
        }

        val directResult = try {
            runCatching { directSend() }
                .getOrElse { error ->
                    TaskMailDirectAttemptResult.FallbackToMail(error.message)
                }
        } finally {
            runCatching { relayBootstrapManager.disconnect() }
        }

        return when (directResult) {
            is TaskMailDirectAttemptResult.Accepted -> {
                TaskMailDirectOrFallbackResult.DirectAccepted(
                    bootstrapStatus = bootstrapStatus,
                    payload = directResult.payload,
                )
            }

            is TaskMailDirectAttemptResult.FallbackToMail -> {
                sendMailFallback(bootstrapStatus, mailFallback)
            }

            is TaskMailDirectAttemptResult.Rejected -> {
                TaskMailDirectOrFallbackResult.DirectRejected(
                    bootstrapStatus = bootstrapStatus,
                    errorMessage = directResult.errorMessage,
                )
            }
        }
    }

    private suspend fun runDirectBootstrapAttempt(): RelayBootstrapResult {
        return runCatching {
            relayBootstrapManager.bootstrapSavedConfig()
        }.getOrElse { error ->
            RelayBootstrapResult(
                status = RelayBootstrapStatus.ConnectFailure,
                detailMessage = error.message,
            )
        }
    }

    private suspend fun sendMailFallback(
        bootstrapStatus: RelayBootstrapStatus,
        mailFallback: suspend () -> Result<Unit>,
    ): TaskMailDirectOrFallbackResult<Nothing> {
        return mailFallback().fold(
            onSuccess = {
                TaskMailDirectOrFallbackResult.MailFallbackSucceeded(
                    bootstrapStatus = bootstrapStatus,
                )
            },
            onFailure = { error ->
                TaskMailDirectOrFallbackResult.MailFallbackFailed(
                    bootstrapStatus = bootstrapStatus,
                    errorMessage = error.message,
                )
            },
        )
    }
}

internal sealed interface TaskMailDirectAttemptResult<out T> {
    data class Accepted<T>(
        val payload: T,
    ) : TaskMailDirectAttemptResult<T>

    data class FallbackToMail(
        val detailMessage: String? = null,
    ) : TaskMailDirectAttemptResult<Nothing>

    data class Rejected(
        val errorMessage: String,
    ) : TaskMailDirectAttemptResult<Nothing>
}

internal sealed interface TaskMailDirectOrFallbackResult<out T> {
    val bootstrapStatus: RelayBootstrapStatus

    data class DirectAccepted<T>(
        override val bootstrapStatus: RelayBootstrapStatus,
        val payload: T,
    ) : TaskMailDirectOrFallbackResult<T>

    data class MailFallbackSucceeded(
        override val bootstrapStatus: RelayBootstrapStatus,
    ) : TaskMailDirectOrFallbackResult<Nothing>

    data class MailFallbackFailed(
        override val bootstrapStatus: RelayBootstrapStatus,
        val errorMessage: String?,
    ) : TaskMailDirectOrFallbackResult<Nothing>

    data class DirectRejected(
        override val bootstrapStatus: RelayBootstrapStatus,
        val errorMessage: String,
    ) : TaskMailDirectOrFallbackResult<Nothing>
}

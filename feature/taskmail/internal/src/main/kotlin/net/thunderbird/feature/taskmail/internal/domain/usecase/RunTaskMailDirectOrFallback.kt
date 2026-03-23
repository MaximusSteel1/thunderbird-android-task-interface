package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectAcceptedEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate

internal class RunTaskMailDirectOrFallback(
    private val relayBootstrapManager: RelayBootstrapManager,
) {
    suspend fun <T> execute(
        directSend: suspend () -> TaskMailDirectAttemptResult<T>,
        mailFallback: suspend () -> Result<Unit>,
    ): TaskMailDirectOrFallbackResult<T> {
        val bootstrapResult = runDirectBootstrapAttempt()
        val bootstrapStatus = bootstrapResult.status
        if (bootstrapStatus != RelayBootstrapStatus.HelloAck) {
            return sendMailFallback(
                bootstrapStatus = bootstrapStatus,
                fallbackReason = bootstrapResult.detailMessage,
                mailFallback = mailFallback,
            )
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
                    payload = directResult.payload,
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = bootstrapStatus,
                        outcome = TaskMailDirectOutcome.DirectAccepted,
                        switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                        requestId = directResult.acceptedEvidence.requestId,
                        receiptId = directResult.acceptedEvidence.receiptId,
                        transportMessageId = directResult.acceptedEvidence.transportMessageId,
                    ),
                )
            }

            is TaskMailDirectAttemptResult.FallbackToMail -> {
                sendMailFallback(
                    bootstrapStatus = bootstrapStatus,
                    fallbackReason = directResult.detailMessage,
                    requestId = directResult.requestId,
                    receiptId = directResult.receiptId,
                    transportMessageId = directResult.transportMessageId,
                    mailFallback = mailFallback,
                )
            }

            is TaskMailDirectAttemptResult.Rejected -> {
                TaskMailDirectOrFallbackResult.DirectRejected(
                    errorMessage = directResult.errorMessage,
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = bootstrapStatus,
                        outcome = TaskMailDirectOutcome.DirectRejected,
                        switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                        requestId = directResult.requestId,
                        receiptId = directResult.receiptId,
                        transportMessageId = directResult.transportMessageId,
                        errorMessage = directResult.errorMessage,
                    ),
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
        fallbackReason: String?,
        requestId: String? = null,
        receiptId: String? = null,
        transportMessageId: String? = null,
        mailFallback: suspend () -> Result<Unit>,
    ): TaskMailDirectOrFallbackResult<Nothing> {
        return mailFallback().fold(
            onSuccess = {
                TaskMailDirectOrFallbackResult.MailFallbackSucceeded(
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = bootstrapStatus,
                        outcome = TaskMailDirectOutcome.MailFallbackSucceeded,
                        switchGate = TaskMailDirectSwitchGate.FallbackRequired,
                        requestId = requestId,
                        receiptId = receiptId,
                        transportMessageId = transportMessageId,
                        fallbackReason = fallbackReason,
                    ),
                )
            },
            onFailure = { error ->
                TaskMailDirectOrFallbackResult.MailFallbackFailed(
                    errorMessage = error.message,
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = bootstrapStatus,
                        outcome = TaskMailDirectOutcome.MailFallbackFailed,
                        switchGate = TaskMailDirectSwitchGate.FallbackRequired,
                        requestId = requestId,
                        receiptId = receiptId,
                        transportMessageId = transportMessageId,
                        fallbackReason = fallbackReason,
                        errorMessage = error.message,
                    ),
                )
            },
        )
    }
}

internal sealed interface TaskMailDirectAttemptResult<out T> {
    data class Accepted<T>(
        val payload: T,
        val acceptedEvidence: TaskMailDirectAcceptedEvidence = TaskMailDirectAcceptedEvidence(),
    ) : TaskMailDirectAttemptResult<T>

    data class FallbackToMail(
        val detailMessage: String? = null,
        val requestId: String? = null,
        val receiptId: String? = null,
        val transportMessageId: String? = null,
    ) : TaskMailDirectAttemptResult<Nothing>

    data class Rejected(
        val errorMessage: String,
        val requestId: String? = null,
        val receiptId: String? = null,
        val transportMessageId: String? = null,
    ) : TaskMailDirectAttemptResult<Nothing>
}

internal sealed interface TaskMailDirectOrFallbackResult<out T> {
    val evidence: TaskMailDirectSendEvidence
    val bootstrapStatus: RelayBootstrapStatus
        get() = evidence.bootstrapStatus

    data class DirectAccepted<T>(
        val payload: T,
        override val evidence: TaskMailDirectSendEvidence,
    ) : TaskMailDirectOrFallbackResult<T>

    data class MailFallbackSucceeded(
        override val evidence: TaskMailDirectSendEvidence,
    ) : TaskMailDirectOrFallbackResult<Nothing>

    data class MailFallbackFailed(
        val errorMessage: String?,
        override val evidence: TaskMailDirectSendEvidence,
    ) : TaskMailDirectOrFallbackResult<Nothing>

    data class DirectRejected(
        val errorMessage: String,
        override val evidence: TaskMailDirectSendEvidence,
    ) : TaskMailDirectOrFallbackResult<Nothing>
}

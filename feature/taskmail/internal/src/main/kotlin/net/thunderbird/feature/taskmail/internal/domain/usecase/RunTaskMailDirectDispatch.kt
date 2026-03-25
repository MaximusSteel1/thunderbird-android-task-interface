package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate

private const val DIRECT_DISPATCH_FAILURE_MESSAGE = "Relay dispatch failed."
private const val DIRECT_BOOTSTRAP_FAILURE_MESSAGE = "Unable to connect to the relay."

internal class RunTaskMailDirectDispatch(
    private val relayBootstrapManager: RelayBootstrapManager,
) {
    suspend fun <T> execute(
        directSend: suspend () -> TaskMailDirectAttemptResult<T>,
    ): TaskMailDirectDispatchResult<T> {
        val bootstrapResult = runDirectBootstrapAttempt()
        val bootstrapStatus = bootstrapResult.status
        if (bootstrapStatus != RelayBootstrapStatus.HelloAck) {
            val errorMessage = bootstrapResult.detailMessage.orDispatchError(DIRECT_BOOTSTRAP_FAILURE_MESSAGE)
            return TaskMailDirectDispatchResult.DirectRejected(
                errorMessage = errorMessage,
                evidence = TaskMailDirectSendEvidence(
                    bootstrapStatus = bootstrapStatus,
                    outcome = TaskMailDirectOutcome.DirectRejected,
                    switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                    errorMessage = errorMessage,
                ),
            )
        }

        val directResult = try {
            runCatching { directSend() }
                .getOrElse { error ->
                    TaskMailDirectAttemptResult.Rejected(
                        errorMessage = error.message.orDispatchError(DIRECT_DISPATCH_FAILURE_MESSAGE),
                    )
                }
        } finally {
            runCatching { relayBootstrapManager.disconnect() }
        }

        return when (directResult) {
            is TaskMailDirectAttemptResult.Accepted -> {
                TaskMailDirectDispatchResult.DirectAccepted(
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
                val errorMessage = directResult.detailMessage.orDispatchError(DIRECT_DISPATCH_FAILURE_MESSAGE)
                TaskMailDirectDispatchResult.DirectRejected(
                    errorMessage = errorMessage,
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = bootstrapStatus,
                        outcome = TaskMailDirectOutcome.DirectRejected,
                        switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                        requestId = directResult.requestId,
                        receiptId = directResult.receiptId,
                        transportMessageId = directResult.transportMessageId,
                        fallbackReason = directResult.detailMessage?.takeIf(String::isNotBlank),
                        errorMessage = errorMessage,
                    ),
                )
            }

            is TaskMailDirectAttemptResult.Rejected -> {
                val errorMessage = directResult.errorMessage.orDispatchError(DIRECT_DISPATCH_FAILURE_MESSAGE)
                TaskMailDirectDispatchResult.DirectRejected(
                    errorMessage = errorMessage,
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = bootstrapStatus,
                        outcome = TaskMailDirectOutcome.DirectRejected,
                        switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                        requestId = directResult.requestId,
                        receiptId = directResult.receiptId,
                        transportMessageId = directResult.transportMessageId,
                        errorMessage = errorMessage,
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
}

internal sealed interface TaskMailDirectDispatchResult<out T> {
    val evidence: TaskMailDirectSendEvidence

    data class DirectAccepted<T>(
        val payload: T,
        override val evidence: TaskMailDirectSendEvidence,
    ) : TaskMailDirectDispatchResult<T>

    data class DirectRejected(
        val errorMessage: String,
        override val evidence: TaskMailDirectSendEvidence,
    ) : TaskMailDirectDispatchResult<Nothing>
}

private fun String?.orDispatchError(defaultMessage: String): String {
    return this?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: defaultMessage
}

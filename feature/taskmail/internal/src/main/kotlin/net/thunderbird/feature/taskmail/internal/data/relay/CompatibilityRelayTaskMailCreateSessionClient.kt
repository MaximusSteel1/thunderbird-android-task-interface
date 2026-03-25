package net.thunderbird.feature.taskmail.internal.data.relay

import java.util.UUID
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionAckStatus
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionClient
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionSubmitAck
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskSender
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft

private const val DIRECT_DISPATCH_FAILURE_MESSAGE = "Relay dispatch failed."
private const val DIRECT_BOOTSTRAP_FAILURE_MESSAGE = "Unable to connect to the relay."
private const val COMMAND_ID_PREFIX = "cmd_android_"

internal class CompatibilityRelayTaskMailCreateSessionClient(
    private val relayBootstrapManager: RelayBootstrapManager,
    private val directNewTaskSender: TaskMailDirectNewTaskSender,
    private val commandIdFactory: () -> String = ::nextCommandId,
) : TaskMailCreateSessionClient {

    override suspend fun createSession(draft: TaskMailNewTaskDraft): TaskMailCreateSessionResult {
        val commandId = commandIdFactory()
        val bootstrapResult = runDirectBootstrapAttempt()
        val bootstrapStatus = bootstrapResult.status
        if (bootstrapStatus != RelayBootstrapStatus.HelloAck) {
            val errorMessage = bootstrapResult.detailMessage.orDispatchError(DIRECT_BOOTSTRAP_FAILURE_MESSAGE)
            return TaskMailCreateSessionResult.Failed(
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
            runCatching { directNewTaskSender.send(draft) }
                .getOrElse { error ->
                    TaskMailDirectNewTaskResult.Rejected(
                        error.message.orDispatchError(DIRECT_DISPATCH_FAILURE_MESSAGE),
                    )
                }
        } finally {
            runCatching { relayBootstrapManager.disconnect() }
        }

        return when (directResult) {
            is TaskMailDirectNewTaskResult.Accepted -> {
                TaskMailCreateSessionResult.Submitted(
                    commandId = commandId,
                    submitAck = TaskMailCreateSessionSubmitAck(
                        ackStatus = TaskMailCreateSessionAckStatus.Accepted,
                    ),
                    // Temporary compatibility bridge: current relay submit does not expose a canonical session binding yet.
                    sessionBinding = null,
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = bootstrapStatus,
                        outcome = TaskMailDirectOutcome.DirectAccepted,
                        switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                        requestId = directResult.requestId,
                        receiptId = directResult.receiptId,
                        transportMessageId = directResult.transportMessageId,
                    ),
                )
            }

            is TaskMailDirectNewTaskResult.FallbackToMail -> {
                val errorMessage = directResult.detailMessage.orDispatchError(DIRECT_DISPATCH_FAILURE_MESSAGE)
                TaskMailCreateSessionResult.Failed(
                    errorMessage = errorMessage,
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = bootstrapStatus,
                        outcome = TaskMailDirectOutcome.DirectRejected,
                        switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                        fallbackReason = directResult.detailMessage?.takeIf(String::isNotBlank),
                        errorMessage = errorMessage,
                    ),
                )
            }

            is TaskMailDirectNewTaskResult.Rejected -> {
                TaskMailCreateSessionResult.Rejected(
                    commandId = commandId,
                    submitAck = TaskMailCreateSessionSubmitAck(
                        ackStatus = TaskMailCreateSessionAckStatus.Rejected,
                        reason = directResult.errorMessage,
                    ),
                    errorMessage = directResult.errorMessage,
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = bootstrapStatus,
                        outcome = TaskMailDirectOutcome.DirectRejected,
                        switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
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

    private companion object {
        fun nextCommandId(): String {
            return COMMAND_ID_PREFIX + UUID.randomUUID().toString().replace("-", "")
        }
    }
}

private fun String?.orDispatchError(defaultMessage: String): String {
    return this?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: defaultMessage
}

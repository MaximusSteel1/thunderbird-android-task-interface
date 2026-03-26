package net.thunderbird.feature.taskmail.internal.domain.usecase

import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.data.relay.RelayConnectionClient
import net.thunderbird.feature.taskmail.internal.data.relay.classifyRelayConnectionFailure
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate

private const val DIRECT_DISPATCH_FAILURE_MESSAGE = "Relay dispatch failed."
private const val DIRECT_BOOTSTRAP_FAILURE_MESSAGE = "Unable to connect to the relay."
private const val CONTROL_PATH = "/control"
private const val CONTROL_BOOTSTRAP_SCHEMA_VERSION = "taskmail-bootstrap-control-contract-v2"
private const val SESSION_ACTION_SCHEMA_VERSION = "post-creation-session-action-contract-v1"
private val CONTROL_SUPPORTED_PAYLOAD_SCHEMAS = listOf(
    CONTROL_BOOTSTRAP_SCHEMA_VERSION,
    SESSION_ACTION_SCHEMA_VERSION,
)

internal class RunTaskMailDirectDispatch(
    private val relayBootstrapManager: RelayBootstrapManager,
    private val relayConnectionClient: RelayConnectionClient,
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
            runCatching { relayConnectionClient.disconnect() }
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
        val normalizedConfig = relayBootstrapManager.loadConfig().normalized()
        if (!normalizedConfig.isConfigured()) {
            return RelayBootstrapResult(
                status = RelayBootstrapStatus.NotConfigured,
                detailMessage = "Relay host, port, and transport token are required.",
            )
        }

        val controlConfig = normalizedConfig.copy(path = CONTROL_PATH)
        return relayConnectionClient.connect(
            config = controlConfig,
            supportedPayloadSchemas = CONTROL_SUPPORTED_PAYLOAD_SCHEMAS,
        ).fold(
            onSuccess = { helloAck ->
                if (!helloAck.acceptedPayloadSchemas.contains(SESSION_ACTION_SCHEMA_VERSION)) {
                    RelayBootstrapResult(
                        status = RelayBootstrapStatus.UnexpectedResponse,
                        helloAck = helloAck,
                        detailMessage = "Relay /control hello_ack did not advertise post-creation session actions.",
                    )
                } else {
                    RelayBootstrapResult(
                        status = RelayBootstrapStatus.HelloAck,
                        helloAck = helloAck,
                    )
                }
            },
            onFailure = { error ->
                RelayBootstrapResult(
                    status = error.classifyRelayConnectionFailure(controlConfig),
                    detailMessage = error.message,
                )
            },
        )
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

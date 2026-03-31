package net.thunderbird.feature.taskmail.internal.data.controlplane

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.toControlPlaneCommandAck
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.toControlPlaneResult
import net.thunderbird.feature.taskmail.internal.data.relay.RelayConnectionClient
import net.thunderbird.feature.taskmail.internal.data.relay.RelayServerException
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommand
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommandAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot
import net.thunderbird.feature.taskmail.internal.domain.newtask.toCanonicalWireValue
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionRequest
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionResult
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionSender
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionType

private const val SESSION_ACTION_SCHEMA_VERSION = "post-creation-session-action-contract-v1"
private const val COMMAND_ID_PREFIX = "android-control:session-action:"
private const val REQUEST_ID_PREFIX = "req_"
private const val TRACE_ID_PREFIX = "trace_session_action_"
private const val CURRENT_SESSION_SCOPE = "current_session"
private const val ORIGIN_CLIENT = "android_taskmail"
private const val RESULT_OBSERVE_WINDOW_MILLIS = 3_000L
private const val DEFAULT_DIRECT_REJECTION_MESSAGE = "Control rejected direct session action request."
private val HARD_REJECTION_CODES = setOf(
    "invalid_payload",
    "validation_failed",
    "unauthorized",
    "session_identity_unresolved",
    "session_identity_mismatch",
    "current_session_only_violation",
    "paused_resume_not_supported",
    "answer_flow_not_supported",
)

internal class RelayControlTaskMailSessionActionSender(
    private val relayConnectionClient: RelayConnectionClient,
    private val timestampProvider: () -> String = ::currentUtcTimestamp,
    private val requestIdFactory: () -> String = ::nextRequestId,
    private val resultObserveWindowMillis: Long = RESULT_OBSERVE_WINDOW_MILLIS,
) : TaskMailDirectSessionActionSender {

    override suspend fun send(
        request: TaskMailDirectSessionActionRequest,
    ): TaskMailDirectSessionActionResult = coroutineScope {
        val requestId = requestIdFactory()
        val packetId = "$COMMAND_ID_PREFIX$requestId"
        val matchingResult = async {
            withTimeoutOrNull(resultObserveWindowMillis) {
                relayConnectionClient.serverResults
                    .filter { result -> result.matches(requestId = requestId, packetId = packetId) }
                    .firstOrNull()
            }
        }

        try {
            relayConnectionClient.sendCommand(
                command = buildCommand(
                    request = request,
                    requestId = requestId,
                    packetId = packetId,
                ),
            ).fold(
                onSuccess = { commandAck ->
                    commandAck.toDirectSessionActionResult(
                        actionType = request.actionType,
                        requestId = requestId,
                        observedResult = matchingResult.await(),
                    )
                },
                onFailure = { error ->
                    error.toDirectSessionActionResult(requestId = requestId)
                },
            )
        } finally {
            matchingResult.cancel()
        }
    }

    private fun buildCommand(
        request: TaskMailDirectSessionActionRequest,
        requestId: String,
        packetId: String,
    ): RelayCommand {
        return RelayCommand(
            requestId = requestId,
            packetId = packetId,
            commandType = request.actionType.wireValue,
            payloadSchema = SESSION_ACTION_SCHEMA_VERSION,
            trace = buildJsonObject {
                put("trace_id", "$TRACE_ID_PREFIX$requestId")
            },
            payload = buildJsonObject {
                put(
                    "origin",
                    buildJsonObject {
                        put("client", ORIGIN_CLIENT)
                    },
                )
                put(
                    "target",
                    buildJsonObject {
                        put("scope", CURRENT_SESSION_SCOPE)
                        request.target.workspaceId?.takeIf(String::isNotBlank)?.let { put("workspace_id", it) }
                        put("session_id", request.target.sessionId)
                        request.target.threadId?.takeIf(String::isNotBlank)?.let { put("thread_id", it) }
                    },
                )
                when (request) {
                    is TaskMailDirectSessionActionRequest.Reply -> {
                        put(
                            "reply",
                            buildJsonObject {
                                put("reply_text", request.replyText)
                                put("permission", request.permission.toCanonicalWireValue())
                            },
                        )
                    }
                    is TaskMailDirectSessionActionRequest.Status -> put("status", buildJsonObject {})
                    else -> error("Unsupported control-plane session action: ${request.actionType.wireValue}")
                }
            },
            related = buildJsonObject {
                put("ui_surface", "task_session_detail")
            },
            sentAt = timestampProvider(),
        )
    }

    private companion object {
        fun currentUtcTimestamp(): String {
            return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
        }

        fun nextRequestId(): String {
            return REQUEST_ID_PREFIX + UUID.randomUUID()
                .toString()
                .replace("-", "")
        }
    }
}

private fun Throwable.toDirectSessionActionResult(
    requestId: String,
): TaskMailDirectSessionActionResult {
    return when (this) {
        is RelayServerException -> {
            if (code in HARD_REJECTION_CODES) {
                TaskMailDirectSessionActionResult.Rejected(
                    errorMessage = message,
                    errorCode = code,
                    requestId = requestId,
                )
            } else {
                TaskMailDirectSessionActionResult.FallbackToMail(
                    detailMessage = message,
                    requestId = requestId,
                )
            }
        }

        else -> TaskMailDirectSessionActionResult.FallbackToMail(
            detailMessage = message,
            requestId = requestId,
        )
    }
}

private fun RelayCommandAck.toDirectSessionActionResult(
    actionType: TaskMailDirectSessionActionType,
    requestId: String,
    observedResult: RelayResult?,
): TaskMailDirectSessionActionResult {
    val controlPlaneSnapshot = TaskSessionControlPlaneSnapshot(
        commandAck = toControlPlaneCommandAck(),
        result = observedResult?.toControlPlaneResult(),
    )
    return if (isAcceptedLike) {
        TaskMailDirectSessionActionResult.Accepted(
            actionType = actionType,
            requestId = requestId,
            receiptId = receiptId,
            transportMessageId = transportMessageId,
            controlPlaneSnapshot = controlPlaneSnapshot,
        )
    } else {
        val relayErrorCode = errorCode.normalizedRelayErrorCode()
        if (relayErrorCode in HARD_REJECTION_CODES) {
            TaskMailDirectSessionActionResult.Rejected(
                errorMessage = errorMessage?.takeIf(String::isNotBlank) ?: DEFAULT_DIRECT_REJECTION_MESSAGE,
                errorCode = relayErrorCode,
                requestId = requestId,
                receiptId = receiptId,
                transportMessageId = transportMessageId,
            )
        } else {
            TaskMailDirectSessionActionResult.FallbackToMail(
                detailMessage = errorMessage,
                requestId = requestId,
                receiptId = receiptId,
                transportMessageId = transportMessageId,
            )
        }
    }
}

private fun RelayResult.matches(
    requestId: String,
    packetId: String,
): Boolean {
    return this.requestId == requestId || this.packetId == packetId
}

private fun String?.normalizedRelayErrorCode(): String? {
    return this?.trim()
        ?.lowercase(Locale.US)
        ?.takeIf(String::isNotEmpty)
}
